package io.traceharbor.memory.canary.monitor

import io.traceharbor.util.TraceHarborHandlerThread
import io.traceharbor.util.TraceHarborLog
import java.util.concurrent.TimeUnit

/**
 * Created by Yves on 2021/10/12
 */
abstract class TimerMonitor(private val cycle: Long = DEFAULT_CHECK_TIME) : Runnable {

    companion object {
        private const val TAG = "TraceHarbor.monitor.TimerMonitor"
        private val DEFAULT_CHECK_TIME = TimeUnit.MINUTES.toMillis(5)
    }

    private val handler = TraceHarborHandlerThread.getDefaultHandler()!!

    fun start() {
        TraceHarborLog.i(TAG, "start ${javaClass.name}")
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(this, cycle)
    }

    fun stop() {
        TraceHarborLog.i(TAG, "stop ${javaClass.name}")
        handler.removeCallbacksAndMessages(null)
    }

    final override fun run() {
        action()

        handler.postDelayed(this, cycle)
    }

    abstract fun action()
}