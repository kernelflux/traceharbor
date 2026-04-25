package com.kernelflux.traceharbor.iocanary.config

object SharePluginInfo {
    const val TAG_PLUGIN = "io"

    object IssueType {
        const val ISSUE_UNKNOWN = 0x0
        const val ISSUE_IO_CLOSABLE_LEAK = 0x4
        const val ISSUE_NETWORK_IO_IN_MAIN_THREAD = 0x5
        const val ISSUE_IO_CURSOR_LEAK = 0x6
    }

    const val ISSUE_FILE_PATH = "path"
    const val ISSUE_FILE_SIZE = "size"
    const val ISSUE_FILE_COST_TIME = "cost"
    const val ISSUE_FILE_STACK = "stack"
    const val ISSUE_FILE_OP_TIMES = "op"
    const val ISSUE_FILE_BUFFER = "buffer"
    const val ISSUE_FILE_THREAD = "thread"
    const val ISSUE_FILE_READ_WRITE_TYPE = "opType"
    const val ISSUE_FILE_OP_SIZE = "opSize"

    const val ISSUE_FILE_REPEAT_COUNT = "repeat"
}
