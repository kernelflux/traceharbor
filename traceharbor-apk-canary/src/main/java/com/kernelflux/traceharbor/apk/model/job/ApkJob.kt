package com.kernelflux.traceharbor.apk.model.job

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kernelflux.traceharbor.apk.ApkChecker
import com.kernelflux.traceharbor.apk.model.output.DefaultTaskResultRegistry
import com.kernelflux.traceharbor.apk.model.result.JobResult
import com.kernelflux.traceharbor.apk.model.result.JobResultFactory
import com.kernelflux.traceharbor.apk.model.result.TaskResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultFactory
import com.kernelflux.traceharbor.apk.model.result.TaskResultRegistry
import com.kernelflux.traceharbor.apk.model.task.ApkTask
import com.kernelflux.traceharbor.apk.model.task.TaskFactory
import com.kernelflux.traceharbor.apk.model.task.util.ApkConstants
import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import org.apache.commons.io.FileUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.URLClassLoader
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarFile

class ApkJob(
    private val args: Array<String>,
    timeoutSeconds: Int = 0,
    threadNum: Int = 0,
) {
    private val jobConfig: JobConfig = JobConfig()
    private val executor: ExecutorService
    private var timeoutSeconds: Int = TIMEOUT_SECONDS
    private var threadNum: Int = THREAD_NUM
    private val preTasks: MutableList<ApkTask> = ArrayList()
    private val taskList: MutableList<ApkTask> = ArrayList()
    private val jobResults: MutableList<JobResult> = ArrayList()

    init {
        if (timeoutSeconds > 0) {
            this.timeoutSeconds = timeoutSeconds
        }
        if (threadNum > 0) {
            this.threadNum = threadNum
        }
        executor = Executors.newFixedThreadPool(this.threadNum)
    }

    private fun parseParams(
        start: Int,
        params: Array<String>,
        result: MutableMap<String, String>
    ): Int {
        var end = params.size
        var key = ""
        for (i in start until params.size) {
            if (params[i].startsWith("-")) {
                if (!params[i].startsWith("--")) {
                    end = i
                    break
                } else {
                    key = params[i]
                }
            } else {
                result[key] = params[i]
            }
        }
        return end - start
    }

    private fun getApkRawName(name: String?): String {
        if (name.isNullOrEmpty()) {
            return ""
        }
        val index = name.indexOf('.')
        if (index == -1) {
            return name
        }
        return name.substring(0, index)
    }

    private fun createTask(name: String, params: Map<String, String>): ApkTask? {
        return when (name) {
            JobConstants.OPTION_MANIFEST -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_MANIFEST,
                jobConfig,
                params
            )

            JobConstants.OPTION_FILE_SIZE -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_SHOW_FILE_SIZE,
                jobConfig,
                params
            )

            JobConstants.OPTION_COUNT_METHOD -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_COUNT_METHOD,
                jobConfig,
                params
            )

            JobConstants.OPTION_CHECK_RES_PROGUARD -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_CHECK_RESGUARD,
                jobConfig,
                params
            )

            JobConstants.OPTION_FIND_NON_ALPHA_PNG -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_FIND_NON_ALPHA_PNG,
                jobConfig,
                params
            )

            JobConstants.OPTION_CHECK_MULTILIB -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_CHECK_MULTILIB,
                jobConfig,
                params
            )

            JobConstants.OPTION_UNCOMPRESSED_FILE -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_UNCOMPRESSED_FILE,
                jobConfig,
                params
            )

            JobConstants.OPTION_COUNT_R_CLASS -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_COUNT_R_CLASS,
                jobConfig,
                params
            )

            JobConstants.OPTION_DUPLICATE_RESOURCES -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_DUPLICATE_FILE,
                jobConfig,
                params
            )

            JobConstants.OPTION_CHECK_MULTISTL -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_CHECK_MULTISTL,
                jobConfig,
                params
            )

            JobConstants.OPTION_UNUSED_RESOURCES -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_UNUSED_RESOURCES,
                jobConfig,
                params
            )

            JobConstants.OPTION_UNUSED_ASSETS -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_UNUSED_ASSETS,
                jobConfig,
                params
            )

            JobConstants.OPTION_UNSTRIPPED_SO -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_UNSTRIPPED_SO,
                jobConfig,
                params
            )

            JobConstants.OPTION_COUNT_CLASS -> TaskFactory.factory(
                TaskFactory.TASK_TYPE_COUNT_CLASS,
                jobConfig,
                params
            )

            else -> null
        }
    }

    @Throws(
        IOException::class,
        ClassNotFoundException::class,
        IllegalAccessException::class,
        InstantiationException::class
    )
    private fun readConfigFile(configPath: String) {
        val jsonStr = StringBuilder()
        val configFile = File(configPath)
        val bufferedReader = BufferedReader(FileReader(configFile))
        bufferedReader.use { bufferedReader ->
            var line = bufferedReader.readLine()
            while (line != null) {
                if (!Util.isNullOrNil(line.trim { it <= ' ' })) {
                    jsonStr.append(line.trim { it <= ' ' })
                }
                line = bufferedReader.readLine()
            }
            val jsonElement = JsonParser.parseString(jsonStr.toString())
            if (jsonElement.isJsonObject) {
                val config = jsonElement as JsonObject
                var value = ""
                if (config.has(JobConstants.PARAM_INPUT)) {
                    value = config.get(JobConstants.PARAM_INPUT).asString
                    if (!Util.isNullOrNil(value)) {
                        jobConfig.inputDir = value
                    }
                }

                value = ""
                if (config.has(JobConstants.PARAM_APK)) {
                    value = config.get(JobConstants.PARAM_APK).asString
                } else {
                    val inputDir = jobConfig.inputDir
                    if (!inputDir.isNullOrEmpty()) {
                        val inputDir = File(inputDir)
                        if (inputDir.isDirectory && inputDir.exists()) {
                            val listFiles = inputDir.listFiles()
                            if (listFiles != null) {
                                for (file in listFiles) {
                                    if (file.isFile && file.name.endsWith(ApkConstants.APK_FILE_SUFFIX)) {
                                        value = file.absolutePath
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
                if (!FileUtil.isLegalFile(value)) {
                    ApkChecker.printError("Input apk path '$value' is illegal!")
                } else {
                    jobConfig.apkPath = value
                }
                val apkPath= jobConfig.apkPath
                val apkFile = File(apkPath)
                value = if (config.has(JobConstants.PARAM_UNZIP) && !Util.isNullOrNil(
                            config.get(
                                JobConstants.PARAM_UNZIP
                            ).asString
                        )
                    ) {
                        config.get(JobConstants.PARAM_UNZIP).asString
                    } else {
                        apkFile.parentFile.absolutePath + File.separator + getApkRawName(apkFile.name) + "_unzip"
                    }
                jobConfig.unzipPath = value

                value =
                    if (config.has(JobConstants.PARAM_FORMAT) && !Util.isNullOrNil(
                            config.get(
                                JobConstants.PARAM_FORMAT
                            ).asString
                        )
                    ) {
                        config.get(JobConstants.PARAM_FORMAT).asString
                    } else {
                        TaskResultFactory.TASK_RESULT_TYPE_HTML
                    }
                val formats =
                    value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val formatList: MutableList<String> = ArrayList()
                for (format in formats) {
                    if (!Util.isNullOrNil(format)) {
                        formatList.add(format.trim { it <= ' ' })
                    }
                }
                Log.i(TAG, "format list $formatList")
                jobConfig.outputFormatList = formatList

                value =
                    if (config.has(JobConstants.PARAM_OUTPUT) && !Util.isNullOrNil(
                            config.get(
                                JobConstants.PARAM_OUTPUT
                            ).asString
                        )
                    ) {
                        config.get(JobConstants.PARAM_OUTPUT).asString
                    } else {
                        apkFile.parentFile.absolutePath + File.separator + getApkRawName(apkFile.name)
                    }
                jobConfig.outputPath = value

                if (config.has(JobConstants.PARAM_FORMAT_JAR) && !Util.isNullOrNil(
                        config.get(
                            JobConstants.PARAM_FORMAT_JAR
                        ).asString
                    )
                ) {
                    value = config.get(JobConstants.PARAM_FORMAT_JAR).asString
                    val file = File(value)
                    val jarFile = JarFile(file)
                    val manifest = jarFile.manifest
                    val registry: Attributes? =
                        manifest.getAttributes(JobConstants.TASK_RESULT_REGISTRY)
                    if (registry != null) {
                        val registryClassPath =
                            registry.getValue(JobConstants.TASK_RESULT_REGISTERY_CLASS)
                        val classLoader = URLClassLoader(arrayOf(file.toURI().toURL()))
                        val registryClass = classLoader.loadClass(registryClassPath)
                        val resultRegistry = registryClass.newInstance() as TaskResultRegistry
                        TaskResultFactory.addCustomTaskJsonResult(resultRegistry.getJsonResult())
                        TaskResultFactory.addCustomTaskHtmlResult(resultRegistry.getHtmlResult())
                    }
                }

                if (config.has(JobConstants.PARAM_FORMAT_CONFIG)) {
                    val outputConfig = config.get(JobConstants.PARAM_FORMAT_CONFIG).asJsonArray
                    jobConfig.outputConfig = outputConfig
                }

                if (config.has(JobConstants.PARAM_LOG_LEVEL)) {
                    Log.setLogLevel(config.get(JobConstants.PARAM_LOG_LEVEL).asString)
                }

                if (config.has(JobConstants.PARAM_MAPPING_TXT) && !Util.isNullOrNil(
                        config.get(
                            JobConstants.PARAM_MAPPING_TXT
                        ).asString
                    )
                ) {
                    jobConfig.mappingFilePath = config.get(JobConstants.PARAM_MAPPING_TXT).asString
                }

                if (config.has(JobConstants.PARAM_RES_MAPPING_TXT) && !Util.isNullOrNil(
                        config.get(
                            JobConstants.PARAM_RES_MAPPING_TXT
                        ).asString
                    )
                ) {
                    jobConfig.resMappingFilePath =
                        config.get(JobConstants.PARAM_RES_MAPPING_TXT).asString
                }

                val options = config.getAsJsonArray("options")
                for (option in options) {
                    if (option.isJsonObject) {
                        val jsonOption = option as JsonObject
                        val name = jsonOption.get("name").asString
                        val params: MutableMap<String, String> = HashMap()
                        for (param in jsonOption.entrySet()) {
                            if (param.key.startsWith("--")) {
                                if (param.value.isJsonPrimitive) {
                                    params[param.key] = param.value.asString
                                } else if (param.key == JobConstants.PARAM_IGNORE_RESOURCES_LIST || param.key == JobConstants.PARAM_IGNORE_ASSETS_LIST) {
                                    val ignoreList = param.value.asJsonArray
                                    val ignoreStrBuilder = StringBuilder()
                                    for (ignore in ignoreList) {
                                        ignoreStrBuilder.append(ignore.asString)
                                        ignoreStrBuilder.append(',')
                                    }
                                    ignoreStrBuilder.deleteCharAt(ignoreStrBuilder.length - 1)
                                    params[param.key] = ignoreStrBuilder.toString()
                                }
                            }
                        }
                        if (name == JobConstants.OPTION_UNUSED_RESOURCES && !params.containsKey(
                                JobConstants.PARAM_R_TXT
                            )
                        ) {
                            val inputDir = jobConfig.inputDir
                            if (!Util.isNullOrNil(inputDir)) {
                                val rTxtFilePath = "$inputDir/${ApkConstants.DEFAULT_RTXT_FILENAME}"
                                params[JobConstants.PARAM_R_TXT] = rTxtFilePath
                            }
                        }
                        val task = createTask(name, params)
                        if (task != null) {
                            taskList.add(task)
                        }
                    } else {
                        ApkChecker.printError("Unknown option: $option")
                    }
                }
            } else {
                ApkChecker.printError("The content of config file is not in json format!")
            }
        }
    }

    private fun parseGlobalParams(): Int {
        var paramLen: Int
        val globalParams: MutableMap<String, String> = HashMap()
        paramLen = parseParams(0, args, globalParams)

        try {
            if (globalParams.containsKey(JobConstants.PARAM_CONFIG)) {
                val configPath = globalParams[JobConstants.PARAM_CONFIG].toString()
                if (!FileUtil.isLegalFile(configPath)) {
                    ApkChecker.printError("Input config file '$configPath' is illegal!")
                } else if (!configPath.endsWith(".json")) {
                    ApkChecker.printError("Input config file must has a suffix '.json'!")
                } else {
                    readConfigFile(configPath)
                }
            } else {
                var apkPath = ""
                var mappingFilePath = ""
                var resMappingFilePath = ""

                if (globalParams.containsKey(JobConstants.PARAM_INPUT)) {
                    val inputDir = globalParams[JobConstants.PARAM_INPUT]
                    if (!Util.isNullOrNil(inputDir)) {
                        val inputFile = File(inputDir)
                        if (inputFile.isDirectory && inputFile.exists()) {
                            jobConfig.inputDir = inputFile.absolutePath
                            val listFiles = inputFile.listFiles()
                            if (listFiles != null) {
                                for (file in listFiles) {
                                    if (file.isFile && file.name.endsWith(ApkConstants.APK_FILE_SUFFIX)) {
                                        apkPath = file.absolutePath
                                        break
                                    }
                                }
                            }
                            mappingFilePath = "$inputDir/${ApkConstants.DEFAULT_MAPPING_FILENAME}"
                            resMappingFilePath =
                                "$inputDir/${ApkConstants.DEFAULT_RESGUARD_MAPPING_FILENAME}"
                        }
                    }
                }

                if (globalParams.containsKey(JobConstants.PARAM_APK)) {
                    apkPath = globalParams[JobConstants.PARAM_APK].toString()
                    if (!FileUtil.isLegalFile(apkPath)) {
                        ApkChecker.printError("Input apk path '$apkPath' is illegal!")
                    }
                }

                jobConfig.apkPath = apkPath
                val apkFile = File(apkPath)

                if (globalParams.containsKey(JobConstants.PARAM_UNZIP)) {
                    jobConfig.unzipPath = globalParams[JobConstants.PARAM_UNZIP]
                } else {
                    jobConfig.unzipPath =
                        apkFile.parentFile.absolutePath + File.separator + getApkRawName(apkFile.name) + "_unzip"
                }

                if (globalParams.containsKey(JobConstants.PARAM_MAPPING_TXT)) {
                    mappingFilePath = globalParams[JobConstants.PARAM_MAPPING_TXT].toString()
                }
                jobConfig.mappingFilePath = mappingFilePath

                if (globalParams.containsKey(JobConstants.PARAM_RES_MAPPING_TXT)) {
                    resMappingFilePath = globalParams[JobConstants.PARAM_RES_MAPPING_TXT].toString()
                }
                jobConfig.resMappingFilePath = resMappingFilePath

                var value =
                    if (globalParams.containsKey(JobConstants.PARAM_FORMAT) && !Util.isNullOrNil(
                            globalParams[JobConstants.PARAM_FORMAT]
                        )
                    ) {
                        globalParams[JobConstants.PARAM_FORMAT].toString()
                    } else {
                        TaskResultFactory.TASK_RESULT_TYPE_HTML
                    }
                val formats =
                    value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val formatList: MutableList<String> = ArrayList()
                for (format in formats) {
                    if (!Util.isNullOrNil(format)) {
                        formatList.add(format.trim { it <= ' ' })
                    }
                }
                Log.i(TAG, "format list $formatList")
                jobConfig.outputFormatList = formatList

                if (globalParams.containsKey(JobConstants.PARAM_OUTPUT)) {
                    jobConfig.outputPath = globalParams[JobConstants.PARAM_OUTPUT]
                } else {
                    jobConfig.outputPath =
                        apkFile.parentFile.absolutePath + File.separator + getApkRawName(apkFile.name)
                }

                if (globalParams.containsKey(JobConstants.PARAM_FORMAT_JAR)) {
                    val file = File(globalParams[JobConstants.PARAM_FORMAT_JAR].toString())
                    val jarFile = JarFile(file)
                    val manifest = jarFile.manifest
                    val registry = manifest.getAttributes(JobConstants.TASK_RESULT_REGISTRY)
                    if (registry != null) {
                        val registryClassPath =
                            registry.getValue(JobConstants.TASK_RESULT_REGISTERY_CLASS)
                        val classLoader = URLClassLoader(arrayOf(file.toURI().toURL()))
                        val registryClass = classLoader.loadClass(registryClassPath)
                        val resultRegistry = registryClass.newInstance() as TaskResultRegistry
                        TaskResultFactory.addCustomTaskJsonResult(resultRegistry.getJsonResult())
                        TaskResultFactory.addCustomTaskHtmlResult(resultRegistry.getHtmlResult())
                    }
                }

                if (globalParams.containsKey(JobConstants.PARAM_FORMAT_CONFIG)) {
                    val jsonElement =
                        JsonParser().parse(globalParams[JobConstants.PARAM_FORMAT_CONFIG])
                    jobConfig.outputConfig = jsonElement as JsonArray
                }

                if (globalParams.containsKey(JobConstants.PARAM_LOG_LEVEL)) {
                    Log.setLogLevel(globalParams[JobConstants.PARAM_LOG_LEVEL] ?: "")
                }
            }

            // register MMTaskResult
            val mmTaskResultRegistry = DefaultTaskResultRegistry()
            TaskResultFactory.addCustomTaskHtmlResult(mmTaskResultRegistry.getHtmlResult())
            TaskResultFactory.addCustomTaskJsonResult(mmTaskResultRegistry.getJsonResult())
        } catch (e: Exception) {
            ApkChecker.printError(e.message ?: "")
        }

        return paramLen
    }

    private fun parseParams(): Boolean {
        if (args.size >= 2) {
            var paramLen = parseGlobalParams()

            var i = paramLen
            while (i < args.size) {
                if (args[i].startsWith("-") && !args[i].startsWith("--")) {
                    val params: MutableMap<String, String> = HashMap()
                    paramLen = parseParams(i + 1, args, params)
                    if (!params.containsKey(JobConstants.PARAM_R_TXT)) {
                        val inputDir = jobConfig.inputDir
                        if (!Util.isNullOrNil(inputDir)) {
                            params[JobConstants.PARAM_R_TXT] =
                                "$inputDir/${ApkConstants.DEFAULT_RTXT_FILENAME}"
                        }
                    }
                    val task = createTask(args[i], params)
                    if (task != null) {
                        taskList.add(task)
                    }
                    i += paramLen
                }
                i++
            }
        } else {
            return false
        }
        return true
    }

    @Throws(Exception::class)
    fun run() {
        if (parseParams()) {
            val unzipTask = TaskFactory.factory(TaskFactory.TASK_TYPE_UNZIP, jobConfig, HashMap())!!
            preTasks.add(unzipTask)
            val outputFormatList = jobConfig.outputFormatList ?: emptyList()
            for (format in outputFormatList) {
                val result = JobResultFactory.factory(format, jobConfig)
                if (result != null) {
                    jobResults.add(result)
                } else {
                    Log.w(TAG, "Unknown output format name '%s' !", format)
                }
            }
            execute()
        } else {
            ApkChecker.printHelp()
        }
    }

    @Throws(Exception::class)
    private fun execute() {
        try {
            for (preTask in preTasks) {
                preTask.init()
                val taskResult = preTask.call()
                var formatResult: TaskResult?
                for (jobResult in jobResults) {
                    formatResult = TaskResultFactory.transferTaskResult(
                        taskResult.taskType,
                        taskResult,
                        jobResult.getFormat(),
                        jobConfig
                    )
                    if (formatResult != null) {
                        jobResult.addTaskResult(formatResult)
                    }
                }
            }

            for (task in taskList) {
                task.init()
            }
            val futures: List<Future<TaskResult>> =
                executor.invokeAll(taskList, timeoutSeconds.toLong(), TimeUnit.SECONDS)
            for (future in futures) {
                val taskResult = future.get()
                var formatResult: TaskResult?
                for (jobResult in jobResults) {
                    formatResult = TaskResultFactory.transferTaskResult(
                        taskResult.taskType,
                        taskResult,
                        jobResult.getFormat(),
                        jobConfig
                    )
                    if (formatResult != null) {
                        jobResult.addTaskResult(formatResult)
                    }
                }
            }
            executor.shutdownNow()

            for (jobResult in jobResults) {
                jobResult.output()
            }
            Log.d(TAG, "parse apk end, try to delete tmp un zip files")
            FileUtils.deleteDirectory(File(jobConfig.unzipPath))
        } catch (e: Exception) {
            Log.e(TAG, "Task executor execute with error:" + e.message)
            throw e
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.ApkJob"
        private const val TIMEOUT_SECONDS = 600
        private const val THREAD_NUM = 1
    }
}

