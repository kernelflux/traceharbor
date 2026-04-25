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

package com.kernelflux.traceharbor.batterycanary.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.text.TextUtils
import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.batterycanary.BatteryMonitorPlugin
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.APP_STAT_BACKGROUND
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.APP_STAT_FLOAT_WINDOW
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.APP_STAT_FOREGROUND
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.APP_STAT_FOREGROUND_SERVICE
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.DEV_STAT_CHARGING
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.DEV_STAT_DOZE_MODE_OFF
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.DEV_STAT_DOZE_MODE_ON
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.DEV_STAT_SAVE_POWER_MODE_OFF
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.DEV_STAT_SAVE_POWER_MODE_ON
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.DEV_STAT_SCREEN_OFF
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.DEV_STAT_SCREEN_ON
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.DEV_STAT_UN_CHARGING
import com.kernelflux.traceharbor.lifecycle.owners.OverlayWindowLifecycleOwner
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Method
import java.util.Collections
import java.util.Comparator
import java.util.LinkedHashMap
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * @author liyongjie
 * Created by liyongjie on 2017/8/14.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
object BatteryCanaryUtil {
    private const val TAG = "TraceHarbor.battery.Utils"
    private const val DEFAULT_MAX_STACK_LAYER = 10
    private const val DEFAULT_AMS_CACHE_MILLIS = 5 * 1000

    const val ONE_MIN: Int = 60 * 1000
    const val ONE_HOR: Int = 60 * 60 * 1000
    const val JIFFY_HZ: Int = 100 // @Os.sysconf(OsConstants._SC_CLK_TCK)
    const val JIFFY_MILLIS: Int = 1000 / JIFFY_HZ

    interface Proxy {
        fun getProcessName(): String
        fun getPackageName(): String
        fun getBatteryTemperature(context: Context): Int
        @AppStats.AppStatusDef
        fun getAppStat(context: Context, isForeground: Boolean): Int
        @AppStats.DevStatusDef
        fun getDevStat(context: Context): Int
        fun updateAppStat(value: Int)
        fun updateDevStat(value: Int)
        fun getBatteryPercentage(context: Context): Int
        fun getBatteryCapacity(context: Context): Int
        fun getBatteryCurrency(context: Context): Long
        fun getCpuCoreNum(): Int

        class ExpireRef<T : Number>(
            @JvmField val value: T,
            @JvmField val aliveMillis: Long,
        ) {
            @JvmField
            val lastMillis: Long = SystemClock.uptimeMillis()

            fun isExpired(): Boolean {
                return SystemClock.uptimeMillis() - lastMillis >= aliveMillis
            }
        }
    }

    @Suppress("SpellCheckingInspection")
    @JvmField
    var sCacheStub: Proxy = object : Proxy {
        private var mProcessName: String? = null
        private var mPackageName: String? = null
        private var mBatteryTemp: Proxy.ExpireRef<Int>? = null
        private var mLastAppStat: Proxy.ExpireRef<Int>? = null
        private var mLastDevStat: Proxy.ExpireRef<Int>? = null
        private var mLastBattPct: Proxy.ExpireRef<Int>? = null
        private var mLastBattCap: Proxy.ExpireRef<Int>? = null
        private var mLastBattCur: Proxy.ExpireRef<Long>? = null
        private var mLastCpuCoreNum: Proxy.ExpireRef<Int>? = null

        override fun getProcessName(): String {
            if (!TextUtils.isEmpty(mProcessName)) {
                return mProcessName!!
            }
            val plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin::class.java)
                ?: throw IllegalStateException("BatteryMonitorPlugin is not yet installed!")
            mProcessName = plugin.processName
            return mProcessName!!
        }

