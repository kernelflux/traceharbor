package com.kernelflux.traceharbor.trace.tracer

import android.os.Process
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
 * Detects "evil methods" — main-looper Message dispatches whose
 * wall-clock duration exceeds [TraceConfig.getEvilThresholdMs] (default
 * 700 ms). For each such dispatch, a snapshot of the [AppMethodBeat]
 * ring buffer is post-processed off the main thread on
 * [TraceHarborHandlerThread] into a trimmed call stack and reported as
 * a `NORMAL` issue (the framework's name for an evil method, not to be
 * confused with [Constants.Type.LAG_*] codes from neighbouring tracers).
 *
 * Hooked into the dispatcher pipeline via [LooperMonitor]; the dispatch
 * timestamps come from there. Java public surface preserved:
 *  - `EvilMethodTracer(TraceConfig)` constructor.
 *  - `modifyEvilThresholdMs(long)` runtime threshold tweak.
 *  - `ILooperListener` overrides (`isValid`, `onDispatchBegin`,
 *    `onDispatchEnd`).
 */
class EvilMethodTracer(private val config: TraceConfig) : Tracer(), ILooperListener {

    private var indexRecord: AppMethodBeat.IndexRecord? = null
    private var evilThresholdMs: Long = config.getEvilThresholdMs().toLong()
    private val isEvilMethodTraceEnable: Boolean = config.isEvilMethodTraceEnable()

    override fun onAlive() {
        super.onAlive()
        if (isEvilMethodTraceEnable) {
            LooperMonitor.register(this)
        }
    }

    override fun onDead() {
        super.onDead()
        if (isEvilMethodTraceEnable) {
            LooperMonitor.unregister(this)
        }
    }

    override fun isValid(): Boolean = true

    override fun onDispatchBegin(log: String) {
        indexRecord = AppMethodBeat.getInstance().maskIndex("EvilMethodTracer#dispatchBegin")
    }

    override fun onDispatchEnd(log: String, beginNs: Long, endNs: Long) {
        val dispatchCost = (endNs - beginNs) / Constants.TIME_MILLIS_TO_NANO
        try {
            if (dispatchCost >= evilThresholdMs) {
                val record = indexRecord ?: return
                val data = AppMethodBeat.getInstance().copyData(record)
                val scene = AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene()
                TraceHarborHandlerThread.getDefaultHandler().post(
                    AnalyseTask(isForeground(), scene, data, dispatchCost, endNs),
                )
            }
        } finally {
            indexRecord?.release()
        }
    }

    fun modifyEvilThresholdMs(evilThresholdMs: Long) {
        this.evilThresholdMs = evilThresholdMs
    }

    private class AnalyseTask(
        val isForeground: Boolean,
        val scene: String?,
        val data: LongArray,
        val cost: Long,
        val endMs: Long,
    ) : Runnable {

        fun analyse() {
            val processStat = Utils.getProcessPriority(Process.myPid())
            val stack = LinkedList<MethodItem>()
            if (data.isNotEmpty()) {
                TraceDataUtils.structuredDataToStack(data, stack, true, endMs)
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
                cost,
                TraceDataUtils.stackToString(stack, reportBuilder, logcatBuilder),
            )
            val stackKey = TraceDataUtils.getTreeKey(stack, stackCost)

            TraceHarborLog.w(
                TAG,
                "%s",
                printEvil(
                    scene,
                    processStat,
                    isForeground,
                    logcatBuilder,
                    stack.size.toLong(),
                    stackKey,
                    cost,
                ),
            )

            try {
                val plugin = TraceHarbor.with()
                    .getPluginByClass(TracePlugin::class.java) ?: return
                val jsonObject = JSONObject()
                DeviceUtil.getDeviceInfo(jsonObject, TraceHarbor.with().getApplication())

                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.NORMAL)
                jsonObject.put(SharePluginInfo.ISSUE_COST, stackCost)
                jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene)
                jsonObject.put(SharePluginInfo.ISSUE_TRACE_STACK, reportBuilder.toString())
                jsonObject.put(SharePluginInfo.ISSUE_STACK_KEY, stackKey)

                val issue = Issue()
                issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                issue.content = jsonObject
                plugin.onDetectIssue(issue)
            } catch (e: JSONException) {
                TraceHarborLog.e(TAG, "[JSONException error: %s", e)
            }
        }

        override fun run() {
            analyse()
        }

        private fun printEvil(
            scene: String?,
            processStat: IntArray,
            isForeground: Boolean,
            stack: StringBuilder,
            stackSize: Long,
            stackKey: String,
            allCost: Long,
        ): String {
            val print = StringBuilder()
            print.append(
                String.format(
                    "-\n>>>>>>>>>>>>>>>>>>>>> maybe happens Jankiness!(%sms) <<<<<<<<<<<<<<<<<<<<<\n",
                    allCost,
                ),
            )
            print.append("|* [Status]").append("\n")
            print.append("|*\t\tScene: ").append(scene).append("\n")
            print.append("|*\t\tForeground: ").append(isForeground).append("\n")
            print.append("|*\t\tPriority: ").append(processStat[0])
                .append("\tNice: ").append(processStat[1]).append("\n")
            print.append("|*\t\tis64BitRuntime: ").append(DeviceUtil.is64BitRuntime()).append("\n")
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

    private companion object {
        private const val TAG = "TraceHarbor.EvilMethodTracer"
    }
}
