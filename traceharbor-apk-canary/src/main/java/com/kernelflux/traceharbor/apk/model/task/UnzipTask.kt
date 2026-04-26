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
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory.TASK_RESULT_TYPE_JSON
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_UNZIP
import com.kernelflux.traceharbor.apk.model.task.util.ResguardUtil
import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import org.apache.commons.io.FileUtils
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.util.Enumeration
import java.util.HashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class UnzipTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    @Suppress("PMD")
    private var inputFile: File? = null
    private var outputFile: File? = null
    private var mappingTxt: File? = null
    private var resMappingTxt: File? = null
    private val proguardClassMap: MutableMap<String, String> = HashMap()
    private val resguardMap: MutableMap<String, String> = HashMap()
    private val resDirMap: MutableMap<String, String> = HashMap()
    private val entryNameMap: MutableMap<String, String> = HashMap()
    private val entrySizeMap: MutableMap<String, Pair<Long, Long>> = HashMap()

    init {
        type = TASK_TYPE_UNZIP
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()
        val currentConfig = config ?: throw TaskInitException("$TAG---jobConfig can not be null!")
        val apkPath = currentConfig.apkPath
        if (apkPath.isNullOrEmpty()) {
            throw TaskInitException("$TAG---APK-FILE-PATH can not be null!")
        }
        Log.i(TAG, "inputPath:%s", apkPath)
        inputFile = File(apkPath)
        if (inputFile?.exists() != true) {
            throw TaskInitException("$TAG---'$apkPath' is not exist!")
        }
        val unzipPath = currentConfig.unzipPath
        if (unzipPath.isNullOrEmpty()) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH can not be null!")
        }
        Log.i(TAG, "outputPath:%s", unzipPath)
        outputFile = File(unzipPath)
        val mappingFilePath = currentConfig.mappingFilePath
        if (!mappingFilePath.isNullOrEmpty()) {
            mappingTxt = File(mappingFilePath)
            if (!FileUtil.isLegalFile(mappingTxt)) {
                throw TaskInitException("$TAG---mapping file $mappingFilePath is not legal!")
            }
        }
        val resMappingFilePath = currentConfig.resMappingFilePath
        if (!resMappingFilePath.isNullOrEmpty()) {
            resMappingTxt = File(resMappingFilePath)
            if (!FileUtil.isLegalFile(resMappingTxt)) {
                throw TaskInitException("$TAG---resguard mapping file $resMappingFilePath is not legal!")
            }
        }
    }

    @Throws(IOException::class)
    private fun readMappingTxtFile() {
        mappingTxt?.apply {
            val bufferedReader = BufferedReader(FileReader(this))
            var line = bufferedReader.readLine()
            var beforeClass: String
            var afterClass: String
            bufferedReader.use { bufferedReader ->
                while (line != null) {
                    if (!line.startsWith(" ")) {
                        val pair =
                            line.split("->".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (pair.size == 2) {
                            beforeClass = pair[0].trim { it <= ' ' }
                            afterClass = pair[1].trim { it <= ' ' }
                            afterClass = afterClass.substring(0, afterClass.length - 1)
                            if (!Util.isNullOrNil(beforeClass) && !Util.isNullOrNil(afterClass)) {
                                Log.d(TAG, "before:%s,after:%s", beforeClass, afterClass)
                                proguardClassMap[afterClass] = beforeClass
                            }
                        }
                    }
                    line = bufferedReader.readLine()
                }
            }
        }
    }

    private fun parseResourceNameFromPath(dir: String, filename: String): String {
        if (Util.isNullOrNil(dir) || Util.isNullOrNil(filename)) {
            return ""
        }

        var type = dir.substring(dir.indexOf('/') + 1)
        var index = type.indexOf('-')
        if (index >= 0) {
            type = type.substring(0, index)
        }
        var fileNameValue = filename
        index = fileNameValue.indexOf('.')
        if (index >= 0) {
            fileNameValue = fileNameValue.substring(0, index)
        }
        return "R.$type.$fileNameValue"
    }

    private fun reverseResguard(dirName: String, filename: String): String {
        var outEntryName = ""
        var fileNameValue = filename
        if (resDirMap.containsKey(dirName)) {
            val newDirName = resDirMap[dirName].toString()
            val resource = parseResourceNameFromPath(newDirName, fileNameValue)
            val suffixIndex = fileNameValue.indexOf('.')
            var suffix = ""
            if (suffixIndex >= 0) {
                suffix = fileNameValue.substring(suffixIndex)
            }
            if (resguardMap.containsKey(resource)) {
                val lastIndex = resguardMap[resource].toString().lastIndexOf('.')
                if (lastIndex >= 0) {
                    fileNameValue =
                        resguardMap[resource].toString().substring(lastIndex + 1) + suffix
                }
            }
            outEntryName = "$newDirName/$fileNameValue"
        }
        return outEntryName
    }

    @Throws(IOException::class)
    private fun writeEntry(zipFile: ZipFile, entry: ZipEntry): String? {
        val entryName = entry.name
        val output = outputFile ?: return null
        if (Util.preventZipSlip(output, entryName)) {
            Log.e(TAG, "writeEntry entry %s failed!", entryName)
            return null
        }

        val readBuffer = ByteArray(4096)
        var bufferedOutput: BufferedOutputStream? = null
        var zipInputStream: InputStream? = null
        var outEntryName: String?
        val file: File?
        val index = entryName.lastIndexOf('/')
        if (index >= 0) {
            val filename = entryName.substring(index + 1)
            val dirName = entryName.substring(0, index)
            val dir = File(output, dirName)
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "%s mkdirs failed!", dir.absolutePath)
                return null
            }
            if (!Util.isNullOrNil(filename)) {
                file = File(dir, filename)
                outEntryName = reverseResguard(dirName, filename)
                if (Util.isNullOrNil(outEntryName)) {
                    outEntryName = entryName
                }
            } else {
                file = null
                outEntryName = null
            }
        } else {
            file = File(output, entryName)
            outEntryName = entryName
        }
        try {
            if (file != null) {
                if (!file.createNewFile()) {
                    Log.e(TAG, "create file %s failed!", file.absolutePath)
                    return null
                }
                bufferedOutput = BufferedOutputStream(FileOutputStream(file))
                zipInputStream = zipFile.getInputStream(entry)
                var readSize = zipInputStream.read(readBuffer)
                while (readSize != -1) {
                    bufferedOutput.write(readBuffer, 0, readSize)
                    readSize = zipInputStream.read(readBuffer)
                }
            } else {
                return null
            }
        } finally {
            zipInputStream?.close()
            bufferedOutput?.close()
        }
        return outEntryName
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        var zipFile: ZipFile? = null
        try {
            val input = inputFile ?: throw TaskExecuteException("$TAG---input file invalid!")
            val output = outputFile ?: throw TaskExecuteException("$TAG---output file invalid!")
            val currentConfig =
                config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            zipFile = ZipFile(input)
            if (output.isDirectory && output.exists()) {
                Log.i(TAG, "%s exists, delete it.", output.absolutePath)
                FileUtils.deleteDirectory(output)
            } else if (output.isFile) {
                throw TaskExecuteException("$TAG---File '" + output.absolutePath + "' is already exists!")
            }
            val taskResult =
                TaskResultFactory.factory(getType(), TASK_RESULT_TYPE_JSON, currentConfig)
                    ?: throw TaskExecuteException("$TAG---task result create failed!")
            val jsonResult = taskResult as TaskJsonResult
            val startTime = System.currentTimeMillis()
            if (!output.mkdir()) {
                throw TaskExecuteException("$TAG---Create directory '" + output.absolutePath + "' failed!")
            }

            jsonResult.add("total-size", input.length())

            readMappingTxtFile()
            currentConfig.proguardClassMap = proguardClassMap
            ResguardUtil.readResMappingTxtFile(resMappingTxt, resDirMap, resguardMap)
            currentConfig.resguardMap = resguardMap

            val entries: Enumeration<out ZipEntry> = zipFile.entries()
            val jsonArray = JsonArray()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outEntryName = writeEntry(zipFile, entry)
                if (!Util.isNullOrNil(outEntryName)) {
                    val outName = outEntryName ?: continue
                    val fileItem = JsonObject()
                    fileItem.addProperty("entry-name", outName)
                    fileItem.addProperty("entry-size", entry.compressedSize)
                    jsonArray.add(fileItem)
                    entrySizeMap[outName] = Pair.of(entry.size, entry.compressedSize)
                    entryNameMap[entry.name] = outName
                }
            }

            currentConfig.entrySizeMap = entrySizeMap
            currentConfig.entryNameMap = entryNameMap
            jsonResult.add("entries", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.UnZipTask"
    }
}

