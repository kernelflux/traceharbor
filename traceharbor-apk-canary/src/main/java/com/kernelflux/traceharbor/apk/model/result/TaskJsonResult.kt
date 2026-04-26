package com.kernelflux.traceharbor.apk.model.result

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kernelflux.traceharbor.apk.model.task.TaskFactory
import com.kernelflux.traceharbor.javalib.util.Util
import javax.xml.parsers.ParserConfigurationException

open class TaskJsonResult
    @Throws(ParserConfigurationException::class)
    constructor(
        taskType: Int,
        @JvmField protected val config: JsonObject?,
    ) : TaskResult(taskType) {
        @JvmField
        protected val jsonObject: JsonObject = JsonObject()

        init {
            jsonObject.addProperty("taskType", taskType)
            jsonObject.addProperty("taskDescription", TaskFactory.TaskDescription[taskType])
        }

        fun add(name: String?, value: String?) {
            if (!Util.isNullOrNil(name)) {
                jsonObject.addProperty(name, value)
            }
        }

        fun add(name: String?, value: Boolean) {
            if (!Util.isNullOrNil(name)) {
                jsonObject.addProperty(name, value)
            }
        }

        fun add(name: String?, value: Number?) {
            if (!Util.isNullOrNil(name)) {
                jsonObject.addProperty(name, value)
            }
        }

        fun add(name: String?, jsonElement: JsonElement?) {
            if (!Util.isNullOrNil(name)) {
                jsonObject.add(name, jsonElement)
            }
        }

        override fun setStartTime(startTime: Long) {
            super.setStartTime(startTime)
            jsonObject.addProperty("start-time", this.startTime)
        }

        override fun setEndTime(endTime: Long) {
            super.setEndTime(endTime)
            jsonObject.addProperty("end-time", this.endTime)
        }

        open fun format(jsonObject: JsonObject) {
            // do nothing
        }

        fun rawJsonObject(): JsonObject = jsonObject

        override fun toString(): String = jsonObject.toString()

        override fun getResult(): JsonObject = jsonObject
    }

