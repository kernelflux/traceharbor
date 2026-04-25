package com.kernelflux.traceharbor.trace.tracer

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import com.kernelflux.traceharbor.AppActiveTraceHarborDelegate
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.trace.TracePlugin
import com.kernelflux.traceharbor.trace.config.SharePluginInfo
import com.kernelflux.traceharbor.trace.config.TraceConfig
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.util.AppForegroundUtil
import com.kernelflux.traceharbor.trace.util.Utils
import com.kernelflux.traceharbor.util.DeviceUtil
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Signal-based ANR detector. Hooks `SIGQUIT` (the signal Android sends
 * to the app when an ANR is detected) via JNI and, on receipt, dumps
 * the main-thread Java stack, parses the ANR trace file, runs a tiny
 * dead-lock detector over it, and emits an [Issue] (or fires the
 * caller-supplied [SignalAnrDetectedListener]).
 *
 * Three constructors are kept verbatim so existing call-sites and the
 * `traceharbor-trace-canary` sample app continue to work.
 */
class SignalAnrTracer : Tracer {

    constructor(traceConfig: TraceConfig) {
        hasInstance = true
        sAnrTraceFilePath = traceConfig.anrTraceFilePath
        sPrintTraceFilePath = traceConfig.printTraceFilePath
    }

    constructor(application: Application) {
        hasInstance = true
        sApplication = application
    }

    constructor(
        application: Application,
        anrTraceFilePath: String,
        printTraceFilePath: String,
    ) {
        hasInstance = true
        sAnrTraceFilePath = anrTraceFilePath
        sPrintTraceFilePath = printTraceFilePath
        sApplication = application
    }

    override fun onAlive() {
        super.onAlive()
        if (!hasInit) {
            nativeInitSignalAnrDetective(sAnrTraceFilePath, sPrintTraceFilePath)
            AppForegroundUtil.INSTANCE.init()
            hasInit = true
        }
    }

    override fun onDead() {
        super.onDead()
        nativeFreeSignalAnrDetective()
    }

    fun setSignalAnrDetectedListener(listener: SignalAnrDetectedListener?) {
        sSignalAnrDetectedListener = listener
    }

    /**
     * Tiny ANR-trace parser + dead-lock detector. Reads each line of
     * the SIGQUIT-dumped trace file once, keying threads by their
     * thin-lock id and edges by `held by thread N`, then runs a DFS
     * over the wait-for graph.
     *
     * Public methods called from inside [onANRDumpTrace] are kept on
     * this private nested class; the inner [Pair] only exists because
     * `android.util.Pair` doesn't implement `Map.Entry`.
     */
    private class SimpleDeadLockDetector {
        private class ThreadNode {
            @JvmField var threadId: Int = 0

            @JvmField var info: String = ""

            @JvmField var lockObjCls: String? = null

            @JvmField var peerId: Int = -1

            // 0 not visited, 1 visiting, 2 visited
            @JvmField var visit: Int = 0
        }

        private val threadPattern: Pattern =
            Pattern.compile("^\"(.*?)\" .*? tid=(\\d+) \\w+$")
        private val lockHeldPattern: Pattern =
            Pattern.compile("^  - .*?\\(a (.*?)\\) held by thread (\\d+)$")
        private val currentSb: StringBuilder = StringBuilder()
        private val threadsWaitingForHeldLock: HashMap<Int, ThreadNode> = HashMap()
        private val waitingList: LinkedList<ThreadNode> = LinkedList()
        private var mainThreadInfo: String = ""
        private var threadInfoBegin: Boolean = false
        private var currentThreadInfo: ThreadNode = ThreadNode()

