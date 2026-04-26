package com.kernelflux.traceharbor.apk.model.task

import com.android.utils.Pair
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.job.JobConstants
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_UNCOMPRESSED_FILE
import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.File
import java.util.HashMap
import java.util.HashSet

class UncompressedFileTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private lateinit var filterSuffix: MutableSet<String>
    private lateinit var uncompressSizeMap: MutableMap<String, Long>
    private lateinit var compressSizeMap: MutableMap<String, Long>

    init {
        type = TASK_TYPE_UNCOMPRESSED_FILE
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()
        val inputPath = config?.apkPath

        if (Util.isNullOrNil(inputPath)) {
            throw TaskInitException("$TAG---APK-FILE-PATH can not be null!")
        }
        inputFile = File(inputPath)
        if (!FileUtil.isLegalFile(inputFile)) {
            throw TaskInitException("$TAG---APK-FILE-PATH '$inputPath' is illegal!")
        }

        filterSuffix = HashSet()
        val currentParams = params ?: emptyMap()

        if (currentParams.containsKey(JobConstants.PARAM_SUFFIX) && !Util.isNullOrNil(currentParams[JobConstants.PARAM_SUFFIX])) {
            val suffix = currentParams[JobConstants.PARAM_SUFFIX]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (suffixStr in suffix) {
                filterSuffix.add(suffixStr.trim { it <= ' ' })
            }
        }

        uncompressSizeMap = HashMap()
        compressSizeMap = HashMap()
    }

    private fun getSuffix(name: String): String {
        val index = name.indexOf('.')
        if (index >= 0 && index < name.length - 1) {
            return name.substring(index + 1)
        }
        return ""
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult = TaskResultFactory.factory(type, TaskResultFactory.TASK_RESULT_TYPE_JSON, currentConfig) ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val jsonArray = JsonArray()
            val entrySizeMap = currentConfig.entrySizeMap ?: emptyMap()
            if (entrySizeMap.isNotEmpty()) { // take advantage of the result of UnzipTask.
                for ((key, size) in entrySizeMap) {
                    val suffix = getSuffix(key)
                    if (filterSuffix.isEmpty() || filterSuffix.contains(suffix)) {
                        if (!uncompressSizeMap.containsKey(suffix)) {
                            uncompressSizeMap[suffix] = size.first
                        } else {
                            uncompressSizeMap[suffix] = uncompressSizeMap[suffix]!! + size.first
                        }
                        if (!compressSizeMap.containsKey(suffix)) {
                            compressSizeMap[suffix] = size.second
                        } else {
                            compressSizeMap[suffix] = compressSizeMap[suffix]!! + size.second
                        }
                    } else {
                        Log.d(TAG, "file: %s, filter by suffix.", key)
                    }
                }
            }

            for (suffix in uncompressSizeMap.keys) {
                if (uncompressSizeMap[suffix] == compressSizeMap[suffix]) {
                    val fileItem = JsonObject()
                    fileItem.addProperty("suffix", suffix)
                    fileItem.addProperty("total-size", uncompressSizeMap[suffix])
                    jsonArray.add(fileItem)
                }
            }
            (taskResult as TaskJsonResult).add("files", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.UncompressedFileTask"
    }
}

