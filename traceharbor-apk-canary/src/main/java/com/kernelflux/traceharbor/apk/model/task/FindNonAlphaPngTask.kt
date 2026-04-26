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
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_FIND_NON_ALPHA_PNG
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.Comparator
import javax.imageio.ImageIO

class FindNonAlphaPngTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private lateinit var nonAlphaPngList: MutableList<Pair<String, Long>>
    private var downLimitSize: Long = 0
    private var entrySizeMap: Map<String, Pair<Long, Long>> = emptyMap()
    private var entryNameMap: Map<String, String> = emptyMap()

    init {
        type = TASK_TYPE_FIND_NON_ALPHA_PNG
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()
        val inputPath = config?.unzipPath
        if (Util.isNullOrNil(inputPath)) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH can not be null!")
        }
        inputFile = File(inputPath)
        if (inputFile?.exists() != true) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath'is not exists!")
        } else if (inputFile?.isDirectory != true) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath' is not directory!")
        }
        val currentParams = params ?: emptyMap()
        if (currentParams.containsKey(JobConstants.PARAM_MIN_SIZE_IN_KB)) {
            try {
                downLimitSize = currentParams[JobConstants.PARAM_MIN_SIZE_IN_KB]!!.toLong()
            } catch (e: NumberFormatException) {
                Log.e(TAG, "DOWN-LIMIT-SIZE '" + currentParams[JobConstants.PARAM_MIN_SIZE_IN_KB] + "' is not number format!")
            }
        }
        nonAlphaPngList = ArrayList()
        val currentConfig = config
        entrySizeMap = currentConfig?.entrySizeMap ?: emptyMap()
        entryNameMap = currentConfig?.entryNameMap ?: emptyMap()
    }

    @Throws(IOException::class)
    private fun findNonAlphaPng(file: File?) {
        if (file != null) {
            if (file.isDirectory) {
                val files = file.listFiles()
                if (files != null) {
                    for (tempFile in files) {
                        findNonAlphaPng(tempFile)
                    }
                }
            } else if (file.isFile && file.name.endsWith(ApkConstants.PNG_FILE_SUFFIX) && !file.name.endsWith(ApkConstants.NINE_PNG)) {
                val bufferedImage: BufferedImage? = ImageIO.read(file)
                if (bufferedImage != null && bufferedImage.colorModel != null && !bufferedImage.colorModel.hasAlpha()) {
                    val root = inputFile ?: return
                    var filename = file.absolutePath.substring(root.absolutePath.length + 1)
                    if (entryNameMap.containsKey(filename)) {
                        filename = entryNameMap[filename].toString()
                    }
                    var size = file.length()
                    if (entrySizeMap.containsKey(filename)) {
                        size = entrySizeMap[filename]!!.first
                    }
                    if (size >= downLimitSize * ApkConstants.K1024) {
                        nonAlphaPngList.add(Pair.of(filename, file.length()))
                    }
                }
            }
        }
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        val rootInput = inputFile ?: throw TaskExecuteException("$TAG---input path invalid!")
        var resDir = File(rootInput, ApkConstants.RESOURCE_DIR_PROGUARD_NAME)
        return try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult =
                TaskResultFactory.factory(getType(), TaskResultFactory.TASK_RESULT_TYPE_JSON, currentConfig)
                    ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            if (resDir.exists() && resDir.isDirectory) {
                findNonAlphaPng(resDir)
            } else {
                resDir = File(rootInput, ApkConstants.RESOURCE_DIR_NAME)
                if (resDir.exists() && resDir.isDirectory) {
                    findNonAlphaPng(resDir)
                }
            }

            Collections.sort(
                nonAlphaPngList,
                Comparator { entry1, entry2 ->
                    val file1Len = entry1.second
                    val file2Len = entry2.second
                    if (file1Len < file2Len) {
                        1
                    } else if (file1Len > file2Len) {
                        -1
                    } else {
                        0
                    }
                },
            )

            val jsonArray = JsonArray()
            for (entry in nonAlphaPngList) {
                if (!Util.isNullOrNil(entry.first)) {
                    val jsonObject = JsonObject()
                    jsonObject.addProperty("entry-name", entry.first)
                    jsonObject.addProperty("entry-size", entry.second)
                    jsonArray.add(jsonObject)
                }
            }
            (taskResult as TaskJsonResult).add("files", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.FindNonAlphaPngTask"
    }
}

