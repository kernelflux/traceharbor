package com.kernelflux.traceharbor.batterycanary.monitor

import android.app.ActivityManager
import android.os.HandlerThread
import androidx.core.util.Pair
import com.kernelflux.traceharbor.batterycanary.BuildConfig
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature.UidCpuStateSnapshot.IpcCpuStat.RemoteStat
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.UidJiffiesSnapshot.IpcJiffies.IpcProcessJiffies
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature
import com.kernelflux.traceharbor.batterycanary.stats.BatteryRecorder
import com.kernelflux.traceharbor.batterycanary.stats.BatteryStats
import com.kernelflux.traceharbor.batterycanary.utils.CallStackCollector
import com.kernelflux.traceharbor.batterycanary.utils.Function
import java.util.Collections
import java.util.concurrent.Callable

/**
 * @author Kaede
 * @since 2020/10/27
 */
@Suppress("SpellCheckingInspection", "DEPRECATION")
class BatteryMonitorConfig private constructor() {
    @JvmField
    var canaryThread: HandlerThread? = null

    @JvmField
    var callback: BatteryMonitorCallback = BatteryMonitorCallback.BatteryPrinter()

    @JvmField
    var onSceneSupplier: Callable<String>? = null

    @JvmField
    var wakelockTimeout: Long = DEF_WAKELOCK_TIMEOUT

    @JvmField
    var wakelockWarnCount: Int = DEF_WAKELOCK_WARN_COUNT

    @JvmField
    var greyTime: Long = DEF_JIFFIES_DELAY

    @JvmField
    var foregroundLoopCheckTime: Long = DEF_FOREGROUND_SCHEDULE_TIME

    @JvmField
    var backgroundLoopCheckTime: Long = DEF_BACKGROUND_SCHEDULE_TIME

    @JvmField
    var overHeatCount: Int = DEF_STAMP_OVERHEAT

    @JvmField
    var foregroundServiceLeakLimit: Int = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    @JvmField
    var fgThreadWatchingLimit: Int = 10000

    @JvmField
    var bgThreadWatchingLimit: Int = 5000

    @JvmField
    var isForegroundModeEnabled: Boolean = true

    @JvmField
    var isBackgroundModeEnabled: Boolean = false

    @JvmField
    var isBuiltinForegroundNotifyEnabled: Boolean = true

    @JvmField
    var isStatAsSample: Boolean = BuildConfig.DEBUG

    @JvmField
    var isStatPidProc: Boolean = BuildConfig.DEBUG

    @JvmField
    var isInspectiffiesError: Boolean = BuildConfig.DEBUG

    @JvmField
    var isAmsHookEnabled: Boolean = BuildConfig.DEBUG

    @JvmField
    var isSkipNewAddedPidTid: Boolean = false

    @JvmField
    var amsHookEnableFlag: Int = 0

    @JvmField
    var isAggressiveMode: Boolean = BuildConfig.DEBUG

    @JvmField
    var isUseThreadClock: Boolean = BuildConfig.DEBUG

    @JvmField
    var tagWhiteList: MutableList<String> = Collections.emptyList()

    @JvmField
    var tagBlackList: MutableList<String> = Collections.emptyList()

    @JvmField
    var looperWatchList: MutableList<String> = Collections.emptyList()

    @JvmField
    var threadWatchList: MutableList<String> = Collections.emptyList()

    @JvmField
    val features: MutableList<MonitorFeature> = ArrayList(3)

    @JvmField
    var batteryRecorder: BatteryRecorder? = null

    @JvmField
    var batteryStats: BatteryStats = BatteryStats.BatteryStatsImpl()

    @JvmField
    var callStackCollector: CallStackCollector = CallStackCollector()

    @JvmField
    var ipcJiffiesCollector: Function<Pair<Int, String>, IpcProcessJiffies>? = null

    @JvmField
    var ipcCpuStatCollector: Function<Pair<Int, String>, RemoteStat>? = null

    @JvmField
    var isTuningPowers: Boolean = BuildConfig.DEBUG

    override fun toString(): String = "BatteryMonitorConfig{features=$features}"

    /**
     * FIXME: suitable builder needed
     */
    class Builder {
        private val config = BatteryMonitorConfig()

        fun setCanaryThread(thread: HandlerThread?): Builder {
            config.canaryThread = thread
            return this
        }

        fun setCallback(callback: BatteryMonitorCallback): Builder {
            config.callback = callback
            return this
        }

        fun setSceneSupplier(block: Callable<String>?): Builder {
            config.onSceneSupplier = block
            return this
        }

        fun wakelockTimeout(timeout: Long): Builder {
            if (timeout > 0) {
                config.wakelockTimeout = timeout
            }
            return this
        }

        fun wakelockWarnCount(count: Int): Builder {
            if (count > 0) {
                config.wakelockWarnCount = count
            }
            return this
        }

        fun greyJiffiesTime(time: Long): Builder {
            if (time > 0) {
                config.greyTime = time
            }
            return this
        }

        fun enableForegroundMode(isEnable: Boolean): Builder {
            config.isForegroundModeEnabled = isEnable
            return this
        }

