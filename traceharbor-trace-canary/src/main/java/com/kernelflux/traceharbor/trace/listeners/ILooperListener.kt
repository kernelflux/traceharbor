package com.kernelflux.traceharbor.trace.listeners

interface ILooperListener {
    fun isValid(): Boolean
    fun onDispatchBegin(log: String)
    fun onDispatchEnd(log: String, beginNs: Long, endNs: Long)
}
