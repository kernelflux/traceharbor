package com.kernelflux.traceharbor.lifecycle.owners

import com.kernelflux.traceharbor.lifecycle.TraceHarborLifecycleThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import kotlin.math.min

/**
 * Created by Yves on 2021/11/24
 */
internal abstract class TimerChecker(
    private val tag: String,
    private val maxIntervalMillis: Long,
    private val maxCheckTimes: Int = -1 // infinity by default
) {

    private val runningHandler by lazy { TraceHarborLifecycleThread.handler }

    /**
     * The initial delay interval is 12 + 31 = 34(ms)
     * Why 34? Removing foreground widgets like floating Views should be run in main thread
     * and might depend on Activity background event, which is dispatched in TraceHarbor handler thread.
     * And for most cases, the onDetach would be called within 10ms.
     *
     * valid intervals
     * 34,55,89,144,233,377,610,987,1597,2584,4181,6765,10946,17711,28657,46368,...,$max
     *
     */
    private class IntervalFactory(private val maxVal: Long) {
        private val initialInterval = arrayOf(13L, 21L)
        private var fibo = initialInterval.copyOf()

        fun next(): Long {
            return min((fibo[0] + fibo[1]).also { fibo[0] = fibo[1]; fibo[1] = it }, maxVal)
        }

        fun reset() {
            fibo = initialInterval.copyOf()
        }
    }

    private val intervalFactory by lazy { IntervalFactory(maxIntervalMillis) }

    private val task by lazy {
        object : Runnable {
            override fun run() {
                TraceHarborLog.i(tag, "run check task")
                if (!action()) {
                    postTimes = 0 // reset so that check task can be resume by checkAndPostIfNeeded
                } else if (maxCheckTimes == -1 || postTimes++ < maxCheckTimes) {
                    val interval = intervalFactory.next()
                    TraceHarborLog.i(tag, "need recheck: next $interval")
                    runningHandler.postDelayed(this, interval)
                } else {
                    TraceHarborLog.i(tag, "paused polling check")
                }
            }
        }
    }

    @Volatile
    private var postTimes = 0

    /**
     * return true if need recheck
     */
    protected abstract fun action(): Boolean

    fun checkAndPostIfNeeded(): Boolean {
        TraceHarborLog.i(tag, "checkAndPostIfNeeded")
        runningHandler.removeCallbacks(task)
        if (action()) {
            runningHandler.postDelayed(task, intervalFactory.next())
            return true
        }
        return false
    }

    fun post() {
        intervalFactory.reset()
        val interval = intervalFactory.next()
        TraceHarborLog.i(tag, "post check: $interval")
        runningHandler.removeCallbacks(task)
        runningHandler.postDelayed(task, interval)
    }

    fun stop() {
        postTimes = 0
        TraceHarborLog.i(tag, "stop")
        intervalFactory.reset()
        runningHandler.removeCallbacks(task)
    }
}