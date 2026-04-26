package com.kernelflux.traceharbor.apk.model.task

import brut.androlib.AndrolibException
import com.google.gson.JsonArray
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.job.JobConstants
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.apk.model.task.util.ApkResourceDecoder
import com.kernelflux.traceharbor.apk.model.task.util.ApkUtil
import com.kernelflux.traceharbor.apk.model.task.util.ResguardUtil
import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.MultiDexContainer
import org.jf.baksmali.BaksmaliOptions
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.HashMap
import java.util.HashSet
import java.util.Stack
import java.util.regex.Pattern

class UnusedResourcesTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private var resourceTxt: File? = null
    private var mappingTxt: File? = null
    private var resMappingTxt: File? = null
    private val dexFileNameList = ArrayList<String>()
    private val rclassProguardMap: MutableMap<String, String> = HashMap()
    private val resguardMap: MutableMap<String, String> = HashMap()
    private val resourceDefMap: MutableMap<String, String> = HashMap()
    private val styleableMap: MutableMap<String, MutableSet<String>> = HashMap()
    private val resourceRefSet: MutableSet<String> = HashSet()
    private val unusedResSet: MutableSet<String> = HashSet()
    private val ignoreSet: MutableSet<String> = HashSet()
    private val nonValueReferences: MutableMap<String, MutableSet<String>> = HashMap()
    private var visitPath = Stack<String>()

    init {
        type = TaskFactory.TASK_TYPE_UNUSED_RESOURCES
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()

        val currentConfig = config ?: throw TaskInitException("$TAG---jobConfig can not be null!")
        val inputPath = currentConfig.unzipPath
        if (Util.isNullOrNil(inputPath)) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH can not be null!")
        }
        val currentParams = params ?: emptyMap()
        if (!currentParams.containsKey(JobConstants.PARAM_R_TXT) || Util.isNullOrNil(currentParams[JobConstants.PARAM_R_TXT])) {
            throw TaskInitException("$TAG---The File 'R.txt' can not be null!")
        }
        resourceTxt = File(currentParams[JobConstants.PARAM_R_TXT])
        if (!FileUtil.isLegalFile(resourceTxt)) {
            throw TaskInitException("$TAG---The Resource declarations file 'R.txt' is not legal!")
        }
        inputFile = File(inputPath)
        if (inputFile?.exists() != true) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath' is not exist!")
        } else if (inputFile?.isDirectory != true) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath' is not directory!")
        }
        if (!Util.isNullOrNil(currentConfig.mappingFilePath)) {
            mappingTxt = File(currentConfig.mappingFilePath)
            if (!FileUtil.isLegalFile(mappingTxt)) {
                throw TaskInitException("$TAG---The Proguard mapping file 'mapping.txt' is not legal!")
            }
        }
        if (currentParams.containsKey(JobConstants.PARAM_IGNORE_RESOURCES_LIST) && !Util.isNullOrNil(currentParams[JobConstants.PARAM_IGNORE_RESOURCES_LIST])) {
            val ignoreRes = currentParams[JobConstants.PARAM_IGNORE_RESOURCES_LIST]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (ignore in ignoreRes) {
                ignoreSet.add(Util.globToRegexp(ignore))
            }
        }
        if (!Util.isNullOrNil(currentConfig.resMappingFilePath)) {
            resMappingTxt = File(currentConfig.resMappingFilePath)
            if (!FileUtil.isLegalFile(resMappingTxt)) {
                throw TaskInitException("$TAG---The Resguard mapping file 'resguard-mapping.txt' is not legal!")
            }
        }

        val files = inputFile?.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isFile && file.name.endsWith(ApkConstants.DEX_FILE_SUFFIX)) {
                    dexFileNameList.add(file.name)
                }
            }
        }
    }

    private fun parseResourceId(resId: String?): String {
        if (!Util.isNullOrNil(resId) && resId!!.startsWith("0x")) {
            if (resId.length == 10) {
                return resId
            } else if (resId.length < 10) {
                val strBuilder = StringBuilder(resId)
                for (i in 0 until 10 - resId.length) {
                    strBuilder.append('0')
                }
                return strBuilder.toString()
            }
        }
        return ""
    }

    private fun parseResourceNameFromProguard(entry: String?): String {
        if (!Util.isNullOrNil(entry)) {
            val columns = entry!!.split("->".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (columns.size == 2) {
                val index = columns[1].indexOf(':')
                if (index >= 0) {
                    val className = ApkUtil.getNormalClassName(columns[0])
                    val fieldName = columns[1].substring(0, index)
                    if (rclassProguardMap.isNotEmpty()) {
                        val resource = className.replace('$', '.') + "." + fieldName
                        if (rclassProguardMap.containsKey(resource)) {
                            return rclassProguardMap[resource].toString()
                        } else {
                            val matcher = sRClassPattern.matcher(className)
                            if (matcher.find()) {
                                val resultBuilder = StringBuilder()
                                resultBuilder.append("R.")
                                resultBuilder.append(matcher.group(3))
                                resultBuilder.append(".")
                                resultBuilder.append(fieldName)
                                return resultBuilder.toString()
                            } else {
                                return ""
                            }
                        }
                    } else {
                        if (ApkUtil.isRClassName(ApkUtil.getPureClassName(className))) {
                            return (ApkUtil.getPureClassName(className) + "." + fieldName).replace('$', '.')
                        }
                    }
                }
            }
        }
        return ""
    }

    @Throws(IOException::class)
    private fun readResourceTxtFile() {
        val resourceFile = resourceTxt ?: return
        val bufferedReader = BufferedReader(FileReader(resourceFile))
        var line = bufferedReader.readLine()
        try {
            while (line != null) {
                val columns = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (columns.size >= 4) {
                    val resourceName = "R." + columns[1] + "." + columns[2]
                    if (!columns[0].endsWith("[]") && columns[3].startsWith("0x")) {
                        if (columns[3].startsWith("0x01")) {
                            Log.d(TAG, "ignore system resource %s", resourceName)
                        } else {
                            val resId = parseResourceId(columns[3])
                            if (!Util.isNullOrNil(resId)) {
                                resourceDefMap[resId] = resourceName
                            }
                        }
                    } else {
                        Log.d(TAG, "ignore resource %s", resourceName)
                        if (columns[0].endsWith("[]") && columns.size > 5) {
                            val attrReferences: MutableSet<String> = HashSet()
                            for (i in 4 until columns.size) {
                                if (columns[i].endsWith(",")) {
                                    attrReferences.add(columns[i].substring(0, columns[i].length - 1))
                                } else {
                                    attrReferences.add(columns[i])
                                }
                            }
                            styleableMap[resourceName] = attrReferences
                        }
                    }
                }
                line = bufferedReader.readLine()
            }
        } finally {
            bufferedReader.close()
        }
    }

    @Throws(IOException::class)
    private fun readMappingTxtFile() {
        if (mappingTxt != null) {
            val bufferedReader = BufferedReader(FileReader(mappingTxt))
            var line = bufferedReader.readLine()
            var readRField = false
            var beforeClass = ""
            var afterClass = ""
            try {
                while (line != null) {
                    if (!line.startsWith(" ")) {
                        val pair = line.split("->".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (pair.size == 2) {
                            beforeClass = pair[0].trim { it <= ' ' }
                            afterClass = pair[1].trim { it <= ' ' }
                            afterClass = afterClass.substring(0, afterClass.length - 1)
                            if (!Util.isNullOrNil(beforeClass) && !Util.isNullOrNil(afterClass) && ApkUtil.isRClassName(ApkUtil.getPureClassName(beforeClass))) {
                                Log.d(TAG, "before:%s,after:%s", beforeClass, afterClass)
                                readRField = true
                            } else {
                                readRField = false
                            }
                        } else {
                            readRField = false
                        }
                    } else {
                        if (readRField) {
                            val entry = line.split("->".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                            if (entry.size == 2) {
                                val key = entry[0].trim { it <= ' ' }
                                val value = entry[1].trim { it <= ' ' }
                                if (!Util.isNullOrNil(key) && !Util.isNullOrNil(value)) {
                                    val field = key.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                    if (field.size == 2) {
                                        Log.d(
                                            TAG,
                                            "%s -> %s",
                                            afterClass.replace('$', '.') + "." + value,
                                            ApkUtil.getPureClassName(beforeClass).replace('$', '.') + "." + field[1],
                                        )
                                        rclassProguardMap[afterClass.replace('$', '.') + "." + value] =
                                            ApkUtil.getPureClassName(beforeClass).replace('$', '.') + "." + field[1]
                                    }
                                }
                            }
                        }
                    }
                    line = bufferedReader.readLine()
                }
            } finally {
                bufferedReader.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun decodeCode() {
        val root = inputFile ?: return
        for (dexFileName in dexFileNameList) {
            val dexFiles: MultiDexContainer<out DexBackedDexFile> = DexFileFactory.loadDexContainer(File(root, dexFileName), Opcodes.forApi(15))

            for (dexEntryName in dexFiles.dexEntryNames) {
                val dexEntry: MultiDexContainer.DexEntry<out DexBackedDexFile>? = dexFiles.getEntry(dexEntryName)
                if (dexEntry == null) {
                    continue
                }
                val options = BaksmaliOptions()
                val classDefs: kotlin.collections.Set<out ClassDef> = dexEntry.dexFile.classes

                for (classDef in classDefs) {
                    val lines = ApkUtil.disassembleClass(classDef, options)
                    if (lines != null) {
                        readSmaliLines(lines)
                    }
                }
            }
        }
    }

    /*
        1. const
        const v6, 0x7f0c0061

        2. sget
        sget v6, Lcom/kernelflux/mm/R$string;->chatting_long_click_menu_revoke_msg:I
        sget v1, Lcom/kernelflux/mm/libmmui/R$id;->property_anim:I

        3. sput
        sput-object v0, Lcom/kernelflux/mm/plugin_welab_api/R$styleable;->ActionBar:[I   //define resource in R.java

        4. array-data
        :array_0
        .array-data 4
            0x7f0a0022
            0x7f0a0023
        .end array-data
    */
    private fun readSmaliLines(lines: Array<String>?) {
        if (lines == null) {
            return
        }
        var arrayData = false
        for (lineItem in lines) {
            val line = lineItem.trim { it <= ' ' }
            if (!Util.isNullOrNil(line)) {
                if (line.startsWith("const")) {
                    val columns = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (columns.size >= 3) {
                        val resId = parseResourceId(columns[2].trim { it <= ' ' })
                        if (!Util.isNullOrNil(resId) && resourceDefMap.containsKey(resId)) {
                            resourceRefSet.add(resourceDefMap[resId].toString())
                        }
                    }
                } else if (line.startsWith("sget")) {
                    val columns = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (columns.size >= 3) {
                        val resourceRef = parseResourceNameFromProguard(columns[2].trim { it <= ' ' })
                        if (!Util.isNullOrNil(resourceRef)) {
                            Log.d(TAG, "find resource reference %s", resourceRef)
                            if (styleableMap.containsKey(resourceRef)) {
                                // reference of R.styleable.XXX
                                for (attr in styleableMap[resourceRef]!!) {
                                    resourceRefSet.add(resourceDefMap[attr].toString())
                                }
                            } else {
                                resourceRefSet.add(resourceRef)
                            }
                        }
                    }
                } else if (line.startsWith(".array-data 4")) {
                    arrayData = true
                } else if (line.startsWith(".end array-data")) {
                    arrayData = false
                } else {
                    if (arrayData) {
                        val columns = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (columns.isNotEmpty()) {
                            val resId = parseResourceId(columns[0].trim { it <= ' ' })
                            if (!Util.isNullOrNil(resId) && resourceDefMap.containsKey(resId)) {
                                Log.d(TAG, "array field resource, %s", resId)
                                resourceRefSet.add(resourceDefMap[resId].toString())
                            }
                        }
                        if (line.trim { it <= ' ' }.startsWith("0x")) {
                            val resId = parseResourceId(line.trim { it <= ' ' })
                            if (!Util.isNullOrNil(resId) && resourceDefMap.containsKey(resId)) {
                                Log.d(TAG, "array field resource, %s", resId)
                                resourceRefSet.add(resourceDefMap[resId].toString())
                            }
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class, InterruptedException::class, AndrolibException::class, XmlPullParserException::class)
    private fun decodeResources() {
        val root = inputFile ?: return
        val manifestFile = File(root, ApkConstants.MANIFEST_FILE_NAME)
        val arscFile = File(root, ApkConstants.ARSC_FILE_NAME)
        var resDir = File(root, ApkConstants.RESOURCE_DIR_NAME)
        if (!resDir.exists()) {
            resDir = File(root, ApkConstants.RESOURCE_DIR_PROGUARD_NAME)
        }

        val fileResMap: MutableMap<String, MutableSet<String>> = HashMap()
        val valuesReferences: MutableSet<String> = HashSet()

        ApkResourceDecoder.decodeResourcesRef(manifestFile, arscFile, resDir, fileResMap, valuesReferences)

        for (resource in fileResMap.keys) {
            val result: MutableSet<String> = HashSet()
            for (resName in fileResMap[resource]!!) {
                if (resguardMap.containsKey(resName)) {
                    result.add(resguardMap[resName].toString())
                } else {
                    result.add(resName)
                }
            }
            if (resguardMap.containsKey(resource)) {
                nonValueReferences[resguardMap[resource].toString()] = result
            } else {
                nonValueReferences[resource] = result
            }
        }

        for (resource in valuesReferences) {
            if (resguardMap.containsKey(resource)) {
                resourceRefSet.add(resguardMap[resource].toString())
            } else {
                resourceRefSet.add(resource)
            }
        }

        for (resource in unusedResSet) {
            if (ignoreResource(resource)) {
                resourceRefSet.add(resource)
            }
        }

        for (resource in resourceRefSet) {
            readChildReference(resource)
        }
    }

    private fun ignoreResource(name: String): Boolean {
        for (pattern in ignoreSet) {
            if (name.matches(pattern.toRegex())) {
                return true
            }
        }
        return false
    }

    @Throws(IllegalStateException::class)
    private fun readChildReference(resource: String) {
        if (nonValueReferences.containsKey(resource)) {
            visitPath.push(resource)
            val childReference = nonValueReferences[resource]
            if (childReference != null) {
                unusedResSet.removeAll(childReference)
                for (reference in childReference) {
                    if (!visitPath.contains(reference)) {
                        readChildReference(reference)
                    } else {
                        visitPath.push(reference)
                        throw IllegalStateException("Found resource cycle! " + visitPath.toString())
                    }
                }
            }
            visitPath.pop()
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
            readMappingTxtFile()
            readResourceTxtFile()
            ResguardUtil.readResMappingTxtFile(resMappingTxt, null, resguardMap)
            unusedResSet.addAll(resourceDefMap.values)
            Log.i(TAG, "find resource declarations %d items.", unusedResSet.size)
            decodeCode()
            Log.i(TAG, "find resource references in classes: %d items.", resourceRefSet.size)
            decodeResources()
            Log.i(TAG, "find resource references %d items.", resourceRefSet.size)
            unusedResSet.removeAll(resourceRefSet)
            Log.i(TAG, "find unused references %d items", unusedResSet.size)
            Log.d(TAG, "find unused references %s", unusedResSet.toString())
            val jsonArray = JsonArray()
            for (name in unusedResSet) {
                jsonArray.add(name)
            }
            (taskResult as TaskJsonResult).add("unused-resources", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.UnusedResourcesTask"
        private val sRClassPattern = Pattern.compile("(([a-zA-Z0-9_]*\\.)*)R\\$([a-z]+)")
    }
}

