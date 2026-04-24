package com.kernelflux.traceharbor.trace.items

/**
 * Public mutable POJO. Java callers (TraceDataUtils, EvilMethodTracer,
 * LooperAnrTracer, StartupTracer) read and write the four fields
 * directly (`item.methodId`, `item.count++`, etc.), so each field is
 * exposed as a `@JvmField` to preserve byte-for-byte the original
 * `public int` access pattern.
 */
class MethodItem(
    @JvmField var methodId: Int,
    @JvmField var durTime: Int,
    @JvmField var depth: Int,
) {
    @JvmField
    var count: Int = 1

    override fun toString(): String =
        "$depth,$methodId,$count,$durTime"

    fun mergeMore(cost: Long) {
        count++
        durTime += cost.toInt()
    }

    fun print(): String {
        val inner = StringBuilder()
        repeat(depth) { inner.append('.') }
        return "$inner$methodId $count $durTime"
    }
}
