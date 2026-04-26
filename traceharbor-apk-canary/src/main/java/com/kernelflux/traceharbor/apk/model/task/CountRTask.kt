package com.kernelflux.traceharbor.apk.model.task

import com.android.dexdeps.ClassRef
import com.android.dexdeps.DexData
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_COUNT_R_CLASS
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.apk.model.task.util.ApkUtil
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile

class CountRTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private val dexFileNameList = ArrayList<String>()
    private val dexFileList = ArrayList<RandomAccessFile>()
    private val classesMap = HashMap<String, Int>()

    init {
        type = TASK_TYPE_COUNT_R_CLASS
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
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath' is not exist!")
        }
        if (inputFile?.isDirectory != true) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath' is not directory!")
        }

        val files = inputFile?.listFiles()
        try {
            if (files != null) {
                for (file in files) {
                    if (file.isFile && file.name.endsWith(ApkConstants.DEX_FILE_SUFFIX)) {
                        dexFileNameList.add(file.name)
                        val randomAccessFile = RandomAccessFile(file, "rw")
                        dexFileList.add(randomAccessFile)
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            throw TaskInitException(e.message ?: "", e)
        }
    }

    private fun getOuterClassName(className: String?): String? {
        var result = className
        if (!Util.isNullOrNil(result)) {
            val index = result!!.indexOf('$')
            if (index >= 0) {
                result = result.substring(0, index)
            }
        }
        return result
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult = TaskResultFactory.factory(type, TaskResultFactory.TASK_RESULT_TYPE_JSON, currentConfig) ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val classProguardMap = currentConfig.proguardClassMap ?: emptyMap()
            for (dexFile in dexFileList) {
                val dexData = DexData(dexFile)
                dexData.load()
                dexFile.close()
                val defClassRefs: Array<ClassRef> = dexData.getInternalReferences()
                for (classRef in defClassRefs) {
                    var className = ApkUtil.getNormalClassName(classRef.getName())
                    className = classProguardMap[className] ?: className
                    val pureClassName = getOuterClassName(className)
                    if (pureClassName != null && (pureClassName.endsWith(".R") || "R" == pureClassName)) {
                        if (!classesMap.containsKey(pureClassName)) {
                            classesMap[pureClassName] = classRef.getFieldArray().size
                        } else {
                            classesMap[pureClassName] = classesMap[pureClassName]!! + classRef.getFieldArray().size
                        }
                    }
                }
            }

            val jsonArray = JsonArray()
            var totalSize: Long = 0
            val proguardClassMap = currentConfig.proguardClassMap ?: emptyMap()
            for (entry in classesMap.entries) {
                val jsonObject = JsonObject()
                if (proguardClassMap.containsKey(entry.key)) {
                    jsonObject.addProperty("name", proguardClassMap[entry.key])
                } else {
                    jsonObject.addProperty("name", entry.key)
                }
                jsonObject.addProperty("field-count", entry.value)
                totalSize += entry.value.toLong()
                jsonArray.add(jsonObject)
            }
            val jsonResult = taskResult as TaskJsonResult
            jsonResult.add("R-count", jsonArray.size())
            jsonResult.add("Field-counts", totalSize)

            jsonResult.add("R-classes", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.CountRTask"
    }
}