        fun parseLine(line: String) {
            if (line.isEmpty()) {
                threadInfoBegin = false

                if (currentSb.isNotEmpty() && currentThreadInfo.peerId >= 0) {
                    val threadInfo = currentSb.toString()
                    if (currentThreadInfo.threadId == 1) {
                        // "currentThreadId" is a thin-lock thread id, a small int
                        // used by ART's thin-lock implementation. NOT the native
                        // tid and NOT java.lang.Thread.getId(). Convention: 0 =
                        // invalid, 1 = main thread.
                        mainThreadInfo = threadInfo
                    }

                    currentThreadInfo.info = threadInfo
                    threadsWaitingForHeldLock[currentThreadInfo.threadId] = currentThreadInfo
                    currentThreadInfo = ThreadNode()
                }
            } else if (!threadInfoBegin) {
                val m = threadPattern.matcher(line)
                if (m.find()) {
                    threadInfoBegin = true

                    currentSb.setLength(0)
                    currentSb.append(line).append('\n')
                    try {
                        currentThreadInfo.threadId = m.group(2)!!.toInt()
                    } catch (e: Exception) {
                        TraceHarborLog.e(TAG, e.toString())
                    }
                }
            } else {
                val m = lockHeldPattern.matcher(line)
                if (m.find()) {
                    try {
                        currentThreadInfo.lockObjCls = m.group(1)
                        currentThreadInfo.peerId = m.group(2)!!.toInt()
                    } catch (e: Exception) {
                        TraceHarborLog.e(TAG, e.toString())
                    }
                }
                currentSb.append(line).append('\n')
            }
        }

        fun hasDeadLock(): Boolean {
            // Flush whatever's in the parser's buffer.
            parseLine("")
            return checkDeadLock()
        }

        fun getMainThreadInfo(): String = mainThreadInfo

        fun getLockHeldThread1Info(): String {
            if (waitingList.isEmpty()) {
                return ""
            }
            val threadId = waitingList[0].threadId
            val node = threadsWaitingForHeldLock[threadId]
            return node?.info ?: ""
        }

        fun getLockHeldThread2Info(): String {
            if (waitingList.isEmpty()) {
                return ""
            }
            val threadId = waitingList[waitingList.size - 1].threadId
            val node = threadsWaitingForHeldLock[threadId]
            return node?.info ?: ""
        }

        /**
         * Local Map.Entry impl — `android.util.Pair` doesn't implement
         * `Map.Entry`, and the public listener API exposes the result
         * type as `Map.Entry<int[], String[]>`. Kept inside the
         * detector since nothing outside cares about its exact type.
         */
        private class Pair<F, S>(
            @JvmField var f: F?,
            @JvmField var s: S?,
        ) : MutableMap.MutableEntry<F?, S?> {
            override val key: F? get() = f
            override val value: S? get() = s
            override fun setValue(newValue: S?): S? {
                s = newValue
                return s
            }

            override fun toString(): String = "Pair{f=$f, s=$s}"
        }

        fun getWaitingThreadsInfo(): MutableMap.MutableEntry<IntArray?, Array<String?>?> {
            return if (waitingList.isEmpty()) {
                Pair(null, null)
            } else {
                val threadsId = IntArray(waitingList.size)
                val locksType = arrayOfNulls<String>(waitingList.size)
                var idx = 0
                for (threadNode in waitingList) {
                    threadsId[idx] = threadNode.threadId
                    locksType[idx] = threadNode.lockObjCls
                    ++idx
                }
                Pair(threadsId, locksType)
            }
        }

        private fun checkDeadLock(): Boolean {
            waitingList.clear()
            for (nodeEntry in threadsWaitingForHeldLock.entries) {
                val node = nodeEntry.value
                if (node.visit == 0) {
                    val ret = dfsSearch(node)
                    if (ret != null) {
                        // retrieve cycle from path and save it in waitingList
                        while (waitingList.isNotEmpty() && waitingList.first !== ret) {
                            waitingList.removeFirst()
                        }
                        return true
                    }
                }
            }
            return false
        }

        // Returns the entry point if a cycle is found, else null.
        private fun dfsSearch(node: ThreadNode): ThreadNode? {
            waitingList.addLast(node)
            node.visit = 1

            val peerNode = threadsWaitingForHeldLock[node.peerId]
            if (peerNode != null) {
                if (peerNode.visit == 1) {
                    return peerNode
                }

                if (peerNode.visit == 0) {
                    val ret = dfsSearch(peerNode)
                    if (ret != null) {
                        return ret
                    }
                }
            }

            node.visit = 2
            waitingList.removeLast()
            return null
        }
    }

    /**
     * Caller-supplied callback fired from the JNI signal-handler
     * thread once an ANR has been confirmed. Multi-method, so kept
     * as a regular `interface` (NOT `fun interface`).
     */
    interface SignalAnrDetectedListener {
        fun onAnrDetected(
            stackTrace: String,
            mMessageString: String,
            mMessageWhen: Long,
            fromProcessErrorState: Boolean,
            cpuset: String,
        )

