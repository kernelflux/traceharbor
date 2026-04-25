package com.kernelflux.traceharbor.trace.tracer

import com.kernelflux.traceharbor.listeners.IAppForeground

interface ITracer : IAppForeground {

    fun isAlive(): Boolean

    fun onStartTrace()

    fun onCloseTrace()
}
