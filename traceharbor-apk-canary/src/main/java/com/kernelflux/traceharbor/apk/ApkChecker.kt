package com.kernelflux.traceharbor.apk

import com.kernelflux.traceharbor.apk.model.job.ApkJob
import com.kernelflux.traceharbor.apk.model.job.JobConstants

class ApkChecker private constructor() {
    private fun run(args: Array<out String>) {
        val job = ApkJob(args.toList().toTypedArray())
        try {
            job.run()
        } catch (e: Exception) {
            e.printStackTrace()
            System.exit(-1)
        }
    }

    companion object {
        private val INTRODUCT =
            "Welcome to use ApkChecker!\n" +
                "ApkChecker can help you do something like:\n" +
                "1. Overview the apk file.\n" +
                "   package name, version code, version name, method count, resources file size, dex file size ...\n" +
                "2. Find out some problems in apk and give suggestions or solutions.\n" +
                "   lack proguard, exceed 65535 methods, abnormal large img file ...\n" +
                "3. Shrink the size of apk.\n" +
                "   non-alpha PNG, large img file, duplicated files ...\n"

        private val HELP =
            "Usages:\n\n" +
                JobConstants.PARAM_CONFIG + " CONFIG-FILE-PATH\n\n" +
                "or\n\n" +
                "[" + JobConstants.PARAM_INPUT + " INPUT-DIR-PATH] [" + JobConstants.PARAM_APK + " APK-FILE-PATH] [" +
                JobConstants.PARAM_UNZIP + " APK-UNZIP-PATH] [" + JobConstants.PARAM_MAPPING_TXT + " MAPPING-FILE-PATH] [" +
                JobConstants.PARAM_RES_MAPPING_TXT + " RESGUARD-MAPPING-FILE-PATH] [" + JobConstants.PARAM_OUTPUT + " OUTPUT-PATH] [" +
                JobConstants.PARAM_FORMAT + " OUTPUT-FORMAT] [" + JobConstants.PARAM_FORMAT_JAR + " OUTPUT-FORMAT-JAR] [" +
                JobConstants.PARAM_FORMAT_CONFIG + " OUTPUT-FORMAT-CONFIG (json-array format)] [" + JobConstants.PARAM_LOG_LEVEL +
                " LOG-LEVEL (v,d,i,w,e)] [Options]\n\n" +
                "Options:\n" +
                JobConstants.OPTION_MANIFEST + "\n" +
                "     Read package info from the AndroidManifest.xml.\n" +
                JobConstants.OPTION_FILE_SIZE + " [" + JobConstants.PARAM_MIN_SIZE_IN_KB + " DOWN-LIMIT-SIZE (KB)] [" +
                JobConstants.PARAM_ORDER + " ORDER-BY ('" + JobConstants.ORDER_ASC + "'|'" + JobConstants.ORDER_DESC + "')] [" +
                JobConstants.PARAM_SUFFIX + " FILTER-SUFFIX-LIST (split by ',')]\n" +
                "     Show files whose size exceed limit size in order.\n" +
                JobConstants.OPTION_COUNT_METHOD + " [" + JobConstants.PARAM_GROUP + " GROUP-BY ('" + JobConstants.GROUP_CLASS + "'|'" +
                JobConstants.GROUP_PACKAGE + "')]\n" +
                "     Count methods in dex file, output results group by class name or package name.\n" +
                JobConstants.OPTION_CHECK_RES_PROGUARD + "\n" +
                "     Check if the resguard was applied.\n" +
                JobConstants.OPTION_FIND_NON_ALPHA_PNG + " [" + JobConstants.PARAM_MIN_SIZE_IN_KB + " DOWN-LIMIT-SIZE (KB)]\n" +
                "     Find out the non-alpha png-format files whose size exceed limit size in desc order.\n" +
                JobConstants.OPTION_CHECK_MULTILIB + "\n" +
                "     Check if there are more than one library dir in the 'lib'.\n" +
                JobConstants.OPTION_UNCOMPRESSED_FILE + " [" + JobConstants.PARAM_SUFFIX + " FILTER-SUFFIX-LIST (split by ',')]\n" +
                "     Show uncompressed file types.\n" +
                JobConstants.OPTION_COUNT_R_CLASS + "\n" +
                "     Count the R class.\n" +
                JobConstants.OPTION_DUPLICATE_RESOURCES + "\n" +
                "     Find out the duplicated resource files in desc order.\n" +
                JobConstants.OPTION_CHECK_MULTISTL + "  " + JobConstants.PARAM_TOOL_NM + " TOOL-NM-PATH\n" +
                "     Check if there are more than one shared library statically linked the STL.\n" +
                JobConstants.OPTION_UNUSED_RESOURCES + " " + JobConstants.PARAM_R_TXT + " R-TXT-FILE-PATH [" +
                JobConstants.PARAM_IGNORE_RESOURCES_LIST + " IGNORE-RESOURCES-LIST (split by ',')]\n" +
                "     Find out the unused resources.\n" +
                JobConstants.OPTION_UNUSED_ASSETS + " [" + JobConstants.PARAM_IGNORE_ASSETS_LIST + " IGNORE-ASSETS-LIST (split by ',')]\n" +
                "     Find out the unused assets file.\n" +
                JobConstants.OPTION_UNSTRIPPED_SO + "  " + JobConstants.PARAM_TOOL_NM + " TOOL-NM-PATH\n" +
                "     Find out the unstripped shared library file.\n" +
                JobConstants.OPTION_COUNT_CLASS + " [" + JobConstants.PARAM_GROUP + " GROUP-BY ('" + JobConstants.GROUP_PACKAGE + "')]\n" +
                "     Count classes in dex file, output results group by package name."

        @JvmStatic
        fun main(vararg args: String) {
            if (args.isNotEmpty()) {
                val m = ApkChecker()
                m.run(args)
            } else {
                println(INTRODUCT + HELP)
                System.exit(0)
            }
        }

        @JvmStatic
        fun printError(error: String) {
            println(error)
            printHelp()
        }

        @JvmStatic
        fun printHelp() {
            println(HELP)
            System.exit(-1)
        }
    }
}