        fun enableBackgroundMode(isEnable: Boolean): Builder {
            config.isBackgroundModeEnabled = isEnable
            return this
        }

        fun enableStatAsSample(isEnable: Boolean): Builder {
            config.isStatAsSample = isEnable
            return this
        }

        fun enableStatPidProc(isEnable: Boolean): Builder {
            config.isStatPidProc = isEnable
            return this
        }

        fun enableInspectJffiesError(isEnable: Boolean): Builder {
            config.isInspectiffiesError = isEnable
            return this
        }

        fun enableAmsHook(isEnable: Boolean): Builder {
            config.isAmsHookEnabled = isEnable
            return this
        }

        fun setAmsHookEnableFlag(flag: Int): Builder {
            if (flag >= 0) {
                config.amsHookEnableFlag = flag
            }
            return this
        }

        fun enableAggressive(isEnable: Boolean): Builder {
            config.isAggressiveMode = isEnable
            return this
        }

        fun useThreadClock(isEnable: Boolean): Builder {
            config.isUseThreadClock = isEnable
            return this
        }

        fun foregroundLoopCheckTime(time: Long): Builder {
            if (time > 0) {
                config.foregroundLoopCheckTime = time
            }
            return this
        }

        fun backgroundLoopCheckTime(time: Long): Builder {
            if (time > 0) {
                config.backgroundLoopCheckTime = time
            }
            return this
        }

        fun foregroundServiceLeakLimit(importanceLimit: Int): Builder {
            config.foregroundServiceLeakLimit = importanceLimit
            return this
        }

        fun setFgThreadWatchingLimit(fgThreadWatchingLimit: Int): Builder {
            if (fgThreadWatchingLimit > 1000) {
                config.fgThreadWatchingLimit = fgThreadWatchingLimit
            }
            return this
        }

        fun setBgThreadWatchingLimit(bgThreadWatchingLimit: Int): Builder {
            if (bgThreadWatchingLimit > 1000) {
                config.bgThreadWatchingLimit = bgThreadWatchingLimit
            }
            return this
        }

        fun enable(pluginClass: Class<out MonitorFeature>): Builder {
            try {
                config.features.add(pluginClass.newInstance())
            } catch (ignored: Exception) {
            }
            return this
        }

        fun enableBuiltinForegroundNotify(enable: Boolean): Builder {
            config.isBuiltinForegroundNotifyEnabled = enable
            return this
        }

        fun addWakeLockWhiteList(tag: String): Builder {
            if (config.tagWhiteList === Collections.EMPTY_LIST) {
                config.tagWhiteList = ArrayList()
            }
            config.tagWhiteList.add(tag)
            return this
        }

        fun addWakeLockBlackList(tag: String): Builder {
            if (config.tagBlackList === Collections.EMPTY_LIST) {
                config.tagBlackList = ArrayList()
            }
            config.tagBlackList.add(tag)
            return this
        }

        fun addLooperWatchList(handlerThreadName: String): Builder {
            if (config.looperWatchList === Collections.EMPTY_LIST) {
                config.looperWatchList = ArrayList()
            }
            config.looperWatchList.add(handlerThreadName)
            return this
        }

        fun addThreadWatchList(threadName: String): Builder {
            if (config.threadWatchList === Collections.EMPTY_LIST) {
                config.threadWatchList = ArrayList()
            }
            config.threadWatchList.add(threadName)
            return this
        }

        fun setOverHeatCount(count: Int): Builder {
            if (count >= 10) {
                config.overHeatCount = count
            }
            return this
        }

        fun setRecorder(recorder: BatteryRecorder?): Builder {
            config.batteryRecorder = recorder
            return this
        }

        fun setStats(stats: BatteryStats?): Builder {
            if (stats != null) {
                config.batteryStats = stats
            }
            return this
        }

        fun setCollector(collector: CallStackCollector?): Builder {
            if (collector != null) {
                config.callStackCollector = collector
            }
            return this
        }

        fun setCollector(collector: Function<Pair<Int, String>, IpcProcessJiffies>?): Builder {
            config.ipcJiffiesCollector = collector
            return this
        }

        fun enableTuningPowers(enable: Boolean): Builder {
            config.isTuningPowers = enable
            return this
        }

        fun skipNewAddedPidTid(skip: Boolean): Builder {
            config.isSkipNewAddedPidTid = skip
            return this
        }

        fun build(): BatteryMonitorConfig {
            config.features.sortWith { o1, o2 -> Integer.compare(o2.weight(), o1.weight()) }
            return config
        }
    }

    companion object {
        const val DEF_STAMP_OVERHEAT = 200
        const val DEF_WAKELOCK_WARN_COUNT = 30
        const val DEF_WAKELOCK_TIMEOUT = 2 * 60 * 1000L // 2min
        const val DEF_JIFFIES_DELAY = 30 * 1000L // 30s
        const val DEF_FOREGROUND_SCHEDULE_TIME = 20 * 60 * 1000L // 10min
        const val DEF_BACKGROUND_SCHEDULE_TIME = 10 * 60 * 1000L // 10min

        const val AMS_HOOK_FLAG_BT = 0b00000001
    }
}
