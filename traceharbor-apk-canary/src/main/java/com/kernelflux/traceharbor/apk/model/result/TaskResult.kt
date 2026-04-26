package com.kernelflux.traceharbor.apk.model.result

import java.text.SimpleDateFormat
import java.util.Calendar

@Suppress("PMD")
abstract class TaskResult(
    @JvmField val taskType: Int,
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS")

    @JvmField
    protected var startTime: String? = null

    @JvmField
    protected var endTime: String? = null

    open fun setStartTime(startTime: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startTime
        this.startTime = dateFormat.format(calendar.time)
    }

    open fun setEndTime(endTime: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = endTime
        this.endTime = dateFormat.format(calendar.time)
    }

    abstract fun getResult(): Any?
}

