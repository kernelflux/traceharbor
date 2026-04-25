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

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.ArrayMap
import android.util.Log
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Field
import java.math.BigInteger
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Objects

/**
 * Created by zhangshaowen on 17/6/1.
 *
 * Static-only utility facade — `class ... private constructor()` plus a
 * companion object hosting every method/field with `@JvmStatic` so the
 * compiled bytecode looks identical to the original Java `final class`.
 */
class TraceHarborUtil private constructor() {

    companion object {
        private const val TAG = "TraceHarbor.TraceHarborUtil"

        @JvmStatic
        private var processName: String? = null

        @JvmStatic
        private var packageName: String? = null

        @JvmStatic
        fun formatTime(format: String, timeMilliSecond: Long): String =
            java.text.SimpleDateFormat(format).format(java.util.Date(timeMilliSecond))

        @JvmStatic
        @Deprecated("use isInMainThread() instead")
        fun isInMainThread(threadId: Long): Boolean =
            Looper.getMainLooper().thread.id == threadId

        @JvmStatic
        fun isInMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

        // Note on nullability: the original Java signatures accepted a raw
        // `Context` (i.e. platform-type), and several Kotlin callers in
        // ProcessSupervisor / SubordinatePacemaker / SupervisorPacemaker pass
        // a nullable `Context?` / `Application?` directly. Keeping these
        // overloads nullable preserves source compat without forcing `!!`
        // sprinkles at every call site.

        @JvmStatic
        fun isInMainProcess(context: Context?): Boolean {
            if (context == null) return false
            val pkgName = context.packageName
            val procName = getProcessName(context).orEmpty()
            return pkgName == procName
        }

        @JvmStatic
        fun isMainProcessName(processName: String?, context: Context): Boolean {
            val pkgName = context.packageName
            return Objects.equals(pkgName, processName)
        }

        @JvmStatic
        fun getPackageName(context: Context?): String {
            packageName?.let { return it }
            if (context == null) return ""
            return context.packageName.also { packageName = it }
        }

        /**
         * add process name cache.
         */
        @JvmStatic
        fun getProcessName(context: Context?): String {
            processName?.let { return it }
            // will not null
            return getProcessNameInternal(context).also { processName = it }
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        private fun getProcessNameInternal(context: Context?): String {
            val myPid = android.os.Process.myPid()
            if (context == null || myPid <= 0) {
                return ""
            }

            var myProcess: ActivityManager.RunningAppProcessInfo? = null
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val appProcessList = activityManager.runningAppProcesses
                if (appProcessList != null) {
                    try {
                        for (process in appProcessList) {
                            if (process.pid == myPid) {
                                myProcess = process
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "getProcessNameInternal exception:" + e.message)
                    }
                    if (myProcess != null) {
                        return myProcess.processName
                    }
                }
            }

            val b = ByteArray(128)
            var input: FileInputStream? = null
            try {
                input = FileInputStream("/proc/$myPid/cmdline")
                var len = input.read(b)
                if (len > 0) {
                    // lots of '0' in tail , remove them
                    for (i in 0 until len) {
                        if (b[i] <= 0) {
                            len = i
                            break
                        }
                    }
                    return String(b, 0, len, Charset.forName("UTF-8"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "getProcessNameInternal exception:" + e.message)
            } finally {
                try {
                    input?.close()
                } catch (e: Exception) {
                    Log.e(TAG, e.message.orEmpty())
                }
            }
            return ""
        }

        @JvmStatic
        fun getLatestStack(stack: String?, count: Int): String {
            if (stack.isNullOrEmpty()) {
                return ""
            }
            val strings = stack.split("\n")
            if (strings.size <= count) {
                return stack
            }
            val sb = StringBuffer(count)
            for (i in 0 until count) {
                sb.append(strings[i]).append('\n')
            }
            return sb.toString()
        }

        @JvmStatic
        fun printException(e: Exception): String {
            // Original Java guarded against `stackTrace == null`, kept verbatim.
            @Suppress("SENSELESS_COMPARISON")
            val stackTrace: Array<StackTraceElement>? = e.stackTrace
            if (stackTrace == null) {
                return ""
            }
            val t = StringBuilder(e.toString())
            for (i in 2 until stackTrace.size) {
                t.append('[')
                t.append(stackTrace[i].className)
                t.append(':')
                t.append(stackTrace[i].methodName)
                t.append("(" + stackTrace[i].lineNumber + ")]")
                t.append("\n")
            }
            return t.toString()
        }

        /**
         * Closes the given [Closeable]. Suppresses any IO exceptions.
         */
        @JvmStatic
        fun closeQuietly(closeable: Closeable?) {
            try {
                closeable?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close resource", e)
            }
        }

        @JvmStatic
        private val hexDigits: CharArray = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
        )

        @JvmStatic
        private val MD5_DIGEST: ThreadLocal<MessageDigest> = object : ThreadLocal<MessageDigest>() {
            override fun initialValue(): MessageDigest = try {
                MessageDigest.getInstance("MD5")
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("Initialize MD5 failed.", e)
            }
        }

        @JvmStatic
        fun getMD5String(s: String): String = getMD5String(s.toByteArray())

        @JvmStatic
        fun getMD5String(bytes: ByteArray): String {
            val digest = MD5_DIGEST.get()!!
            return bufferToHex(digest.digest(bytes))
        }

        @JvmStatic
        private val SHA256_DIGEST: ThreadLocal<MessageDigest> =
            object : ThreadLocal<MessageDigest>() {
                override fun initialValue(): MessageDigest = try {
                    MessageDigest.getInstance("SHA-256")
                } catch (e: NoSuchAlgorithmException) {
                    throw RuntimeException("Initialize SHA256-DIGEST failed.", e)
                }
            }

        @JvmStatic
        @Throws(NoSuchAlgorithmException::class)
        private fun getSHA(input: String): ByteArray {
            // Static getInstance method is called with hashing SHA
            val md = SHA256_DIGEST.get()!!
            // digest() method called
            // to calculate message digest of an input
            // and return array of byte
            return md.digest(input.toByteArray(StandardCharsets.UTF_8))
        }

        @JvmStatic
        private fun toHexString(hash: ByteArray): String {
            // Convert byte array into signum representation
            val number = BigInteger(1, hash)
            // Convert message digest into hex value
            val hexString = StringBuilder(number.toString(16))
            // Pad with leading zeros
            while (hexString.length < 32) {
                hexString.insert(0, '0')
            }
            return hexString.toString()
        }

        @JvmStatic
        @Throws(NoSuchAlgorithmException::class)
        fun getSHA256String(s: String): String = toHexString(getSHA(s))

        @JvmStatic
        private fun bufferToHex(bytes: ByteArray): String =
            bufferToHex(bytes, 0, bytes.size)

        @JvmStatic
        private fun bufferToHex(bytes: ByteArray, m: Int, n: Int): String {
            val stringbuffer = StringBuffer(2 * n)
            val k = m + n
            for (l in m until k) {
                appendHexPair(bytes[l], stringbuffer)
            }
            return stringbuffer.toString()
        }

        @JvmStatic
        private fun appendHexPair(bt: Byte, stringbuffer: StringBuffer) {
            val c0 = hexDigits[(bt.toInt() and 0xf0) shr 4]
            val c1 = hexDigits[bt.toInt() and 0xf]
            stringbuffer.append(c0)
            stringbuffer.append(c1)
        }

        @JvmStatic
        @Throws(IOException::class)
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

        @JvmStatic
        @Throws(IOException::class)
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
        fun parseLong(string: String?, def: Long): Long {
            try {
                return if (string.isNullOrEmpty()) def else java.lang.Long.decode(string)
            } catch (e: NumberFormatException) {
                TraceHarborLog.w(TAG, "parseLong error: " + e.message)
            }
            return def
        }

        @JvmStatic
        @Throws(IOException::class)
        fun printFileByLine(printTAG: String, filePath: String) {
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(FileInputStream(File(filePath)), "UTF-8"))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    TraceHarborLog.i(printTAG, line!!)
                }
            } catch (t: Throwable) {
                TraceHarborLog.e(printTAG, "printFileByLine failed e : " + t.message)
            } finally {
                reader?.close()
            }
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        fun getTopActivityName(): String? {
            val start = System.currentTimeMillis()
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val activityThread =
                    activityThreadClass.getMethod("currentActivityThread").invoke(null)
                val activitiesField: Field = activityThreadClass.getDeclaredField("mActivities")
                activitiesField.isAccessible = true

                val activities: Map<Any, Any> = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    activitiesField.get(activityThread) as HashMap<Any, Any>
                } else {
                    activitiesField.get(activityThread) as ArrayMap<Any, Any>
                }
                if (activities.size < 1) {
                    return null
                }
                for (activityRecord in activities.values) {
                    val activityRecordClass: Class<*> = activityRecord.javaClass
                    val pausedField: Field = activityRecordClass.getDeclaredField("paused")
                    pausedField.isAccessible = true
                    if (!pausedField.getBoolean(activityRecord)) {
                        val activityField: Field = activityRecordClass.getDeclaredField("activity")
                        activityField.isAccessible = true
                        val activity = activityField.get(activityRecord) as Activity
                        return activity.javaClass.name
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                val cost = System.currentTimeMillis() - start
                TraceHarborLog.d(TAG, "[getTopActivityName] Cost:%s", cost)
            }
            return null
        }
    }
}
