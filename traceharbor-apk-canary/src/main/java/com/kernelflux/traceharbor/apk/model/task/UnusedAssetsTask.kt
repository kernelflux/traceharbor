package com.kernelflux.traceharbor.apk.model.task

import com.google.gson.JsonArray
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.job.JobConstants
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.apk.model.task.util.ApkUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.MultiDexContainer
import java.io.File
import java.io.IOException
import java.util.HashSet

class UnusedAssetsTask(
    config: JobConfig,
    params: Map<String, String>,
) : ApkTask(config, params) {
    private var inputFile: File? = null
    private val dexFileNameList = ArrayList<String>()
    private val ignoreSet: MutableSet<String> = HashSet()
    private val assetsPathSet: MutableSet<String> = HashSet()
    private val assetRefSet: MutableSet<String> = HashSet()

    init {
        type = TaskFactory.TASK_TYPE_UNUSED_ASSETS
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
        } else if (inputFile?.isDirectory != true) {
            throw TaskInitException("$TAG---APK-UNZIP-PATH '$inputPath' is not directory!")
        }
        val currentParams = params ?: emptyMap()
        if (currentParams.containsKey(JobConstants.PARAM_IGNORE_ASSETS_LIST) && !Util.isNullOrNil(currentParams[JobConstants.PARAM_IGNORE_ASSETS_LIST])) {
            val ignoreAssets = currentParams[JobConstants.PARAM_IGNORE_ASSETS_LIST]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            Log.i(TAG, "ignore assets %d", ignoreAssets.size)
            for (ignore in ignoreAssets) {
                ignoreSet.add(Util.globToRegexp(ignore))
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

    @Throws(IOException::class)
    private fun findAssetsFile(dir: File?) {
        if (dir != null && dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        findAssetsFile(file)
                    } else {
                        Log.d(TAG, "find asset file %s", file.absolutePath)
                        assetsPathSet.add(file.absolutePath)
                    }
                }
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

    private fun readSmaliLines(lines: Array<String>?) {
        if (lines == null) {
            return
        }
        for (lineItem in lines) {
            var line = lineItem.trim { it <= ' ' }
            if (!Util.isNullOrNil(line) && line.startsWith("const-string")) {
                val columns = line.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (columns.size == 2) {
                    var assetFileName = columns[1].trim { it <= ' ' }
                    assetFileName = assetFileName.substring(1, assetFileName.length - 1)
                    if (!Util.isNullOrNil(assetFileName)) {
                        for (path in assetsPathSet) {
                            if (assetFileName.endsWith(path)) {
                                assetRefSet.add(path)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ignoreAsset(name: String): Boolean {
        for (pattern in ignoreSet) {
            Log.d(TAG, "pattern %s", pattern)
            if (name.matches(pattern.toRegex())) {
                return true
            }
        }
        return false
    }

    private fun generateAssetsSet(rootPath: String) {
        val relativeAssetsSet: HashSet<String> = HashSet()
        for (path in assetsPathSet) {
            val index = path.indexOf(rootPath)
            if (index >= 0) {
                val relativePath = path.substring(index + rootPath.length + 1)
                Log.d(TAG, "assets %s", relativePath)
                relativeAssetsSet.add(relativePath)
                if (ignoreAsset(relativePath)) {
                    Log.d(TAG, "ignore assets %s", relativePath)
                    assetRefSet.add(relativePath)
                }
            }
        }
        assetsPathSet.clear()
        assetsPathSet.addAll(relativeAssetsSet)
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult =
                TaskResultFactory.factory(type, TaskResultFactory.TASK_RESULT_TYPE_JSON, currentConfig)
                    ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val root = inputFile ?: throw TaskExecuteException("$TAG---input path invalid!")
            val assetDir = File(root, ApkConstants.ASSETS_DIR_NAME)
            findAssetsFile(assetDir)
            generateAssetsSet(assetDir.absolutePath)
            Log.i(TAG, "find all assets count: %d", assetsPathSet.size)
            decodeCode()
            Log.i(TAG, "find reference assets count: %d", assetRefSet.size)
            assetsPathSet.removeAll(assetRefSet)
            val jsonArray = JsonArray()
            for (name in assetsPathSet) {
                jsonArray.add(name)
            }
            (taskResult as TaskJsonResult).add("unused-assets", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.UnusedAssetsTask"
    }
}

