package com.kernelflux.traceharbor.batterycanary.stats

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.health.HealthStats
import android.os.health.PidHealthStats
import android.os.health.ProcessHealthStats
import android.os.health.TimerStat
import android.os.health.UidHealthStats
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AbsMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Differ
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.PowerProfile
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Method
import java.util.Collections

/**
 * @author Kaede
 * @since 18/7/2022
 */
open class HealthStatsFeature : AbsMonitorFeature() {
    override fun getTag(): String = TAG

    override fun weight(): Int = 0

    open fun currHealthStats(): HealthStats? = HealthStatsHelper.getCurrStats(mCore.getContext())

    @SuppressLint("VisibleForTests")
    open fun currHealthStatsSnapshot(): HealthStatsSnapshot {
        val snapshot = HealthStatsSnapshot()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return snapshot
        }
        val healthStats = currHealthStats()
        if (healthStats != null) {
            snapshot.healthStats = healthStats

            // Power
            val cpuStatFeat = mCore.getMonitorFeature(CpuStatFeature::class.java)
            if (cpuStatFeat != null) {
                val powerProfile = cpuStatFeat.powerProfile
                if (powerProfile != null && powerProfile.isSupported) {
                    snapshot.cpuPower =
                        DigitEntry.of(HealthStatsHelper.calcCpuPower(powerProfile, healthStats))
                    snapshot.wakelocksPower = DigitEntry.of(
                        HealthStatsHelper.calcWakelocksPower(
                            powerProfile,
                            healthStats
                        )
                    )
                    snapshot.mobilePower =
                        DigitEntry.of(HealthStatsHelper.calcMobilePower(powerProfile, healthStats))
                    snapshot.wifiPower =
                        DigitEntry.of(HealthStatsHelper.calcWifiPower(powerProfile, healthStats))
                    snapshot.blueToothPower = DigitEntry.of(
                        HealthStatsHelper.calcBlueToothPower(
                            powerProfile,
                            healthStats
                        )
                    )
                    snapshot.gpsPower =
                        DigitEntry.of(HealthStatsHelper.calcGpsPower(powerProfile, healthStats))
                    snapshot.sensorsPower = DigitEntry.of(
                        HealthStatsHelper.calcSensorsPower(
                            mCore.getContext(),
                            healthStats
                        )
                    )
                    snapshot.cameraPower =
                        DigitEntry.of(HealthStatsHelper.calcCameraPower(powerProfile, healthStats))
                    snapshot.flashLightPower = DigitEntry.of(
                        HealthStatsHelper.calcFlashLightPower(
                            powerProfile,
                            healthStats
                        )
                    )
                    snapshot.audioPower =
                        DigitEntry.of(HealthStatsHelper.calcAudioPower(powerProfile, healthStats))
                    snapshot.videoPower =
                        DigitEntry.of(HealthStatsHelper.calcVideoPower(powerProfile, healthStats))
                    snapshot.screenPower =
                        DigitEntry.of(HealthStatsHelper.calcScreenPower(powerProfile, healthStats))
                    snapshot.systemServicePower = DigitEntry.of(
                        HealthStatsHelper.calcSystemServicePower(
                            powerProfile,
                            healthStats
                        )
                    )
                    snapshot.idlePower =
                        DigitEntry.of(HealthStatsHelper.calcIdlePower(powerProfile, healthStats))
                }
            }

            // Meta data
            snapshot.cpuPowerMams = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_CPU_POWER_MAMS
                )
            )
            snapshot.cpuUsrTimeMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_USER_CPU_TIME_MS
                )
            )
            snapshot.cpuSysTimeMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_SYSTEM_CPU_TIME_MS
                )
            )
            snapshot.realTimeMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_REALTIME_BATTERY_MS
                )
            )
            snapshot.upTimeMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_UPTIME_BATTERY_MS
                )
            )
            snapshot.offRealTimeMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_REALTIME_SCREEN_OFF_BATTERY_MS
                )
            )
            snapshot.offUpTimeMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_UPTIME_SCREEN_OFF_BATTERY_MS
                )
            )

            snapshot.mobilePowerMams = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_MOBILE_POWER_MAMS
                )
            )
            snapshot.mobileRadioActiveMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_MOBILE_RADIO_ACTIVE
                ) / 1000L
            )
            snapshot.mobileIdleMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_MOBILE_IDLE_MS
                )
            )
            snapshot.mobileRxMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_MOBILE_RX_MS
                )
            )
            snapshot.mobileTxMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_MOBILE_TX_MS
                )
            )

            snapshot.mobileRxBytes = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_MOBILE_RX_BYTES
                )
            )
            snapshot.mobileTxBytes = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_MOBILE_TX_BYTES
                )
            )
            snapshot.mobileRxPackets = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_MOBILE_RX_PACKETS
                )
            )
            snapshot.mobileTxPackets = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_MOBILE_TX_PACKETS
                )
            )

            snapshot.wifiPowerMams = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_POWER_MAMS
                )
            )
            snapshot.wifiIdleMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_IDLE_MS
                )
            )
            snapshot.wifiRxMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_RX_MS
                )
            )
            snapshot.wifiTxMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_TX_MS
                )
            )
            snapshot.wifiRunningMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_RUNNING_MS
                )
            )
            snapshot.wifiLockMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_FULL_LOCK_MS
                )
            )
            snapshot.wifiScanMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_WIFI_SCAN
                )
            )
            snapshot.wifiMulticastMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_MULTICAST_MS
                )
            )
            snapshot.wifiRxBytes = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_RX_BYTES
                )
            )
            snapshot.wifiTxBytes = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_TX_BYTES
                )
            )
            snapshot.wifiRxPackets = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_RX_PACKETS
                )
            )
            snapshot.wifiTxPackets = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_WIFI_TX_PACKETS
                )
            )

            snapshot.blueToothPowerMams = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_BLUETOOTH_POWER_MAMS
                )
            )
            snapshot.blueToothIdleMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_BLUETOOTH_IDLE_MS
                )
            )
            snapshot.blueToothRxMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_BLUETOOTH_RX_MS
                )
            )
            snapshot.blueToothTxMs = DigitEntry.of(
                HealthStatsHelper.getMeasure(
                    healthStats,
                    UidHealthStats.MEASUREMENT_BLUETOOTH_TX_MS
                )
            )

            if (healthStats.hasTimers(UidHealthStats.TIMERS_WAKELOCKS_PARTIAL)) {
                var timeMs = 0L
                val timers: Map<String, TimerStat> =
                    healthStats.getTimers(UidHealthStats.TIMERS_WAKELOCKS_PARTIAL)
                for ((tag, timer) in timers) {
                    val lockTime = timer.time
                    if (snapshot.tagWakelocksPartialMs.isEmpty()) {
                        snapshot.tagWakelocksPartialMs = HashMap()
                    }
                    snapshot.tagWakelocksPartialMs[tag] = DigitEntry.of(lockTime)
                    timeMs += lockTime
                }
                snapshot.wakelocksPartialMs = DigitEntry.of(timeMs)
            }
            if (healthStats.hasTimers(UidHealthStats.TIMERS_WAKELOCKS_FULL)) {
                var timeMs = 0L
                val timers: Map<String, TimerStat> =
                    healthStats.getTimers(UidHealthStats.TIMERS_WAKELOCKS_FULL)
                for ((tag, timer) in timers) {
                    val lockTime = timer.time
                    if (snapshot.tagWakelocksFullMs.isEmpty()) {
                        snapshot.tagWakelocksFullMs = HashMap()
                    }
                    snapshot.tagWakelocksFullMs[tag] = DigitEntry.of(lockTime)
                    timeMs += lockTime
                }
                snapshot.wakelocksFullMs = DigitEntry.of(timeMs)
            }
            if (healthStats.hasTimers(UidHealthStats.TIMERS_WAKELOCKS_WINDOW)) {
                val timers: Map<String, TimerStat> =
                    healthStats.getTimers(UidHealthStats.TIMERS_WAKELOCKS_WINDOW)
                var timeMs = 0L
                for (item in timers.values) {
                    timeMs += item.time
                }
                snapshot.wakelocksWindowMs = DigitEntry.of(timeMs)
            }
            if (healthStats.hasTimers(UidHealthStats.TIMERS_WAKELOCKS_DRAW)) {
                val timers: Map<String, TimerStat> =
                    healthStats.getTimers(UidHealthStats.TIMERS_WAKELOCKS_DRAW)
                var timeMs = 0L
                for (item in timers.values) {
                    timeMs += item.time
                }
                snapshot.wakelocksDrawMs = DigitEntry.of(timeMs)
            }
            if (healthStats.hasStats(UidHealthStats.STATS_PIDS)) {
                var sum = 0L
                val pidStats: Map<String, HealthStats> =
                    healthStats.getStats(UidHealthStats.STATS_PIDS)
                for (item in pidStats.values) {
                    if (item.hasMeasurement(PidHealthStats.MEASUREMENT_WAKE_SUM_MS)) {
                        sum += item.getMeasurement(PidHealthStats.MEASUREMENT_WAKE_SUM_MS)
                    }
                }
                snapshot.wakelocksPidSum = DigitEntry.of(sum)
            }

            snapshot.gpsMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_GPS_SENSOR
                )
            )
            if (healthStats.hasTimers(UidHealthStats.TIMERS_SENSORS)) {
                val sm =
                    mCore.getContext().getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                if (sm != null) {
                    val sensorList = sm.getSensorList(Sensor.TYPE_ALL)
                    val sensorMap: MutableMap<String, Sensor> = HashMap()
                    for (item in sensorList) {
                        try {
                            @SuppressLint("DiscouragedPrivateApi")
                            val method: Method = item.javaClass.getDeclaredMethod("getHandle")
                            val handle = method.invoke(item) as Int
                            sensorMap[handle.toString()] = item
                        } catch (e: Throwable) {
                            TraceHarborLog.w(TAG, "getSensorHandle err: " + e.message)
                        }
                    }

                    var sensorsPowerMams = 0L
                    val timers: Map<String, TimerStat> =
                        healthStats.getTimers(UidHealthStats.TIMERS_SENSORS)
                    for ((handle, timer) in timers) {
                        val timeMs = timer.time
                        if (handle == "-10000") {
                            continue // skip GPS Sensors
                        }
                        val sensor = sensorMap[handle]
                        if (sensor != null) {
                            sensorsPowerMams += (sensor.power * timeMs).toLong()
                        }
                    }
                    snapshot.sensorsPowerMams = DigitEntry.of(sensorsPowerMams)
                }
            }
            snapshot.cameraMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_CAMERA
                )
            )
            snapshot.flashLightMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_FLASHLIGHT
                )
            )
            snapshot.audioMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_AUDIO
                )
            )
            snapshot.videoMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_VIDEO
                )
            )
            if (healthStats.hasTimers(UidHealthStats.TIMERS_JOBS)) {
                var timeMs = 0L
                val timers: Map<String, TimerStat> =
                    healthStats.getTimers(UidHealthStats.TIMERS_JOBS)
                for (item in timers.values) {
                    timeMs += item.time
                }
                snapshot.jobsMs = DigitEntry.of(timeMs)
            }
            if (healthStats.hasTimers(UidHealthStats.TIMERS_SYNCS)) {
                var timeMs = 0L
                val timers: Map<String, TimerStat> =
                    healthStats.getTimers(UidHealthStats.TIMERS_SYNCS)
                for (item in timers.values) {
                    timeMs += item.time
                }
                snapshot.syncMs = DigitEntry.of(timeMs)
            }

            snapshot.fgActMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_FOREGROUND_ACTIVITY
                )
            )
            snapshot.procTopAppMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_PROCESS_STATE_TOP_MS
                )
            )
            snapshot.procTopSleepMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_PROCESS_STATE_TOP_SLEEPING_MS
                )
            )
            snapshot.procFgMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_PROCESS_STATE_FOREGROUND_MS
                )
            )
            snapshot.procFgSrvMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_PROCESS_STATE_FOREGROUND_SERVICE_MS
                )
            )
            snapshot.procBgMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_PROCESS_STATE_BACKGROUND_MS
                )
            )
            snapshot.procCacheMs = DigitEntry.of(
                HealthStatsHelper.getTimerTime(
                    healthStats,
                    UidHealthStats.TIMER_PROCESS_STATE_CACHED_MS
                )
            )

            if (healthStats.hasStats(UidHealthStats.STATS_PROCESSES)) {
                val processes: Map<String, HealthStats> =
                    healthStats.getStats(UidHealthStats.STATS_PROCESSES)
                for ((pkg, procStats) in processes) {
                    if (snapshot.procStatsCpuUsrTimeMs.isEmpty()) {
                        snapshot.procStatsCpuUsrTimeMs = HashMap()
                    }
                    snapshot.procStatsCpuUsrTimeMs[pkg] = DigitEntry.of(
                        HealthStatsHelper.getMeasure(
                            procStats,
                            ProcessHealthStats.MEASUREMENT_USER_TIME_MS
                        )
                    )

                    if (snapshot.procStatsCpuSysTimeMs.isEmpty()) {
                        snapshot.procStatsCpuSysTimeMs = HashMap()
                    }
                    snapshot.procStatsCpuSysTimeMs[pkg] = DigitEntry.of(
                        HealthStatsHelper.getMeasure(
                            procStats,
                            ProcessHealthStats.MEASUREMENT_SYSTEM_TIME_MS
                        )
                    )

                    if (snapshot.procStatsCpuFgTimeMs.isEmpty()) {
                        snapshot.procStatsCpuFgTimeMs = HashMap()
                    }
                    snapshot.procStatsCpuFgTimeMs[pkg] = DigitEntry.of(
                        HealthStatsHelper.getMeasure(
                            procStats,
                            ProcessHealthStats.MEASUREMENT_FOREGROUND_MS
                        )
                    )

                    if (snapshot.procStatsStartCount.isEmpty()) {
                        snapshot.procStatsStartCount = HashMap()
                    }
                    snapshot.procStatsStartCount[pkg] = DigitEntry.of(
                        HealthStatsHelper.getMeasure(
                            procStats,
                            ProcessHealthStats.MEASUREMENT_STARTS_COUNT
                        )
                    )
                }
            }
        }
        return snapshot
    }

    open class HealthStatsSnapshot : Snapshot<HealthStatsSnapshot>() {
        @JvmField
        var accCollector: AccCollector? = null

        @VisibleForTesting
        @JvmField
        var healthStats: HealthStats? = null

        // For test & tunings values
        @JvmField
        var extras: MutableMap<String, Any> = Collections.emptyMap()

        // Estimated Powers
        @JvmField
        var cpuPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var wakelocksPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var mobilePower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var wifiPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var blueToothPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var gpsPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var sensorsPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var cameraPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var flashLightPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var audioPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var videoPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var screenPower: DigitEntry<Double> = DigitEntry.of(0.0)
        @JvmField
        var systemServicePower: DigitEntry<Double> = DigitEntry.of(0.0) // WIP
        @JvmField
        var idlePower: DigitEntry<Double> = DigitEntry.of(0.0)

        // Meta Data:
        // CPU
        @JvmField
        var cpuPowerMams: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var cpuUsrTimeMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var cpuSysTimeMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var realTimeMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var upTimeMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var offRealTimeMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var offUpTimeMs: DigitEntry<Long> = DigitEntry.of(0L)

        // Network
        @JvmField
        var mobilePowerMams: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var mobileRadioActiveMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var mobileIdleMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var mobileRxMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var mobileTxMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var mobileRxBytes: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var mobileTxBytes: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var mobileRxPackets: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var mobileTxPackets: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var wifiPowerMams: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiIdleMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiRxMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiTxMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiRunningMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiLockMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiScanMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiMulticastMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiRxBytes: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiTxBytes: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiRxPackets: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wifiTxPackets: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var blueToothPowerMams: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var blueToothIdleMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var blueToothRxMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var blueToothTxMs: DigitEntry<Long> = DigitEntry.of(0L)

        // SystemService & Media
        @JvmField
        var wakelocksPartialMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wakelocksFullMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wakelocksWindowMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wakelocksDrawMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var wakelocksPidSum: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var gpsMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var sensorsPowerMams: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var cameraMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var flashLightMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var audioMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var videoMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var jobsMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var syncMs: DigitEntry<Long> = DigitEntry.of(0L)

        // Foreground
        @JvmField
        var fgActMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var procTopAppMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var procTopSleepMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var procFgMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var procFgSrvMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var procBgMs: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField
        var procCacheMs: DigitEntry<Long> = DigitEntry.of(0L)

        // Map: Nested data in collections
        //      Process
        @JvmField
        var procStatsCpuUsrTimeMs: MutableMap<String, DigitEntry<Long>> = Collections.emptyMap()
        @JvmField
        var procStatsCpuSysTimeMs: MutableMap<String, DigitEntry<Long>> = Collections.emptyMap()
        @JvmField
        var procStatsCpuFgTimeMs: MutableMap<String, DigitEntry<Long>> = Collections.emptyMap()
        @JvmField
        var procStatsStartCount: MutableMap<String, DigitEntry<Long>> = Collections.emptyMap()

        //     Wakelocks
        @JvmField
        var tagWakelocksPartialMs: MutableMap<String, DigitEntry<Long>> = Collections.emptyMap()
        @JvmField
        var tagWakelocksFullMs: MutableMap<String, DigitEntry<Long>> = Collections.emptyMap()

        open fun getTotalPower(): Double {
            return cpuPower.get() +
                    wakelocksPower.get() +
                    mobilePower.get() +
                    wifiPower.get() +
                    blueToothPower.get() +
                    gpsPower.get() +
                    sensorsPower.get() +
                    flashLightPower.get() +
                    audioPower.get() +
                    videoPower.get() +
                    screenPower.get() +
                    // + cameraPower.get()        // WIP
                    // + systemServicePower.get() // WIP
                    idlePower.get()
        }

        open fun startAccCollecting(): AccCollector {
            return AccCollector(this).also {
                accCollector = it
            }
        }

        open fun accCollect(curr: HealthStatsSnapshot): Delta<HealthStatsSnapshot>? {
            val collector = accCollector ?: throw IllegalStateException("Call start collect first!")
            return collector.collect(curr)
        }

        open fun diffByAccCollector(bgn: HealthStatsSnapshot): Delta<HealthStatsSnapshot> {
            val collector =
                bgn.accCollector ?: throw IllegalStateException("Call start collect first!")
            bgn.accCollect(this)
            val delta = collector.accDelta
            return Delta.SimpleDelta(bgn, this, delta)
        }

        open fun diffInternal(bgn: HealthStatsSnapshot): Delta<HealthStatsSnapshot> {
            return object : Delta<HealthStatsSnapshot>(bgn, this) {
                override fun computeDelta(): HealthStatsSnapshot {
                    val delta = HealthStatsSnapshot()

                    // UID
                    delta.cpuPower = Differ.DigitDiffer.globalDiff(bgn.cpuPower, end.cpuPower)
                    delta.wakelocksPower =
                        Differ.DigitDiffer.globalDiff(bgn.wakelocksPower, end.wakelocksPower)
                    delta.mobilePower =
                        Differ.DigitDiffer.globalDiff(bgn.mobilePower, end.mobilePower)
                    delta.wifiPower = Differ.DigitDiffer.globalDiff(bgn.wifiPower, end.wifiPower)
                    delta.blueToothPower =
                        Differ.DigitDiffer.globalDiff(bgn.blueToothPower, end.blueToothPower)
                    delta.gpsPower = Differ.DigitDiffer.globalDiff(bgn.gpsPower, end.gpsPower)
                    delta.sensorsPower =
                        Differ.DigitDiffer.globalDiff(bgn.sensorsPower, end.sensorsPower)
                    delta.cameraPower =
                        Differ.DigitDiffer.globalDiff(bgn.cameraPower, end.cameraPower)
                    delta.flashLightPower =
                        Differ.DigitDiffer.globalDiff(bgn.flashLightPower, end.flashLightPower)
                    delta.audioPower = Differ.DigitDiffer.globalDiff(bgn.audioPower, end.audioPower)
                    delta.videoPower = Differ.DigitDiffer.globalDiff(bgn.videoPower, end.videoPower)
                    delta.screenPower =
                        Differ.DigitDiffer.globalDiff(bgn.screenPower, end.screenPower)
                    delta.systemServicePower = Differ.DigitDiffer.globalDiff(
                        bgn.systemServicePower,
                        end.systemServicePower
                    )
                    delta.idlePower = Differ.DigitDiffer.globalDiff(bgn.idlePower, end.idlePower)

                    delta.cpuPowerMams =
                        Differ.DigitDiffer.globalDiff(bgn.cpuPowerMams, end.cpuPowerMams)
                    delta.cpuUsrTimeMs =
                        Differ.DigitDiffer.globalDiff(bgn.cpuUsrTimeMs, end.cpuUsrTimeMs)
                    delta.cpuSysTimeMs =
                        Differ.DigitDiffer.globalDiff(bgn.cpuSysTimeMs, end.cpuSysTimeMs)
                    delta.realTimeMs = Differ.DigitDiffer.globalDiff(bgn.realTimeMs, end.realTimeMs)
                    delta.upTimeMs = Differ.DigitDiffer.globalDiff(bgn.upTimeMs, end.upTimeMs)

                    delta.mobilePowerMams =
                        Differ.DigitDiffer.globalDiff(bgn.mobilePowerMams, end.mobilePowerMams)
                    delta.mobileRadioActiveMs = Differ.DigitDiffer.globalDiff(
                        bgn.mobileRadioActiveMs,
                        end.mobileRadioActiveMs
                    )
                    delta.mobileIdleMs =
                        Differ.DigitDiffer.globalDiff(bgn.mobileIdleMs, end.mobileIdleMs)
                    delta.mobileRxMs = Differ.DigitDiffer.globalDiff(bgn.mobileRxMs, end.mobileRxMs)
                    delta.mobileTxMs = Differ.DigitDiffer.globalDiff(bgn.mobileTxMs, end.mobileTxMs)
                    delta.mobileRxBytes =
                        Differ.DigitDiffer.globalDiff(bgn.mobileRxBytes, end.mobileRxBytes)
                    delta.mobileTxBytes =
                        Differ.DigitDiffer.globalDiff(bgn.mobileTxBytes, end.mobileTxBytes)
                    delta.mobileRxPackets =
                        Differ.DigitDiffer.globalDiff(bgn.mobileRxPackets, end.mobileRxPackets)
                    delta.mobileTxPackets =
                        Differ.DigitDiffer.globalDiff(bgn.mobileTxPackets, end.mobileTxPackets)

                    delta.wifiPowerMams =
                        Differ.DigitDiffer.globalDiff(bgn.wifiPowerMams, end.wifiPowerMams)
                    delta.wifiIdleMs = Differ.DigitDiffer.globalDiff(bgn.wifiIdleMs, end.wifiIdleMs)
                    delta.wifiRxMs = Differ.DigitDiffer.globalDiff(bgn.wifiRxMs, end.wifiRxMs)
                    delta.wifiTxMs = Differ.DigitDiffer.globalDiff(bgn.wifiTxMs, end.wifiTxMs)
                    delta.wifiRunningMs =
                        Differ.DigitDiffer.globalDiff(bgn.wifiRunningMs, end.wifiRunningMs)
                    delta.wifiLockMs = Differ.DigitDiffer.globalDiff(bgn.wifiLockMs, end.wifiLockMs)
                    delta.wifiScanMs = Differ.DigitDiffer.globalDiff(bgn.wifiScanMs, end.wifiScanMs)
                    delta.wifiMulticastMs =
                        Differ.DigitDiffer.globalDiff(bgn.wifiMulticastMs, end.wifiMulticastMs)
                    delta.wifiRxBytes =
                        Differ.DigitDiffer.globalDiff(bgn.wifiRxBytes, end.wifiRxBytes)
                    delta.wifiTxBytes =
                        Differ.DigitDiffer.globalDiff(bgn.wifiTxBytes, end.wifiTxBytes)
                    delta.wifiRxPackets =
                        Differ.DigitDiffer.globalDiff(bgn.wifiRxPackets, end.wifiRxPackets)
                    delta.wifiTxPackets =
                        Differ.DigitDiffer.globalDiff(bgn.wifiTxPackets, end.wifiTxPackets)

                    delta.blueToothPowerMams = Differ.DigitDiffer.globalDiff(
                        bgn.blueToothPowerMams,
                        end.blueToothPowerMams
                    )
                    delta.blueToothIdleMs =
                        Differ.DigitDiffer.globalDiff(bgn.blueToothIdleMs, end.blueToothIdleMs)
                    delta.blueToothRxMs =
                        Differ.DigitDiffer.globalDiff(bgn.blueToothRxMs, end.blueToothRxMs)
                    delta.blueToothTxMs =
                        Differ.DigitDiffer.globalDiff(bgn.blueToothTxMs, end.blueToothTxMs)

                    delta.wakelocksPartialMs = Differ.DigitDiffer.globalDiff(
                        bgn.wakelocksPartialMs,
                        end.wakelocksPartialMs
                    )
                    delta.wakelocksFullMs =
                        Differ.DigitDiffer.globalDiff(bgn.wakelocksFullMs, end.wakelocksFullMs)
                    delta.wakelocksWindowMs =
                        Differ.DigitDiffer.globalDiff(bgn.wakelocksWindowMs, end.wakelocksWindowMs)
                    delta.wakelocksDrawMs =
                        Differ.DigitDiffer.globalDiff(bgn.wakelocksDrawMs, end.wakelocksDrawMs)
                    delta.wakelocksPidSum =
                        Differ.DigitDiffer.globalDiff(bgn.wakelocksPidSum, end.wakelocksPidSum)
                    delta.gpsMs = Differ.DigitDiffer.globalDiff(bgn.gpsMs, end.gpsMs)
                    delta.sensorsPowerMams =
                        Differ.DigitDiffer.globalDiff(bgn.sensorsPowerMams, end.sensorsPowerMams)
                    delta.cameraMs = Differ.DigitDiffer.globalDiff(bgn.cameraMs, end.cameraMs)
                    delta.flashLightMs =
                        Differ.DigitDiffer.globalDiff(bgn.flashLightMs, end.flashLightMs)
                    delta.audioMs = Differ.DigitDiffer.globalDiff(bgn.audioMs, end.audioMs)
                    delta.videoMs = Differ.DigitDiffer.globalDiff(bgn.videoMs, end.videoMs)
                    delta.jobsMs = Differ.DigitDiffer.globalDiff(bgn.jobsMs, end.jobsMs)
                    delta.syncMs = Differ.DigitDiffer.globalDiff(bgn.syncMs, end.syncMs)

                    delta.fgActMs = Differ.DigitDiffer.globalDiff(bgn.fgActMs, end.fgActMs)
                    delta.procTopAppMs =
                        Differ.DigitDiffer.globalDiff(bgn.procTopAppMs, end.procTopAppMs)
                    delta.procTopSleepMs =
                        Differ.DigitDiffer.globalDiff(bgn.procTopSleepMs, end.procTopSleepMs)
                    delta.procFgMs = Differ.DigitDiffer.globalDiff(bgn.procFgMs, end.procFgMs)
                    delta.procFgSrvMs =
                        Differ.DigitDiffer.globalDiff(bgn.procFgSrvMs, end.procFgSrvMs)
                    delta.procBgMs = Differ.DigitDiffer.globalDiff(bgn.procBgMs, end.procBgMs)
                    delta.procCacheMs =
                        Differ.DigitDiffer.globalDiff(bgn.procCacheMs, end.procCacheMs)

                    delta.procStatsCpuUsrTimeMs =
                        diffMap(bgn.procStatsCpuUsrTimeMs, end.procStatsCpuUsrTimeMs)
                    delta.procStatsCpuSysTimeMs =
                        diffMap(bgn.procStatsCpuSysTimeMs, end.procStatsCpuSysTimeMs)
                    delta.procStatsCpuFgTimeMs =
                        diffMap(bgn.procStatsCpuFgTimeMs, end.procStatsCpuFgTimeMs)
                    delta.procStatsStartCount =
                        diffMap(bgn.procStatsStartCount, end.procStatsStartCount)
                    delta.tagWakelocksPartialMs =
                        diffMap(bgn.tagWakelocksPartialMs, end.tagWakelocksPartialMs)
                    delta.tagWakelocksFullMs =
                        diffMap(bgn.tagWakelocksFullMs, end.tagWakelocksFullMs)
                    return delta
                }
            }
        }

        override fun diff(bgn: HealthStatsSnapshot): Delta<HealthStatsSnapshot> {
            val delta = diffInternal(bgn)
            // Sort
            delta.dlt.procStatsCpuUsrTimeMs = decrease(procStatsCpuUsrTimeMs)
            delta.dlt.procStatsCpuSysTimeMs = decrease(procStatsCpuSysTimeMs)
            delta.dlt.procStatsCpuFgTimeMs = decrease(procStatsCpuFgTimeMs)
            delta.dlt.procStatsStartCount = decrease(procStatsStartCount)
            delta.dlt.tagWakelocksPartialMs = decrease(tagWakelocksPartialMs)
            delta.dlt.tagWakelocksFullMs = decrease(tagWakelocksFullMs)
            return delta
        }

        private fun decrease(input: Map<String, DigitEntry<Long>>): MutableMap<String, DigitEntry<Long>> {
            return BatteryCanaryUtil.sortMapByValue(input) { o1, o2 ->
                val sumLeft = o1.value.get()
                val sumRight = o2.value.get()
                val minus = sumLeft - sumRight
                when {
                    minus == 0L -> 0
                    minus > 0L -> -1
                    else -> 1
                }
            }
        }

        open class AccCollector(bgn: HealthStatsSnapshot) {
            @JvmField
            var count: Int = 0
            @JvmField
            var beginMs: Long = bgn.time
            @JvmField
            var duringMs: Long = 0
            @JvmField
            var last: HealthStatsSnapshot = bgn
            @JvmField
            var accDelta: HealthStatsSnapshot = HealthStatsSnapshot()

            init {
                accDelta.procStatsCpuUsrTimeMs = HashMap()
                accDelta.procStatsCpuSysTimeMs = HashMap()
                accDelta.procStatsCpuFgTimeMs = HashMap()
                accDelta.procStatsStartCount = HashMap()
                accDelta.tagWakelocksPartialMs = HashMap()
                accDelta.tagWakelocksFullMs = HashMap()
            }

            open fun collect(curr: HealthStatsSnapshot): Delta<HealthStatsSnapshot>? {
                var delta: Delta<HealthStatsSnapshot>? = null
                if (isHealthStatsNotReset(last, curr)) {
                    delta = curr.diffInternal(last)

                    accDelta.cpuPower =
                        DigitEntry.of(accDelta.cpuPower.get() + delta.dlt.cpuPower.get())
                    accDelta.wakelocksPower =
                        DigitEntry.of(accDelta.wakelocksPower.get() + delta.dlt.wakelocksPower.get())
                    accDelta.mobilePower =
                        DigitEntry.of(accDelta.mobilePower.get() + delta.dlt.mobilePower.get())
                    accDelta.wifiPower =
                        DigitEntry.of(accDelta.wifiPower.get() + delta.dlt.wifiPower.get())
                    accDelta.blueToothPower =
                        DigitEntry.of(accDelta.blueToothPower.get() + delta.dlt.blueToothPower.get())
                    accDelta.gpsPower =
                        DigitEntry.of(accDelta.gpsPower.get() + delta.dlt.gpsPower.get())
                    accDelta.sensorsPower =
                        DigitEntry.of(accDelta.sensorsPower.get() + delta.dlt.sensorsPower.get())
                    accDelta.cameraPower =
                        DigitEntry.of(accDelta.cameraPower.get() + delta.dlt.cameraPower.get())
                    accDelta.flashLightPower =
                        DigitEntry.of(accDelta.flashLightPower.get() + delta.dlt.flashLightPower.get())
                    accDelta.audioPower =
                        DigitEntry.of(accDelta.audioPower.get() + delta.dlt.audioPower.get())
                    accDelta.videoPower =
                        DigitEntry.of(accDelta.videoPower.get() + delta.dlt.videoPower.get())
                    accDelta.screenPower =
                        DigitEntry.of(accDelta.screenPower.get() + delta.dlt.screenPower.get())
                    accDelta.systemServicePower =
                        DigitEntry.of(accDelta.systemServicePower.get() + delta.dlt.systemServicePower.get())
                    accDelta.idlePower =
                        DigitEntry.of(accDelta.idlePower.get() + delta.dlt.idlePower.get())

                    accDelta.cpuPowerMams =
                        DigitEntry.of(accDelta.cpuPowerMams.get() + delta.dlt.cpuPowerMams.get())
                    accDelta.cpuUsrTimeMs =
                        DigitEntry.of(accDelta.cpuUsrTimeMs.get() + delta.dlt.cpuUsrTimeMs.get())
                    accDelta.cpuSysTimeMs =
                        DigitEntry.of(accDelta.cpuSysTimeMs.get() + delta.dlt.cpuSysTimeMs.get())
                    accDelta.realTimeMs =
                        DigitEntry.of(accDelta.realTimeMs.get() + delta.dlt.realTimeMs.get())
                    accDelta.upTimeMs =
                        DigitEntry.of(accDelta.upTimeMs.get() + delta.dlt.upTimeMs.get())

                    accDelta.mobilePowerMams =
                        DigitEntry.of(accDelta.mobilePowerMams.get() + delta.dlt.mobilePowerMams.get())
                    accDelta.mobileRadioActiveMs =
                        DigitEntry.of(accDelta.mobileRadioActiveMs.get() + delta.dlt.mobileRadioActiveMs.get())
                    accDelta.mobileIdleMs =
                        DigitEntry.of(accDelta.mobileIdleMs.get() + delta.dlt.mobileIdleMs.get())
                    accDelta.mobileRxMs =
                        DigitEntry.of(accDelta.mobileRxMs.get() + delta.dlt.mobileRxMs.get())
                    accDelta.mobileTxMs =
                        DigitEntry.of(accDelta.mobileTxMs.get() + delta.dlt.mobileTxMs.get())
                    accDelta.mobileRxBytes =
                        DigitEntry.of(accDelta.mobileRxBytes.get() + delta.dlt.mobileRxBytes.get())
                    accDelta.mobileTxBytes =
                        DigitEntry.of(accDelta.mobileTxBytes.get() + delta.dlt.mobileTxBytes.get())
                    accDelta.mobileRxPackets =
                        DigitEntry.of(accDelta.mobileRxPackets.get() + delta.dlt.mobileRxPackets.get())
                    accDelta.mobileTxPackets =
                        DigitEntry.of(accDelta.mobileTxPackets.get() + delta.dlt.mobileTxPackets.get())

                    accDelta.wifiPowerMams =
                        DigitEntry.of(accDelta.wifiPowerMams.get() + delta.dlt.wifiPowerMams.get())
                    accDelta.wifiIdleMs =
                        DigitEntry.of(accDelta.wifiIdleMs.get() + delta.dlt.wifiIdleMs.get())
                    accDelta.wifiRxMs =
                        DigitEntry.of(accDelta.wifiRxMs.get() + delta.dlt.wifiRxMs.get())
                    accDelta.wifiTxMs =
                        DigitEntry.of(accDelta.wifiTxMs.get() + delta.dlt.wifiTxMs.get())
                    accDelta.wifiRunningMs =
                        DigitEntry.of(accDelta.wifiRunningMs.get() + delta.dlt.wifiRunningMs.get())
                    accDelta.wifiLockMs =
                        DigitEntry.of(accDelta.wifiLockMs.get() + delta.dlt.wifiLockMs.get())
                    accDelta.wifiScanMs =
                        DigitEntry.of(accDelta.wifiScanMs.get() + delta.dlt.wifiScanMs.get())
                    accDelta.wifiMulticastMs =
                        DigitEntry.of(accDelta.wifiMulticastMs.get() + delta.dlt.wifiMulticastMs.get())
                    accDelta.wifiRxBytes =
                        DigitEntry.of(accDelta.wifiRxBytes.get() + delta.dlt.wifiRxBytes.get())
                    accDelta.wifiTxBytes =
                        DigitEntry.of(accDelta.wifiTxBytes.get() + delta.dlt.wifiTxBytes.get())
                    accDelta.wifiRxPackets =
                        DigitEntry.of(accDelta.wifiRxPackets.get() + delta.dlt.wifiRxPackets.get())
                    accDelta.wifiTxPackets =
                        DigitEntry.of(accDelta.wifiTxPackets.get() + delta.dlt.wifiTxPackets.get())

                    accDelta.blueToothPowerMams =
                        DigitEntry.of(accDelta.blueToothPowerMams.get() + delta.dlt.blueToothPowerMams.get())
                    accDelta.blueToothIdleMs =
                        DigitEntry.of(accDelta.blueToothIdleMs.get() + delta.dlt.blueToothIdleMs.get())
                    accDelta.blueToothRxMs =
                        DigitEntry.of(accDelta.blueToothRxMs.get() + delta.dlt.blueToothRxMs.get())
                    accDelta.blueToothTxMs =
                        DigitEntry.of(accDelta.blueToothTxMs.get() + delta.dlt.blueToothTxMs.get())

                    accDelta.wakelocksPartialMs =
                        DigitEntry.of(accDelta.wakelocksPartialMs.get() + delta.dlt.wakelocksPartialMs.get())
                    accDelta.wakelocksFullMs =
                        DigitEntry.of(accDelta.wakelocksFullMs.get() + delta.dlt.wakelocksFullMs.get())
                    accDelta.wakelocksWindowMs =
                        DigitEntry.of(accDelta.wakelocksWindowMs.get() + delta.dlt.wakelocksWindowMs.get())
                    accDelta.wakelocksDrawMs =
                        DigitEntry.of(accDelta.wakelocksDrawMs.get() + delta.dlt.wakelocksDrawMs.get())
                    accDelta.wakelocksPidSum =
                        DigitEntry.of(accDelta.wakelocksPidSum.get() + delta.dlt.wakelocksPidSum.get())
                    accDelta.gpsMs = DigitEntry.of(accDelta.gpsMs.get() + delta.dlt.gpsMs.get())
                    accDelta.sensorsPowerMams =
                        DigitEntry.of(accDelta.sensorsPowerMams.get() + delta.dlt.sensorsPowerMams.get())
                    accDelta.cameraMs =
                        DigitEntry.of(accDelta.cameraMs.get() + delta.dlt.cameraMs.get())
                    accDelta.flashLightMs =
                        DigitEntry.of(accDelta.flashLightMs.get() + delta.dlt.flashLightMs.get())
                    accDelta.audioMs =
                        DigitEntry.of(accDelta.audioMs.get() + delta.dlt.audioMs.get())
                    accDelta.videoMs =
                        DigitEntry.of(accDelta.videoMs.get() + delta.dlt.videoMs.get())
                    accDelta.jobsMs = DigitEntry.of(accDelta.jobsMs.get() + delta.dlt.jobsMs.get())
                    accDelta.syncMs = DigitEntry.of(accDelta.syncMs.get() + delta.dlt.syncMs.get())

                    accDelta.fgActMs =
                        DigitEntry.of(accDelta.fgActMs.get() + delta.dlt.fgActMs.get())
                    accDelta.procTopAppMs =
                        DigitEntry.of(accDelta.procTopAppMs.get() + delta.dlt.procTopAppMs.get())
                    accDelta.procTopSleepMs =
                        DigitEntry.of(accDelta.procTopSleepMs.get() + delta.dlt.procTopSleepMs.get())
                    accDelta.procFgMs =
                        DigitEntry.of(accDelta.procFgMs.get() + delta.dlt.procFgMs.get())
                    accDelta.procFgSrvMs =
                        DigitEntry.of(accDelta.procFgSrvMs.get() + delta.dlt.procFgSrvMs.get())
                    accDelta.procBgMs =
                        DigitEntry.of(accDelta.procBgMs.get() + delta.dlt.procBgMs.get())
                    accDelta.procCacheMs =
                        DigitEntry.of(accDelta.procCacheMs.get() + delta.dlt.procCacheMs.get())

                    accumulateMap(accDelta.procStatsCpuUsrTimeMs, delta.dlt.procStatsCpuUsrTimeMs)
                    accumulateMap(accDelta.procStatsCpuSysTimeMs, delta.dlt.procStatsCpuSysTimeMs)
                    accumulateMap(accDelta.procStatsCpuFgTimeMs, delta.dlt.procStatsCpuFgTimeMs)
                    accumulateMap(accDelta.procStatsStartCount, delta.dlt.procStatsStartCount)
                    accumulateMap(accDelta.tagWakelocksPartialMs, delta.dlt.tagWakelocksPartialMs)
                    accumulateMap(accDelta.tagWakelocksFullMs, delta.dlt.tagWakelocksFullMs)

                    count++
                    duringMs += delta.during
                }
                last = curr
                return delta
            }

            companion object {
                @JvmStatic
                fun isHealthStatsNotReset(
                    bgn: HealthStatsSnapshot,
                    end: HealthStatsSnapshot
                ): Boolean {
                    return try {
                        assertNotNegative(
                            "cpuPowerMams",
                            bgn.cpuPowerMams.get(),
                            end.cpuPowerMams.get()
                        )
                        assertNotNegative(
                            "cpuUsrTimeMs",
                            bgn.cpuUsrTimeMs.get(),
                            end.cpuUsrTimeMs.get()
                        )
                        assertNotNegative(
                            "cpuSysTimeMs",
                            bgn.cpuSysTimeMs.get(),
                            end.cpuSysTimeMs.get()
                        )
                        assertNotNegative("realTimeMs", bgn.realTimeMs.get(), end.realTimeMs.get())
                        assertNotNegative("upTimeMs", bgn.upTimeMs.get(), end.upTimeMs.get())
                        assertNotNegative(
                            "offRealTimeMs",
                            bgn.offRealTimeMs.get(),
                            end.offRealTimeMs.get()
                        )
                        assertNotNegative(
                            "offUpTimeMs",
                            bgn.offUpTimeMs.get(),
                            end.offUpTimeMs.get()
                        )

                        assertNotNegative(
                            "mobilePowerMams",
                            bgn.mobilePowerMams.get(),
                            end.mobilePowerMams.get()
                        )
                        assertNotNegative(
                            "mobileRadioActiveMs",
                            bgn.mobileRadioActiveMs.get(),
                            end.mobileRadioActiveMs.get()
                        )
                        assertNotNegative(
                            "mobileIdleMs",
                            bgn.mobileIdleMs.get(),
                            end.mobileIdleMs.get()
                        )
                        assertNotNegative("mobileRxMs", bgn.mobileRxMs.get(), end.mobileRxMs.get())
                        assertNotNegative("mobileTxMs", bgn.mobileTxMs.get(), end.mobileTxMs.get())
                        assertNotNegative(
                            "mobileRxBytes",
                            bgn.mobileRxBytes.get(),
                            end.mobileRxBytes.get()
                        )
                        assertNotNegative(
                            "mobileTxBytes",
                            bgn.mobileTxBytes.get(),
                            end.mobileTxBytes.get()
                        )
                        assertNotNegative(
                            "mobileRxPackets",
                            bgn.mobileRxPackets.get(),
                            end.mobileRxPackets.get()
                        )
                        assertNotNegative(
                            "mobileTxPackets",
                            bgn.mobileTxPackets.get(),
                            end.mobileTxPackets.get()
                        )

                        assertNotNegative(
                            "wifiPowerMams",
                            bgn.wifiPowerMams.get(),
                            end.wifiPowerMams.get()
                        )
                        assertNotNegative("wifiIdleMs", bgn.wifiIdleMs.get(), end.wifiIdleMs.get())
                        assertNotNegative("wifiRxMs", bgn.wifiRxMs.get(), end.wifiRxMs.get())
                        assertNotNegative("wifiTxMs", bgn.wifiTxMs.get(), end.wifiTxMs.get())
                        assertNotNegative(
                            "wifiRunningMs",
                            bgn.wifiRunningMs.get(),
                            end.wifiRunningMs.get()
                        )
                        assertNotNegative("wifiLockMs", bgn.wifiLockMs.get(), end.wifiLockMs.get())
                        assertNotNegative("wifiScanMs", bgn.wifiScanMs.get(), end.wifiScanMs.get())
                        assertNotNegative(
                            "wifiMulticastMs",
                            bgn.wifiMulticastMs.get(),
                            end.wifiMulticastMs.get()
                        )
                        assertNotNegative(
                            "wifiRxBytes",
                            bgn.wifiRxBytes.get(),
                            end.wifiRxBytes.get()
                        )
                        assertNotNegative(
                            "wifiTxBytes",
                            bgn.wifiTxBytes.get(),
                            end.wifiTxBytes.get()
                        )
                        assertNotNegative(
                            "wifiRxPackets",
                            bgn.wifiRxPackets.get(),
                            end.wifiRxPackets.get()
                        )
                        assertNotNegative(
                            "wifiTxPackets",
                            bgn.wifiTxPackets.get(),
                            end.wifiTxPackets.get()
                        )

                        assertNotNegative(
                            "blueToothPowerMams",
                            bgn.blueToothPowerMams.get(),
                            end.blueToothPowerMams.get()
                        )
                        assertNotNegative(
                            "blueToothIdleMs",
                            bgn.blueToothIdleMs.get(),
                            end.blueToothIdleMs.get()
                        )
                        assertNotNegative(
                            "blueToothRxMs",
                            bgn.blueToothRxMs.get(),
                            end.blueToothRxMs.get()
                        )
                        assertNotNegative(
                            "blueToothTxMs",
                            bgn.blueToothTxMs.get(),
                            end.blueToothTxMs.get()
                        )

                        assertNotNegative(
                            "wakelocksPartialMs",
                            bgn.wakelocksPartialMs.get(),
                            end.wakelocksPartialMs.get()
                        )
                        assertNotNegative(
                            "wakelocksFullMs",
                            bgn.wakelocksFullMs.get(),
                            end.wakelocksFullMs.get()
                        )
                        assertNotNegative(
                            "wakelocksWindowMs",
                            bgn.wakelocksWindowMs.get(),
                            end.wakelocksWindowMs.get()
                        )
                        assertNotNegative(
                            "wakelocksDrawMs",
                            bgn.wakelocksDrawMs.get(),
                            end.wakelocksDrawMs.get()
                        )
                        assertNotNegative(
                            "wakelocksPidSum",
                            bgn.wakelocksPidSum.get(),
                            end.wakelocksPidSum.get()
                        )
                        assertNotNegative("gpsMs", bgn.gpsMs.get(), end.gpsMs.get())
                        assertNotNegative(
                            "sensorsPowerMams",
                            bgn.sensorsPowerMams.get(),
                            end.sensorsPowerMams.get()
                        )
                        assertNotNegative("cameraMs", bgn.cameraMs.get(), end.cameraMs.get())
                        assertNotNegative(
                            "flashLightMs",
                            bgn.flashLightMs.get(),
                            end.flashLightMs.get()
                        )
                        assertNotNegative("audioMs", bgn.audioMs.get(), end.audioMs.get())
                        assertNotNegative("videoMs", bgn.videoMs.get(), end.videoMs.get())
                        assertNotNegative("jobsMs", bgn.jobsMs.get(), end.jobsMs.get())
                        assertNotNegative("syncMs", bgn.syncMs.get(), end.syncMs.get())

                        true
                    } catch (e: Exception) {
                        TraceHarborLog.w(TAG, "skip, " + e.message)
                        false
                    }
                }

                @JvmStatic
                fun assertNotNegative(key: String, bgn: Long, end: Long) {
                    if (bgn > end) {
                        throw IllegalStateException("negative stats: $key")
                    }
                }
            }
        }

        companion object {
            @JvmStatic
            fun getPower(extra: Map<String, *>, key: String): Double {
                val value = extra[key]
                return if (value is Double) value else 0.0
            }

            private fun diffMap(
                bgn: Map<String, DigitEntry<Long>>,
                end: Map<String, DigitEntry<Long>>,
            ): MutableMap<String, DigitEntry<Long>> {
                val map: MutableMap<String, DigitEntry<Long>> = HashMap()
                for ((key, endEntry) in end) {
                    val bgnValue = bgn[key]?.get() ?: 0L
                    map[key] = Differ.DigitDiffer.globalDiff(DigitEntry.of(bgnValue), endEntry)
                }
                return map
            }

            private fun accumulateMap(
                accMap: MutableMap<String, DigitEntry<Long>>,
                deltaMap: Map<String, DigitEntry<Long>>,
            ) {
                for ((key, value) in deltaMap) {
                    val acc = accMap[key]
                    accMap[key] = DigitEntry.of(value.get() + (acc?.get() ?: 0L))
                }
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.HealthStats"
    }
}
