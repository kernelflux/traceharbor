package com.kernelflux.traceharbor.iocanary.core

import com.kernelflux.traceharbor.iocanary.config.IOConfig
import com.kernelflux.traceharbor.iocanary.util.IOCanaryUtil
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.ArrayList

class IOCanaryJniBridge private constructor() {
    private class JavaContext {
        @JvmField
        val stack: String = IOCanaryUtil.getThrowableStack(Throwable())

        @JvmField
        var threadName: String? = Thread.currentThread()?.name
    }

    private object DetectorType {
        const val MAIN_THREAD_IO = 0
        const val SMALL_BUFFER = 1
        const val REPEAT_READ = 2
    }

    private object ConfigKey {
        const val MAIN_THREAD_THRESHOLD = 0
        const val SMALL_BUFFER_THRESHOLD = 1
        const val REPEAT_READ_THRESHOLD = 2
    }

    companion object {
        private const val TAG = "TraceHarbor.IOCanaryJniBridge"

        private var sOnIssuePublishListener: OnJniIssuePublishListener? = null
        private var sIsTryInstall = false
        private var sIsLoadJniLib = false

        @JvmStatic
        fun install(config: IOConfig?, listener: OnJniIssuePublishListener?) {
            TraceHarborLog.v(TAG, "install sIsTryInstall:%b", sIsTryInstall)
            if (sIsTryInstall) {
                return
            }

            if (!loadJni()) {
                TraceHarborLog.e(TAG, "install loadJni failed")
                return
            }

            sOnIssuePublishListener = listener

            try {
                if (config != null) {
                    if (config.isDetectFileIOInMainThread()) {
                        enableDetector(DetectorType.MAIN_THREAD_IO)
                        setConfig(
                            ConfigKey.MAIN_THREAD_THRESHOLD,
                            config.getFileMainThreadTriggerThreshold() * 1000L,
                        )
                    }

                    if (config.isDetectFileIOBufferTooSmall()) {
                        enableDetector(DetectorType.SMALL_BUFFER)
                        setConfig(ConfigKey.SMALL_BUFFER_THRESHOLD, config.getFileBufferSmallThreshold().toLong())
                    }

                    if (config.isDetectFileIORepeatReadSameFile()) {
                        enableDetector(DetectorType.REPEAT_READ)
                        setConfig(ConfigKey.REPEAT_READ_THRESHOLD, config.getFileRepeatReadThreshold().toLong())
                    }
                }

                doHook()
                sIsTryInstall = true
            } catch (e: Error) {
                TraceHarborLog.printErrStackTrace(TAG, e, "call jni method error")
            }
        }

        @JvmStatic
        fun uninstall() {
            if (!sIsTryInstall) {
                return
            }

            doUnHook()
            sIsTryInstall = false
        }

        private fun loadJni(): Boolean {
            if (sIsLoadJniLib) {
                return true
            }

            try {
                System.loadLibrary("io-canary")
            } catch (e: Exception) {
                TraceHarborLog.e(TAG, "hook: e: %s", e.localizedMessage)
                sIsLoadJniLib = false
                return false
            }

            sIsLoadJniLib = true
            return true
        }

        @JvmStatic
        fun onIssuePublish(issues: ArrayList<IOIssue>?) {
            val listener = sOnIssuePublishListener ?: return
            listener.onIssuePublish(issues)
        }

        /**
         * Called from native code. Keep private + static JVM shape.
         */
        @JvmStatic
        private fun getJavaContext(): JavaContext? {
            try {
                return JavaContext()
            } catch (th: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, th, "get javacontext exception")
            }

            return null
        }

        @JvmStatic
        private external fun enableDetector(detectorType: Int)

        @JvmStatic
        private external fun setConfig(key: Int, `val`: Long)

        @JvmStatic
        private external fun doHook(): Boolean

        @JvmStatic
        private external fun doUnHook(): Boolean
    }
}
