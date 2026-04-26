package com.kernelflux.traceharbor.sqlitelint.util

import com.kernelflux.traceharbor.sqlitelint.SQLiteLint
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date

class SQLiteLintUtil private constructor() {
    companion object {
        private const val TAG = "SQLiteLint.SQLiteLintUtil"
        private const val DEFAULT_MAX_STACK_LAYER = 6

        const val YYYY_MM_DD_HH_mm = "yyyy-MM-dd HH:mm"

        @JvmStatic
        fun isNullOrNil(obj: String?): Boolean {
            return obj == null || obj.isEmpty()
        }

        @JvmStatic
        fun nullAsNil(obj: String?): String {
            return obj ?: ""
        }

        @JvmStatic
        fun extractDbName(dbPath: String?): String? {
            if (isNullOrNil(dbPath)) {
                return null
            }
            var dbName: String? = null
            val arr = dbPath!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (arr.isNotEmpty()) {
                dbName = arr[arr.size - 1]
            }
            return dbName
        }

        @JvmStatic
        fun getInt(string: String?, def: Int): Int {
            return try {
                if (string == null || string.isEmpty()) def else string.toInt()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                def
            }
        }

        @JvmStatic
        fun formatTime(format: String, timeMilliSecond: Long): String {
            return SimpleDateFormat(format).format(Date(timeMilliSecond))
        }

        @JvmStatic
        fun mkdirs(filePath: String) {
            val file = File(filePath)
            if (!file.exists()) {
                val parentFile = file.parentFile
                parentFile?.mkdirs()
            }
        }

        @JvmStatic
        fun stackTraceToString(arr: Array<StackTraceElement>?): String {
            if (arr == null) {
                return ""
            }
            val stacks = ArrayList<StackTraceElement>(arr.size)
            for (stack in arr) {
                val className = stack.className
                if (className.contains("com.kernelflux.traceharbor.sqlitelint")) {
                    continue
                }
                stacks.add(stack)
            }
            if (stacks.size > DEFAULT_MAX_STACK_LAYER && SQLiteLint.sPackageName != null) {
                val iterator = stacks.listIterator(stacks.size)
                while (iterator.hasPrevious()) {
                    val stack = iterator.previous()
                    val className = stack.className
                    if (!className.contains(SQLiteLint.sPackageName!!)) {
                        iterator.remove()
                    }
                    if (stacks.size <= DEFAULT_MAX_STACK_LAYER) {
                        break
                    }
                }
            }
            val sb = StringBuffer(stacks.size)
            for (stackTraceElement in stacks) {
                sb.append(stackTraceElement).append('\n')
            }
            return sb.toString()
        }

        @JvmStatic
        fun getThrowableStack(throwable: Throwable?): String {
            if (throwable == null) {
                return ""
            }
            return stackTraceToString(throwable.stackTrace)
        }

        @JvmStatic
        fun getThrowableStack(): String {
            return try {
                getThrowableStack(Throwable())
            } catch (e: Throwable) {
                SLog.e(TAG, "getThrowableStack ex %s", e.message)
                ""
            }
        }
    }
}
