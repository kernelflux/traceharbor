package com.kernelflux.traceharbor.batterycanary.utils

/**
 * Compat version of [java.util.function.Consumer]
 *
 * @param T the type of the input to the operation
 */
fun interface Consumer<T> {
    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     */
    fun accept(t: T)
}

