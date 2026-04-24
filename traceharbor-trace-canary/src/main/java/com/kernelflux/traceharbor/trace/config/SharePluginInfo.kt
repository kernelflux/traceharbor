package com.kernelflux.traceharbor.trace.config

/**
 * Pure constants holder. Wrapped in a class with `companion object` so Java
 * callers continue to write `SharePluginInfo.TAG_PLUGIN` etc. unchanged.
 */
class SharePluginInfo {
    companion object {
        const val TAG_PLUGIN: String = "Trace"
        const val TAG_PLUGIN_FPS: String = "Trace_FPS"
        const val TAG_PLUGIN_EVIL_METHOD: String = "Trace_EvilMethod"
        const val TAG_PLUGIN_STARTUP: String = "Trace_StartUp"

        // const val ISSUE_DEVICE: String = "machine"
        const val ISSUE_SCENE: String = "scene"
        const val ISSUE_DROP_LEVEL: String = "dropLevel"
        const val ISSUE_DROP_SUM: String = "dropSum"
        const val ISSUE_FPS: String = "fps"
        const val ISSUE_TRACE_STACK: String = "stack"
        const val ISSUE_THREAD_STACK: String = "threadStack"
        const val ISSUE_PROCESS_PRIORITY: String = "processPriority"
        const val ISSUE_PROCESS_TIMER_SLACK: String = "processTimerSlack"
        const val ISSUE_PROCESS_NICE: String = "processNice"
        const val ISSUE_PROCESS_FOREGROUND: String = "isProcessForeground"
        const val ISSUE_STACK_KEY: String = "stackKey"
        const val ISSUE_MEMORY: String = "memory"
        const val ISSUE_MEMORY_NATIVE: String = "native_heap"
        const val ISSUE_MEMORY_DALVIK: String = "dalvik_heap"
        const val ISSUE_MEMORY_VM_SIZE: String = "vm_size"
        const val ISSUE_COST: String = "cost"
        const val ISSUE_STACK_TYPE: String = "detail"
        const val ISSUE_IS_WARM_START_UP: String = "is_warm_start_up"
        const val ISSUE_SUB_TYPE: String = "subType"
        const val STAGE_APPLICATION_CREATE: String = "application_create"
        const val STAGE_APPLICATION_CREATE_SCENE: String = "application_create_scene"
        const val STAGE_FIRST_ACTIVITY_CREATE: String = "first_activity_create"
        const val STAGE_STARTUP_DURATION: String = "startup_duration"
    }
}
