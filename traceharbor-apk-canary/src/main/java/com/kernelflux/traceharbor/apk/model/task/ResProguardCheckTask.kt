package com.kernelflux.traceharbor.apk.model.task

import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory.TASK_RESULT_TYPE_JSON
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_CHECK_RESGUARD
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.File
import java.util.regex.Pattern

class ResProguardCheckTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private var fileNamePattern: Pattern? = null

    init {
        type = TASK_TYPE_CHECK_RESGUARD
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()
        val inputPath = config?.unzipPath
        if (Util.isNullOrNil(inputPath)) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH can not be null!")
        }
        Log.i(TAG, "inputPath:%s", inputPath)
        inputFile = File(inputPath)
        if (inputFile?.exists() != true) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath' is not exist!")
        } else if (inputFile?.isDirectory != true) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath' is not directory!")
        }
        fileNamePattern = Pattern.compile("[a-z_0-9]{1,3}")
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        val rootInput = inputFile ?: throw TaskExecuteException("$TAG---input path invalid!")
        var resDir = File(rootInput, ApkConstants.RESOURCE_DIR_PROGUARD_NAME)
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult = TaskResultFactory.factory(getType(), TASK_RESULT_TYPE_JSON, currentConfig) ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val jsonResult = taskResult as TaskJsonResult
            if (resDir.exists() && resDir.isDirectory) {
                Log.i(TAG, "find resource directory " + resDir.absolutePath)
                jsonResult.add("hasResProguard", true)
            } else {
                resDir = File(rootInput, ApkConstants.RESOURCE_DIR_NAME)
                if (resDir.exists() && resDir.isDirectory) {
                    val dirs = resDir.listFiles()
                    var hasProguard = true
                    if (dirs != null) {
                        for (dir in dirs) {
                            if (dir.isDirectory && fileNamePattern?.matcher(dir.name)?.matches() == false) {
                                hasProguard = false
                                Log.i(TAG, "directory " + dir.name + " has a non-proguard name!")
                                break
                            }
                        }
                    }
                    jsonResult.add("hasResProguard", hasProguard)
                } else {
                    throw TaskExecuteException("$TAG---No resource directory found!")
                }
            }
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.ResProguardCheckTask"
    }
}

