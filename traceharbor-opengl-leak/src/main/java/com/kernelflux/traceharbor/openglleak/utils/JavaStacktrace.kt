package com.kernelflux.traceharbor.openglleak.utils

import java.util.concurrent.ConcurrentHashMap

// todo: deprecated and move to native
class JavaStacktrace private constructor() {
    class Trace {
        val content: String
        private var refCount: Int = 0

        constructor() {
            content = ""
        }

        constructor(content: String) {
            this.content = content
        }

        fun addReference() {
            refCount++
        }

        fun reduceReference() {
            refCount--
            if (refCount == 0) {
                sString2Trace.remove(content)
            }
        }
    }

    companion object {
        private val sThrowableMap: MutableMap<Int, Throwable> = ConcurrentHashMap()
        private val sString2Trace: MutableMap<String, Trace> = ConcurrentHashMap()
        private var sCollision: Int = 0

        @JvmStatic
        fun getBacktraceKey(): Int {
            val throwable = Throwable()
            val key = throwable.hashCode()
            if (sThrowableMap[key] != null) {
                sCollision++
            }
            sThrowableMap[key] = throwable
            return key
        }

        @JvmStatic
        fun getBacktraceValue(key: Int): Trace {
            val throwable = sThrowableMap[key]
            if (throwable == null) {
                return Trace()
            }
            val traceKey = android.util.Log.getStackTraceString(throwable)
            val mapTrace = sString2Trace[traceKey]
            return if (mapTrace == null) {
                val resultTrace = Trace(traceKey)
                resultTrace.addReference()
                sString2Trace[traceKey] = resultTrace
                sThrowableMap.remove(key)
                resultTrace
            } else {
                sThrowableMap.remove(key)
                mapTrace.addReference()
                mapTrace
            }
        }

        @JvmStatic
        fun getCollision(): Int = sCollision
    }
}

