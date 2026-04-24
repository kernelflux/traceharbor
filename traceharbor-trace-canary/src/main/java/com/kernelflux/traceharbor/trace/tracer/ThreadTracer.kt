package com.kernelflux.traceharbor.trace.tracer

import androidx.annotation.Keep
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.trace.TracePlugin
import com.kernelflux.traceharbor.trace.config.SharePluginInfo
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.util.Utils
import com.kernelflux.traceharbor.util.DeviceUtil
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.json.JSONObject

/**
 * Native-driven tracer for two distinct conditions:
 *  1. main-thread priority / timerSlack mutations
 *  2. `pthread_key_create` / `pthread_key_delete` audit
 *
 * Both signals are surfaced from libtrace-canary.so via four `@Keep`d
 * static JNI upcalls. Their names + `(I I)V`, `(J)V`, `(I I I L L)V`
 * signatures must remain stable.
 *
 * Public API kept byte-for-byte:
 *  - Two nested interfaces (`MainThreadPriorityModifiedListener`,
 *    `PthreadKeyCallback`) preserved verbatim.
 *  - Instance setters (`setMainThreadPriorityModifiedListener`,
 *    `setPthreadKeyCallback`) preserved.
 *  - Static `getPthreadKeySeq()` exposed via `@JvmStatic` on the
 *    companion object.
 */
class ThreadTracer : Tracer() {

    override fun onAlive() {
        super.onAlive()
        if (enableThreadPriorityTracer || enablePthreadKeyTracer) {
            nativeInitThreadHook(
                if (enableThreadPriorityTracer) 1 else 0,
                if (enablePthreadKeyTracer) 1 else 0,
            )
        }
    }

    override fun onDead() {
        super.onDead()
    }

    fun setMainThreadPriorityModifiedListener(
        mainThreadPriorityModifiedListener: MainThreadPriorityModifiedListener?,
    ) {
        enableThreadPriorityTracer = true
        sMainThreadPriorityModifiedListener = mainThreadPriorityModifiedListener
    }

    fun setPthreadKeyCallback(callback: PthreadKeyCallback?) {
        enablePthreadKeyTracer = true
        sPthreadKeyCallback = callback
    }

    interface MainThreadPriorityModifiedListener {
        fun onMainThreadPriorityModified(priorityBefore: Int, priorityAfter: Int)
        fun onMainThreadTimerSlackModified(timerSlack: Long)
    }

    interface PthreadKeyCallback {
        fun onPthreadCreate(ret: Int, keyIndex: Int, soPath: String, backtrace: String)
        fun onPthreadDelete(ret: Int, keyIndex: Int, soPath: String, backtrace: String)
    }

    companion object {
        private const val TAG = "ThreadPriorityTracer"

        @JvmStatic
        private var sMainThreadPriorityModifiedListener: MainThreadPriorityModifiedListener? = null

        @JvmStatic
        private var sPthreadKeyCallback: PthreadKeyCallback? = null

        @JvmStatic
        private var enableThreadPriorityTracer: Boolean = false

        @JvmStatic
        private var enablePthreadKeyTracer: Boolean = false

        init {
            System.loadLibrary("trace-canary")
        }

        @JvmStatic
        fun getPthreadKeySeq(): Int = nativeGetPthreadKeySeq()

        @JvmStatic
        private external fun nativeInitThreadHook(priority: Int, phreadKey: Int)

        @JvmStatic
        private external fun nativeGetPthreadKeySeq(): Int

        /**
         * JNI upcall — must stay `private static onMainThreadPriorityModified(II)V`
         * on this class.
         */
        @JvmStatic
        @Keep
        private fun onMainThreadPriorityModified(priorityBefore: Int, priorityAfter: Int) {
            sMainThreadPriorityModifiedListener?.let {
                it.onMainThreadPriorityModified(priorityBefore, priorityAfter)
                return
            }
            try {
                val plugin = TraceHarbor.with()
                    .getPluginByClass(TracePlugin::class.java) ?: return

                val stackTrace = Utils.getMainThreadJavaStackTrace()

                var jsonObject = JSONObject()
                jsonObject = DeviceUtil.getDeviceInfo(
                    jsonObject,
                    TraceHarbor.with().getApplication(),
                )
                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.PRIORITY_MODIFIED)
                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace)
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_PRIORITY, priorityAfter)

                val issue = Issue()
                issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                issue.content = jsonObject
                plugin.onDetectIssue(issue)
                TraceHarborLog.e(
                    TAG,
                    "happens MainThreadPriorityModified : %s ",
                    jsonObject.toString(),
                )
            } catch (t: Throwable) {
                TraceHarborLog.e(TAG, "onMainThreadPriorityModified error: %s", t.message)
            }
        }

        /**
         * JNI upcall — must stay `private static onMainThreadTimerSlackModified(J)V`
         * on this class.
         */
        @JvmStatic
        @Keep
        private fun onMainThreadTimerSlackModified(timerSlack: Long) {
            try {
                sMainThreadPriorityModifiedListener?.let {
                    it.onMainThreadTimerSlackModified(timerSlack)
                    return
                }

                val plugin = TraceHarbor.with()
                    .getPluginByClass(TracePlugin::class.java) ?: return

                val stackTrace = Utils.getMainThreadJavaStackTrace()

                var jsonObject = JSONObject()
                jsonObject = DeviceUtil.getDeviceInfo(
                    jsonObject,
                    TraceHarbor.with().getApplication(),
                )
                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.TIMERSLACK_MODIFIED)
                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace)
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_TIMER_SLACK, timerSlack)

                val issue = Issue()
                issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                issue.content = jsonObject
                plugin.onDetectIssue(issue)
                TraceHarborLog.e(
                    TAG,
                    "happens MainThreadPriorityModified : %s ",
                    jsonObject.toString(),
                )
            } catch (t: Throwable) {
                TraceHarborLog.e(TAG, "onMainThreadPriorityModified error: %s", t.message)
            }
        }

        /**
         * JNI upcall — must stay `private static pthreadKeyCallback(IIILjava/lang/String;Ljava/lang/String;)V`
         * on this class.
         */
        @JvmStatic
        @Keep
        private fun pthreadKeyCallback(
            type: Int,
            ret: Int,
            keySeq: Int,
            soPath: String,
            backtrace: String,
        ) {
            sPthreadKeyCallback?.let {
                if (type == 0) {
                    it.onPthreadCreate(ret, keySeq, soPath, backtrace)
                } else if (type == 1) {
                    it.onPthreadDelete(ret, keySeq, soPath, backtrace)
                }
            }
        }
    }
}
