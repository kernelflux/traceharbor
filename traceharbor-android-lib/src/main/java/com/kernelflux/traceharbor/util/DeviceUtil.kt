/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
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

package com.kernelflux.traceharbor.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Debug
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.util.regex.Pattern

/**
 * Created by caichongyang on 17/5/18.
 * about Device Info
 *
 * Static-only facade. `class DeviceUtil` (open per the original Java
 * declaration — note the Java class wasn't `final`, so a subclass might
 * exist downstream) plus a companion object that hosts every method
 * and constant with `@JvmStatic` so the bytecode shape matches the
 * original Java `public class DeviceUtil`.
 */
open class DeviceUtil {

    companion object {
        private const val MB: Long = 1024L * 1024L
        private const val TAG = "TraceHarbor.DeviceUtil"
        private const val INVALID = 0
        private const val MEMORY_FILE_PATH = "/proc/meminfo"
        private const val CPU_FILE_PATH_0 = "/sys/devices/system/cpu/"
        private const val CPU_FILE_PATH_1 = "/sys/devices/system/cpu/possible"
        private const val CPU_FILE_PATH_2 = "/sys/devices/system/cpu/present"

        @JvmStatic
        private var sLevelCache: LEVEL? = null

        @JvmField
        val DEVICE_MACHINE: String = "machine"
        private const val DEVICE_MEMORY_FREE = "mem_free"
        private const val DEVICE_MEMORY = "mem"
        private const val DEVICE_CPU = "cpu_app"

        @JvmStatic
        private var sTotalMemory: Long = 0

        @JvmStatic
        private var sLowMemoryThresold: Long = 0

        @JvmStatic
        private var sMemoryClass: Int = 0

        @JvmStatic
        fun getDeviceInfo(oldObj: JSONObject, context: Application): JSONObject {
            try {
                oldObj.put(DEVICE_MACHINE, getLevel(context))
                oldObj.put(DEVICE_CPU, getAppCpuRate())
                oldObj.put(DEVICE_MEMORY, getTotalMemory(context))
                oldObj.put(DEVICE_MEMORY_FREE, getMemFree(context))
            } catch (e: JSONException) {
                TraceHarborLog.e(TAG, "[JSONException for stack, error: %s", e)
            }
            return oldObj
        }

        @JvmStatic
        fun getLevel(context: Context): LEVEL {
            sLevelCache?.let { return it }
            val start = System.currentTimeMillis()
            val totalMemory = getTotalMemory(context)
            val coresNum = getNumOfCores()
            TraceHarborLog.i(TAG, "[getLevel] totalMemory:%s coresNum:%s", totalMemory, coresNum)
            sLevelCache = when {
                totalMemory >= 8 * 1024 * MB -> LEVEL.BEST
                totalMemory >= 6 * 1024 * MB -> LEVEL.HIGH
                totalMemory >= 4 * 1024 * MB -> LEVEL.MIDDLE
                totalMemory >= 2 * 1024 * MB -> when {
                    coresNum >= 4 -> LEVEL.MIDDLE
                    coresNum > 0 -> LEVEL.LOW
                    // Original Java fell through and left sLevelCache untouched
                    // (i.e. still null) for the impossible coresNum <= 0 case.
                    // Match that behaviour by leaving cache null then re-reading.
                    else -> sLevelCache
                }
                totalMemory >= 0 -> LEVEL.BAD
                else -> LEVEL.UN_KNOW
            }

            TraceHarborLog.i(
                TAG,
                "getLevel, cost:" + (System.currentTimeMillis() - start) + ", level:" + sLevelCache,
            )
            // Same null-fallthrough as above; the Java contract is "non-null
            // for any normal device", so use UN_KNOW for the cores<=0 path.
            return sLevelCache ?: LEVEL.UN_KNOW
        }

        @JvmStatic
        private fun getAppId(): Int = android.os.Process.myPid()

        @JvmStatic
        fun getLowMemoryThresold(context: Context): Long {
            if (sLowMemoryThresold != 0L) {
                return sLowMemoryThresold
            }
            getTotalMemory(context)
            return sLowMemoryThresold
        }

        // in KB
        @JvmStatic
        fun getMemoryClass(context: Context): Int {
            if (sMemoryClass != 0) {
                return sMemoryClass * 1024
            }
            getTotalMemory(context)
            return sMemoryClass * 1024
        }

        @JvmStatic
        @Suppress("DEPRECATION")
        fun getTotalMemory(context: Context): Long {
            if (sTotalMemory != 0L) {
                return sTotalMemory
            }

            val start = System.currentTimeMillis()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                val memInfo = ActivityManager.MemoryInfo()
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.getMemoryInfo(memInfo)
                sTotalMemory = memInfo.totalMem
                sLowMemoryThresold = memInfo.threshold

                val memClass = Runtime.getRuntime().maxMemory()
                sMemoryClass = if (memClass == Long.MAX_VALUE) {
                    am.memoryClass // if not set maxMemory, then is not large heap
                } else {
                    (memClass / MB).toInt()
                }
                // int isLargeHeap = (context.getApplicationInfo().flags | ApplicationInfo.FLAG_LARGE_HEAP);
                // if (isLargeHeap > 0) {
                //     sMemoryClass = am.getLargeMemoryClass();
                // } else {
                //     sMemoryClass = am.getMemoryClass();
                // }

                TraceHarborLog.i(
                    TAG,
                    "getTotalMemory cost:" + (System.currentTimeMillis() - start) + ", total_mem:" + sTotalMemory +
                            ", LowMemoryThresold:" + sLowMemoryThresold + ", Memory Class:" + sMemoryClass,
                )
                return sTotalMemory
            }
            return 0
        }

