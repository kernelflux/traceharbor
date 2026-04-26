package com.kernelflux.traceharbor.apk.model.task

import com.google.gson.JsonObject
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory.TASK_RESULT_TYPE_JSON
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_MANIFEST
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.apk.model.task.util.ManifestParser
import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.File

class ManifestAnalyzeTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private var arscFile: File? = null

    init {
        type = TASK_TYPE_MANIFEST
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()
        val inputPath = config?.unzipPath
        if (Util.isNullOrNil(inputPath)) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH can not be null!")
        }
        Log.i(TAG, "inputPath:%s", inputPath)
        inputFile = File(inputPath, ApkConstants.MANIFEST_FILE_NAME)
        if (inputFile?.exists() != true) {
            throw TaskInitException("$TAG---Manifest file '" + inputPath + File.separator + ApkConstants.MANIFEST_FILE_NAME + "' is not exist!")
        }

        arscFile = File(inputPath, ApkConstants.ARSC_FILE_NAME)
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val parser =
                if (!FileUtil.isLegalFile(arscFile)) {
                    ManifestParser(inputFile)
                } else {
                    ManifestParser(inputFile, arscFile)
                }
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult = TaskResultFactory.factory(getType(), TASK_RESULT_TYPE_JSON, currentConfig) ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val jsonObject: JsonObject? = parser.parse()
            Log.d(TAG, jsonObject.toString())
            (taskResult as TaskJsonResult).add("manifest", jsonObject)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.ManifestAnalyzeTask"
    }
}

