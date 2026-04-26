package com.kernelflux.traceharbor.resource.config

class SharePluginInfo private constructor() {
    companion object {
        const val TAG_PLUGIN: String = "memory"

        const val ISSUE_RESULT_PATH: String = "resultZipPath"

        const val ISSUE_DUMP_MODE: String = "dump_mode"
        const val ISSUE_ACTIVITY_NAME: String = "activity"
        const val ISSUE_REF_KEY: String = "ref_key"
        const val ISSUE_LEAK_DETAIL: String = "leak_detail"
        const val ISSUE_COST_MILLIS: String = "cost_millis"
        const val ISSUE_RETRY_COUNT: String = "retry_count"
        const val ISSUE_LEAK_PROCESS: String = "leak_process"

        @Deprecated("Use ISSUE_HPROF_PATH")
        const val ISSUE_DUMP_DATA: String = "dump_data"

        const val ISSUE_HPROF_PATH: String = "hprof_path"
        const val ISSUE_NOTIFICATION_ID: String = "notification_id"
    }

    object IssueType {
        const val LEAK_FOUND: Int = 0
        const val ERR_FILE_NOT_FOUND: Int = 2
        const val ERR_ANALYSE_OOM: Int = 3
        const val ERR_UNSUPPORTED_API: Int = 4
        const val ERR_EXCEPTION: Int = 5
    }
}

