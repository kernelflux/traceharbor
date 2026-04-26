package com.squareup.haha.perflib

object HahaSpy {
    @JvmStatic
    fun allocatingThread(instance: Instance): Instance? {
        val snapshot = instance.mHeap.mSnapshot
        val threadSerialNumber =
            if (instance is RootObj) {
                instance.mThread
            } else {
                instance.mStack.mThreadSerialNumber
            }
        val thread = snapshot.getThread(threadSerialNumber)
        return snapshot.findInstance(thread.mId)
    }
}

