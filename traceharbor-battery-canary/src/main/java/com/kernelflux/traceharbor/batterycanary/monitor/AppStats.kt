package com.kernelflux.traceharbor.batterycanary.monitor

import androidx.annotation.IntDef
import androidx.annotation.Nullable
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.batterycanary.BatteryCanary
import com.kernelflux.traceharbor.batterycanary.BatteryMonitorPlugin
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AppStatMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.DeviceStatMonitorFeature
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.TimeBreaker
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Kaede
 * @since 2021/1/27
 */
open class AppStats {
    @IntDef(
        value = [
            APP_STAT_FOREGROUND,
            APP_STAT_FOREGROUND_SERVICE,
            APP_STAT_FLOAT_WINDOW,
            APP_STAT_BACKGROUND,
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class AppStatusDef

    @IntDef(
        value = [
            DEV_STAT_CHARGING,
            DEV_STAT_UN_CHARGING,
            DEV_STAT_SCREEN_ON,
            DEV_STAT_SCREEN_OFF,
            DEV_STAT_SAVE_POWER_MODE_ON,
            DEV_STAT_SAVE_POWER_MODE_OFF,
            DEV_STAT_DOZE_MODE_ON,
            DEV_STAT_DOZE_MODE_OFF,
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class DevStatusDef

    @JvmField
    var appFgRatio = 0

    @JvmField
    var appBgRatio = 0

    @JvmField
    var appFgSrvRatio = 0

    @JvmField
    var appFloatRatio = 0

    @JvmField
    var devChargingRatio = 0

    @JvmField
    var devUnChargingRatio = 0

    @JvmField
    var devSceneOffRatio = 0

    @JvmField
    var devLowEnergyRatio = 0

    @JvmField
    var sceneTop1: String = ""

    @JvmField
    var sceneTop1Ratio = 0

    @JvmField
    var sceneTop2: String = ""

    @JvmField
    var sceneTop2Ratio = 0

    @JvmField
    var isValid = false

    @JvmField
    var duringMillis = 0L

    @Nullable
    private var foregroundOverride: AtomicBoolean? = null

    open fun getMinute(): Long = maxOf(1L, duringMillis / BatteryCanaryUtil.ONE_MIN)

    open fun isForeground(): Boolean {
        val override = foregroundOverride
        if (override != null) {
            return override.get()
        }
        return getAppStat() == APP_STAT_FOREGROUND
    }

    open fun hasForegroundService(): Boolean = getAppStat() == APP_STAT_FOREGROUND_SERVICE

    open fun isCharging(): Boolean = getDevStat() == DEV_STAT_CHARGING

    @AppStatusDef
    open fun getAppStat(): Int {
        val override = foregroundOverride
        if (override != null && override.get()) {
            return APP_STAT_FOREGROUND
        }
        // FIXME: return the max one might be better
        if (appFgRatio >= 50) return APP_STAT_FOREGROUND
        if (appFgSrvRatio >= 50) return APP_STAT_FOREGROUND_SERVICE
        if (appFloatRatio >= 50) return APP_STAT_FLOAT_WINDOW
        return APP_STAT_BACKGROUND
    }

    @DevStatusDef
    open fun getDevStat(): Int {
        // FIXME: return the max one might be better
        if (devChargingRatio >= 50) return DEV_STAT_CHARGING
        if (devSceneOffRatio >= 50) return DEV_STAT_SCREEN_OFF
        if (devLowEnergyRatio >= 50) return DEV_STAT_SAVE_POWER_MODE_ON
        return DEV_STAT_UN_CHARGING
    }

    fun setForeground(bool: Boolean): AppStats {
        foregroundOverride = AtomicBoolean(bool)
        return this
    }

    override fun toString(): String {
        return "AppStats{" +
            "appFgRatio=$appFgRatio" +
            ", appBgRatio=$appBgRatio" +
            ", appFgSrvRatio=$appFgSrvRatio" +
            ", appFloatRatio=$appFloatRatio" +
            ", devChargingRatio=$devChargingRatio" +
            ", devUnChargingRatio=$devUnChargingRatio" +
            ", devSceneOffRatio=$devSceneOffRatio" +
            ", devLowEnergyRatio=$devLowEnergyRatio" +
            ", sceneTop1='$sceneTop1'" +
            ", sceneTop1Ratio=$sceneTop1Ratio" +
            ", sceneTop2='$sceneTop2'" +
            ", sceneTop2Ratio=$sceneTop2Ratio" +
            ", isValid=$isValid" +
            ", duringMillis=$duringMillis" +
            ", foregroundOverride=$foregroundOverride" +
            '}'
    }

    class CurrAppStats(private val mCore: BatteryMonitorCore) : AppStats() {
        override fun getMinute(): Long = 0

        override fun isForeground(): Boolean = mCore.isForeground()

        override fun hasForegroundService(): Boolean = BatteryCanaryUtil.hasForegroundService(mCore.getContext())

        override fun isCharging(): Boolean = BatteryCanaryUtil.isDeviceCharging(mCore.getContext())

        override fun getAppStat(): Int = BatteryCanaryUtil.getAppStat(mCore.getContext(), isForeground())

        override fun getDevStat(): Int = BatteryCanaryUtil.getDeviceStat(mCore.getContext())
    }

    companion object {
        const val APP_STAT_FOREGROUND = 1
        const val APP_STAT_FOREGROUND_SERVICE = 3
        const val APP_STAT_FLOAT_WINDOW = 4
        const val APP_STAT_BACKGROUND = 2

        const val DEV_STAT_CHARGING = 1
        const val DEV_STAT_UN_CHARGING = 2
        const val DEV_STAT_SCREEN_OFF = 3
        const val DEV_STAT_SAVE_POWER_MODE_ON = 4
        const val DEV_STAT_SCREEN_ON = 5
        const val DEV_STAT_SAVE_POWER_MODE_OFF = 6
        const val DEV_STAT_DOZE_MODE_ON = 7
        const val DEV_STAT_DOZE_MODE_OFF = 8

        @JvmStatic
        fun current(): AppStats {
            if (TraceHarbor.isInstalled()) {
                val plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin::class.java)
                if (plugin != null) {
                    val currAppStats = CurrAppStats(plugin.core())
                    currAppStats.isValid = true
                    return currAppStats
                }
            }
            return current(1L)
        }

        @JvmStatic
        fun current(millisFromNow: Long): AppStats {
            val duringMillis = if (millisFromNow > 0) millisFromNow else 0L

            val stats = AppStats()
            stats.duringMillis = duringMillis
            val appStatFeat = BatteryCanary.getMonitorFeature(AppStatMonitorFeature::class.java)
            if (appStatFeat != null) {
                // configure appStat & scene
                val appStats = appStatFeat.currentAppStatSnapshot(duringMillis)
                if (appStats.isValid()) {
                    stats.appFgRatio = appStats.fgRatio.get().toInt()
                    stats.appBgRatio = appStats.bgRatio.get().toInt()
                    stats.appFgSrvRatio = appStats.fgSrvRatio.get().toInt()
                    stats.appFloatRatio = appStats.floatRatio.get().toInt()

                    val portions = appStatFeat.currentSceneSnapshot(duringMillis)
                    val top1 = portions.top1()
                    if (top1 != null) {
                        stats.sceneTop1 = top1.key
                        stats.sceneTop1Ratio = top1.ratio
                        val top2 = portions.top2()
                        if (top2 != null) {
                            stats.sceneTop2 = top2.key
                            stats.sceneTop2Ratio = top2.ratio
                        }

                        val devStatFeat = BatteryCanary.getMonitorFeature(DeviceStatMonitorFeature::class.java)
                        if (devStatFeat != null) {
                            // configure devStat
                            val devStat = devStatFeat.currentDevStatSnapshot(duringMillis)
                            if (devStat.isValid()) {
                                stats.devChargingRatio = devStat.chargingRatio.get().toInt()
                                stats.devUnChargingRatio = devStat.unChargingRatio.get().toInt()
                                stats.devSceneOffRatio = devStat.screenOff.get().toInt()
                                stats.devLowEnergyRatio = devStat.lowEnergyRatio.get().toInt()
                                stats.isValid = true
                            }
                        }
                    }
                }
            }
            return stats
        }
    }
}

