package com.kernelflux.traceharbor.resource.watcher

interface Watcher {
    fun start()

    fun stop()

    fun destroy()
}

