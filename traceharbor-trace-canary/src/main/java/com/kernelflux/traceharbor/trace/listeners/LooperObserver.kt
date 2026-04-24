package com.kernelflux.traceharbor.trace.listeners

import androidx.annotation.CallSuper

/**
 * Use [ILooperListener] or [IFrameListener] instead.
 */
@Deprecated("Use ILooperListener or IFrameListener instead.")
abstract class LooperObserver {

    private var isDispatchBegin: Boolean = false

    @CallSuper
    open fun dispatchBegin(beginNs: Long, cpuBeginNs: Long, token: Long) {
        isDispatchBegin = true
    }

    open fun doFrame(
        focusedActivity: String,
        startNs: Long,
        endNs: Long,
        isVsyncFrame: Boolean,
        intendedFrameTimeNs: Long,
        inputCostNs: Long,
        animationCostNs: Long,
        traversalCostNs: Long,
    ) {
    }

    @CallSuper
    open fun dispatchEnd(
        beginNs: Long,
        cpuBeginMs: Long,
        endNs: Long,
        cpuEndMs: Long,
        token: Long,
        isVsyncFrame: Boolean,
    ) {
        isDispatchBegin = false
    }

    fun isDispatchBegin(): Boolean = isDispatchBegin
}
