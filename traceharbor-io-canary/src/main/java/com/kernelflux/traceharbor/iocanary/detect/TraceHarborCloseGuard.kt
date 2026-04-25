package com.kernelflux.traceharbor.iocanary.detect

import com.kernelflux.traceharbor.util.TraceHarborLog

class TraceHarborCloseGuard private constructor() {
    private var allocationSite: Throwable? = null

    fun open(closer: String?) {
        if (closer == null) {
            throw NullPointerException("closer == null")
        }
        if (this === NOOP || !sENABLED) {
            return
        }
        val message = "Explicit termination method '$closer' not called"
        allocationSite = Throwable(message)
    }

    fun close() {
        allocationSite = null
    }

    fun warnIfOpen() {
        val site = allocationSite
        if (site == null || !sENABLED) {
            return
        }

        val message = "A resource was acquired at attached stack trace but never released. " +
            "See java.io.Closeable for information on avoiding resource leaks."
        sREPORTER.report(message, site)
    }

    fun interface Reporter {
        fun report(message: String?, allocationSite: Throwable?)
    }

    private class DefaultReporter : Reporter {
        override fun report(message: String?, allocationSite: Throwable?) {
            TraceHarborLog.e(TAG, message.orEmpty(), allocationSite)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.CloseGuard"

        private val NOOP = TraceHarborCloseGuard()

        @Volatile
        private var sENABLED = true

        @Volatile
        private var sREPORTER: Reporter = DefaultReporter()

        @JvmStatic
        fun get(): TraceHarborCloseGuard {
            if (!sENABLED) {
                return NOOP
            }
            return TraceHarborCloseGuard()
        }

        @JvmStatic
        fun setEnabled(enabled: Boolean) {
            sENABLED = enabled
        }

        @JvmStatic
        fun setReporter(reporter: Reporter?) {
            if (reporter == null) {
                throw NullPointerException("reporter == null")
            }
            sREPORTER = reporter
        }

        @JvmStatic
        fun getReporter(): Reporter = sREPORTER
    }
}
