package com.kernelflux.traceharbor.batterycanary.stats

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.health.HealthStats
import android.os.health.SystemHealthManager
import android.os.health.TimerStat
import android.os.health.UidHealthStats
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.batterycanary.BatteryCanary
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature.CpuStateSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.ListEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.TrafficMonitorFeature.RadioStatSnapshot
import com.kernelflux.traceharbor.batterycanary.utils.PowerProfile
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Method
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * totalPowerMah = usagePowerMah + wifiPowerMah + gpsPowerMah + cpuPowerMah +
 *                 sensorPowerMah + mobileRadioPowerMah + wakeLockPowerMah + cameraPowerMah +
 *                 flashlightPowerMah + bluetoothPowerMah + audioPowerMah + videoPowerMah
 *                 + systemServiceCpuPowerMah;
 * if (customMeasuredPowerMah != null) {
 *     for (int idx = 0; idx < customMeasuredPowerMah.length; idx++) {
 *         totalPowerMah += customMeasuredPowerMah[idx];
 *     }
 * }
 * // powerAttributedToOtherSippersMah is negative or zero
 * totalPowerMah = totalPowerMah + powerReattributedToOtherSippersMah;
 * totalSmearedPowerMah = totalPowerMah + screenPowerMah + proportionalSmearMah;
 *
 * @see com.android.internal.os.BatterySipper#sumPower
 * @see com.android.internal.os.BatteryStatsHelper
 * @see com.android.internal.os.BatteryStatsImpl.Uid
 *
 * @author Kaede
 * @since 6/7/2022
 */
@SuppressLint("RestrictedApi")
object HealthStatsHelper {
    const val TAG: String = "HealthStatsHelper"

    class UsageBasedPowerEstimator(averagePowerMilliAmp: Double) {
        private val mAveragePowerMahPerMs: Double = averagePowerMilliAmp / MILLIS_IN_HOUR

        fun isSupported(): Boolean = mAveragePowerMahPerMs != 0.0

        fun calculatePower(durationMs: Long): Double = mAveragePowerMahPerMs * durationMs

        private companion object {
            private const val MILLIS_IN_HOUR = 1000.0 * 60 * 60
        }
    }

    @JvmStatic
    fun round(input: Double, decimalPlace: Int): Double {
        val decimal = 10.0.pow(decimalPlace.toDouble())
        return round(input * decimal) / decimal
    }

    @JvmStatic
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    @JvmStatic
    fun getCurrStats(context: Context): HealthStats? {
        if (isSupported()) {
            try {
                val shm = context.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as SystemHealthManager
                return shm.takeMyUidSnapshot()
            } catch (e: Exception) {
                TraceHarborLog.w(TAG, "takeMyUidSnapshot err: $e")
            }
        }
        return null
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun getMeasure(healthStats: HealthStats?, key: Int): Long {
        return if (healthStats != null && healthStats.hasMeasurement(key)) healthStats.getMeasurement(key) else 0L
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun getTimerTime(healthStats: HealthStats?, key: Int): Long {
        return if (healthStats != null && healthStats.hasTimer(key)) healthStats.getTimerTime(key) else 0L
    }

    /**
     * @see com.android.internal.os.CpuPowerCalculator
     * @see com.android.internal.os.PowerProfile
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcCpuPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        // double mams = getMeasure(healthStats, UidHealthStats.MEASUREMENT_CPU_POWER_MAMS) / (UsageBasedPowerEstimator.MILLIS_IN_HOUR * 1000L);
        // if (mams > 0) {
        //     TraceHarborLog.i(TAG, "estimate CPU by mams");
        //     return mams;
        // }
        var power = 0.0
        /*
         * POWER_CPU_SUSPEND: Power consumption when CPU is in power collapse mode.
         * POWER_CPU_IDLE: Power consumption when CPU is awake (when a wake lock is held). This should
         *                 be zero on devices that can go into full CPU power collapse even when a wake
         *                 lock is held. Otherwise, this is the power consumption in addition to
         * POWER_CPU_SUSPEND due to a wake lock being held but with no CPU activity.
         * POWER_CPU_ACTIVE: Power consumption when CPU is running, excluding power consumed by clusters
         *                   and cores.
         *
         * CPU Power Equation (assume two clusters):
         * Total power = POWER_CPU_SUSPEND  (always added)
         *               + POWER_CPU_IDLE   (skip this and below if in power collapse mode)
         *               + POWER_CPU_ACTIVE (skip this and below if CPU is not running, but a wakelock
         *                                   is held)
         *               + cluster_power.cluster0 + cluster_power.cluster1 (skip cluster not running)
         *               + core_power.cluster0 * num running cores in cluster 0
         *               + core_power.cluster1 * num running cores in cluster 1
         */
        val cpuTimeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_USER_CPU_TIME_MS) +
            getMeasure(healthStats, UidHealthStats.MEASUREMENT_SYSTEM_CPU_TIME_MS)
        power += estimateCpuActivePower(powerProfile, cpuTimeMs)
        val feat = BatteryCanary.getMonitorFeature(CpuStatFeature::class.java)
        if (feat != null && feat.isSupported) {
            val snapshot = feat.currentCpuStateSnapshot()
            if (snapshot != null) {
                power += estimateCpuClustersPower(powerProfile, snapshot, cpuTimeMs, false)
                power += estimateCpuCoresPower(powerProfile, snapshot, cpuTimeMs, false)
            }
        }
        return if (power > 0) power else 0.0
    }