        @JvmStatic
        fun isLowMemory(context: Context): Boolean {
            val memInfo = ActivityManager.MemoryInfo()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getMemoryInfo(memInfo)
            return memInfo.lowMemory
        }

        // return in KB
        @JvmStatic
        fun getAvailMemory(@Suppress("UNUSED_PARAMETER") context: Context): Long {
            val runtime = Runtime.getRuntime()
            return runtime.freeMemory() / 1024 // in KB
        }

        @JvmStatic
        @Suppress("DEPRECATION")
        fun getMemFree(context: Context): Long {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                val memInfo = ActivityManager.MemoryInfo()
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.getMemoryInfo(memInfo)
                memInfo.availMem / 1024
            } else {
                var availMemory: Long = INVALID.toLong()
                var bufferedReader: BufferedReader? = null
                try {
                    bufferedReader = BufferedReader(
                        InputStreamReader(FileInputStream(MEMORY_FILE_PATH), "UTF-8"),
                    )
                    var line: String? = bufferedReader.readLine()
                    while (line != null) {
                        val args = line.split("\\s+".toRegex()).toTypedArray()
                        if ("MemAvailable:" == args[0]) {
                            availMemory = args[1].toInt() * 1024L
                            break
                        } else {
                            line = bufferedReader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    TraceHarborLog.i(TAG, "[getAvailMemory] error! %s", e.toString())
                } finally {
                    try {
                        bufferedReader?.close()
                    } catch (e: Exception) {
                        TraceHarborLog.i(TAG, "close reader %s", e.toString())
                    }
                }
                availMemory / 1024
            }
        }

        @JvmStatic
        fun getAppCpuRate(): Double {
            val start = System.currentTimeMillis()
            var cpuTime: Long = 0L
            var appTime: Long = 0L
            var cpuRate = 0.0
            var procStatFile: RandomAccessFile? = null
            var appStatFile: RandomAccessFile? = null

            try {
                procStatFile = RandomAccessFile("/proc/stat", "r")
                val procStatString = procStatFile.readLine()
                val procStats = procStatString.split(" ").toTypedArray()
                cpuTime = procStats[2].toLong() + procStats[3].toLong() +
                        procStats[4].toLong() + procStats[5].toLong() +
                        procStats[6].toLong() + procStats[7].toLong() +
                        procStats[8].toLong()
            } catch (e: Exception) {
                TraceHarborLog.i(
                    TAG,
                    "RandomAccessFile(Process Stat) reader fail, error: %s",
                    e.toString(),
                )
            } finally {
                try {
                    procStatFile?.close()
                } catch (e: Exception) {
                    TraceHarborLog.i(TAG, "close process reader %s", e.toString())
                }
            }

            try {
                appStatFile = RandomAccessFile("/proc/" + getAppId() + "/stat", "r")
                val appStatString = appStatFile.readLine()
                val appStats = appStatString.split(" ").toTypedArray()
                appTime = appStats[13].toLong() + appStats[14].toLong()
            } catch (e: Exception) {
                TraceHarborLog.i(
                    TAG,
                    "RandomAccessFile(App Stat) reader fail, error: %s",
                    e.toString(),
                )
            } finally {
                try {
                    appStatFile?.close()
                } catch (e: Exception) {
                    TraceHarborLog.i(TAG, "close app reader %s", e.toString())
                }
            }

            if (cpuTime != 0L) {
                cpuRate = (appTime.toDouble() / cpuTime.toDouble()) * 100.0
            }

            TraceHarborLog.i(
                TAG,
                "getAppCpuRate cost:" + (System.currentTimeMillis() - start) + ",rate:" + cpuRate,
            )
            return cpuRate
        }

        @JvmStatic
        fun getAppMemory(context: Context): Debug.MemoryInfo? {
            try {
                // 统计进程的内存信息 totalPss
                val activityManager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(getAppId()))
                if (memInfo.isNotEmpty()) {
                    return memInfo[0]
                }
            } catch (e: Exception) {
                TraceHarborLog.i(TAG, "getProcessMemoryInfo fail, error: %s", e.toString())
            }
            return null
        }

