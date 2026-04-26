package com.kernelflux.traceharbor.openglleak.statistics

import com.kernelflux.traceharbor.openglleak.statistics.resource.OpenGLInfo

class BindCenter private constructor() {
    fun glBindResource(type: OpenGLInfo.TYPE, target: Int, eglContextId: Long, info: OpenGLInfo?) {
        BindMap.getInstance().putBindInfo(type, target, eglContextId, info)
    }

    fun findCurrentResourceIdByTarget(type: OpenGLInfo.TYPE, eglContextId: Long, target: Int): OpenGLInfo? {
        return BindMap.getInstance().getBindInfo(type, eglContextId, target)
    }

    companion object {
        @JvmField
        val mInstance: BindCenter = BindCenter()

        @JvmStatic
        fun getInstance(): BindCenter = mInstance
    }
}

