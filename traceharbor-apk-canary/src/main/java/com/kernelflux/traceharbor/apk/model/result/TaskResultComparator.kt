package com.kernelflux.traceharbor.apk.model.result

import com.kernelflux.traceharbor.apk.model.task.TaskFactory
import java.util.Comparator

class TaskResultComparator : Comparator<TaskResult> {
    override fun compare(taskResult1: TaskResult, taskResult2: TaskResult): Int {
        return getImportLevel(taskResult1.taskType) - getImportLevel(taskResult2.taskType)
    }

    companion object {
        private const val TASK_IMPORT_LEVEL_1 = 1
        private const val TASK_IMPORT_LEVEL_2 = 2
        private const val TASK_IMPORT_LEVEL_3 = 3
        private const val TASK_IMPORT_LEVEL_LOWEST = 0

        @JvmStatic
        fun getImportLevel(taskType: Int): Int {
            var level = TASK_IMPORT_LEVEL_LOWEST
            when (taskType) {
                TaskFactory.TASK_TYPE_UNZIP,
                TaskFactory.TASK_TYPE_MANIFEST,
                -> level = TASK_IMPORT_LEVEL_1

                TaskFactory.TASK_TYPE_CHECK_RESGUARD,
                TaskFactory.TASK_TYPE_DUPLICATE_FILE,
                TaskFactory.TASK_TYPE_FIND_NON_ALPHA_PNG,
                TaskFactory.TASK_TYPE_UNSTRIPPED_SO,
                TaskFactory.TASK_TYPE_UNUSED_ASSETS,
                TaskFactory.TASK_TYPE_UNUSED_RESOURCES,
                TaskFactory.TASK_TYPE_UNCOMPRESSED_FILE,
                -> level = TASK_IMPORT_LEVEL_2

                TaskFactory.TASK_TYPE_CHECK_MULTILIB,
                TaskFactory.TASK_TYPE_CHECK_MULTISTL,
                TaskFactory.TASK_TYPE_COUNT_METHOD,
                TaskFactory.TASK_TYPE_COUNT_R_CLASS,
                TaskFactory.TASK_TYPE_SHOW_FILE_SIZE,
                -> level = TASK_IMPORT_LEVEL_3
            }
            return level
        }
    }
}

