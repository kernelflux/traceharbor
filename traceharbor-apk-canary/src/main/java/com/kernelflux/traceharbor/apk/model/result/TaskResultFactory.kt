package com.kernelflux.traceharbor.apk.model.result

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.task.TaskFactory
import com.kernelflux.traceharbor.javalib.util.Log
import java.lang.reflect.InvocationTargetException
import javax.xml.parsers.ParserConfigurationException

object TaskResultFactory {
    private const val TAG = "TraceHarbor.TaskResultFactory"

    const val TASK_RESULT_TYPE_JSON = "json"
    const val TASK_RESULT_TYPE_HTML = "html"

    private val customHtmlResultMap: MutableMap<String, Class<out TaskHtmlResult>> = HashMap()
    private val customJsonResultMap: MutableMap<String, Class<out TaskJsonResult>> = HashMap()

    @JvmStatic
    @Throws(
        ParserConfigurationException::class,
        IllegalAccessException::class,
        InstantiationException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class,
    )
    fun factory(taskType: Int, resultType: String, config: JobConfig): TaskResult {
        var taskConfig: JsonObject? = null
        val taskConfigs = config.outputConfig
        if (taskConfigs != null) {
            for (element in taskConfigs) {
                val obj = element.asJsonObject
                if (obj.get("name").asString == TaskFactory.TaskOptionName[taskType]) {
                    taskConfig = obj
                    break
                }
            }
        }

        return when {
            TASK_RESULT_TYPE_JSON == resultType -> TaskJsonResult(taskType, taskConfig)
            TASK_RESULT_TYPE_HTML == resultType -> TaskHtmlResult(taskType, taskConfig)
            customHtmlResultMap.containsKey(resultType) -> {
                val class1 = customHtmlResultMap[resultType]
                val constructor = class1!!.getDeclaredConstructor(Int::class.javaPrimitiveType, JsonObject::class.java)
                constructor.newInstance(taskType, taskConfig)
            }

            customJsonResultMap.containsKey(resultType) -> {
                val class1 = customJsonResultMap[resultType]
                val constructor = class1!!.getDeclaredConstructor(Int::class.javaPrimitiveType, JsonObject::class.java)
                constructor.newInstance(taskType, taskConfig)
            }

            else -> TaskHtmlResult(taskType, taskConfig)
        }
    }

    @JvmStatic
    fun transferTaskResult(taskType: Int, source: TaskResult, destResultType: String, config: JobConfig): TaskResult? {
        var result: TaskResult?
        try {
            result =
                if (source is TaskJsonResult) {
                    when {
                        customHtmlResultMap.containsKey(destResultType) || destResultType == TASK_RESULT_TYPE_HTML -> {
                            val html = factory(taskType, destResultType, config) as TaskHtmlResult
                            transferJsonToHtml(source, html)
                            html
                        }

                        customJsonResultMap.containsKey(destResultType) -> {
                            val json = factory(taskType, destResultType, config) as TaskJsonResult
                            formatJson(source, json)
                            json
                        }

                        else -> source
                    }
                } else {
                    source
                }
        } catch (e: ParserConfigurationException) {
            Log.e(TAG, "transfer task result failed! ${e.message}")
            result = null
        } catch (e: InstantiationException) {
            Log.e(TAG, "transfer task result failed! ${e.message}")
            result = null
        } catch (e: IllegalAccessException) {
            Log.e(TAG, "transfer task result failed! ${e.message}")
            result = null
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "transfer task result failed! ${e.message}")
            result = null
        } catch (e: InvocationTargetException) {
            Log.e(TAG, "transfer task result failed! ${e.message}")
            result = null
        }
        return result
    }

    @Throws(ParserConfigurationException::class)
    private fun transferJsonToHtml(source: TaskJsonResult, dest: TaskHtmlResult) {
        val jsonObject = JsonParser().parse(source.toString()) as JsonObject
        dest.format(jsonObject)
    }

    private fun formatJson(source: TaskJsonResult, dest: TaskJsonResult) {
        dest.format(source.rawJsonObject())
    }

    @JvmStatic
    fun addCustomTaskHtmlResult(customTaskHtmlResult: Map<String, Class<out TaskHtmlResult>>) {
        customHtmlResultMap.putAll(customTaskHtmlResult)
    }

    @JvmStatic
    fun addCustomTaskJsonResult(customTaskJsonResult: Map<String, Class<out TaskJsonResult>>) {
        customJsonResultMap.putAll(customTaskJsonResult)
    }

    @JvmStatic
    fun isJsonResult(resultType: String): Boolean {
        return resultType == TASK_RESULT_TYPE_JSON || customJsonResultMap.containsKey(resultType)
    }

    @JvmStatic
    fun isHtmlResult(resultType: String): Boolean {
        return resultType == TASK_RESULT_TYPE_HTML || customHtmlResultMap.containsKey(resultType)
    }
}

