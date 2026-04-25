package com.kernelflux.traceharbor.trace.util

import android.os.Looper
import com.kernelflux.traceharbor.util.DeviceUtil
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Pure static utility — wrapped in a `class private constructor()` with a
 * `companion object` so all callers keep using `Utils.getStack()`,
 * `Utils.getProcessPriority(pid)`, etc. unchanged.
 */
class Utils private constructor() {
    companion object {
        @JvmStatic
        fun getStack(): String {
            val trace = Throwable().stackTrace
            return getStack(trace)
        }

        @JvmStatic
        fun getStack(trace: Array<StackTraceElement>?): String =
            getStack(trace, "", -1)

        @JvmStatic
        fun getStack(trace: Array<StackTraceElement>?, preFixStr: String, limit: Int): String {
            if (trace == null || trace.size < 3) {
                return ""
            }
            val effectiveLimit = if (limit < 0) Int.MAX_VALUE else limit
            val t = StringBuilder(" \n")
            var i = 3
            while (i < trace.size - 3 && i < effectiveLimit) {
                t.append(preFixStr)
                t.append("at ")
                t.append(trace[i].className)
                t.append(":")
                t.append(trace[i].methodName)
                t.append("(").append(trace[i].lineNumber).append(")")
                t.append("\n")
                i++
            }
            return t.toString()
        }

        @JvmStatic
        fun getWholeStack(trace: Array<StackTraceElement>?, preFixStr: String): String {
            if (trace == null || trace.size < 3) {
                return ""
            }
            val t = StringBuilder(" \n")
            for (element in trace) {
                t.append(preFixStr)
                t.append("at ")
                t.append(element.className)
                t.append(":")
                t.append(element.methodName)
                t.append("(").append(element.lineNumber).append(")")
                t.append("\n")
            }
            return t.toString()
        }

        @JvmStatic
        fun getWholeStack(trace: Array<StackTraceElement>): String {
            val stackTrace = StringBuilder()
            for (element in trace) {
                stackTrace.append(element.toString()).append("\n")
            }
            return stackTrace.toString()
        }

        @JvmStatic
        fun getMainThreadJavaStackTrace(): String {
            val stackTrace = StringBuilder()
            for (element in Looper.getMainLooper().thread.stackTrace) {
                stackTrace.append(element.toString()).append("\n")
            }
            return stackTrace.toString()
        }

        @JvmStatic
        fun getJavaStackTrace(): String {
            val stackTrace = StringBuilder()
            for (element in Thread.currentThread().stackTrace) {
                stackTrace.append(element.toString()).append("\n")
            }
            return stackTrace.toString()
        }

        @JvmStatic
        fun isEmpty(str: String?): Boolean = str.isNullOrEmpty()

        @JvmStatic
        fun getProcessPriority(pid: Int): IntArray {
            val name = String.format("/proc/%s/stat", pid)
            var priority = Int.MIN_VALUE
            var nice = Int.MAX_VALUE
            try {
                val content = DeviceUtil.getStringFromFile(name).trim()
                val args = content.split(" ".toRegex()).toTypedArray()
                if (args.size >= 19) {
                    priority = args[17].trim().toInt()
                    nice = args[18].trim().toInt()
                }
            } catch (e: Exception) {
                return intArrayOf(priority, nice)
            }
            return intArrayOf(priority, nice)
        }

        @JvmStatic
        fun formatTime(timestamp: Long): String =
            SimpleDateFormat("[yy-MM-dd HH:mm:ss]").format(Date(timestamp))
    }
}
