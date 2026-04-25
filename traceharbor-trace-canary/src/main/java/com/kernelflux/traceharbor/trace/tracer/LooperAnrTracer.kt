package com.kernelflux.traceharbor.trace.tracer

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.kernelflux.traceharbor.AppActiveTraceHarborDelegate
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.trace.TracePlugin
import com.kernelflux.traceharbor.trace.config.SharePluginInfo
import com.kernelflux.traceharbor.trace.config.TraceConfig
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.core.AppMethodBeat
import com.kernelflux.traceharbor.trace.core.LooperMonitor
import com.kernelflux.traceharbor.trace.items.MethodItem
import com.kernelflux.traceharbor.trace.listeners.ILooperListener
import com.kernelflux.traceharbor.trace.util.TraceDataUtils
import com.kernelflux.traceharbor.trace.util.Utils
import com.kernelflux.traceharbor.util.DeviceUtil
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.json.JSONException
import org.json.JSONObject
import java.util.LinkedList

/**
 * Tracks main-looper Message dispatches and posts two watchdog tasks
 * to the TraceHarbor handler thread when each dispatch begins:
 *   - [LagHandleTask] fires after [Constants.DEFAULT_NORMAL_LAG] ms
 *     and emits a `LAG` issue with the current main-thread Java stack.
 *   - [AnrHandleTask] fires after [Constants.DEFAULT_ANR] ms and emits
 *     an `ANR` issue with the post-processed [AppMethodBeat] call stack
 *     plus memory + thread-state snapshot.
 *
 * Both watchdogs are cancelled in `onDispatchEnd` if the dispatch
 * completes in time. Hooked via [LooperMonitor].
 */
class LooperAnrTracer(private val traceConfig: TraceConfig) : Tracer(), ILooperListener {

    private var anrHandler: Handler? = null
    private var lagHandler: Handler? = null
    private val anrTask = AnrHandleTask()
    private val lagTask = LagHandleTask()
    private val isAnrTraceEnable: Boolean = traceConfig.isAnrTraceEnable()

    override fun onAlive() {
        super.onAlive()
        if (isAnrTraceEnable) {
            LooperMonitor.register(this)
            anrHandler = Handler(TraceHarborHandlerThread.getDefaultHandler().looper)
            lagHandler = Handler(TraceHarborHandlerThread.getDefaultHandler().looper)
        }
    }

    override fun onDead() {
        super.onDead()
        if (isAnrTraceEnable) {
            LooperMonitor.unregister(this)
            anrTask.getBeginRecord()?.release()
            anrHandler?.removeCallbacksAndMessages(null)
            lagHandler?.removeCallbacksAndMessages(null)
        }
    }

    override fun isValid(): Boolean = true

    override fun onDispatchBegin(log: String) {
        anrTask.beginRecord = AppMethodBeat.getInstance().maskIndex("AnrTracer#dispatchBegin")

        if (traceConfig.isDevEnv()) {
            TraceHarborLog.v(TAG, "* [dispatchBegin] index:%s", anrTask.beginRecord?.index)
        }
        anrHandler?.postDelayed(anrTask, Constants.DEFAULT_ANR.toLong())
        lagHandler?.postDelayed(lagTask, Constants.DEFAULT_NORMAL_LAG.toLong())
    }

    override fun onDispatchEnd(log: String, beginNs: Long, endNs: Long) {
        if (traceConfig.isDevEnv()) {
            val cost = (endNs - beginNs) / Constants.TIME_MILLIS_TO_NANO
            TraceHarborLog.v(
                TAG,
                "[dispatchEnd] beginNs:%s endNs:%s cost:%sms",
                beginNs,
                endNs,
                cost,
            )
        }
        anrTask.getBeginRecord()?.release()
        anrHandler?.removeCallbacks(anrTask)
        lagHandler?.removeCallbacks(lagTask)
    }

