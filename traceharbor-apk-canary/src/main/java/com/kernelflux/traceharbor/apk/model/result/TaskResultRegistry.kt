package com.kernelflux.traceharbor.apk.model.result

abstract class TaskResultRegistry {
    abstract fun getHtmlResult(): Map<String, Class<out TaskHtmlResult>>

    abstract fun getJsonResult(): Map<String, Class<out TaskJsonResult>>
}

