package com.kernelflux.traceharbor.apk.model.task

import com.android.dexdeps.ClassRef
import com.android.dexdeps.DexData
import com.android.dexdeps.MethodRef
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
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_COUNT_METHOD
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.apk.model.task.util.ApkUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Collections
import java.util.Comparator
import java.util.LinkedList

class MethodCountTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private var group = JobConstants.GROUP_PACKAGE
    private val dexFileNameList = ArrayList<String>()
    private val dexFileList = ArrayList<RandomAccessFile>()
    private val classInternalMethod = HashMap<String, Int>()
    private val classExternalMethod = HashMap<String, Int>()
    private val pkgInternalRefMethod = HashMap<String, Int>()
    private val pkgExternalMethod = HashMap<String, Int>()

    init {
        type = TASK_TYPE_COUNT_METHOD
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
        } else if (inputFile?.isDirectory != true) {
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
            } else if (JobConstants.GROUP_CLASS == currentParams[JobConstants.PARAM_GROUP]) {
                group = JobConstants.GROUP_CLASS
            } else {
                Log.e(TAG, "GROUP-BY '" + currentParams[JobConstants.PARAM_GROUP] + "' is not correct!")
            }
        }
    }

    @Throws(IOException::class)
    private fun countDex(dexFile: RandomAccessFile) {
        classInternalMethod.clear()
        classExternalMethod.clear()
        pkgInternalRefMethod.clear()
        pkgExternalMethod.clear()
        val dexData = DexData(dexFile)
        dexData.load()
        val methodRefs: Array<MethodRef> = dexData.getMethodRefs()
        val externalClassRefs: Array<ClassRef> = dexData.getExternalReferences()
        val proguardClassMap = config?.proguardClassMap ?: emptyMap()
        var className: String
        for (classRef in externalClassRefs) {
            className = ApkUtil.getNormalClassName(classRef.getName())
            if (proguardClassMap.containsKey(className)) {
                className = proguardClassMap[className].toString()
            }
            if (className.indexOf('.') == -1) {
                continue
            }
            classExternalMethod[className] = 0
        }
        for (methodRef in methodRefs) {
            className = ApkUtil.getNormalClassName(methodRef.getDeclClassName())
            if (proguardClassMap.containsKey(className)) {
                className = proguardClassMap[className].toString()
            }
            if (!Util.isNullOrNil(className)) {
                if (className.indexOf('.') == -1) {
                    continue
                }
                if (classExternalMethod.containsKey(className)) {
                    classExternalMethod[className] = classExternalMethod[className]!! + 1
                } else if (classInternalMethod.containsKey(className)) {
                    classInternalMethod[className] = classInternalMethod[className]!! + 1
                } else {
                    classInternalMethod[className] = 1
                }
            }
        }

        // remove 0-method referenced class
        val iterator = classExternalMethod.keys.iterator()
        while (iterator.hasNext()) {
            if (classExternalMethod[iterator.next()] == 0) {
                iterator.remove()
            }
        }
    }

    private fun sortKeyByValue(map: Map<String, Int>): List<String> {
        val list: MutableList<String> = LinkedList()
        list.addAll(map.keys)
        Collections.sort(
            list,
            Comparator { class1, class2 ->
                if (map[class1]!! > map[class2]!!) {
                    -1
                } else if (map[class1]!! < map[class2]!!) {
                    1
                } else {
                    0
                }
            },
        )
        return list
    }

    private fun sumOfValue(map: Map<String, Int>): Int {
        var sum = 0
        for (value in map.values) {
            sum += value
        }
        return sum
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult = TaskResultFactory.factory(getType(), TASK_RESULT_TYPE_JSON, currentConfig) ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val jsonArray = JsonArray()
            for (i in dexFileList.indices) {
                val dexFile = dexFileList[i]
                countDex(dexFile)
                dexFile.close()
                val totalInternalMethods = sumOfValue(classInternalMethod)
                val totalExternalMethods = sumOfValue(classExternalMethod)
                val jsonObject = JsonObject()
                jsonObject.addProperty("dex-file", dexFileNameList[i])

                if (JobConstants.GROUP_CLASS == group) {
                    val sortList = sortKeyByValue(classInternalMethod)
                    val classes = JsonArray()
                    for (className in sortList) {
                        val classObj = JsonObject()
                        classObj.addProperty("name", className)
                        classObj.addProperty("methods", classInternalMethod[className])
                        classes.add(classObj)
                    }
                    jsonObject.add("internal-classes", classes)
                } else if (JobConstants.GROUP_PACKAGE == group) {
                    var packageName: String
                    for ((key, value) in classInternalMethod) {
                        packageName = ApkUtil.getPackageName(key)
                        if (!Util.isNullOrNil(packageName)) {
                            if (!pkgInternalRefMethod.containsKey(packageName)) {
                                pkgInternalRefMethod[packageName] = value
                            } else {
                                pkgInternalRefMethod[packageName] = pkgInternalRefMethod[packageName]!! + value
                            }
                        }
                    }
                    val sortList = sortKeyByValue(pkgInternalRefMethod)
                    val packages = JsonArray()
                    for (pkgName in sortList) {
                        val pkgObj = JsonObject()
                        pkgObj.addProperty("name", pkgName)
                        pkgObj.addProperty("methods", pkgInternalRefMethod[pkgName])
                        packages.add(pkgObj)
                    }
                    jsonObject.add("internal-packages", packages)
                }
                jsonObject.addProperty("total-internal-classes", classInternalMethod.size)
                jsonObject.addProperty("total-internal-methods", totalInternalMethods)

                if (JobConstants.GROUP_CLASS == group) {
                    val sortList = sortKeyByValue(classExternalMethod)
                    val classes = JsonArray()
                    for (className in sortList) {
                        val classObj = JsonObject()
                        classObj.addProperty("name", className)
                        classObj.addProperty("methods", classExternalMethod[className])
                        classes.add(classObj)
                    }
                    jsonObject.add("external-classes", classes)
                } else if (JobConstants.GROUP_PACKAGE == group) {
                    var packageName = ""
                    for ((key, value) in classExternalMethod) {
                        packageName = ApkUtil.getPackageName(key)
                        if (!Util.isNullOrNil(packageName)) {
                            if (!pkgExternalMethod.containsKey(packageName)) {
                                pkgExternalMethod[packageName] = value
                            } else {
                                pkgExternalMethod[packageName] = pkgExternalMethod[packageName]!! + value
                            }
                        }
                    }
                    val sortList = sortKeyByValue(pkgExternalMethod)
                    val packages = JsonArray()
                    for (pkgName in sortList) {
                        val pkgObj = JsonObject()
                        pkgObj.addProperty("name", pkgName)
                        pkgObj.addProperty("methods", pkgExternalMethod[pkgName])
                        packages.add(pkgObj)
                    }
                    jsonObject.add("external-packages", packages)
                }
                jsonObject.addProperty("total-external-classes", classExternalMethod.size)
                jsonObject.addProperty("total-external-methods", totalExternalMethods)
                jsonArray.add(jsonObject)
            }
            (taskResult as TaskJsonResult).add("dex-files", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.MethodCountTask"
    }
}

