package com.kernelflux.traceharbor.apk.model.output

import com.kernelflux.traceharbor.apk.model.result.TaskHtmlResult
import com.kernelflux.traceharbor.apk.model.result.TaskJsonResult
import com.kernelflux.traceharbor.apk.model.result.TaskResultRegistry

class DefaultTaskResultRegistry : TaskResultRegistry() {
    override fun getHtmlResult(): Map<String, Class<out TaskHtmlResult>> {
        val map: MutableMap<String, Class<out TaskHtmlResult>> = HashMap()
        map["mm.html"] = DefaultTaskHtmlResult::class.java
        return map
    }

    override fun getJsonResult(): Map<String, Class<out TaskJsonResult>> {
        val map: MutableMap<String, Class<out TaskJsonResult>> = HashMap()
        map["mm.json"] = DefaultTaskJsonResult::class.java
        return map
    }
}

