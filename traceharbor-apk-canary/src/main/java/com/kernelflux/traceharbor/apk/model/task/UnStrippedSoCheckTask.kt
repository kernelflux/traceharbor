package com.kernelflux.traceharbor.apk.model.task

import com.google.gson.JsonArray
import com.kernelflux.traceharbor.apk.model.exception.TaskExecuteException
import com.kernelflux.traceharbor.apk.model.exception.TaskInitException
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.job.JobConstants
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory.TASK_RESULT_TYPE_JSON
import com.kernelflux.traceharbor.apk.model.task.TaskFactory.TASK_TYPE_UNSTRIPPED_SO
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

class UnStrippedSoCheckTask(
    jobConfig: JobConfig,
    params: Map<String, String>,
) : ApkTask(jobConfig, params) {
    private var libDir: File? = null
    private var toolnmPath: String? = null

    init {
        type = TASK_TYPE_UNSTRIPPED_SO
    }

    @Throws(TaskInitException::class)
    override fun init() {
        super.init()
        val inputPath = config?.unzipPath
        val currentParams = params ?: throw TaskInitException("$TAG---params can not be null!")
        toolnmPath = currentParams[JobConstants.PARAM_TOOL_NM]
        if (Util.isNullOrNil(toolnmPath)) {
            throw TaskInitException("$TAG---The path of tool 'nm' is not given!")
        } else {
            val envPattern = Pattern.compile("(\\$[a-zA-Z_-]+)")
            val matcher = envPattern.matcher(toolnmPath ?: "")
            while (matcher.find()) {
                if (!Util.isNullOrNil(matcher.group())) {
                    val env = System.getenv(matcher.group().substring(1))
                    Log.d(TAG, "%s -> %s", matcher.group().substring(1), env)
                    if (!Util.isNullOrNil(env)) {
                        toolnmPath = toolnmPath?.replace(matcher.group(), env)
                    }
                }
            }
            Log.i(TAG, "toolnm pah is %s", toolnmPath)
        }
        val resolvedToolnmPath = toolnmPath ?: throw TaskInitException("$TAG---The path of tool 'nm' is not given!")
        if (!FileUtil.isLegalFile(resolvedToolnmPath)) {
            throw TaskInitException("$TAG---Can not find the tool 'nm'!")
        }
        if (!Util.isNullOrNil(inputPath)) {
            Log.i(TAG, "inputPath:%s", inputPath)
            libDir = File(inputPath, "lib")
        } else {
            throw TaskInitException("$TAG---APK-UNZIP-PATH can not be null!")
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun isSoStripped(libFile: File): Boolean {
        val processBuilder = ProcessBuilder(toolnmPath ?: "", libFile.absolutePath)
        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.errorStream))
        val line = reader.readLine()
        var result = false
        if (!Util.isNullOrNil(line)) {
            Log.d(TAG, "%s", line)
            val columns = line.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (columns.size == 3 && "no symbols".equals(columns[2].trim { it <= ' ' }, ignoreCase = true)) {
                result = true
            }
        }
        reader.close()
        process.waitFor()
        return result
    }

    @Throws(TaskExecuteException::class)
    override fun call(): TaskResult {
        try {
            val currentConfig = config ?: throw TaskExecuteException("$TAG---jobConfig can not be null!")
            val taskResult = TaskResultFactory.factory(getType(), TASK_RESULT_TYPE_JSON, currentConfig) ?: throw TaskExecuteException("$TAG---task result create failed!")
            val startTime = System.currentTimeMillis()
            val libFiles = ArrayList<File>()
            val jsonArray = JsonArray()
            if (libDir?.exists() == true && libDir?.isDirectory == true) {
                val dirs = libDir?.listFiles()
                if (dirs != null) {
                    for (dir in dirs) {
                        if (dir.isDirectory) {
                            val libs = dir.listFiles()
                            if (libs != null) {
                                for (libFile in libs) {
                                    if (libFile.isFile && libFile.name.endsWith(ApkConstants.DYNAMIC_LIB_FILE_SUFFIX)) {
                                        libFiles.add(libFile)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (libFile in libFiles) {
                if (!isSoStripped(libFile)) {
                    Log.i(TAG, "lib: %s is not stripped", libFile.name)
                    jsonArray.add(libFile.name)
                }
            }
            (taskResult as TaskJsonResult).add("unstripped-lib", jsonArray)
            taskResult.setStartTime(startTime)
            taskResult.setEndTime(System.currentTimeMillis())
            return taskResult
        } catch (e: Exception) {
            throw TaskExecuteException(e.message ?: "", e)
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.UnStrippedSoCheckTask"
    }
}

