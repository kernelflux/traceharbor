package com.kernelflux.traceharbor.resource.leakcanary.internal

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class FutureResult<T> {
    private val resultHolder = AtomicReference<T>()
    private val latch = CountDownLatch(1)

    fun wait(timeout: Long, unit: TimeUnit): Boolean {
        return try {
            latch.await(timeout, unit)
        } catch (e: InterruptedException) {
            throw RuntimeException("Did not expect thread to be interrupted", e)
        }
    }

    fun get(): T {
        if (latch.count > 0) {
            throw IllegalStateException("Call wait() and check its result")
        }
        return resultHolder.get()
    }

    fun set(result: T) {
        resultHolder.set(result)
        latch.countDown()
    }
}

