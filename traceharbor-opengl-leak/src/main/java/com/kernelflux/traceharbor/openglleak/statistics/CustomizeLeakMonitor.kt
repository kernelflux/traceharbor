package com.kernelflux.traceharbor.openglleak.statistics

import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo
import com.kernelflux.traceharbor.openglleak.statistics.resource.ResRecorder

class CustomizeLeakMonitor {
    private val mResRecorder: ResRecorder = ResRecorder()

    fun checkStart() {
        mResRecorder.clear()
        mResRecorder.start()
    }

    fun checkEnd(): List<OpenGLInfo> {
        mResRecorder.end()
        return mResRecorder.getCurList()
    }
}

