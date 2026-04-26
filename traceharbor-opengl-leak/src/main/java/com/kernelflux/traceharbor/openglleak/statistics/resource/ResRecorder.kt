package com.kernelflux.traceharbor.openglleak.statistics.resource

import java.util.LinkedList

class ResRecorder : ResRecordManager.Callback {
    private val mList: MutableList<OpenGLInfo> = LinkedList()

    fun start() {
        ResRecordManager.getInstance().registerCallback(this)
    }

    fun end() {
        ResRecordManager.getInstance().unregisterCallback(this)
    }

    override fun gen(res: OpenGLInfo) {
        synchronized(mList) {
            mList.add(res)
        }
    }

    override fun delete(res: OpenGLInfo) {
        synchronized(mList) {
            mList.remove(res)
        }
    }

    fun getCurList(): List<OpenGLInfo> = mList

    fun clear() {
        synchronized(mList) {
            mList.clear()
        }
    }
}