        fun onNativeBacktraceDetected(
            backtrace: String,
            mMessageString: String,
            mMessageWhen: Long,
            fromProcessErrorState: Boolean,
        )

        fun onDeadLockAnrDetected(
            mainThreadStackTrace: String,
            lockHeldThread1: String,
            lockHeldThread2: String,
            waitingList: MutableMap.MutableEntry<IntArray?, Array<String?>?>,
        )

        fun onMainThreadStuckAtNativePollOnce(mainThreadStackTrace: String)
    }

    companion object {
        private const val TAG = "SignalAnrTracer"

        private const val CHECK_ANR_STATE_THREAD_NAME = "Check-ANR-State-Thread"
        private const val ANR_DUMP_THREAD_NAME = "ANR-Dump-Thread"
        private const val CHECK_ERROR_STATE_INTERVAL = 500
        private const val ANR_DUMP_MAX_TIME = 20000

        @JvmStatic
        private var anrReportTimeout: Long = ANR_DUMP_MAX_TIME.toLong()

        private const val CHECK_ERROR_STATE_COUNT: Int =
            ANR_DUMP_MAX_TIME / CHECK_ERROR_STATE_INTERVAL
        private const val FOREGROUND_MSG_THRESHOLD: Long = -2000L
        private const val BACKGROUND_MSG_THRESHOLD: Long = -10000L

        @JvmStatic private var currentForeground: Boolean = false
        @JvmStatic private var sAnrTraceFilePath: String = ""
        @JvmStatic private var sPrintTraceFilePath: String = ""

        @JvmStatic
        private var sSignalAnrDetectedListener: SignalAnrDetectedListener? = null

        @JvmStatic private var sApplication: Application? = null
        @JvmStatic private var hasInit: Boolean = false

        /**
         * Read by `TracePlugin.kt` to gate the second-construction-
         * forbidden warning. Must remain a public mutable Boolean
         * static field — `@JvmField var` preserves direct field
         * access for any future Java caller and lets Kotlin callers
         * use `SignalAnrTracer.hasInstance` directly.
         */
        @JvmField
        var hasInstance: Boolean = false

        @JvmStatic private var anrMessageWhen: Long = 0L
        @JvmStatic private var anrMessageString: String = ""
        @JvmStatic private var cgroup: String = ""
        @JvmStatic private var stackTrace: String = ""
        @JvmStatic private var nativeBacktraceStackTrace: String = ""
        @JvmStatic private var lastReportedTimeStamp: Long = 0
        @JvmStatic private var onAnrDumpedTimeStamp: Long = 0

        init {
            System.loadLibrary("trace-canary")
        }

        @JvmStatic
        fun setAnrReportTimeout(timeout: Long) {
            anrReportTimeout = timeout
        }

        @JvmStatic
        fun readCgroup(): String {
            val ret = StringBuilder()
            try {
                BufferedReader(
                    InputStreamReader(FileInputStream("/proc/self/cgroup")),
                ).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        ret.append(line).append("\n")
                        line = reader.readLine()
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            return ret.toString()
        }

        @JvmStatic
        @RequiresApi(api = Build.VERSION_CODES.M)
        private fun confirmRealAnr(isSigQuit: Boolean) {
            TraceHarborLog.i(TAG, "confirmRealAnr, isSigQuit = $isSigQuit")
            val needReport = isMainThreadBlocked()
            if (needReport) {
                report(false, isSigQuit)
            } else {
                Thread(
                    Runnable { checkErrorStateCycle(isSigQuit) },
                    CHECK_ANR_STATE_THREAD_NAME,
                ).start()
            }
        }

        @JvmStatic
        @Keep
        @Synchronized
        @RequiresApi(api = Build.VERSION_CODES.M)
        private fun onANRDumped() {
            val anrDumpLatch = CountDownLatch(1)
            Thread(
                Runnable {
                    onAnrDumpedTimeStamp = System.currentTimeMillis()
                    TraceHarborLog.i(TAG, "onANRDumped")
                    stackTrace = Utils.getMainThreadJavaStackTrace()
                    TraceHarborLog.i(
                        TAG,
                        "onANRDumped, stackTrace = %s, duration = %d",
                        stackTrace,
                        System.currentTimeMillis() - onAnrDumpedTimeStamp,
                    )
                    cgroup = readCgroup()
                    TraceHarborLog.i(
                        TAG,
                        "onANRDumped, read cgroup duration = %d",
                        System.currentTimeMillis() - onAnrDumpedTimeStamp,
                    )
                    currentForeground = AppForegroundUtil.isInterestingToUser()
                    TraceHarborLog.i(
                        TAG,
                        "onANRDumped, isInterestingToUser duration = %d",
                        System.currentTimeMillis() - onAnrDumpedTimeStamp,
                    )
                    confirmRealAnr(true)
                    anrDumpLatch.countDown()
                },
                ANR_DUMP_THREAD_NAME,
            ).start()

            try {
                anrDumpLatch.await(anrReportTimeout, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                // empty here
            }
        }

        @JvmStatic
        @Keep
        private fun onANRDumpTrace() {
            try {
                try {
                    BufferedReader(
                        InputStreamReader(
                            FileInputStream(File(sAnrTraceFilePath)),
                            "UTF-8",
                        ),
                    ).use { reader ->
                        val detector = SimpleDeadLockDetector()
                        var line: String? = reader.readLine()
                        while (line != null) {
                            detector.parseLine(line)
                            TraceHarborLog.i(TAG, line)
                            line = reader.readLine()
                        }
                        val listener = sSignalAnrDetectedListener
                        if (listener != null) {
                            if (detector.hasDeadLock()) {
                                listener.onDeadLockAnrDetected(
                                    detector.getMainThreadInfo(),
                                    detector.getLockHeldThread1Info(),
                                    detector.getLockHeldThread2Info(),
                                    detector.getWaitingThreadsInfo(),
                                )
                            } else if (detector.getMainThreadInfo()
                                    .contains("android.os.MessageQueue.nativePollOnce")
                            ) {
                                listener.onMainThreadStuckAtNativePollOnce(
                                    detector.getMainThreadInfo(),
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    TraceHarborLog.e(
                        TAG,
                        "printFileByLine failed e : " + t.message,
                    )
                }
            } catch (t: Throwable) {
                TraceHarborLog.e(TAG, "onANRDumpTrace error: %s", t.message)
            }
        }

        @JvmStatic
        @Keep
        private fun onPrintTrace() {
            try {
                TraceHarborUtil.printFileByLine(TAG, sPrintTraceFilePath)
            } catch (t: Throwable) {
                TraceHarborLog.e(TAG, "onPrintTrace error: %s", t.message)
            }
        }

        @JvmStatic
        @Keep
        @RequiresApi(api = Build.VERSION_CODES.M)
        private fun onNativeBacktraceDumped() {
            TraceHarborLog.i(TAG, "happens onNativeBacktraceDumped")
            if (System.currentTimeMillis() - lastReportedTimeStamp < ANR_DUMP_MAX_TIME) {
                TraceHarborLog.i(TAG, "report SIGQUIT recently, just return")
                return
            }
            nativeBacktraceStackTrace = Utils.getMainThreadJavaStackTrace()
            TraceHarborLog.i(
                TAG,
                "happens onNativeBacktraceDumped, mainThreadStackTrace = $stackTrace",
            )

            confirmRealAnr(false)
        }

        @JvmStatic
        private fun report(fromProcessErrorState: Boolean, isSigQuit: Boolean) {
            try {
                val listener = sSignalAnrDetectedListener
                if (listener != null) {
                    if (isSigQuit) {
                        listener.onAnrDetected(
                            stackTrace,
                            anrMessageString,
                            anrMessageWhen,
                            fromProcessErrorState,
                            cgroup,
                        )
                    } else {
                        listener.onNativeBacktraceDetected(
                            nativeBacktraceStackTrace,
                            anrMessageString,
                            anrMessageWhen,
                            fromProcessErrorState,
                        )
                    }
                    return
                }

                val plugin: TracePlugin? =
                    TraceHarbor.with().getPluginByClass(TracePlugin::class.java)
                if (null == plugin) {
                    return
                }

                val scene = AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene()

                var jsonObject = JSONObject()
                jsonObject = DeviceUtil.getDeviceInfo(jsonObject, TraceHarbor.with().application)
                if (isSigQuit) {
                    jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.SIGNAL_ANR)
                    jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace)
                } else {
                    jsonObject.put(
                        SharePluginInfo.ISSUE_STACK_TYPE,
                        Constants.Type.SIGNAL_ANR_NATIVE_BACKTRACE,
                    )
                    jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, nativeBacktraceStackTrace)
                }
                jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene)
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, currentForeground)

                val issue = Issue()
                issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                issue.content = jsonObject
                plugin.onDetectIssue(issue)
                TraceHarborLog.e(TAG, "happens real ANR : %s ", jsonObject.toString())
            } catch (e: JSONException) {
                TraceHarborLog.e(TAG, "[JSONException error: %s", e)
            } finally {
                lastReportedTimeStamp = System.currentTimeMillis()
            }
        }

        @JvmStatic
        @RequiresApi(api = Build.VERSION_CODES.M)
        private fun isMainThreadBlocked(): Boolean {
            try {
                val mainQueue = Looper.getMainLooper().queue
                val field = mainQueue.javaClass.getDeclaredField("mMessages")
                field.isAccessible = true
                val mMessage = field.get(mainQueue) as Message?
                if (mMessage != null) {
                    anrMessageString = mMessage.toString()
                    TraceHarborLog.i(TAG, "anrMessageString = $anrMessageString")
                    val whenAt = mMessage.`when`
                    if (whenAt == 0L) {
                        return false
                    }
                    val time = whenAt - SystemClock.uptimeMillis()
                    anrMessageWhen = time
                    var timeThreshold = BACKGROUND_MSG_THRESHOLD
                    if (currentForeground) {
                        timeThreshold = FOREGROUND_MSG_THRESHOLD
                    }
                    return time < timeThreshold
                } else {
                    TraceHarborLog.i(TAG, "mMessage is null")
                }
            } catch (e: Exception) {
                return false
            }
            return false
        }

        @JvmStatic
        private fun checkErrorStateCycle(isSigQuit: Boolean) {
            var checkErrorStateCount = 0
            while (checkErrorStateCount < CHECK_ERROR_STATE_COUNT) {
                try {
                    checkErrorStateCount++
                    val myAnr = checkErrorState()
                    if (myAnr) {
                        report(true, isSigQuit)
                        break
                    }

                    Thread.sleep(CHECK_ERROR_STATE_INTERVAL.toLong())
                } catch (t: Throwable) {
                    TraceHarborLog.e(
                        TAG,
                        "checkErrorStateCycle error, e : " + t.message,
                    )
                    break
                }
            }
        }

        @JvmStatic
        private fun checkErrorState(): Boolean {
            try {
                TraceHarborLog.i(TAG, "[checkErrorState] start")
                val application: Application? = sApplication ?: TraceHarbor.with().application
                if (application == null) {
                    TraceHarborLog.i(TAG, "[checkErrorState] application == null")
                    return false
                }
                val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

                val procs: List<ActivityManager.ProcessErrorStateInfo>? =
                    am.processesInErrorState
                if (procs == null) {
                    TraceHarborLog.i(TAG, "[checkErrorState] procs == null")
                    return false
                }

                for (proc in procs) {
                    TraceHarborLog.i(
                        TAG,
                        "[checkErrorState] found Error State proccessName = %s, proc.condition = %d",
                        proc.processName,
                        proc.condition,
                    )

                    if (proc.uid != android.os.Process.myUid() &&
                        proc.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING
                    ) {
                        TraceHarborLog.i(TAG, "maybe received other apps ANR signal")
                        return false
                    }

                    if (proc.pid != android.os.Process.myPid()) continue

                    if (proc.condition != ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING) {
                        continue
                    }

                    TraceHarborLog.i(TAG, "error sate longMsg = %s", proc.longMsg)

                    return true
                }
                return false
            } catch (t: Throwable) {
                TraceHarborLog.e(TAG, "[checkErrorState] error : %s", t.message)
            }
            return false
        }

        @JvmStatic
        fun printTrace() {
            if (!hasInstance) {
                TraceHarborLog.e(TAG, "SignalAnrTracer has not been initialize")
                return
            }
            if (sPrintTraceFilePath == "") {
                TraceHarborLog.e(TAG, "PrintTraceFilePath has not been set")
                return
            }
            nativePrintTrace()
        }

        @JvmStatic
        private external fun nativeInitSignalAnrDetective(
            anrPrintTraceFilePath: String,
            printTraceFilePath: String,
        )

        @JvmStatic
        private external fun nativeFreeSignalAnrDetective()

        @JvmStatic
        private external fun nativePrintTrace()
    }
}
