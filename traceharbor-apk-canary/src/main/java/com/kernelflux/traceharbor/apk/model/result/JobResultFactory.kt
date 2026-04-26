package com.kernelflux.traceharbor.apk.model.result

import com.kernelflux.traceharbor.apk.model.job.JobConfig

object JobResultFactory {
    @JvmStatic
    fun factory(format: String, config: JobConfig?): JobResult? {
        var jobResult: JobResult? = null
        if (config != null) {
            val outputPath = config.outputPath ?: ""
            if (TaskResultFactory.isJsonResult(format)) {
                jobResult = JobJsonResult(format, outputPath)
            } else if (TaskResultFactory.isHtmlResult(format)) {
                jobResult = JobHtmlResult(format, outputPath)
            }
        }
        return jobResult
    }
}

