package com.kernelflux.traceharbor.apk.model.task

import com.android.dexdeps.ClassRef
import com.android.dexdeps.DexData
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.job.JobConstants
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_COUNT_CLASS
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.apk.model.task.util.ApkUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.HashMap
import java.util.HashSet

class CountClassTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private var group = JobConstants.GROUP_PACKAGE
    private val dexFileNameList = ArrayList<String>()
    private val dexFileList = ArrayList<RandomAccessFile>()

    init {
        type = TASK_TYPE_COUNT_CLASS
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()
        val inputPath = config?.unzipPath

        if (Util.isNullOrNil(inputPath)) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH can not be null!")
        }

        Log.i(TAG, "input path:%s", inputPath)

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

        val currentParams = params ?: emptyMap()
        if (currentParams.containsKey(JobConstants.PARAM_GROUP)) {
            if (JobConstants.GROUP_PACKAGE == currentParams[JobConstants.PARAM_GROUP]) {
                group = JobConstants.GROUP_PACKAGE
            } else {
                Log.e(TAG, "GROUP-BY '" + currentParams[JobConstants.PARAM_GROUP] + "' is not correct!")
            }
        }
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult =
                TaskResultFactory.factory(type, TaskResultFactory.TASK_RESULT_TYPE_JSON, currentConfig)
                    ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val classProguardMap = currentConfig.proguardClassMap ?: emptyMap()
            val dexFiles = JsonArray()

            for (i in dexFileList.indices) {
                val dexFile = dexFileList[i]
                val dexData = DexData(dexFile)
                dexData.load()
                dexFile.close()
                val defClassRefs: Array<ClassRef> = dexData.getInternalReferences()
                val classNameSet: MutableSet<String> = HashSet()
                for (classRef in defClassRefs) {
                    var className = ApkUtil.getNormalClassName(classRef.getName())
                    className = classProguardMap[className] ?: className
                    if (className.indexOf('.') == -1) {
                        continue
                    }
                    classNameSet.add(className)
                }
                val jsonObject = JsonObject()
                jsonObject.addProperty("dex-file", dexFileNameList[i])
                Log.d(TAG, "dex %s, classes %s", dexFileNameList[i], classNameSet.toString())

                val packageClass: MutableMap<String, MutableSet<String>> = HashMap()
                if (JobConstants.GROUP_PACKAGE == group) {
                    var packageName = ""
                    for (clazzName in classNameSet) {
                        packageName = ApkUtil.getPackageName(clazzName)
                        if (!Util.isNullOrNil(packageName)) {
                            if (!packageClass.containsKey(packageName)) {
                                packageClass[packageName] = HashSet()
                            }
                            packageClass[packageName]!!.add(clazzName)
                        }
                    }
                    val packages = JsonArray()
                    for ((key, value) in packageClass) {
                        val pkgObj = JsonObject()
                        pkgObj.addProperty("package", key)
                        val classArray = JsonArray()
                        for (clazz in value) {
                            classArray.add(clazz)
                        }
                        pkgObj.add("classes", classArray)
                        packages.add(pkgObj)
                    }
                    jsonObject.add("packages", packages)
                }
                dexFiles.add(jsonObject)
            }

            (taskResult as TaskJsonResult).add("dex-files", dexFiles)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    override fun getType(): Int = super.getType()

    companion object {
        private const val TAG = "TraceHarbor.CountClassTask"
    }
}

