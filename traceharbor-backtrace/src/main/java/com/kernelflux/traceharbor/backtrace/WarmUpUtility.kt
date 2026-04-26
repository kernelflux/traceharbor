/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kernelflux.traceharbor.backtrace

import android.content.Context
import android.os.CancellationSignal
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.File
import java.io.FileFilter
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.HashMap

class WarmUpUtility private constructor() {
    class UnfinishedManagement private constructor() {
        companion object {
            private var unfinishedWarmUp: MutableMap<String, Int>? = null

            private fun retryCount(context: Context, key: String): Int {
                if (unfinishedWarmUp == null) {
                    unfinishedWarmUp = readUnfinishedMaps(context)
                }
                return unfinishedWarmUp?.get(key) ?: 0
            }

            @JvmStatic
            fun check(context: Context, pathOfElf: String, offset: Int): Boolean {
                val key = unfinishedKey(pathOfElf, offset)
                val retryCount = retryCount(context, key)
                return retryCount < WARM_UP_FILE_MAX_RETRY
            }

            @JvmStatic
            fun checkAndMark(context: Context, pathOfElf: String, offset: Int): Boolean {
                val key = unfinishedKey(pathOfElf, offset)
                val retryCount = retryCount(context, key)
                if (retryCount >= WARM_UP_FILE_MAX_RETRY) {
                    return false
                }
                unfinishedWarmUp?.put(key, retryCount + 1)
                flushUnfinishedMaps(context, unfinishedWarmUp ?: emptyMap())
                return true
            }

            @JvmStatic
            fun result(context: Context, pathOfElf: String, offset: Int, success: Boolean) {
                val key = unfinishedKey(pathOfElf, offset)
                val retryCount = retryCount(context, key)
                if (success) {
                    unfinishedWarmUp?.remove(key)
                } else {
                    unfinishedWarmUp?.put(key, retryCount + 1)
                }
                flushUnfinishedMaps(context, unfinishedWarmUp ?: emptyMap())
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.Backtrace.WarmUp"
        private const val DIR_WECHAT_BACKTRACE = "wechat-backtrace"
        private const val FILE_DEFAULT_SAVING_PATH = "saving-cache"
        private const val FILE_WARMED_UP = "warmed-up"
        private const val FILE_DISK_USAGE = "disk-usage.timestamp"
        private const val FILE_CLEAN_UP_TIMESTAMP = "clean-up.timestamp"
        private const val FILE_UNFINISHED = "unfinished"

        const val DURATION_LAST_ACCESS_FAR_FUTURE = 7L * 24 * 3600 * 1000 // milliseconds
        const val DURATION_LAST_ACCESS_EXPIRED = 3L * 24 * 3600 * 1000 // milliseconds
        const val DURATION_CLEAN_UP_EXPIRED = 2L * 24 * 3600 * 1000 // milliseconds
        const val DURATION_CLEAN_UP = 2L * 24 * 3600 * 1000 // milliseconds
        const val DURATION_DISK_USAGE_COMPUTATION = 3L * 24 * 3600 * 1000 // milliseconds

        const val WARM_UP_FILE_MAX_RETRY = 3

        private const val UNFINISHED_KEY_SPLIT = ":"
        private const val UNFINISHED_RETRY_SPLIT = "|"

        @JvmStatic
        fun cleanUpTimestampFile(context: Context): File {
            val file = File(
                context.filesDir.absolutePath + "/" + DIR_WECHAT_BACKTRACE + "/" + FILE_CLEAN_UP_TIMESTAMP
            )
            file.parentFile?.mkdirs()
            return file
        }

        @JvmStatic
        fun warmUpMarkedFile(context: Context): File {
            val file = File(
                context.filesDir.absolutePath + "/" + DIR_WECHAT_BACKTRACE + "/" + FILE_WARMED_UP
            )
            file.parentFile?.mkdirs()
            return file
        }

        @JvmStatic
        fun diskUsageFile(context: Context): File {
            val file = File(
                context.filesDir.absolutePath + "/" + DIR_WECHAT_BACKTRACE + "/" + FILE_DISK_USAGE
            )
            file.parentFile?.mkdirs()
            return file
        }

        @JvmStatic
        fun defaultSavingPath(configuration: TraceHarborBacktrace.Configuration): String {
            return configuration.mContext.filesDir.absolutePath +
                "/" + DIR_WECHAT_BACKTRACE + "/" + FILE_DEFAULT_SAVING_PATH + "/"
        }

        @JvmStatic
        fun validateSavingPath(configuration: TraceHarborBacktrace.Configuration): String {
            return if (pathValidation(configuration)) {
                configuration.mSavingPath!!
            } else {
                defaultSavingPath(configuration)
            }
        }

        @JvmStatic
        fun pathValidation(configuration: TraceHarborBacktrace.Configuration): Boolean {
            if (configuration.mSavingPath == null) {
                return false
            }
            val savingPath = File(configuration.mSavingPath!!)
            val privateStorageParent = configuration.mContext.filesDir.parentFile ?: return false
            return try {
                val privateRoot = privateStorageParent.canonicalFile.absolutePath
                if (savingPath.canonicalPath.startsWith(privateRoot)) {
                    true
                } else {
                    TraceHarborLog.e(
                        TAG,
                        "Saving path should under private storage path %s",
                        privateStorageParent.absolutePath
                    )
                    false
                }
            } catch (e: IOException) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
                false
            }
        }

        @JvmStatic
        fun unfinishedFile(context: Context): File {
            val file = File(
                context.filesDir.absolutePath + "/" + DIR_WECHAT_BACKTRACE + "/" + FILE_UNFINISHED
            )
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                try {
                    file.createNewFile()
                } catch (e: IOException) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "")
                }
            }
            return file
        }

        @JvmStatic
        fun unfinishedKey(pathOfElf: String, offset: Int): String {
            return pathOfElf + UNFINISHED_KEY_SPLIT + offset
        }

        @JvmStatic
        fun flushUnfinishedMaps(context: Context, unfinished: Map<String, Int>) {
            val file = unfinishedFile(context)
            val sb = StringBuilder()
            for ((key, value) in unfinished) {
                sb.append(key).append(UNFINISHED_RETRY_SPLIT).append(value).append('\n')
            }
            writeContentToFile(file, sb.toString())
        }

        @JvmStatic
        fun readUnfinishedMaps(context: Context): MutableMap<String, Int> {
            val maps: MutableMap<String, Int> = HashMap()
            val file = unfinishedFile(context)
            val content = readFileContent(file, 512_000)
            if (content == null) {
                TraceHarborLog.w(TAG, "Read unfinished maps file failed, file size %s", file.length())
                if (file.length() > 512_000) {
                    file.delete()
                }
            } else {
                val lines = content.split("\n")
                for (line in lines) {
                    val index = line.lastIndexOf(UNFINISHED_RETRY_SPLIT)
                    if (index < 0) {
                        continue
                    }
                    try {
                        val key = line.substring(0, index)
                        val value = line.substring(index + 1)
                        maps[key] = value.toInt()
                    } catch (ignore: Throwable) {
                        TraceHarborLog.printErrStackTrace(TAG, ignore, "")
                    }
                }
            }
            return maps
        }

        @JvmStatic
        fun needCleanUp(context: Context): Boolean {
            val timestamp = cleanUpTimestampFile(context)
            if (!timestamp.exists()) {
                try {
                    timestamp.createNewFile()
                } catch (e: IOException) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "")
                }
                return false
            }
            return System.currentTimeMillis() - timestamp.lastModified() >= DURATION_CLEAN_UP
        }

        @JvmStatic
        fun shouldComputeDiskUsage(context: Context): Boolean {
            val timestamp = diskUsageFile(context)
            if (!timestamp.exists()) {
                try {
                    timestamp.createNewFile()
                } catch (e: IOException) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "")
                }
                return false
            }
            return System.currentTimeMillis() - timestamp.lastModified() >= DURATION_DISK_USAGE_COMPUTATION
        }

        @JvmStatic
        fun markComputeDiskUsageTimestamp(context: Context) {
            val timestamp = diskUsageFile(context)
            try {
                timestamp.createNewFile()
                timestamp.setLastModified(System.currentTimeMillis())
            } catch (e: IOException) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
            }
        }

        @JvmStatic
        fun hasWarmedUp(context: Context): Boolean {
            return warmUpMarkedFile(context).exists()
        }

        @JvmStatic
        fun markCleanUpTimestamp(context: Context) {
            val timestamp = cleanUpTimestampFile(context)
            try {
                timestamp.createNewFile()
                timestamp.setLastModified(System.currentTimeMillis())
            } catch (e: IOException) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
            }
        }

        @JvmStatic
        fun iterateTargetDirectory(target: File, cs: CancellationSignal, filter: FileFilter) {
            if (target.isDirectory) {
                val files = target.listFiles()
                if (files != null) {
                    for (file in files) {
                        iterateTargetDirectory(file, cs, filter)
                        cs.throwIfCanceled()
                    }
                }
            } else {
                filter.accept(target)
                cs.throwIfCanceled()
            }
        }

        @JvmStatic
        fun readFileContent(file: File, max: Int): String? {
            if (!file.isFile) {
                return null
            }
            var reader: FileReader? = null
            try {
                val sb = StringBuilder(4096)
                reader = FileReader(file)
                val buffer = CharArray(1024)
                var accumulated = 0
                while (true) {
                    val len = reader.read(buffer)
                    if (len <= 0) {
                        break
                    }
                    sb.append(buffer, 0, len)
                    accumulated += len
                    if (accumulated > max) {
                        return null
                    }
                }
                return sb.toString()
            } catch (e: Exception) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
            } finally {
                if (reader != null) {
                    try {
                        reader.close()
                    } catch (e: IOException) {
                        TraceHarborLog.printErrStackTrace(TAG, e, "")
                    }
                }
            }
            return null
        }

        @JvmStatic
        fun writeContentToFile(file: File, content: String): Boolean {
            if (!file.isFile) {
                return false
            }
            var writer: FileWriter? = null
            try {
                writer = FileWriter(file)
                writer.write(content)
                return true
            } catch (e: Exception) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
            } finally {
                if (writer != null) {
                    try {
                        writer.close()
                    } catch (e: IOException) {
                        TraceHarborLog.printErrStackTrace(TAG, e, "")
                    }
                }
            }
            return false
        }
    }
}

