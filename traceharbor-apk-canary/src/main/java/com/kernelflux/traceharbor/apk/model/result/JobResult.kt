package com.kernelflux.traceharbor.apk.model.result

@Suppress("PMD")
abstract class JobResult {
    @JvmField
    protected var format: String = TaskResultFactory.TASK_RESULT_TYPE_HTML

    @JvmField
    protected var resultList: MutableList<TaskResult>? = null

    fun getFormat(): String = format

    fun addTaskResult(result: TaskResult) {
        resultList?.add(result)
    }

    abstract fun output()
}

