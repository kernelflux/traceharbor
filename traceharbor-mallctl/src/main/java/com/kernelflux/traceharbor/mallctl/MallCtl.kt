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

package com.kernelflux.traceharbor.mallctl

import androidx.annotation.Keep
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Created by Yves on 2020/7/15
 *
 * Static facade — same pattern as FDDumpBridge / TraceHarborHandlerThread.
 * All public/native methods stay in the `companion object` with `@JvmStatic`
 * so JNI symbols (Java_com_kernelflux_traceharbor_mallctl_MallCtl_*)
 * and existing Java callers (`MallCtl.mallopt()`, `MallCtl.MALLOPT_FAILED`)
 * keep their bytecode.
 */
class MallCtl private constructor() {

    companion object {
        private const val TAG = "TraceHarbor.JeCtl"

        @JvmStatic
        private var initialized: Boolean = false

        init {
            try {
                System.loadLibrary("traceharbor-mallctl")
                initNative()
                initialized = true
            } catch (e: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
            }
        }

        @JvmStatic
        @Synchronized
        fun jeVersion(): String {
            if (!initialized) {
                TraceHarborLog.e(TAG, "JeCtl init failed! check if so exists")
                return "VER_UNKNOWN"
            }
            return getVersionNative()
        }

        @JvmStatic
        @Synchronized
        fun jeSetRetain(enable: Boolean): Boolean {
            try {
                return setRetainNative(enable)
            } catch (e: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, e, "set retain failed")
            }
            return false
        }

        const val MALLOPT_FAILED: Int = 0
        const val MALLOPT_SUCCESS: Int = 1
        const val MALLOPT_SYM_NOT_FOUND: Int = -1
        const val MALLOPT_EXCEPTION: Int = -2

        /**
         * @return On success, mallopt() returns 1.  On error, it returns 0.
         */
        @JvmStatic
        @Synchronized
        fun mallopt(): Int {
            try {
                return malloptNative()
            } catch (e: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, e, "mallopt failed")
            }
            return MALLOPT_EXCEPTION
        }

        @JvmStatic
        @Synchronized
        fun flushReadOnlyFilePages(prediction: TrimPrediction?) {
            val pred: TrimPrediction = prediction ?: DefaultPrediction()
            val pattern = Pattern.compile(
                "^([0-9a-f]+)-([0-9a-f]+)\\s+([rwxps-]{4})\\s+[0-9a-f]+\\s+[0-9a-f]+:[0-9a-f]+\\s+\\d+\\s*(.*)$",
            )
            try {
                BufferedReader(InputStreamReader(FileInputStream("/proc/self/maps"))).use { br ->
                    var line: String? = br.readLine()
                    while (line != null) {
                        val matcher = pattern.matcher(line)
                        if (matcher.find()) {
                            val beginStr = matcher.group(1)
                            val endStr = matcher.group(2)
                            val permission = matcher.group(3)
                            var name = matcher.group(4)
                            if (name.isNullOrEmpty()) {
                                name = "[no-name]"
                            }
                            if (permission != null && pred.canBeTrim(name, permission) && beginStr != null && endStr != null) {
                                try {
                                    val beginPtr = java.lang.Long.parseLong(beginStr, 16)
                                    val endPtr = java.lang.Long.parseLong(endStr, 16)
                                    val size = endPtr - beginPtr
                                    flushReadOnlyFilePagesNative(beginPtr, size)
                                } catch (e: Throwable) {
                                    TraceHarborLog.printErrStackTrace(
                                        TAG, e, "%s-%s %s %s",
                                        beginStr, endStr, permission, name,
                                    )
                                }
                            }
                        }
                        line = br.readLine()
                    }
                }
            } catch (e: IOException) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
            }
        }

        @JvmStatic
        @Keep
        private external fun initNative()

        @JvmStatic
        @Keep
        private external fun getVersionNative(): String

        @JvmStatic
        @Keep
        private external fun malloptNative(): Int

        @JvmStatic
        @Keep
        private external fun setRetainNative(enable: Boolean): Boolean

        @JvmStatic
        private external fun flushReadOnlyFilePagesNative(begin: Long, size: Long): Int
    }

    fun interface TrimPrediction {
        fun canBeTrim(pathName: String, permission: String): Boolean
    }

    open class DefaultPrediction : TrimPrediction {
        override fun canBeTrim(pathName: String, permission: String): Boolean {
            var name = pathName
            if (name.endsWith(" (deleted)")) {
                name = name.substring(0, name.length - " (deleted)".length)
            } else if (name.endsWith("]")) {
                name = name.substring(0, name.length - "]".length)
            }
            return !permission.contains("w") &&
                    (name.endsWith(".so") ||
                            name.endsWith(".dex") ||
                            name.endsWith(".apk") ||
                            name.endsWith(".vdex") ||
                            name.endsWith(".odex") ||
                            name.endsWith(".oat") ||
                            name.endsWith(".art") ||
                            name.endsWith(".ttf") ||
                            name.endsWith(".otf") ||
                            name.endsWith(".jar"))
        }
    }
}
