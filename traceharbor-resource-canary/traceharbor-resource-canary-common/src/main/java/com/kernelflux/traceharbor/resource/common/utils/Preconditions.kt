// Originally Apache 2.0 (Square, Inc.) — ported via LeakCanary, then to TraceHarbor.
package com.kernelflux.traceharbor.resource.common.utils

object Preconditions {

    /**
     * Returns [instance] unless it's `null`.
     *
     * @throws NullPointerException if [instance] is `null`.
     */
    @JvmStatic
    fun <T : Any> checkNotNull(instance: T?, name: String): T {
        return instance ?: throw NullPointerException("$name must not be null")
    }
}
