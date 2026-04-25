package com.kernelflux.traceharbor.javalib.util

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Tiny stdout-backed logging facade — Java callers indirect through `Log.x(tag, msg, args...)`
 * static helpers, which then forward to a swappable [LogImp].
 *
 * Java callers depended on these exact static signatures:
 *   Log.v / d / i / w / e (String tag, String msg, Object... obj)
 *   Log.printErrStackTrace(String tag, Throwable tr, String format, Object... obj)
 *   Log.setLogImp(LogImp)
 *   Log.getImpl()
 *   Log.setLogLevel(String)
 *   Log.LOG_LEVEL_VERBOSE / DEBUG / INFO / WARN / ERROR
 * All are kept binary-compatible via `@JvmStatic` + companion `const val`.
 */
class Log private constructor() {

    /**
     * Pluggable implementation. Java callers implemented this interface directly,
     * so it stays a regular interface rather than collapsing to a fun-type.
     */
    interface LogImp {
        fun v(tag: String, msg: String, vararg obj: Any?)
        fun i(tag: String, msg: String, vararg obj: Any?)
        fun w(tag: String, msg: String, vararg obj: Any?)
        fun d(tag: String, msg: String, vararg obj: Any?)
        fun e(tag: String, msg: String, vararg obj: Any?)
        fun printErrStackTrace(tag: String, tr: Throwable, format: String?, vararg obj: Any?)
        fun setLogLevel(logLevel: Int)
    }

    companion object {
        const val LOG_LEVEL_VERBOSE: Int = 0
        const val LOG_LEVEL_DEBUG:   Int = 1
        const val LOG_LEVEL_INFO:    Int = 2
        const val LOG_LEVEL_WARN:    Int = 3
        const val LOG_LEVEL_ERROR:   Int = 4

        private val LOG_LEVELS = arrayOf(
            arrayOf("V", "VERBOSE", "0"),
            arrayOf("D", "DEBUG",   "1"),
            arrayOf("I", "INFO",    "2"),
            arrayOf("W", "WARN",    "3"),
            arrayOf("E", "ERROR",   "4"),
        )

        private val debugLog: LogImp = object : LogImp {
            private var level: Int = LOG_LEVEL_INFO

            override fun v(tag: String, msg: String, vararg obj: Any?) {
                if (level == LOG_LEVEL_VERBOSE) {
                    val log = if (obj.isEmpty()) msg else String.format(msg, *obj)
                    println(String.format("[V][%s] %s", tag, Util.capitalize(log)))
                }
            }

            override fun d(tag: String, msg: String, vararg obj: Any?) {
                if (level <= LOG_LEVEL_DEBUG) {
                    val log = if (obj.isEmpty()) msg else String.format(msg, *obj)
                    println(String.format("[D][%s] %s", tag, Util.capitalize(log)))
                }
            }

            override fun i(tag: String, msg: String, vararg obj: Any?) {
                if (level <= LOG_LEVEL_INFO) {
                    val log = if (obj.isEmpty()) msg else String.format(msg, *obj)
                    println(String.format("[I][%s] %s", tag, Util.capitalize(log)))
                }
            }

            override fun w(tag: String, msg: String, vararg obj: Any?) {
                if (level <= LOG_LEVEL_WARN) {
                    val log = if (obj.isEmpty()) msg else String.format(msg, *obj)
                    println(String.format("[W][%s] %s", tag, Util.capitalize(log)))
                }
            }

            override fun e(tag: String, msg: String, vararg obj: Any?) {
                if (level <= LOG_LEVEL_ERROR) {
                    val log = if (obj.isEmpty()) msg else String.format(msg, *obj)
                    println(String.format("[E][%s] %s", tag, Util.capitalize(log)))
                }
            }

            override fun printErrStackTrace(tag: String, tr: Throwable, format: String?, vararg obj: Any?) {
                var log = when {
                    format == null -> ""
                    obj.isEmpty()  -> format
                    else           -> String.format(format, *obj)
                }
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                tr.printStackTrace(pw)
                log += "  $sw"
                println(String.format("[E][%s] %s", tag, Util.capitalize(log)))
            }

            override fun setLogLevel(logLevel: Int) {
                this.level = logLevel
            }
        }

        @JvmStatic
        private var logImp: LogImp = debugLog

        @JvmStatic
        private var level: Int = LOG_LEVEL_INFO

        @JvmStatic
        fun setLogImp(imp: LogImp) {
            logImp = imp
        }

        @JvmStatic
        fun getImpl(): LogImp = logImp

        @JvmStatic
        fun setLogLevel(logLevel: String) {
            for (pattern in LOG_LEVELS) {
                if (pattern[0].equals(logLevel, ignoreCase = true) ||
                    pattern[1].equals(logLevel, ignoreCase = true)
                ) {
                    level = pattern[2].toInt()
                }
            }
            getImpl().setLogLevel(level)
        }

        @JvmStatic fun v(tag: String, msg: String, vararg obj: Any?) { logImp.v(tag, msg, *obj) }
        @JvmStatic fun e(tag: String, msg: String, vararg obj: Any?) { logImp.e(tag, msg, *obj) }
        @JvmStatic fun w(tag: String, msg: String, vararg obj: Any?) { logImp.w(tag, msg, *obj) }
        @JvmStatic fun i(tag: String, msg: String, vararg obj: Any?) { logImp.i(tag, msg, *obj) }
        @JvmStatic fun d(tag: String, msg: String, vararg obj: Any?) { logImp.d(tag, msg, *obj) }

        @JvmStatic
        fun printErrStackTrace(tag: String, tr: Throwable, format: String?, vararg obj: Any?) {
            logImp.printErrStackTrace(tag, tr, format, *obj)
        }
    }
}
