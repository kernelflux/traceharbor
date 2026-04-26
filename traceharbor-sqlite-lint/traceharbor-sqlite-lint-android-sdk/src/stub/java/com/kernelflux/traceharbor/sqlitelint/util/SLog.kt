package com.kernelflux.traceharbor.sqlitelint.util

import android.util.Log
import com.kernelflux.traceharbor.util.TraceHarborLog

class SLog {
    fun printLog(priority: Int, tag: String, msg: String) {
        when (priority) {
            Log.VERBOSE -> TraceHarborLog.v(tag, msg)
            Log.DEBUG -> TraceHarborLog.d(tag, msg)
            Log.INFO -> TraceHarborLog.i(tag, msg)
            Log.WARN -> TraceHarborLog.w(tag, msg)
            Log.ERROR, Log.ASSERT -> TraceHarborLog.e(tag, msg)
            else -> TraceHarborLog.i(tag, msg)
        }
    }

    companion object {
        @Volatile
        private var mInstance: SLog? = null

        @JvmStatic
        fun getInstance(): SLog {
            val current = mInstance
            if (current != null) {
                return current
            }
            synchronized(SLog::class.java) {
                val synchronizedCurrent = mInstance
                if (synchronizedCurrent != null) {
                    return synchronizedCurrent
                }
                val created = SLog()
                mInstance = created
                return created
            }
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
