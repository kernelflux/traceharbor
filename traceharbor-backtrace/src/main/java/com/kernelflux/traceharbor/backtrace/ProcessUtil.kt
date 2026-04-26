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

import android.app.ActivityManager
import android.content.Context
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream

object ProcessUtil {
    private var processNameCache: String? = null

    @JvmStatic
    @Synchronized
    fun getProcessNameByPid(context: Context?): String {
        if (processNameCache == null) {
            processNameCache = getProcessNameByPidImpl(context, android.os.Process.myPid())
        }
        return processNameCache.orEmpty()
    }

    private fun getProcessNameByPidImpl(context: Context?, pid: Int): String {
        if (context == null || pid <= 0) {
            return ""
        }
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses
            if (processes != null) {
                for (info in processes) {
                    if (info.pid == pid && !info.processName.isNullOrEmpty()) {
                        return info.processName
                    }
                }
            }
        } catch (_: Exception) {
        }

        val b = ByteArray(128)
        var input: InputStream? = null
        try {
            input = BufferedInputStream(FileInputStream("/proc/$pid/cmdline"))
            var len = input.read(b)
            if (len > 0) {
                for (i in 0 until len) {
                    if (b[i].toInt() > 128 || b[i] <= 0) {
                        len = i
                        break
                    }
                }
                return String(b, 0, len)
            }
        } catch (_: Exception) {
        } finally {
            try {
                input?.close()
            } catch (_: Exception) {
            }
        }

        return ""
    }

    @JvmStatic
    fun isMainProcess(context: Context): Boolean {
        val processName = getProcessNameByPid(context)
        return context.packageName.equals(processName, ignoreCase = true)
    }
}

