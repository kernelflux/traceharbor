package com.kernelflux.traceharbor.trace.tracer

import androidx.annotation.Keep
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
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.json.JSONObject

/**
 * Native-driven tracer that fires when the input dispatcher takes longer
 * than [Constants.DEFAULT_TOUCH_EVENT_LAG] ms to consume an InputEvent.
 *
 * The two `@Keep`d static methods (`onTouchEventLagDumpTrace`,
 * `onTouchEventLag`) are JNI upcalls invoked from libtrace-canary.so —
 * they MUST stay `private static` on this class with their exact names
 * and `(int)V` signatures, hence the `@JvmStatic @Keep` placement on
 * the companion. Renaming them would break the native binding.
 */
class TouchEventLagTracer(config: TraceConfig) : Tracer() {

    init {
        traceConfig = config
    }

    @Synchronized
    override fun onAlive() {
        super.onAlive()
        traceConfig?.let {
            if (it.isTouchEventTraceEnable()) {
                nativeInitTouchEventLagDetective(it.touchEventLagThreshold)
            }
        }
    }

    override fun onDead() {
        super.onDead()
    }

    companion object {
        private const val TAG = "TraceHarbor.TouchEventLagTracer"

        @JvmStatic
        private var traceConfig: TraceConfig? = null

        @JvmStatic
        private var lastLagTime: Long = 0

        @JvmStatic
        private var currentLagFdStackTrace: String? = null

        init {
            System.loadLibrary("trace-canary")
        }

        @JvmStatic
        external fun nativeInitTouchEventLagDetective(lagThreshold: Int)

        /**
         * JNI upcall — must stay `private static` named
         * `onTouchEventLagDumpTrace` on this class.
         */
        @JvmStatic
        @Keep
        private fun onTouchEventLagDumpTrace(fd: Int) {
            TraceHarborLog.e(TAG, "onTouchEventLagDumpTrace, fd = $fd")
            currentLagFdStackTrace = Utils.getMainThreadJavaStackTrace()
        }

        /**
         * JNI upcall — must stay `private static` named `onTouchEventLag`
         * on this class.
         */
        @JvmStatic
        @Keep
        private fun onTouchEventLag(fd: Int) {
            TraceHarborLog.e(TAG, "onTouchEventLag, fd = $fd")
            TraceHarborHandlerThread.getDefaultHandler().post {
                try {
                    if (System.currentTimeMillis() - lastLagTime <
                        Constants.DEFAULT_TOUCH_EVENT_LAG * 2
                    ) {
                        return@post
                    }
                    TraceHarborLog.i(TAG, "onTouchEventLag report")

                    lastLagTime = System.currentTimeMillis()

                    val plugin = TraceHarbor.with()
                        .getPluginByClass(TracePlugin::class.java) ?: return@post

                    val stackTrace = currentLagFdStackTrace
                    val currentForeground = AppForegroundUtil.isInterestingToUser()
                    val scene = AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene()

                    var jsonObject = JSONObject()
                    jsonObject = DeviceUtil.getDeviceInfo(
                        jsonObject,
                        TraceHarbor.with().getApplication(),
                    )
                    jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.LAG_TOUCH)
                    jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene)
                    jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace)
                    jsonObject.put(
                        SharePluginInfo.ISSUE_PROCESS_FOREGROUND,
                        currentForeground,
                    )

                    val issue = Issue()
                    issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                    issue.content = jsonObject
                    plugin.onDetectIssue(issue)
                } catch (t: Throwable) {
                    TraceHarborLog.e(TAG, "TraceHarbor error, error = ${t.message}")
                }
            }
        }
    }
}