    @JvmStatic
    @VisibleForTesting
    fun estimateCpuActivePower(powerProfile: PowerProfile, cpuTimeMs: Long): Double {
        val timeMs = cpuTimeMs
        val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_CPU_ACTIVE)
        return UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
    }

    @JvmStatic
    @VisibleForTesting
    fun estimateCpuClustersPower(
        powerProfile: PowerProfile,
        snapshot: CpuStateSnapshot,
        cpuTimeMs: Long,
        scaled: Boolean,
    ): Double {
        var isUidStatsAvailable = false
        for (listEntry in snapshot.procCpuCoreStates) {
            for (item in listEntry.list) {
                if (item.get() > 0L) {
                    isUidStatsAvailable = true
                    break
                }
            }
        }
        return if (isUidStatsAvailable) {
            estimateCpuClustersPowerByUidStats(powerProfile, snapshot, cpuTimeMs, scaled)
        } else {
            TraceHarborLog.i(TAG, "estimate CPU by device stats")
            estimateCpuClustersPowerByDevStats(powerProfile, snapshot, cpuTimeMs)
        }
    }

    @JvmStatic
    @VisibleForTesting
    fun estimateCpuCoresPower(
        powerProfile: PowerProfile,
        snapshot: CpuStateSnapshot,
        cpuTimeMs: Long,
        scaled: Boolean,
    ): Double {
        var isUidStatsAvailable = false
        for (listEntry in snapshot.procCpuCoreStates) {
            for (item in listEntry.list) {
                if (item.get() > 0L) {
                    isUidStatsAvailable = true
                    break
                }
            }
        }
        return if (isUidStatsAvailable) {
            estimateCpuCoresPowerByUidStats(powerProfile, snapshot, cpuTimeMs, scaled)
        } else {
            TraceHarborLog.i(TAG, "estimate CPU by device stats")
            estimateCpuCoresPowerByDevStats(powerProfile, snapshot, cpuTimeMs)
        }
    }

    @JvmStatic
    @VisibleForTesting
    fun estimateCpuClustersPowerByUidStats(
        powerProfile: PowerProfile,
        snapshot: CpuStateSnapshot,
        cpuTimeMs: Long,
        scaled: Boolean,
    ): Double {
        if (cpuTimeMs > 0) {
            var jiffySum = 0L
            for (i in 0 until snapshot.procCpuCoreStates.size) {
                val stepJiffies = snapshot.procCpuCoreStates[i].list
                val scale = if (scaled) powerProfile.getNumCoresInCpuCluster(i) else 1
                for (item in stepJiffies) {
                    jiffySum += item.get() * scale
                }
            }
            var powerMah = 0.0
            for (i in 0 until snapshot.procCpuCoreStates.size) {
                val stepJiffies = snapshot.procCpuCoreStates[i].list
                val scale = if (scaled) powerProfile.getNumCoresInCpuCluster(i) else 1
                var jiffySumInCluster = 0L
                for (j in 0 until stepJiffies.size) {
                    val jiffy = stepJiffies[j].get()
                    jiffySumInCluster += jiffy * scale
                }
                val figuredCpuTimeMs = ((jiffySumInCluster * 1.0f / jiffySum) * cpuTimeMs).toLong()
                val powerMa = powerProfile.getAveragePowerForCpuCluster(i)
                powerMah += UsageBasedPowerEstimator(powerMa).calculatePower(figuredCpuTimeMs)
            }
            return powerMah
        }
        return 0.0
    }

    @JvmStatic
    @VisibleForTesting
    fun estimateCpuCoresPowerByUidStats(
        powerProfile: PowerProfile,
        snapshot: CpuStateSnapshot,
        cpuTimeMs: Long,
        scaled: Boolean,
    ): Double {
        if (cpuTimeMs > 0) {
            var jiffySum = 0L
            for (i in 0 until snapshot.procCpuCoreStates.size) {
                val stepJiffies = snapshot.procCpuCoreStates[i].list
                val scale = if (scaled) powerProfile.getNumCoresInCpuCluster(i) else 1
                for (item in stepJiffies) {
                    jiffySum += item.get() * scale
                }
            }
            var powerMah = 0.0
            for (i in 0 until snapshot.procCpuCoreStates.size) {
                val stepJiffies = snapshot.procCpuCoreStates[i].list
                val scale = if (scaled) powerProfile.getNumCoresInCpuCluster(i) else 1
                for (j in 0 until stepJiffies.size) {
                    val jiffy = stepJiffies[j].get()
                    val figuredCpuTimeMs = ((jiffy * scale * 1.0f / jiffySum) * cpuTimeMs).toLong()
                    val powerMa = powerProfile.getAveragePowerForCpuCore(i, j)
                    powerMah += UsageBasedPowerEstimator(powerMa).calculatePower(figuredCpuTimeMs)
                }
            }
            return powerMah
        }
        return 0.0
    }

    @JvmStatic
    @VisibleForTesting
    fun estimateCpuClustersPowerByDevStats(powerProfile: PowerProfile, snapshot: CpuStateSnapshot, cpuTimeMs: Long): Double {
        if (cpuTimeMs > 0) {
            var jiffySum = 0L
            for (i in 0 until snapshot.cpuCoreStates.size) {
                val stepJiffies = snapshot.cpuCoreStates[i].list
                for (item in stepJiffies) {
                    jiffySum += item.get()
                }
            }
            var powerMah = 0.0
            for (i in 0 until snapshot.cpuCoreStates.size) {
                val stepJiffies = snapshot.cpuCoreStates[i].list
                var jiffySumInCluster = 0L
                for (j in 0 until stepJiffies.size) {
                    val jiffy = stepJiffies[j].get()
                    jiffySumInCluster += jiffy
                }
                val figuredCpuTimeMs = ((jiffySumInCluster * 1.0f / jiffySum) * cpuTimeMs).toLong()
                val clusterNum = powerProfile.getClusterByCpuNum(i)
                if (clusterNum >= 0 && clusterNum < powerProfile.getNumCpuClusters()) {
                    val powerMa = powerProfile.getAveragePowerForCpuCluster(clusterNum)
                    powerMah += UsageBasedPowerEstimator(powerMa).calculatePower(figuredCpuTimeMs)
                }
            }
            return powerMah
        }
        return 0.0
    }

    @JvmStatic
    @VisibleForTesting
    fun estimateCpuCoresPowerByDevStats(powerProfile: PowerProfile, snapshot: CpuStateSnapshot, cpuTimeMs: Long): Double {
        if (cpuTimeMs > 0) {
            var jiffySum = 0L
            for (i in 0 until snapshot.cpuCoreStates.size) {
                val stepJiffies = snapshot.cpuCoreStates[i].list
                for (item in stepJiffies) {
                    jiffySum += item.get()
                }
            }
            var powerMah = 0.0
            for (i in 0 until snapshot.cpuCoreStates.size) {
                val stepJiffies = snapshot.cpuCoreStates[i].list
                for (j in 0 until stepJiffies.size) {
                    val jiffy = stepJiffies[j].get()
                    val figuredCpuTimeMs = ((jiffy * 1.0f / jiffySum) * cpuTimeMs).toLong()
                    val clusterNum = powerProfile.getClusterByCpuNum(i)
                    if (clusterNum >= 0 && clusterNum < powerProfile.getNumCpuClusters()) {
                        val powerMa = powerProfile.getAveragePowerForCpuCore(clusterNum, j)
                        powerMah += UsageBasedPowerEstimator(powerMa).calculatePower(figuredCpuTimeMs)
                    }
                }
            }
            return powerMah
        }
        return 0.0
    }

    /**
     * WIP
     * Memory TimeStats support needed, see "com.android.internal.os.KernelMemoryBandwidthStats"
     *
     * @see com.android.internal.os.MemoryPowerCalculator
     */
    @JvmStatic
    fun calcMemoryPower(powerProfile: PowerProfile): Double {
        var power = 0.0
        val numBuckets = powerProfile.getNumElements(PowerProfile.POWER_MEMORY)
        for (i in 0 until numBuckets) {
            val timeMs = 0L
            power += UsageBasedPowerEstimator(powerProfile.getAveragePower(PowerProfile.POWER_MEMORY, i)).calculatePower(timeMs)
        }
        return power
    }

    /**
     * @see com.android.internal.os.WakelockPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcWakelocksPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val stats = healthStats ?: return 0.0
        var power = 0.0
        if (stats.hasTimers(UidHealthStats.TIMERS_WAKELOCKS_PARTIAL)) {
            val timers: Map<String, TimerStat> = stats.getTimers(UidHealthStats.TIMERS_WAKELOCKS_PARTIAL)
            var timeMs = 0L
            for (item in timers.values) {
                timeMs += item.time
            }
            val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_CPU_IDLE)
            power = UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        }
        return power
    }

    /**
     * @see com.android.internal.os.MobileRadioPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcMobilePower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        var power = calcMobilePowerByRadioActive(powerProfile, healthStats)
        if (power > 0) {
            TraceHarborLog.i(TAG, "estimate Mobile by radioActive")
            return power
        }
        // power = calcMobilePowerByController(powerProfile, healthStats);
        // if (power > 0) {
        //     TraceHarborLog.i(TAG, "estimate Mobile by controller");
        //     return power;
        // }
        return 0.0
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    @VisibleForTesting
    fun calcMobilePowerByRadioActive(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val timeMs = getTimerTime(healthStats, UidHealthStats.TIMER_MOBILE_RADIO_ACTIVE) / 1000
        var powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_RADIO_ACTIVE)
        if (powerMa <= 0) {
            var sum = 0.0
            sum += powerProfile.getAveragePower(PowerProfile.POWER_MODEM_CONTROLLER_RX)
            val num = powerProfile.getNumElements(PowerProfile.POWER_MODEM_CONTROLLER_TX)
            for (i in 0 until num) {
                sum += powerProfile.getAveragePower(PowerProfile.POWER_MODEM_CONTROLLER_TX, i)
            }
            powerMa = sum / (num + 1)
        }
        return UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    @VisibleForTesting
    fun calcMobilePowerByController(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        var power = 0.0
        run {
            val timeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_MOBILE_IDLE_MS)
            val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_MODEM_CONTROLLER_IDLE)
            power += UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        }
        run {
            val timeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_MOBILE_RX_MS)
            val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_MODEM_CONTROLLER_RX)
            power += UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        }
        run {
            val timeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_MOBILE_TX_MS)
            val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_MODEM_CONTROLLER_TX)
            power += UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        }
        return power
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    @VisibleForTesting
    fun calcMobilePowerByPackets(powerProfile: PowerProfile, healthStats: HealthStats?, rxBps: Double, txBps: Double): Double {
        var power = 0.0
        run {
            var powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_RADIO_ACTIVE)
            if (powerMa <= 0) {
                var sum = 0.0
                sum += powerProfile.getAveragePower(PowerProfile.POWER_MODEM_CONTROLLER_RX)
                val num = powerProfile.getNumElements(PowerProfile.POWER_MODEM_CONTROLLER_TX)
                for (i in 0 until num) {
                    sum += powerProfile.getAveragePower(PowerProfile.POWER_MODEM_CONTROLLER_TX, i)
                }
                powerMa = sum / (num + 1)
            }
            val mobileBps = rxBps + txBps
            val powerPs = powerMa / 3600
            val mobilePps = mobileBps / 8 / 2048
            val powerMaPerPacket = (powerPs / mobilePps) / (60 * 60)
            val packets = getMeasure(healthStats, UidHealthStats.MEASUREMENT_MOBILE_RX_PACKETS) +
                getMeasure(healthStats, UidHealthStats.MEASUREMENT_MOBILE_TX_PACKETS)
            power += powerMaPerPacket * packets
        }
        return power
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @VisibleForTesting
    fun calcMobilePowerByNetworkStatBytes(
        powerProfile: PowerProfile,
        snapshot: RadioStatSnapshot,
        rxBps: Double,
        txBps: Double,
    ): Double {
        val rxMs = ((snapshot.mobileRxBytes.get() / (rxBps / 8)) * 1000).toLong()
        val txMs = ((snapshot.mobileTxBytes.get() / (txBps / 8)) * 1000).toLong()
        var power = 0.0
        run {
            val avgPower = powerProfile.getAveragePowerUni(PowerProfile.POWER_MODEM_CONTROLLER_RX)
            val estimator = UsageBasedPowerEstimator(avgPower)
            power += estimator.calculatePower(rxMs)
        }
        run {
            val avgPower = powerProfile.getAveragePowerUni(PowerProfile.POWER_MODEM_CONTROLLER_TX)
            val estimator = UsageBasedPowerEstimator(avgPower)
            power += estimator.calculatePower(txMs)
        }
        run {
            val avgPower = powerProfile.getAveragePowerUni(PowerProfile.POWER_MODEM_CONTROLLER_IDLE)
            val estimator = UsageBasedPowerEstimator(avgPower)
            power += estimator.calculatePower(txMs + rxMs)
        }
        return power
    }

    @JvmStatic
    @VisibleForTesting
    fun calcMobilePowerByNetworkStatPackets(
        powerProfile: PowerProfile,
        snapshot: RadioStatSnapshot,
        rxBps: Double,
        txBps: Double,
    ): Double {
        var power = 0.0
        run {
            var powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_RADIO_ACTIVE)
            if (powerMa <= 0) {
                var sum = 0.0
                sum += powerProfile.getAveragePower(PowerProfile.POWER_MODEM_CONTROLLER_RX)
                val num = powerProfile.getNumElements(PowerProfile.POWER_MODEM_CONTROLLER_TX)
                for (i in 0 until num) {
                    sum += powerProfile.getAveragePower(PowerProfile.POWER_MODEM_CONTROLLER_TX, i)
                }
                powerMa = sum / (num + 1)
            }
            val mobileBps = rxBps + txBps
            val powerPs = powerMa / 3600
            val mobilePps = mobileBps / 8 / 2048
            val powerMaPerPacket = (powerPs / mobilePps) / (60 * 60)
            val packets = snapshot.mobileRxPackets.get() + snapshot.mobileTxPackets.get()
            power += powerMaPerPacket * packets
        }
        return power
    }

    /**
     * @see com.android.internal.os.WifiPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcWifiPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val power = calcWifiPowerByController(powerProfile, healthStats)
        if (power > 0) {
            TraceHarborLog.i(TAG, "estimate WIFI by controller")
            return power
        }
        // power = calcWifiPowerByPackets(powerProfile, healthStats, 500000, 500000);
        // if (power > 0) {
        //     TraceHarborLog.i(TAG, "estimate WIFI by packets");
        //     return power;
        // }
        return 0.0
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    @VisibleForTesting
    fun calcWifiPowerByController(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        var power = 0.0
        run {
            val wifiIdlePower = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_CONTROLLER_IDLE)
            val idleMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_WIFI_IDLE_MS)
            val etmWifiIdlePower = UsageBasedPowerEstimator(wifiIdlePower)
            power += etmWifiIdlePower.calculatePower(idleMs)
        }
        run {
            val wifiRxPower = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_CONTROLLER_RX)
            val etmWifiRxPower = UsageBasedPowerEstimator(wifiRxPower)
            val rxMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_WIFI_RX_MS)
            power += etmWifiRxPower.calculatePower(rxMs)
        }
        run {
            val wifiTxPower = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_CONTROLLER_TX)
            val txMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_WIFI_TX_MS)
            val etmWifiTxPower = UsageBasedPowerEstimator(wifiTxPower)
            power += etmWifiTxPower.calculatePower(txMs)
        }
        return power
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    @VisibleForTesting
    fun calcWifiPowerByPackets(powerProfile: PowerProfile, healthStats: HealthStats?, rxBps: Double, txBps: Double): Double {
        var power = 0.0
        if (rxBps >= 0 && txBps >= 0) {
            if (rxBps == 0.0 && txBps == 0.0) {
                return power
            }
            run {
                val wifiBps = rxBps + txBps
                val averageWifiActivePower = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_ACTIVE) / 3600
                val powerMaPerPacket = averageWifiActivePower / (wifiBps / 8 / 2048)
                val packets = getMeasure(healthStats, UidHealthStats.MEASUREMENT_WIFI_RX_PACKETS) +
                    getMeasure(healthStats, UidHealthStats.MEASUREMENT_WIFI_TX_PACKETS)
                power += powerMaPerPacket * packets
            }
            run {
                val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_ON)
                val timeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_WIFI_RUNNING_MS)
                power += UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
            }
            run {
                val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_SCAN)
                val timeMs = getTimerTime(healthStats, UidHealthStats.TIMER_WIFI_SCAN)
                power += UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
            }
        }
        return power
    }

    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @VisibleForTesting
    fun calcWifiPowerByNetworkStatBytes(
        powerProfile: PowerProfile,
        snapshot: RadioStatSnapshot,
        rxBps: Double,
        txBps: Double,
    ): Double {
        val rxMs = ((snapshot.wifiRxBytes.get() / (rxBps / 8)) * 1000).toLong()
        val txMs = ((snapshot.wifiTxBytes.get() / (txBps / 8)) * 1000).toLong()
        var power = 0.0
        run {
            val avgPower = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_CONTROLLER_RX)
            val estimator = UsageBasedPowerEstimator(avgPower)
            power += estimator.calculatePower(rxMs)
        }
        run {
            val avgPower = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_CONTROLLER_TX)
            val estimator = UsageBasedPowerEstimator(avgPower)
            power += estimator.calculatePower(txMs)
        }
        run {
            val avgPower = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_CONTROLLER_IDLE)
            val estimator = UsageBasedPowerEstimator(avgPower)
            power += estimator.calculatePower(txMs + rxMs)
        }
        return power
    }

    @JvmStatic
    @VisibleForTesting
    fun calcWifiPowerByNetworkStatPackets(powerProfile: PowerProfile, snapshot: RadioStatSnapshot, rxBps: Double, txBps: Double): Double {
        var power = 0.0
        run {
            val wifiBps = rxBps + txBps
            val averageWifiActivePower = powerProfile.getAveragePowerUni(PowerProfile.POWER_WIFI_ACTIVE) / 3600
            val powerMaPerPacket = averageWifiActivePower / (wifiBps / 8 / 2048)
            val packets = snapshot.wifiRxPackets.get() + snapshot.wifiTxPackets.get()
            power += powerMaPerPacket * packets
        }
        return power
    }

    /**
     * @see com.android.internal.os.BluetoothPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcBlueToothPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        var power = 0.0
        run {
            val timeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_BLUETOOTH_IDLE_MS)
            val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_BLUETOOTH_CONTROLLER_IDLE)
            power += UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        }
        run {
            val timeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_BLUETOOTH_RX_MS)
            val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_BLUETOOTH_CONTROLLER_RX)
            power += UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        }
        run {
            val timeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_BLUETOOTH_TX_MS)
            val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_BLUETOOTH_CONTROLLER_TX)
            power += UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        }
        return if (power > 0) power else 0.0
    }

    /**
     * @see com.android.internal.os.GnssPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcGpsPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val timeMs = getTimerTime(healthStats, UidHealthStats.TIMER_GPS_SENSOR)
        var powerMa = 0.0
        if (timeMs > 0) {
            powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_GPS_ON)
            if (powerMa <= 0) {
                val num = powerProfile.getNumElements(PowerProfile.POWER_GPS_SIGNAL_QUALITY_BASED)
                var sumMa = 0.0
                for (i in 0 until num) {
                    sumMa += powerProfile.getAveragePower(PowerProfile.POWER_GPS_SIGNAL_QUALITY_BASED, i)
                }
                powerMa = sumMa / num
            }
        }
        val power = UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        return if (power > 0) power else 0.0
    }

    /**
     * @see com.android.internal.os.SensorPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcSensorsPower(context: Context, healthStats: HealthStats?): Double {
        val stats = healthStats ?: return 0.0
        var power = 0.0
        if (stats.hasTimers(UidHealthStats.TIMERS_SENSORS)) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
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

            val timers: Map<String, TimerStat> = stats.getTimers(UidHealthStats.TIMERS_SENSORS)
            for ((handle, timer) in timers) {
                val timeMs = timer.time
                if (handle == "-10000") {
                    continue
                }
                val sensor = sensorMap[handle]
                if (sensor != null) {
                    power += UsageBasedPowerEstimator(sensor.power.toDouble()).calculatePower(timeMs)
                }
            }
        }
        return if (power > 0) power else 0.0
    }

    /**
     * WIP
     * Calculate camera power usage.  Right now, this is a (very) rough estimate based on the
     * average power usage for a typical camera application.
     *
     * @see com.android.internal.os.CameraPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcCameraPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val timeMs = getTimerTime(healthStats, UidHealthStats.TIMER_CAMERA)
        val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_CAMERA)
        val power = UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        return if (power > 0) power else 0.0
    }

    /**
     * @see com.android.internal.os.FlashlightPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcFlashLightPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val timeMs = getTimerTime(healthStats, UidHealthStats.TIMER_FLASHLIGHT)
        val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_FLASHLIGHT)
        val power = UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        return if (power > 0) power else 0.0
    }

    /**
     * @see com.android.internal.os.MediaPowerCalculator
     * @see com.android.internal.os.AudioPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcAudioPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val timeMs = getTimerTime(healthStats, UidHealthStats.TIMER_AUDIO)
        var powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_AUDIO)
        if (powerMa == 0.0) {
            powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_AUDIO_DSP)
        }
        val power = UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        return if (power > 0) power else 0.0
    }

    /**
     * @see com.android.internal.os.MediaPowerCalculator
     * @see com.android.internal.os.VideoPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcVideoPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val timeMs = getTimerTime(healthStats, UidHealthStats.TIMER_VIDEO)
        var powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_VIDEO)
        if (powerMa == 0.0) {
            powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_VIDEO_DSP)
        }
        val power = UsageBasedPowerEstimator(powerMa).calculatePower(timeMs)
        return if (power > 0) power else 0.0
    }

    /**
     * @see com.android.internal.os.ScreenPowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcScreenPower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val topAppMs = getTimerTime(healthStats, UidHealthStats.TIMER_PROCESS_STATE_TOP_MS)
        val fgActivityMs = getTimerTime(healthStats, UidHealthStats.TIMER_FOREGROUND_ACTIVITY)
        val screenOnTimeMs = min(topAppMs, fgActivityMs)
        val powerMa = powerProfile.getAveragePowerUni(PowerProfile.POWER_SCREEN_ON)
        val power = UsageBasedPowerEstimator(powerMa).calculatePower(screenOnTimeMs)
        return if (power > 0) power else 0.0
    }

    /**
     * WIP
     * Binder cup time_in_state can not be collected right now
     *
     * @see com.android.internal.os.SystemServicePowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcSystemServicePower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val stats = healthStats ?: return 0.0
        var power = 0.0
        var timeMs = 0L
        if (stats.hasTimers(UidHealthStats.TIMERS_JOBS)) {
            val timers: Map<String, TimerStat> = stats.getTimers(UidHealthStats.TIMERS_JOBS)
            for (item in timers.values) {
                timeMs += item.time
            }
        }
        if (stats.hasTimers(UidHealthStats.TIMERS_SYNCS)) {
            val timers: Map<String, TimerStat> = stats.getTimers(UidHealthStats.TIMERS_SYNCS)
            for (item in timers.values) {
                timeMs += item.time
            }
        }

        power += estimateCpuActivePower(powerProfile, timeMs)
        val feat = BatteryCanary.getMonitorFeature(CpuStatFeature::class.java)
        if (feat != null && feat.isSupported) {
            val snapshot = feat.currentCpuStateSnapshot()
            if (snapshot != null) {
                power += estimateCpuClustersPower(powerProfile, snapshot, timeMs, false)
                power += estimateCpuCoresPower(powerProfile, snapshot, timeMs, false)
            }
        }
        return if (power > 0) power else 0.0
    }

    /**
     * @see com.android.internal.os.IdlePowerCalculator
     */
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun calcIdlePower(powerProfile: PowerProfile, healthStats: HealthStats?): Double {
        val batteryRealtimeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_REALTIME_BATTERY_MS)
        val batteryUptimeMs = getMeasure(healthStats, UidHealthStats.MEASUREMENT_UPTIME_BATTERY_MS)
        val suspendPowerMah = UsageBasedPowerEstimator(powerProfile.getAveragePowerUni(PowerProfile.POWER_CPU_SUSPEND)).calculatePower(batteryRealtimeMs)
        val idlePowerMah = UsageBasedPowerEstimator(powerProfile.getAveragePowerUni(PowerProfile.POWER_CPU_IDLE)).calculatePower(batteryUptimeMs)
        val power = suspendPowerMah + idlePowerMah
        return if (power > 0) power else 0.0
    }
}
