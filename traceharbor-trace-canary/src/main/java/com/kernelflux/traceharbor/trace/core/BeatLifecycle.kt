package com.kernelflux.traceharbor.trace.core

interface BeatLifecycle {

    fun onStart()

    fun onStop()

    fun isAlive(): Boolean
}