    inner class LagHandleTask : Runnable {
        override fun run() {
            val scene = AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene()
            val isForeground = isForeground()
            try {
                val plugin = TraceHarbor.with()
                    .getPluginByClass(TracePlugin::class.java) ?: return

                val stackTrace = Looper.getMainLooper().thread.stackTrace
                val dumpStack = Utils.getWholeStack(stackTrace)

                val jsonObject = JSONObject()
                DeviceUtil.getDeviceInfo(jsonObject, TraceHarbor.with().application)
                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.LAG)
                jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene)
                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, dumpStack)
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, isForeground)

                val issue = Issue()
                issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                issue.content = jsonObject
                plugin.onDetectIssue(issue)
                TraceHarborLog.e(TAG, "happens lag : %s, scene : %s ", dumpStack, scene)
            } catch (e: JSONException) {
                TraceHarborLog.e(TAG, "[JSONException error: %s", e)
            }
        }
    }

    inner class AnrHandleTask : Runnable {

        @JvmField
        var beginRecord: AppMethodBeat.IndexRecord? = null

        @JvmField
        var token: Long = 0

        fun getBeginRecord(): AppMethodBeat.IndexRecord? = beginRecord

        constructor()

        constructor(record: AppMethodBeat.IndexRecord?, token: Long) {
            this.beginRecord = record
            this.token = token
        }

        override fun run() {
            val curTime = SystemClock.uptimeMillis()
            val isForeground = isForeground()
            val processStat = Utils.getProcessPriority(Process.myPid())
            val record = beginRecord ?: return
            val data = AppMethodBeat.getInstance().copyData(record)
            record.release()
            val scene = AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene()

            val memoryInfo = dumpMemory()

            val status = Looper.getMainLooper().thread.state
            val dumpStack: String = when (traceConfig.getLooperPrinterStackStyle()) {
                TraceConfig.STACK_STYLE_WHOLE ->
                    Utils.getWholeStack(Looper.getMainLooper().thread.stackTrace, "|*\t\t")
                TraceConfig.STACK_STYLE_RAW ->
                    Utils.getMainThreadJavaStackTrace()
                else -> // STACK_STYLE_SIMPLE + default
                    Utils.getStack(Looper.getMainLooper().thread.stackTrace, "|*\t\t", 12)
            }

            val stack = LinkedList<MethodItem>()
            if (data.isNotEmpty()) {
                TraceDataUtils.structuredDataToStack(data, stack, true, curTime)
                TraceDataUtils.trimStack(
                    stack,
                    Constants.TARGET_EVIL_METHOD_STACK,
                    object : TraceDataUtils.IStructuredDataFilter {
                        override fun isFilter(during: Long, filterCount: Int): Boolean =
                            during < filterCount.toLong() * Constants.TIME_UPDATE_CYCLE_MS

                        override fun getFilterMaxCount(): Int =
                            Constants.FILTER_STACK_MAX_COUNT

                        override fun fallback(stack: List<MethodItem>, size: Int) {
                            TraceHarborLog.w(
                                TAG,
                                "[fallback] size:%s targetSize:%s stack:%s",
                                size,
                                Constants.TARGET_EVIL_METHOD_STACK,
                                stack,
                            )
                            val it = (stack as MutableList<MethodItem>)
                                .listIterator(Math.min(size, Constants.TARGET_EVIL_METHOD_STACK))
                            while (it.hasNext()) {
                                it.next()
                                it.remove()
                            }
                        }
                    },
                )
            }

            val reportBuilder = StringBuilder()
            val logcatBuilder = StringBuilder()
            val stackCost = Math.max(
                Constants.DEFAULT_ANR.toLong(),
                TraceDataUtils.stackToString(stack, reportBuilder, logcatBuilder),
            )

            val stackKey = TraceDataUtils.getTreeKey(stack, stackCost)
            TraceHarborLog.w(
                TAG,
                "%s \npostTime:%s curTime:%s",
                printAnr(
                    scene,
                    processStat,
                    memoryInfo,
                    status,
                    logcatBuilder,
                    isForeground,
                    stack.size.toLong(),
                    stackKey,
                    dumpStack,
                    stackCost,
                ),
                token / Constants.TIME_MILLIS_TO_NANO,
                curTime,
            )

            if (stackCost >= Constants.DEFAULT_ANR_INVALID) {
                TraceHarborLog.w(
                    TAG,
                    "The checked anr task was not executed on time. " +
                        "The possible reason is that the current process has a low priority. just pass this report",
                )
                return
            }
            try {
                val plugin = TraceHarbor.with()
                    .getPluginByClass(TracePlugin::class.java) ?: return
                val jsonObject = JSONObject()
                DeviceUtil.getDeviceInfo(jsonObject, TraceHarbor.with().application)
                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.ANR)
                jsonObject.put(SharePluginInfo.ISSUE_COST, stackCost)
                jsonObject.put(SharePluginInfo.ISSUE_STACK_KEY, stackKey)
                jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene)
                jsonObject.put(SharePluginInfo.ISSUE_TRACE_STACK, reportBuilder.toString())
                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, dumpStack)
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_PRIORITY, processStat[0])
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_NICE, processStat[1])
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, isForeground)

                val memJsonObject = JSONObject()
                memJsonObject.put(SharePluginInfo.ISSUE_MEMORY_DALVIK, memoryInfo[0])
                memJsonObject.put(SharePluginInfo.ISSUE_MEMORY_NATIVE, memoryInfo[1])
                memJsonObject.put(SharePluginInfo.ISSUE_MEMORY_VM_SIZE, memoryInfo[2])
                jsonObject.put(SharePluginInfo.ISSUE_MEMORY, memJsonObject)

                val issue = Issue()
                issue.key = token.toString()
                issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                issue.content = jsonObject
                plugin.onDetectIssue(issue)
            } catch (e: JSONException) {
                TraceHarborLog.e(TAG, "[JSONException error: %s", e)
            }
        }

        private fun printAnr(
            scene: String?,
            processStat: IntArray,
            memoryInfo: LongArray,
            state: Thread.State,
            stack: StringBuilder,
            isForeground: Boolean,
            stackSize: Long,
            stackKey: String,
            dumpStack: String,
            stackCost: Long,
        ): String {
            val print = StringBuilder()
            print.append(
                String.format(
                    "-\n>>>>>>>>>>>>>>>>>>>>>>> maybe happens ANR(%s ms)! <<<<<<<<<<<<<<<<<<<<<<<\n",
                    stackCost,
                ),
            )
            print.append("|* [Status]").append("\n")
            print.append("|*\t\tScene: ").append(scene).append("\n")
            print.append("|*\t\tForeground: ").append(isForeground).append("\n")
            print.append("|*\t\tPriority: ").append(processStat[0])
                .append("\tNice: ").append(processStat[1]).append("\n")
            print.append("|*\t\tis64BitRuntime: ").append(DeviceUtil.is64BitRuntime()).append("\n")

            print.append("|* [Memory]").append("\n")
            print.append("|*\t\tDalvikHeap: ").append(memoryInfo[0]).append("kb\n")
            print.append("|*\t\tNativeHeap: ").append(memoryInfo[1]).append("kb\n")
            print.append("|*\t\tVmSize: ").append(memoryInfo[2]).append("kb\n")
            print.append("|* [Thread]").append("\n")
            print.append(String.format("|*\t\tStack(%s): ", state)).append(dumpStack)
            print.append("|* [Trace]").append("\n")
            if (stackSize > 0) {
                print.append("|*\t\tStackKey: ").append(stackKey).append("\n")
                print.append(stack.toString())
            } else {
                print.append(
                    String.format(
                        "AppMethodBeat is close[%s].",
                        AppMethodBeat.getInstance().isAlive(),
                    ),
                ).append("\n")
            }
            print.append("=========================================================================")
            return print.toString()
        }
    }

    /**
     * Unused — kept for parity with the Java original. Builds a
     * human-readable ANR report for synthetic input-expired ANRs.
     */
    @Suppress("unused")
    private fun printInputExpired(inputCost: Long): String {
        val print = StringBuilder()
        val scene = AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene()
        val isForeground = isForeground()
        val memoryInfo = dumpMemory()
        val processStat = Utils.getProcessPriority(Process.myPid())
        print.append(
            String.format(
                "-\n>>>>>>>>>>>>>>>>>>>>>>> maybe happens Input ANR(%s ms)! <<<<<<<<<<<<<<<<<<<<<<<\n",
                inputCost,
            ),
        )
        print.append("|* [Status]").append("\n")
        print.append("|*\t\tScene: ").append(scene).append("\n")
        print.append("|*\t\tForeground: ").append(isForeground).append("\n")
        print.append("|*\t\tPriority: ").append(processStat[0])
            .append("\tNice: ").append(processStat[1]).append("\n")
        print.append("|*\t\tis64BitRuntime: ").append(DeviceUtil.is64BitRuntime()).append("\n")
        print.append("|* [Memory]").append("\n")
        print.append("|*\t\tDalvikHeap: ").append(memoryInfo[0]).append("kb\n")
        print.append("|*\t\tNativeHeap: ").append(memoryInfo[1]).append("kb\n")
        print.append("|*\t\tVmSize: ").append(memoryInfo[2]).append("kb\n")
        print.append("=========================================================================")
        return print.toString()
    }

    private fun dumpMemory(): LongArray = longArrayOf(
        DeviceUtil.getDalvikHeap().toLong(),
        DeviceUtil.getNativeHeap().toLong(),
        DeviceUtil.getVmSize().toLong(),
    )

    private companion object {
        private const val TAG = "TraceHarbor.AnrTracer"
    }
}