        override fun getPackageName(): String {
            if (!TextUtils.isEmpty(mPackageName)) {
                return mPackageName!!
            }
            val plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin::class.java)
                ?: throw IllegalStateException("BatteryMonitorPlugin is not yet installed!")
            mPackageName = plugin.packageName
            return mPackageName!!
        }

        override fun getBatteryTemperature(context: Context): Int {
            val ref = mBatteryTemp
            if (ref != null && !ref.isExpired()) {
                return ref.value
            }
            val tmp = getBatteryTemperatureImmediately(context)
            mBatteryTemp = Proxy.ExpireRef(tmp, DEFAULT_AMS_CACHE_MILLIS.toLong())
            return mBatteryTemp!!.value
        }

        override fun getAppStat(context: Context, isForeground: Boolean): Int {
            if (isForeground) return APP_STAT_FOREGROUND // 前台
            val ref = mLastAppStat
            if (ref != null && !ref.isExpired()) {
                return ref.value
            }
            val value = getAppStatImmediately(context, false)
            mLastAppStat = Proxy.ExpireRef(value, DEFAULT_AMS_CACHE_MILLIS.toLong())
            return mLastAppStat!!.value
        }

        override fun getDevStat(context: Context): Int {
            val ref = mLastDevStat
            if (ref != null && !ref.isExpired()) {
                return ref.value
            }
            val value = getDeviceStatImmediately(context)
            mLastDevStat = Proxy.ExpireRef(value, DEFAULT_AMS_CACHE_MILLIS.toLong())
            return mLastDevStat!!.value
        }

        override fun updateAppStat(value: Int) {
            synchronized(this) {
                mLastAppStat = Proxy.ExpireRef(value, DEFAULT_AMS_CACHE_MILLIS.toLong())
            }
        }

        override fun updateDevStat(value: Int) {
            synchronized(this) {
                mLastDevStat = Proxy.ExpireRef(value, DEFAULT_AMS_CACHE_MILLIS.toLong())
            }
        }

        override fun getBatteryPercentage(context: Context): Int {
            val ref = mLastBattPct
            if (ref != null && !ref.isExpired()) {
                return ref.value
            }
            val value = getBatteryPercentageImmediately(context)
            mLastBattPct = Proxy.ExpireRef(value, ONE_MIN.toLong())
            return mLastBattPct!!.value
        }

        override fun getBatteryCapacity(context: Context): Int {
            val ref = mLastBattCap
            if (ref != null && !ref.isExpired()) {
                return ref.value
            }
            val value = getBatteryCapacityImmediately(context)
            mLastBattCap = Proxy.ExpireRef(value, ONE_MIN.toLong())
            return mLastBattCap!!.value
        }

        override fun getBatteryCurrency(context: Context): Long {
            val ref = mLastBattCur
            if (ref != null && !ref.isExpired()) {
                return ref.value
            }
            val value = getBatteryCurrencyImmediately(context)
            mLastBattCur = Proxy.ExpireRef(value, ONE_MIN.toLong())
            return mLastBattCur!!.value
        }

        override fun getCpuCoreNum(): Int {
            val ref = mLastCpuCoreNum
            if (ref != null && !ref.isExpired()) {
                return ref.value
            }
            val value = getCpuCoreNumImmediately()
            if (value <= 1) {
                return value
            }
            mLastCpuCoreNum = Proxy.ExpireRef(value, ONE_HOR.toLong())
            return mLastCpuCoreNum!!.value
        }
    }

    @JvmStatic
    fun setProxy(stub: Proxy) {
        sCacheStub = stub
    }

    @JvmStatic
    fun getProxy(): Proxy = sCacheStub

    @JvmStatic
    fun getProcessName(): String = sCacheStub.getProcessName()

    @JvmStatic
    fun getPackageName(): String = sCacheStub.getPackageName()

    @JvmStatic
    fun stackTraceToString(arr: Array<StackTraceElement>?): String {
        return stackTraceToString(arr, false)
    }

    @JvmStatic
    fun stackTraceToString(arr: Array<StackTraceElement>?, trim: Boolean): String {
        if (arr == null) {
            return ""
        }
        val stacks = ArrayList<StackTraceElement>(arr.size)
        for (traceElement in arr) {
            val className = traceElement.className
            // remove unused stacks
            if (className.contains("com.kernelflux.traceharbor") ||
                className.contains("java.lang.reflect") ||
                className.contains("\$Proxy2") ||
                className.contains("android.os")
            ) {
                continue
            }

            stacks.add(traceElement)
        }
        // stack still too large
        if (trim) {
            val pkg = getPackageName()
            if (stacks.size > DEFAULT_MAX_STACK_LAYER && !TextUtils.isEmpty(pkg)) {
                val iterator = stacks.listIterator(stacks.size)
                // from backward to forward
                while (iterator.hasPrevious()) {
                    val stack = iterator.previous()
                    val className = stack.className
                    if (!className.contains(pkg)) {
                        iterator.remove()
                    }
                    if (stacks.size <= DEFAULT_MAX_STACK_LAYER) {
                        break
                    }
                }
            }
        }

        val sb = StringBuilder()
        for (traceElement in stacks) {
            sb.append("\n").append("at ").append(traceElement)
        }
        return if (sb.isNotEmpty()) "TraceHarbor$sb" else ""
    }

    @JvmStatic
    fun getThrowableStack(throwable: Throwable?): String {
        if (throwable == null) {
            return ""
        }
        return stackTraceToString(throwable.stackTrace, true)
    }

    @JvmStatic
    fun getUTCTriggerAtMillis(triggerAtMillis: Long, type: Int): Long {
        if (type == AlarmManager.RTC || type == AlarmManager.RTC_WAKEUP) {
            return triggerAtMillis
        }

        return triggerAtMillis + System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }

    @JvmStatic
    fun getAlarmTypeString(type: Int): String? {
        var typeStr: String? = null
        when (type) {
            AlarmManager.RTC -> typeStr = "RTC"
            AlarmManager.RTC_WAKEUP -> typeStr = "RTC_WAKEUP"
            AlarmManager.ELAPSED_REALTIME -> typeStr = "ELAPSED_REALTIME"
            AlarmManager.ELAPSED_REALTIME_WAKEUP -> typeStr = "ELAPSED_REALTIME_WAKEUP"
            else -> {
            }
        }
        return typeStr
    }

    @JvmStatic
    fun getCpuCurrentFreq(): IntArray {
        val cpuCoreNum = getCpuCoreNum()
        val output = IntArray(cpuCoreNum)
        for (i in 0 until cpuCoreNum) {
            output[i] = 0
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
            val cat = cat(path)
            if (!TextUtils.isEmpty(cat)) {
                try {
                    output[i] = cat!!.toInt() / 1000
                } catch (ignored: Exception) {
                }
            }
        }
        return output
    }

    @JvmStatic
    fun getCpuFreqSteps(): List<IntArray> {
        val cpuCoreNum = getCpuCoreNum()
        val output: MutableList<IntArray> = ArrayList(cpuCoreNum)
        for (i in 0 until cpuCoreNum) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_available_frequencies"
            val cat = cat(path)
            if (!TextUtils.isEmpty(cat)) {
                val split = cat!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val steps = IntArray(split.size)
                for (j in split.indices) {
                    try {
                        val item = split[j]
                        steps[j] = item.toInt() / 1000
                    } catch (ignored: Exception) {
                        steps[j] = 0
                    }
                }
                output.add(steps)
            }
        }
        return output
    }

    @JvmStatic
    fun getCpuCoreNum(): Int = sCacheStub.getCpuCoreNum()

    @JvmStatic
    fun getCpuCoreNumImmediately(): Int {
        return try {
            // Get directory containing CPU info
            val dir = File("/sys/devices/system/cpu/")
            // Filter to only list the devices we care about
            val files = dir.listFiles { pathname -> Pattern.matches("cpu[0-9]+", pathname.name) }
            // Return the number of cores (virtual CPU devices)
            files!!.size
        } catch (ignored: Exception) {
            // Default to return 1 core
            getCpuCoreNumFromRuntime()
        }
    }

    @JvmStatic
    fun getCpuCoreNumFromRuntime(): Int {
        // fastest
        return Runtime.getRuntime().availableProcessors()
    }

    @JvmStatic
    fun cat(path: String?): String? {
        if (TextUtils.isEmpty(path)) return null
        return try {
            RandomAccessFile(path, "r").use { restrictedFile ->
                restrictedFile.readLine()
            }
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "cat file fail")
            null
        }
    }

    @JvmStatic
    fun getBatteryTemperature(context: Context): Int = sCacheStub.getBatteryTemperature(context)

    @JvmStatic
    fun getBatteryTemperatureImmediately(context: Context): Int {
        return try {
            val batIntent = getBatteryStickyIntent(context) ?: return 0
            batIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        } catch (e: Exception) {
            TraceHarborLog.w(TAG, "get EXTRA_TEMPERATURE failed: " + e.message)
            0
        }
    }

    @JvmStatic
    fun getThermalStat(context: Context): Int = getThermalStatImmediately(context)

    @JvmStatic
    fun getThermalStatImmediately(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                return powerManager.currentThermalStatus
            } catch (e: Exception) {
                TraceHarborLog.w(TAG, "getCurrentThermalStatus failed: " + e.message)
            }
        }
        return -1
    }

    @JvmStatic
    fun getThermalHeadroom(context: Context, @IntRange(from = 0, to = 60) forecastSeconds: Int): Float {
        return getThermalHeadroomImmediately(context, forecastSeconds)
    }

    @JvmStatic
    fun getThermalHeadroomImmediately(context: Context, forecastSeconds: Int): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                return powerManager.getThermalHeadroom(forecastSeconds)
            } catch (e: Exception) {
                TraceHarborLog.w(TAG, "getThermalHeadroom failed: " + e.message)
            }
        }
        return -1f
    }

    @JvmStatic
    fun getChargingWatt(context: Context): Int = getChargingWattImmediately(context)

    @JvmStatic
    fun getChargingWattImmediately(context: Context): Int {
        // @See com.android.settingslib.fuelgauge.BatteryStatus
        // Calculating muW = muA * muV / (10^6 mu^2 / mu); splitting up the divisor
        // to maintain precision equally on both factors.
        val intent = getBatteryStickyIntent(context)
        if (intent != null) {
            val maxCurrent = intent.getIntExtra("max_charging_current", -1)
            val maxVoltage = intent.getIntExtra("max_charging_voltage", -1)
            if (maxCurrent > 0 && maxVoltage > 0) {
                return (maxCurrent / 1000) * (maxVoltage / 1000) / 1000000
            }
        }
        return -1
    }

    @JvmStatic
    @AppStats.AppStatusDef
    fun getAppStat(context: Context, isForeground: Boolean): Int = sCacheStub.getAppStat(context, isForeground)

    @JvmStatic
    @AppStats.AppStatusDef
    fun getAppStatImmediately(context: Context, isForeground: Boolean): Int {
        if (isForeground) return APP_STAT_FOREGROUND // 前台
        if (hasForegroundService(context)) {
            return APP_STAT_FOREGROUND_SERVICE // 后台（有前台服务）
        }
        if (OverlayWindowLifecycleOwner.hasOverlayWindow()) {
            return APP_STAT_FLOAT_WINDOW // 浮窗
        }
        return APP_STAT_BACKGROUND // 后台
    }

    @JvmStatic
    @AppStats.DevStatusDef
    fun getDeviceStat(context: Context): Int = sCacheStub.getDevStat(context)

    @JvmStatic
    @AppStats.DevStatusDef
    fun getDeviceStatImmediately(context: Context): Int {
        if (isDeviceCharging(context)) {
            return DEV_STAT_CHARGING // 充电中
        }

        // 未充电状态细分:
        if (!isDeviceScreenOn(context)) {
            return DEV_STAT_SCREEN_OFF // 息屏
        }
        if (isDeviceOnPowerSave(context)) {
            return DEV_STAT_SAVE_POWER_MODE_ON // 省电模式开启
        }
        return DEV_STAT_UN_CHARGING
    }

    @JvmStatic
    fun convertAppStat(@AppStats.AppStatusDef appStat: Int): String {
        return when (appStat) {
            APP_STAT_FOREGROUND -> "fg"
            APP_STAT_BACKGROUND -> "bg"
            APP_STAT_FOREGROUND_SERVICE -> "fgSrv"
            APP_STAT_FLOAT_WINDOW -> "float"
            else -> "unknown"
        }
    }

    @JvmStatic
    fun convertDevStat(@AppStats.DevStatusDef devStat: Int): String {
        return when (devStat) {
            DEV_STAT_CHARGING -> "charging"
            DEV_STAT_UN_CHARGING -> "non_charge"
            DEV_STAT_SCREEN_ON -> "screen_on"
            DEV_STAT_SCREEN_OFF -> "screen_off"
            DEV_STAT_DOZE_MODE_ON -> "doze_on"
            DEV_STAT_DOZE_MODE_OFF -> "doze_off"
            DEV_STAT_SAVE_POWER_MODE_ON -> "standby_on"
            DEV_STAT_SAVE_POWER_MODE_OFF -> "standby_off"
            else -> "unknown"
        }
    }

    @JvmStatic
    fun isDeviceChargingV1(context: Context): Boolean {
        val batIntent = getBatteryStickyIntent(context) ?: return false
        val status = batIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    @JvmStatic
    fun isDeviceChargingV2(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val myBatteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (myBatteryManager != null) {
                return myBatteryManager.isCharging
            }
        }
        return try {
            val batIntent = getBatteryStickyIntent(context) ?: return false
            val plugged = batIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
        } catch (ignored: Throwable) {
            false
        }
    }

    @JvmStatic
    fun isDeviceCharging(context: Context): Boolean = isDeviceChargingV1(context)

    @JvmStatic
    @Suppress("DEPRECATION")
    fun isDeviceScreenOn(context: Context): Boolean {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) pm.isInteractive else pm.isScreenOn
            }
        } catch (ignored: Exception) {
        }
        return false
    }

    /**
     * System Doze Mode
     */
    @JvmStatic
    fun isDeviceOnIdleMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    return pm.isDeviceIdleMode
                }
            } catch (ignored: Exception) {
            }
        }
        return false
    }

    /**
     * App Standby Mode
     */
    @JvmStatic
    fun isDeviceOnPowerSave(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    return pm.isPowerSaveMode
                }
            } catch (ignored: Exception) {
            }
        }
        return false
    }

    @JvmStatic
    fun getBatteryStickyIntent(context: Context): Intent? {
        return try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            TraceHarborLog.w(TAG, "get ACTION_BATTERY_CHANGED failed: " + e.message)
            null
        }
    }

    @JvmStatic
    fun isLowBattery(context: Context): Boolean {
        val batIntent = getBatteryStickyIntent(context)
        if (batIntent != null) {
            batIntent.getBooleanExtra(Intent.ACTION_BATTERY_LOW, false)
        }
        return false
    }

    @JvmStatic
    fun getBatteryPercentage(context: Context): Int = sCacheStub.getBatteryPercentage(context)

    @JvmStatic
    fun getBatteryPercentageImmediately(context: Context): Int {
        val batIntent = getBatteryStickyIntent(context)
        if (batIntent != null) {
            val level = batIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (scale > 0) {
                return level * 100 / scale
            }
        }
        return -1
    }

    @JvmStatic
    fun getBatteryCapacity(context: Context): Int = sCacheStub.getBatteryCapacity(context)

    @SuppressLint("PrivateApi")
    @JvmStatic
    fun getBatteryCapacityImmediately(context: Context): Int {
        /*
         * TraceHarbor PowerProfile (static) >> OS PowerProfile (static) >> BatteryManager (dynamic)
         */
        try {
            if (PowerProfile.getResType() == "framework" || PowerProfile.getResType() == "custom") {
                return PowerProfile.init(context).batteryCapacity.toInt()
            }
        } catch (ignored: Throwable) {
        }

        try {
            val profileClass = Class.forName("com.android.internal.os.PowerProfile")
            val profileObject = profileClass.getConstructor(Context::class.java).newInstance(context)
            var method: Method
            try {
                method = profileClass.getMethod("getAveragePower", String::class.java)
                val capacity = method.invoke(profileObject, PowerProfile.POWER_BATTERY_CAPACITY) as Double
                return capacity.toInt()
            } catch (e: Throwable) {
                TraceHarborLog.w(TAG, "get PowerProfile failed: " + e.message)
            }
            method = profileClass.getMethod("getBatteryCapacity")
            return method.invoke(profileObject) as Int
        } catch (ignored: Throwable) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mBatteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (mBatteryManager != null) {
                val chargeCounter = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                val capacity = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (chargeCounter > 0 && capacity > 0) {
                    return ((chargeCounter / capacity.toFloat()) * 100 / 1000).toInt()
                }
            }
        }
        return -1
    }

    @JvmStatic
    fun getBatteryCurrency(context: Context): Long = sCacheStub.getBatteryCurrency(context)

    @JvmStatic
    fun getBatteryCurrencyImmediately(context: Context): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mBatteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (mBatteryManager != null) {
                return mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            }
        }
        return -1
    }

    @JvmStatic
    fun hasForegroundService(context: Context): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val runningServices = am.getRunningServices(Int.MAX_VALUE)
                if (runningServices != null) {
                    for (runningServiceInfo in runningServices) {
                        if (!TextUtils.isEmpty(runningServiceInfo.process) &&
                            runningServiceInfo.process.startsWith(context.packageName)
                        ) {
                            if (runningServiceInfo.foreground) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (ignored: Throwable) {
        }
        return false
    }

    @JvmStatic
    fun listForegroundServices(context: Context): List<ActivityManager.RunningServiceInfo> {
        var list: MutableList<ActivityManager.RunningServiceInfo>? = null
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val runningServices = am.getRunningServices(Int.MAX_VALUE)
                if (runningServices != null) {
                    for (runningServiceInfo in runningServices) {
                        if (!TextUtils.isEmpty(runningServiceInfo.process) &&
                            runningServiceInfo.process.startsWith(context.packageName)
                        ) {
                            if (runningServiceInfo.foreground) {
                                if (list == null) {
                                    list = ArrayList()
                                }
                                list.add(runningServiceInfo)
                            }
                        }
                    }
                }
            }
        } catch (ignored: Throwable) {
        }
        return list ?: Collections.emptyList()
    }

    @JvmStatic
    fun polishStack(stackTrace: String, startSymbol: String?): String {
        val stacks: MutableList<String> = ArrayList()
        val splits = stackTrace.split("\n\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var startFilter = TextUtils.isEmpty(startSymbol)
        for (line in splits) {
            if (!TextUtils.isEmpty(line)) {
                if (!startFilter && line.startsWith(startSymbol!!)) {
                    startFilter = true
                }
                if (startFilter) {
                    stacks.add(line.trim())
                }
            }
        }
        return TextUtils.join(";", stacks.subList(0, min(5, stacks.size)))
    }

    @JvmStatic
    fun computeAvgByMinute(input: Long, millis: Long): Long {
        return if (millis < ONE_MIN) {
            val scale = 100L
            val divideBase = max(1, millis * scale / ONE_MIN)
            input / divideBase * scale
        } else {
            input / max(1, millis / ONE_MIN)
        }
    }

    @JvmStatic
    fun <K, V> sortMapByValue(map: Map<K, V>, comparator: Comparator<in Map.Entry<K, V>>): MutableMap<K, V> {
        val list: MutableList<Map.Entry<K, V>> = ArrayList(map.entries)
        list.sortWith(comparator)

        val result: MutableMap<K, V> = LinkedHashMap()
        for (entry in list) {
            result[entry.key] = entry.value
        }
        return result
    }
}
