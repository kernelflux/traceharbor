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
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory.TASK_RESULT_TYPE_JSON
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_SHOW_FILE_SIZE
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.File
import java.util.Collections
import java.util.Comparator
import java.util.HashSet

class ShowFileSizeTask(
    jobConfig: JobConfig,
    params: Map<String, String>,
) : ApkTask(jobConfig, params) {
    private var inputFile: File? = null
    private var order = JobConstants.ORDER_DESC
    private var downLimit: Long = 0
    private lateinit var filterSuffix: MutableSet<String>
    private lateinit var entryList: MutableList<Pair<String, Long>>

    init {
        type = TASK_TYPE_SHOW_FILE_SIZE
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
        val currentParams = params ?: emptyMap()
        if (currentParams.containsKey(JobConstants.PARAM_MIN_SIZE_IN_KB)) {
            try {
                downLimit = currentParams[JobConstants.PARAM_MIN_SIZE_IN_KB]!!.toLong()
            } catch (e: NumberFormatException) {
                Log.e(TAG, "DOWN-LIMIT-SIZE '" + currentParams[JobConstants.PARAM_MIN_SIZE_IN_KB] + "' is not number format!")
            }
        }

        if (currentParams.containsKey(JobConstants.PARAM_ORDER)) {
            if (JobConstants.ORDER_ASC == currentParams[JobConstants.PARAM_ORDER]) {
                order = JobConstants.ORDER_ASC
            } else if (JobConstants.ORDER_DESC == currentParams[JobConstants.PARAM_ORDER]) {
                order = JobConstants.ORDER_DESC
            } else {
                Log.e(TAG, "ORDER-BY '" + currentParams[JobConstants.PARAM_ORDER] + "' is not correct!")
            }
        }

        filterSuffix = HashSet()

        if (currentParams.containsKey(JobConstants.PARAM_SUFFIX) && !Util.isNullOrNil(currentParams[JobConstants.PARAM_SUFFIX])) {
            val suffix = currentParams[JobConstants.PARAM_SUFFIX]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (suffixStr in suffix) {
                filterSuffix.add(suffixStr.trim { it <= ' ' })
            }
        }

        entryList = ArrayList()
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult = TaskResultFactory.factory(getType(), TASK_RESULT_TYPE_JSON, currentConfig) ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()

            val entrySizeMap = currentConfig.entrySizeMap ?: emptyMap()
            if (entrySizeMap.isNotEmpty()) { // take advantage of the result of UnzipTask.
                for ((key, size) in entrySizeMap) {
                    val suffix = getSuffix(key)
                    if (size.first >= downLimit * ApkConstants.K1024) {
                        if (filterSuffix.isEmpty() || filterSuffix.contains(suffix)) {
                            entryList.add(Pair.of(key, size.first))
                        } else {
                            Log.d(TAG, "file: %s, filter by suffix.", key)
                        }
                    } else {
                        Log.d(TAG, "file:%s, size:%d B, downlimit:%d KB", key, size.first, downLimit)
                    }
                }
            }

            Collections.sort(
                entryList,
                Comparator { entry1, entry2 ->
                    val file1Len = entry1.second
                    val file2Len = entry2.second
                    if (file1Len < file2Len) {
                        if (order == JobConstants.ORDER_ASC) {
                            -1
                        } else {
                            1
                        }
                    } else if (file1Len > file2Len) {
                        if (order == JobConstants.ORDER_DESC) {
                            -1
                        } else {
                            1
                        }
                    } else {
                        0
                    }
                },
            )

            val jsonArray = JsonArray()
            for (sortFile in entryList) {
                val fileItem = JsonObject()
                fileItem.addProperty("entry-name", sortFile.first)
                fileItem.addProperty("entry-size", sortFile.second)
                jsonArray.add(fileItem)
            }
            (taskResult as TaskJsonResult).add("files", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    private fun getSuffix(name: String): String {
        val index = name.indexOf('.')
        if (index >= 0 && index < name.length - 1) {
            return name.substring(index + 1)
        }
        return ""
    }

    companion object {
        private const val TAG = "TraceHarbor.ShowFileSizeTask"
    }
}

