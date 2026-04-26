package com.kernelflux.traceharbor.apk.model.task

import com.google.gson.JsonArray
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory.TASK_RESULT_TYPE_JSON
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_CHECK_MULTILIB
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.File

class MultiLibCheckTask(
    jobConfig: JobConfig,
    params: Map<String, String>,
) : ApkTask(jobConfig, params) {
    private var libDir: File? = null

    init {
        type = TASK_TYPE_CHECK_MULTILIB
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()
        val inputPath = config?.unzipPath
        if (!Util.isNullOrNil(inputPath)) {
            Log.i(TAG, "inputPath:%s", inputPath)
            libDir = File(inputPath, "lib")
        } else {
            throw TaskInitException("$TAG---APK-UNZIP-PATH can not be null!")
        }
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult = TaskResultFactory.factory(getType(), TASK_RESULT_TYPE_JSON, currentConfig) ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val jsonArray = JsonArray()
            if (libDir?.exists() == true && libDir?.isDirectory == true) {
                val dirs = libDir?.listFiles()
                if (dirs != null) {
                    for (dir in dirs) {
                        if (dir.isDirectory) {
                            jsonArray.add(dir.name)
                        }
                    }
                }
            }
            val jsonResult = taskResult as TaskJsonResult
            jsonResult.add("lib-dirs", jsonArray)
            jsonResult.add("multi-lib", jsonArray.size() > 1)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.MultiLibCheckTask"
    }
}

