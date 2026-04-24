package com.kernelflux.traceharbor.trace.listeners

import androidx.annotation.CallSuper
import com.kernelflux.traceharbor.trace.constants.Constants
import java.util.LinkedList
import java.util.concurrent.Executor

/**
 * Deprecated frame listener — use [IFrameListener] or [ISceneFrameListener]
 * instead. Kept on the public surface because external SDK consumers may
 * still subclass it.
 *
 * Conversion notes:
 * - Class is `open` so external Java subclasses keep working.
 * - Lifecycle methods (`collect`, `doFrameAsync`, `doFrameSync`,
 *   `doReplay`, `getIntervalFrameReplay`, `getExecutor`) are all `open`.
 * - The original Java had two constructors: a no-arg one that fetched
 *   `intervalFrame` from the (overridable) `getIntervalFrameReplay()`, and
 *   an `Executor` overload that did NOT initialize `intervalFrame` from
 *   the override. Both are reproduced verbatim via `@JvmOverloads`-free
 *   secondary constructors so byte-compatibility holds.
 * - Nested `FrameReplay` is a public mutable POJO (9 fields read/written
 *   by FrameTracer), so each field is exposed as `@JvmField`.
 */
@Deprecated("Use IFrameListener or ISceneFrameListener")
open class IDoFrameListener {

    private var executor: Executor? = null

    @JvmField
    var time: Long = 0

    private var intervalFrame: Int = 0

    private val list: MutableList<FrameReplay> = LinkedList()

    constructor() {
        intervalFrame = getIntervalFrameReplay()
    }

    constructor(executor: Executor?) {
        this.executor = executor
    }

    @CallSuper
    open fun collect(
        focusedActivity: String,
        startNs: Long,
        endNs: Long,
        dropFrame: Int,
        isVsyncFrame: Boolean,
        intendedFrameTimeNs: Long,
        inputCostNs: Long,
        animationCostNs: Long,
        traversalCostNs: Long,
    ) {
        val replay = FrameReplay.create()
        replay.focusedActivity = focusedActivity
        replay.startNs = startNs
        replay.endNs = endNs
        replay.dropFrame = dropFrame
        replay.isVsyncFrame = isVsyncFrame
        replay.intendedFrameTimeNs = intendedFrameTimeNs
        replay.inputCostNs = inputCostNs
        replay.animationCostNs = animationCostNs
        replay.traversalCostNs = traversalCostNs
        list.add(replay)
        if (list.size >= intervalFrame && getExecutor() != null) {
            val copy: List<FrameReplay> = LinkedList(list)
            list.clear()
            getExecutor()?.execute {
                doReplay(copy)
                for (record in copy) {
                    record.recycle()
                }
            }
        }
    }

    @Deprecated("retained for binary compatibility")
    open fun doFrameAsync(
        visibleScene: String,
        taskCost: Long,
        frameCostMs: Long,
        droppedFrames: Int,
        isVsyncFrame: Boolean,
    ) {
    }

    @Deprecated("retained for binary compatibility")
    open fun doFrameSync(
        visibleScene: String,
        taskCost: Long,
        frameCostMs: Long,
        droppedFrames: Int,
        isVsyncFrame: Boolean,
    ) {
    }

    @CallSuper
    open fun doFrameAsync(
        focusedActivity: String,
        startNs: Long,
        endNs: Long,
        dropFrame: Int,
        isVsyncFrame: Boolean,
        intendedFrameTimeNs: Long,
        inputCostNs: Long,
        animationCostNs: Long,
        traversalCostNs: Long,
    ) {
        val cost = (endNs - intendedFrameTimeNs) / Constants.TIME_MILLIS_TO_NANO
        @Suppress("DEPRECATION")
        doFrameAsync(focusedActivity, cost, cost, dropFrame, isVsyncFrame)
    }

    @CallSuper
    open fun doFrameSync(
        focusedActivity: String,
        startNs: Long,
        endNs: Long,
        dropFrame: Int,
        isVsyncFrame: Boolean,
        intendedFrameTimeNs: Long,
        inputCostNs: Long,
        animationCostNs: Long,
        traversalCostNs: Long,
    ) {
        val cost = (endNs - intendedFrameTimeNs) / Constants.TIME_MILLIS_TO_NANO
        @Suppress("DEPRECATION")
        doFrameSync(focusedActivity, cost, cost, dropFrame, isVsyncFrame)
    }

    open fun doReplay(list: List<FrameReplay>) {
    }

    open fun getExecutor(): Executor? = executor

    open fun getIntervalFrameReplay(): Int = 0

    /**
     * Public mutable POJO recycled through a synchronized free-list pool of
     * size 1000. All fields are @JvmField for direct Java field access.
     */
    class FrameReplay {
        @JvmField var focusedActivity: String? = null
        @JvmField var startNs: Long = 0
        @JvmField var endNs: Long = 0
        @JvmField var dropFrame: Int = 0
        @JvmField var isVsyncFrame: Boolean = false
        @JvmField var intendedFrameTimeNs: Long = 0
        @JvmField var inputCostNs: Long = 0
        @JvmField var animationCostNs: Long = 0
        @JvmField var traversalCostNs: Long = 0

        fun recycle() {
            if (sPool.size <= 1000) {
                this.focusedActivity = ""
                this.startNs = 0
                this.endNs = 0
                this.dropFrame = 0
                this.isVsyncFrame = false
                this.intendedFrameTimeNs = 0
                this.inputCostNs = 0
                this.animationCostNs = 0
                this.traversalCostNs = 0
                synchronized(sPool) {
                    sPool.add(this)
                }
            }
        }

        companion object {
            private val sPool = LinkedList<FrameReplay>()

            @JvmStatic
            fun create(): FrameReplay {
                val replay: FrameReplay? = synchronized(sPool) { sPool.poll() }
                return replay ?: FrameReplay()
            }
        }
    }
}