        @JvmStatic
        @Suppress("DEPRECATION")
        private fun getNumOfCores(): Int {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
                return 1
            }
            var cores: Int
            try {
                cores = getCoresFromFile(CPU_FILE_PATH_1)
                if (cores == INVALID) {
                    cores = getCoresFromFile(CPU_FILE_PATH_2)
                }
                if (cores == INVALID) {
                    cores = getCoresFromCPUFiles(CPU_FILE_PATH_0)
                }
            } catch (e: Exception) {
                cores = INVALID
            }
            if (cores == INVALID) {
                cores = 1
            }
            return cores
        }

        @JvmStatic
        private fun getCoresFromCPUFiles(path: String): Int {
            val list = File(path).listFiles(CPU_FILTER)
            return list?.size ?: 0
        }

        @JvmStatic
        private fun getCoresFromFile(file: String): Int {
            var input: InputStream? = null
            try {
                input = FileInputStream(file)
                val buf = BufferedReader(InputStreamReader(input, "UTF-8"))
                val fileContents = buf.readLine()
                buf.close()
                if (fileContents == null || !fileContents.matches(Regex("0-[\\d]+$"))) {
                    return INVALID
                }
                val num = fileContents.substring(2)
                return num.toInt() + 1
            } catch (e: IOException) {
                TraceHarborLog.i(TAG, "[getCoresFromFile] error! %s", e.toString())
                return INVALID
            } finally {
                try {
                    input?.close()
                } catch (e: IOException) {
                    TraceHarborLog.i(TAG, "[getCoresFromFile] error! %s", e.toString())
                }
            }
        }

        @JvmStatic
        private val CPU_FILTER: FileFilter = FileFilter { pathname ->
            Pattern.matches("cpu[0-9]", pathname.name)
        }

        @JvmStatic
        fun getDalvikHeap(): Long {
            val runtime = Runtime.getRuntime()
            return (runtime.totalMemory() - runtime.freeMemory()) / 1024 // in KB
        }

        @JvmStatic
        fun getNativeHeap(): Long = Debug.getNativeHeapAllocatedSize() / 1024 // in KB

        @JvmStatic
        fun getVmSize(): Long {
            val status = String.format("/proc/%s/status", getAppId())
            try {
                val content = getStringFromFile(status).trim()
                val args = content.split("\n").toTypedArray()
                for (str in args) {
                    if (str.startsWith("VmSize")) {
                        val p = Pattern.compile("\\d+")
                        val matcher = p.matcher(str)
                        if (matcher.find()) {
                            return matcher.group().toLong()
                        }
                    }
                }
                if (args.size > 12) {
                    val p = Pattern.compile("\\d+")
                    val matcher = p.matcher(args[12])
                    if (matcher.find()) {
                        return matcher.group().toLong()
                    }
                }
            } catch (e: Exception) {
                return -1
            }
            return -1
        }

        @JvmStatic
        @Throws(Exception::class)
        fun convertStreamToString(`is`: InputStream): String {
            var reader: BufferedReader? = null
            val sb = StringBuilder()
            try {
                reader = BufferedReader(InputStreamReader(`is`, "UTF-8"))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append('\n')
                }
            } finally {
                reader?.close()
            }
            return sb.toString()
        }

        @JvmStatic
        @Throws(Exception::class)
        fun getStringFromFile(filePath: String): String {
            val fl = File(filePath)
            var fin: FileInputStream? = null
            return try {
                fin = FileInputStream(fl)
                convertStreamToString(fin)
            } finally {
                fin?.close()
            }
        }

        /**
         * Check if current runtime is 64bit.
         *
         * @return True if current runtime is 64bit abi. Otherwise return false instead.
         */
        @JvmStatic
        @Suppress("DEPRECATION")
        fun is64BitRuntime(): Boolean {
            val currRuntimeABI = Build.CPU_ABI
            return "arm64-v8a".equals(currRuntimeABI, ignoreCase = true) ||
                    "x86_64".equals(currRuntimeABI, ignoreCase = true) ||
                    "mips64".equals(currRuntimeABI, ignoreCase = true)
        }
    }

    enum class LEVEL(val value: Int) {
        BEST(5), HIGH(4), MIDDLE(3), LOW(2), BAD(1), UN_KNOW(-1);
        // Kotlin auto-generates `int getValue()` for the constructor val,
        // matching the original Java `public int getValue()` accessor.
    }
}
