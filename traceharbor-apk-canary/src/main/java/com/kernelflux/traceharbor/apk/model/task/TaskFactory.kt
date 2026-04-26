package com.kernelflux.traceharbor.apk.model.task

import com.kernelflux.traceharbor.apk.model.job.JobConfig
import com.kernelflux.traceharbor.apk.model.job.JobConstants
import java.util.Arrays
import java.util.Collections

object TaskFactory {
    /*
            The value of TASK_TYPE_XXX is also the index of TaskDescription.
      */
    const val TASK_TYPE_UNZIP = 1
    const val TASK_TYPE_MANIFEST = 2
    const val TASK_TYPE_SHOW_FILE_SIZE = 3
    const val TASK_TYPE_COUNT_METHOD = 4
    const val TASK_TYPE_CHECK_RESGUARD = 5
    const val TASK_TYPE_FIND_NON_ALPHA_PNG = 6
    const val TASK_TYPE_CHECK_MULTILIB = 7
    const val TASK_TYPE_UNCOMPRESSED_FILE = 8
    const val TASK_TYPE_COUNT_R_CLASS = 9
    const val TASK_TYPE_DUPLICATE_FILE = 10
    const val TASK_TYPE_CHECK_MULTISTL = 11
    const val TASK_TYPE_UNUSED_RESOURCES = 12
    const val TASK_TYPE_UNUSED_ASSETS = 13
    const val TASK_TYPE_UNSTRIPPED_SO = 14
    const val TASK_TYPE_COUNT_CLASS = 15

    @JvmField
    val TaskDescription: List<String> =
        Collections.unmodifiableList(
            Arrays.asList(
                "Useless Task for default task type.",
                "Unzip the apk file to dest path.",
                "Read package info from the AndroidManifest.xml.",
                "Show files whose size exceed limit size in order.",
                "Count methods in dex file, output results group by class name or package name.",
                "Check if the apk handled by resguard.",
                "Find out the non-alpha png-format files whose size exceed limit size in desc order.",
                "Check if there are more than one library dir in the 'lib'.",
                "Show uncompressed file types.",
                "Count the R class.",
                "Find out the duplicated files.",
                "Check if there are more than one shared library statically linked the STL.",
                "Find out the unused resources.",
                "Find out the unused assets.",
                "Find out the unstripped shared library files.",
                "Count classes in dex file, output results group by package name.",
            ),
        )

    @JvmField
    val TaskOptionName: List<String> =
        Collections.unmodifiableList(
            Arrays.asList(
                "",
                "",
                JobConstants.OPTION_MANIFEST,
                JobConstants.OPTION_FILE_SIZE,
                JobConstants.OPTION_COUNT_METHOD,
                JobConstants.OPTION_CHECK_RES_PROGUARD,
                JobConstants.OPTION_FIND_NON_ALPHA_PNG,
                JobConstants.OPTION_CHECK_MULTILIB,
                JobConstants.OPTION_UNCOMPRESSED_FILE,
                JobConstants.OPTION_COUNT_R_CLASS,
                JobConstants.OPTION_DUPLICATE_RESOURCES,
                JobConstants.OPTION_CHECK_MULTISTL,
                JobConstants.OPTION_UNUSED_RESOURCES,
                JobConstants.OPTION_UNUSED_ASSETS,
                JobConstants.OPTION_UNSTRIPPED_SO,
                JobConstants.OPTION_COUNT_CLASS,
            ),
        )

    @JvmStatic
    fun factory(taskType: Int, config: JobConfig, params: Map<String, String>): ApkTask? {
        return when (taskType) {
            TASK_TYPE_UNZIP -> UnzipTask(config, params)
            TASK_TYPE_MANIFEST -> ManifestAnalyzeTask(config, params)
            TASK_TYPE_SHOW_FILE_SIZE -> ShowFileSizeTask(config, params)
            TASK_TYPE_COUNT_METHOD -> MethodCountTask(config, params)
            TASK_TYPE_CHECK_RESGUARD -> ResProguardCheckTask(config, params)
            TASK_TYPE_FIND_NON_ALPHA_PNG -> FindNonAlphaPngTask(config, params)
            TASK_TYPE_CHECK_MULTILIB -> MultiLibCheckTask(config, params)
            TASK_TYPE_UNCOMPRESSED_FILE -> UncompressedFileTask(config, params)
            TASK_TYPE_COUNT_R_CLASS -> CountRTask(config, params)
            TASK_TYPE_DUPLICATE_FILE -> DuplicateFileTask(config, params)
            TASK_TYPE_CHECK_MULTISTL -> MultiSTLCheckTask(config, params)
            TASK_TYPE_UNUSED_RESOURCES -> UnusedResourcesTask(config, params)
            TASK_TYPE_UNUSED_ASSETS -> UnusedAssetsTask(config, params)
            TASK_TYPE_UNSTRIPPED_SO -> UnStrippedSoCheckTask(config, params)
            TASK_TYPE_COUNT_CLASS -> CountClassTask(config, params)
            else -> null
        }
    }
}

