package com.kernelflux.traceharbor.batterycanary.utils

import android.os.Looper
import android.os.Process

/**
 * @author Kaede
 * @since 2021/12/17
 */
class CallStackCollector {
    fun collectCurr(): String = collect(Throwable())

    fun collectUiThread(): String = collect(Looper.getMainLooper().thread)

    fun collect(throwable: Throwable): String = collect(throwable.stackTrace)

    fun collect(thread: Thread): String = collect(thread.stackTrace)

    fun collect(elements: Array<StackTraceElement>): String = BatteryCanaryUtil.stackTraceToString(elements)

    fun collect(tid: Int): String {
        if (tid == Process.myTid()) {
            return collectCurr()
        }
        if (tid == Process.myPid()) {
            return collectUiThread()
        }
        return "" // Unwind needed
    }
}

