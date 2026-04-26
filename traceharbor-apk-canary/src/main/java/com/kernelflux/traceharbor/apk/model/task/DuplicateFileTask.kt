package com.kernelflux.traceharbor.apk.model.task

import com.android.utils.Pair
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Collections
import java.util.Comparator

class DuplicateFileTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private lateinit var md5Map: MutableMap<String, MutableList<String>>
    private lateinit var fileSizeList: MutableList<Pair<String, Long>>
    private var entrySizeMap: Map<String, Pair<Long, Long>> = emptyMap()
    private var entryNameMap: Map<String, String> = emptyMap()

    init {
        type = TaskFactory.TASK_TYPE_DUPLICATE_FILE
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
        md5Map = HashMap()
        fileSizeList = ArrayList()
        val currentConfig = config
        entrySizeMap = currentConfig?.entrySizeMap ?: emptyMap()
        entryNameMap = currentConfig?.entryNameMap ?: emptyMap()
    }

    @Throws(NoSuchAlgorithmException::class, IOException::class)
    private fun computeMD5(file: File?) {
        if (file != null) {
            if (file.isDirectory) {
                val files = file.listFiles()
                if (files != null) {
                    for (resFile in files) {
                        computeMD5(resFile)
                    }
                }
            } else {
                val msgDigest = MessageDigest.getInstance("MD5")
                val inputStream = BufferedInputStream(FileInputStream(file))
                val buffer = ByteArray(512)
                var totalRead: Long = 0
                var readSize = inputStream.read(buffer)
                while (readSize > 0) {
                    msgDigest.update(buffer, 0, readSize)
                    totalRead += readSize.toLong()
                    readSize = inputStream.read(buffer)
                }
                inputStream.close()
                if (totalRead > 0) {
                    val md5 = Util.byteArrayToHex(msgDigest.digest())
                    val root = inputFile ?: return
                    var filename = file.absolutePath.substring(root.absolutePath.length + 1)
                    if (entryNameMap.containsKey(filename)) {
                        filename = entryNameMap[filename].toString()
                    }
                    if (!md5Map.containsKey(md5)) {
                        md5Map[md5] = ArrayList()
                        if (entrySizeMap.containsKey(filename)) {
                            fileSizeList.add(Pair.of(md5, entrySizeMap[filename]!!.first))
                        } else {
                            fileSizeList.add(Pair.of(md5, totalRead))
                        }
                    }
                    md5Map[md5]!!.add(filename)
                }
            }
        }
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult =
                TaskResultFactory.factory(getType(), TaskResultFactory.TASK_RESULT_TYPE_JSON, currentConfig)
                    ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val jsonArray = JsonArray()

            computeMD5(inputFile)

            Collections.sort(
                fileSizeList,
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

            for (entry in fileSizeList) {
                if (md5Map[entry.first]!!.size > 1) {
                    val jsonObject = JsonObject()
                    jsonObject.addProperty("md5", entry.first)
                    jsonObject.addProperty("size", entry.second)
                    val jsonFiles = JsonArray()
                    for (filename in md5Map[entry.first]!!) {
                        jsonFiles.add(filename)
                    }
                    jsonObject.add("files", jsonFiles)
                    jsonArray.add(jsonObject)
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
        private const val TAG = "TraceHarbor.DuplicateFileTask"
    }
}

