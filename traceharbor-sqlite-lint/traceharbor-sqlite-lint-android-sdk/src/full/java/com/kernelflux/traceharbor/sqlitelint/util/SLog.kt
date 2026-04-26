package com.kernelflux.traceharbor.sqlitelint.util

import android.util.Log
import com.kernelflux.traceharbor.util.TraceHarborLog

class SLog {
    fun printLog(priority: Int, tag: String, msg: String) {
        try {
            when (priority) {
                Log.VERBOSE -> {
                    TraceHarborLog.v(tag, msg)
                    return
                }

                Log.DEBUG -> {
                    TraceHarborLog.d(tag, msg)
                    return
                }

                Log.INFO -> {
                    TraceHarborLog.i(tag, msg)
                    return
                }

                Log.WARN -> {
                    TraceHarborLog.w(tag, msg)
                    return
                }

                Log.ERROR, Log.ASSERT -> {
                    TraceHarborLog.e(tag, msg)
                    return
                }

                else -> {
                    TraceHarborLog.i(tag, msg)
                    return
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "printLog ex " + e.message)
        }
    }

    companion object {
        private const val TAG = "SQLiteLint.SLog"

        @Volatile
        private var mInstance: SLog? = null

        @JvmStatic
        fun getInstance(): SLog {
            if (mInstance == null) {
                synchronized(SLog::class.java) {
                    if (mInstance == null) {
                        mInstance = SLog()
                    }
                }
            }
            return mInstance!!
        }

        @JvmStatic
        external fun nativeSetLogger(logLevel: Int)

        @JvmStatic
        fun e(tag: String, format: String, vararg args: Any?) {
            getInstance().printLog(Log.ERROR, tag, String.format(format, *args))
        }

        @JvmStatic
        fun w(tag: String, format: String, vararg args: Any?) {
            getInstance().printLog(Log.WARN, tag, String.format(format, *args))
        }

        @JvmStatic
        fun i(tag: String, format: String, vararg args: Any?) {
            getInstance().printLog(Log.INFO, tag, String.format(format, *args))
        }

        @JvmStatic
        fun d(tag: String, format: String, vararg args: Any?) {
            getInstance().printLog(Log.DEBUG, tag, String.format(format, *args))
        }

        @JvmStatic
        fun v(tag: String, format: String, vararg args: Any?) {
            getInstance().printLog(Log.VERBOSE, tag, String.format(format, *args))
        }
    }
}
