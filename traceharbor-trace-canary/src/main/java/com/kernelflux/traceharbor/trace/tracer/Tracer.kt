package com.kernelflux.traceharbor.trace.tracer

import androidx.annotation.CallSuper
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * Base class for all tracers. Eight Java subclasses live in the same
 * package (FrameTracer, EvilMethodTracer, StartupTracer, …) and override
 * the `protected onAlive()` / `protected onDead()` lifecycle hooks.
 * Kotlin's `protected` is class-scoped, but since the subclasses are real
 * Tracer subclasses (not just package siblings) the original protected
 * contract still holds — no widening / bridging required here.
 */
abstract class Tracer : ITracer {

    @Volatile
    private var isAliveInternal: Boolean = false

    @CallSuper
    protected open fun onAlive() {
        TraceHarborLog.i(TAG, "[onAlive] %s", this.javaClass.name)
    }

    @CallSuper
    protected open fun onDead() {
        TraceHarborLog.i(TAG, "[onDead] %s", this.javaClass.name)
    }

    @Synchronized
    final override fun onStartTrace() {
        if (!isAliveInternal) {
            this.isAliveInternal = true
            onAlive()
        }
    }

    @Synchronized
    final override fun onCloseTrace() {
        if (isAliveInternal) {
            this.isAliveInternal = false
            onDead()
        }
    }

    override fun onForeground(isForeground: Boolean) {
        // intentionally empty default
    }

    override fun isAlive(): Boolean = isAliveInternal

    open fun isForeground(): Boolean = ProcessUILifecycleOwner.isProcessForeground

    private companion object {
        private const val TAG = "TraceHarbor.Tracer"
    }
}
