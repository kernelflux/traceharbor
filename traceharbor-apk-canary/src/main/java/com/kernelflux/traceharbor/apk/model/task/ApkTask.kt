package com.kernelflux.traceharbor.apk.model.task

import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import java.util.LinkedList
import java.util.concurrent.Callable

abstract class ApkTask(
    @JvmField protected val config: JobConfig?,
    @JvmField protected val params: Map<String, String>?,
) : Callable<TaskResult> {
    @JvmField
    protected var type: Int = 0

    @JvmField
    protected val progressListeners: MutableList<ApkTaskProgressListener> = LinkedList()

    interface ApkTaskProgressListener {
        fun getProgress(progress: Int, message: String)
    }

    open fun getType(): Int = type

    @Throws(TaskInitException::class)
    open fun init() {
        if (config == null) {
            throw TaskInitException("$TAG---jobConfig can not be null!")
        }
        if (params == null) {
            throw TaskInitException("$TAG---params can not be null!")
        }
    }

    fun addProgressListener(listener: ApkTaskProgressListener?) {
        if (listener != null) {
            progressListeners.add(listener)
        }
    }

    fun removeProgressListener(listener: ApkTaskProgressListener?) {
        if (listener != null) {
            progressListeners.remove(listener)
        }
    }

    protected fun notifyProgress(progress: Int, message: String) {
        for (listener in progressListeners) {
            listener.getProgress(progress, message)
        }
    }

    @Throws(TaskExecuteException::class)
    abstract override fun call(): TaskResult

    companion object {
        private const val TAG = "TraceHarbor.ApkTask"
    }
}

