package com.kernelflux.traceharbor.openglleak.utils

import android.os.HandlerThread

class GlLeakHandlerThread private constructor(name: String) : HandlerThread(name) {
    companion object {
        @JvmField
        val mInstance: GlLeakHandlerThread = GlLeakHandlerThread("GpuResLeakMonitor")

        @JvmStatic
        fun getInstance(): GlLeakHandlerThread = mInstance
    }
}

