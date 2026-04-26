package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.kernelflux.traceharbor.batterycanary.BatteryEventDelegate
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Differ
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.ListEntry
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.Consumer
import com.kernelflux.traceharbor.batterycanary.utils.TimeBreaker
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Collections

/**
 * Device Status Monitoring:
 * - CpuFreq
 * - Battery Status
 * - Temperatures
 *
 * @author Kaede
 * @since 2020/11/1
 */
@Suppress("MemberVisibilityCanBePrivate", "NotNullFieldNotInitialized", "DEPRECATION")
class DeviceStatMonitorFeature : AbsMonitorFeature() {
    private lateinit var mDevStatListener: DevStatListener

    @JvmField
    var mStampList: MutableList<TimeBreaker.Stamp> = EMPTY_STAMP_LIST

    @JvmField
    val coolingTask: Runnable = Runnable {
        if (mStampList.size >= core.getConfig().overHeatCount) {
            synchronized(TAG) {
                TimeBreaker.gcList(mStampList)
            }
        }
    }

    protected override fun getTag(): String = TAG

    override fun configure(monitor: BatteryMonitorCore) {
        super.configure(monitor)
        mDevStatListener = DevStatListener()
    }

    @SuppressLint("VisibleForTests")
    override fun onTurnOn() {
        super.onTurnOn()
        val deviceStat = BatteryCanaryUtil.getDeviceStat(core.getContext())
        val firstStamp = TimeBreaker.Stamp(deviceStat.toString())
        synchronized(TAG) {
            mStampList = ArrayList()
            mStampList.add(0, firstStamp)
        }

        mDevStatListener.setListener(Consumer { integer -> onStatDevStat(integer) })

        if (!mDevStatListener.isListening()) {
            mDevStatListener.startListen(core.getContext())
        }
    }

    fun onStatDevStat(devStat: Int) {
        BatteryCanaryUtil.getProxy().updateDevStat(devStat)
        synchronized(TAG) {
            if (mStampList !== EMPTY_STAMP_LIST) {
                TraceHarborLog.i(BatteryEventDelegate.TAG, "onStat >> " + BatteryCanaryUtil.convertDevStat(devStat))
                mStampList.add(0, TimeBreaker.Stamp(devStat.toString()))
                checkOverHeat()
            }
        }
    }

    override fun onTurnOff() {
        super.onTurnOff()
        mDevStatListener.stopListen()
    }

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
    }

    private fun checkOverHeat() {
        core.getHandler().removeCallbacks(coolingTask)
        core.getHandler().postDelayed(coolingTask, 1000L)
    }

    override fun weight(): Int = Int.MAX_VALUE

    fun currentCpuFreq(): CpuFreqSnapshot {
        return try {
            val cpuFreqs = BatteryCanaryUtil.getCpuCurrentFreq()
            currentCpuFreq(cpuFreqs)
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "#currentCpuFreq error")
            currentCpuFreq(intArrayOf())
        }
    }

    fun currentCpuFreq(cpuFreqs: IntArray): CpuFreqSnapshot {
        val snapshot = CpuFreqSnapshot()
        snapshot.cpuFreqs = ListEntry.ofDigits(cpuFreqs)
        return snapshot
    }

    fun currentBatteryTemperature(context: Context): BatteryTmpSnapshot {
        val snapshot = BatteryTmpSnapshot()
        snapshot.temp = DigitEntry.of(core.getCurrentBatteryTemperature(context))
        return snapshot
    }

    fun currentDevStatSnapshot(): DevStatSnapshot = currentDevStatSnapshot(0L)

    fun currentDevStatSnapshot(windowMillis: Long): DevStatSnapshot {
        return try {
            val timePortions = TimeBreaker.configurePortions(
                mStampList,
                windowMillis,
                10L
            ) {
                val devStat = BatteryCanaryUtil.getDeviceStat(core.getContext())
                TimeBreaker.Stamp(devStat.toString())
            }
            val snapshot = DevStatSnapshot()
            snapshot.setValid(timePortions.isValid)
            snapshot.uptime = DigitEntry.of(timePortions.totalUptime)
            snapshot.chargingRatio = DigitEntry.of(timePortions.getRatio("1").toLong())
            snapshot.unChargingRatio = DigitEntry.of(timePortions.getRatio("2").toLong())
            snapshot.screenOff = DigitEntry.of(timePortions.getRatio("3").toLong())
            snapshot.lowEnergyRatio = DigitEntry.of(timePortions.getRatio("4").toLong())
            snapshot
        } catch (e: Throwable) {
            TraceHarborLog.w(TAG, "configureSnapshot fail: " + e.message)
            val snapshot = DevStatSnapshot()
            snapshot.setValid(false)
            snapshot
        }
    }

    fun getStampList(): List<TimeBreaker.Stamp> {
        if (mStampList.isEmpty()) return Collections.emptyList()
        return ArrayList(mStampList)
    }

    fun currentThermalStat(context: Context): ThermalStatSnapshot {
        val snapshot = ThermalStatSnapshot()
        snapshot.stat = DigitEntry.of(BatteryCanaryUtil.getThermalStat(context))
        return snapshot
    }

    fun currentThermalHeadroom(context: Context, forecastSeconds: Int): ThermalHeadroomSnapshot {
        val snapshot = ThermalHeadroomSnapshot()
        snapshot.stat = DigitEntry.of(BatteryCanaryUtil.getThermalHeadroom(context, forecastSeconds))
        return snapshot
    }

    fun currentChargeWattage(context: Context): ChargeWattageSnapshot {
        val snapshot = ChargeWattageSnapshot()
        snapshot.stat = DigitEntry.of(BatteryCanaryUtil.getChargingWatt(context))
        return snapshot
    }

    fun currentBatteryCurrency(context: Context): BatteryCurrentSnapshot {
        val snapshot = BatteryCurrentSnapshot()
        snapshot.stat = DigitEntry.of(BatteryCanaryUtil.getBatteryCurrencyImmediately(context))
        return snapshot
    }

    class DevStatListener {
        @JvmField
        var mListener: Consumer<Int> = Consumer { }

        @JvmField
        var mIsCharging: Boolean = true

        @JvmField
        var mIsListening: Boolean = false

        private var mBatterStatListener: BatteryEventDelegate.Listener? = null

        fun setListener(listener: Consumer<Int>) {
            mListener = listener
        }

        fun isListening(): Boolean = mIsListening

        fun startListen(context: Context): Boolean {
            if (!mIsListening) {
                if (!BatteryEventDelegate.isInit()) {
                    throw IllegalStateException("BatteryEventDelegate is not yet init!")
                }

                val batteryStatListener = object : BatteryEventDelegate.Listener {
                    override fun onStateChanged(event: String?): Boolean {
                        when (event) {
                            Intent.ACTION_POWER_CONNECTED -> {
                                mIsCharging = true
                                mListener.accept(AppStats.DEV_STAT_CHARGING)
                            }
                            Intent.ACTION_POWER_DISCONNECTED -> {
                                mIsCharging = false
                                mListener.accept(AppStats.DEV_STAT_UN_CHARGING)
                            }
                            Intent.ACTION_SCREEN_ON -> {
                                if (!mIsCharging) {
                                    mListener.accept(AppStats.DEV_STAT_SCREEN_ON)
                                }
                            }
                            Intent.ACTION_SCREEN_OFF -> {
                                if (!mIsCharging) {
                                    mListener.accept(AppStats.DEV_STAT_SCREEN_OFF)
                                }
                            }
                            else -> {
                            }
                        }
                        return false
                    }

                    override fun onAppLowEnergy(
                        batteryState: BatteryEventDelegate.BatteryState,
                        backgroundMillis: Long
                    ): Boolean = false
                }
                mBatterStatListener = batteryStatListener

                mIsCharging = BatteryCanaryUtil.isDeviceCharging(context)
                BatteryEventDelegate.getInstance().addListener(batteryStatListener)
                mIsListening = true
            }
            return true
        }

        fun stopListen() {
            if (mIsListening) {
                try {
                    val batteryStatListener = mBatterStatListener
                    if (batteryStatListener != null && BatteryEventDelegate.isInit()) {
                        BatteryEventDelegate.getInstance().removeListener(batteryStatListener)
                    }
                } catch (ignored: Throwable) {
                }
                mIsListening = false
            }
        }
    }

    class CpuFreqSnapshot : Snapshot<CpuFreqSnapshot>() {
        @JvmField
        var cpuFreqs: ListEntry<DigitEntry<Int>> = ListEntry.ofEmpty()

        override fun diff(bgn: CpuFreqSnapshot): Delta<CpuFreqSnapshot> {
            return object : Delta<CpuFreqSnapshot>(bgn, this) {
                override fun computeDelta(): CpuFreqSnapshot {
                    val delta = CpuFreqSnapshot()
                    delta.cpuFreqs = Differ.ListDiffer.globalDiff(bgn.cpuFreqs, end.cpuFreqs)
                    return delta
                }
            }
        }
    }

    class BatteryTmpSnapshot : Snapshot<BatteryTmpSnapshot>() {
        @JvmField
        var temp: DigitEntry<Int> = DigitEntry.of(0)

        override fun diff(bgn: BatteryTmpSnapshot): Delta<BatteryTmpSnapshot> {
            return object : Delta<BatteryTmpSnapshot>(bgn, this) {
                override fun computeDelta(): BatteryTmpSnapshot {
                    val delta = BatteryTmpSnapshot()
                    delta.temp = Differ.DigitDiffer.globalDiff(bgn.temp, end.temp)
                    return delta
                }
            }
        }
    }

    class ThermalStatSnapshot : Snapshot<ThermalStatSnapshot>() {
        @JvmField
        var stat: DigitEntry<Int> = DigitEntry.of(0)

        override fun diff(bgn: ThermalStatSnapshot): Delta<ThermalStatSnapshot> {
            return object : Delta<ThermalStatSnapshot>(bgn, this) {
                override fun computeDelta(): ThermalStatSnapshot {
                    val delta = ThermalStatSnapshot()
                    delta.stat = Differ.DigitDiffer.globalDiff(bgn.stat, end.stat)
                    return delta
                }
            }
        }
    }

    class ThermalHeadroomSnapshot : Snapshot<ThermalHeadroomSnapshot>() {
        @JvmField
        var stat: DigitEntry<Float> = DigitEntry.of(0f)

        override fun diff(bgn: ThermalHeadroomSnapshot): Delta<ThermalHeadroomSnapshot> {
            return object : Delta<ThermalHeadroomSnapshot>(bgn, this) {
                override fun computeDelta(): ThermalHeadroomSnapshot {
                    val delta = ThermalHeadroomSnapshot()
                    delta.stat = Differ.DigitDiffer.globalDiff(bgn.stat, end.stat)
                    return delta
                }
            }
        }
    }

    class ChargeWattageSnapshot : Snapshot<ChargeWattageSnapshot>() {
        @JvmField
        var stat: DigitEntry<Int> = DigitEntry.of(0)

        override fun diff(bgn: ChargeWattageSnapshot): Delta<ChargeWattageSnapshot> {
            return object : Delta<ChargeWattageSnapshot>(bgn, this) {
                override fun computeDelta(): ChargeWattageSnapshot {
                    val delta = ChargeWattageSnapshot()
                    delta.stat = Differ.DigitDiffer.globalDiff(bgn.stat, end.stat)
                    return delta
                }
            }
        }
    }

    class BatteryCurrentSnapshot : Snapshot<BatteryCurrentSnapshot>() {
        @JvmField
        var stat: DigitEntry<Long> = DigitEntry.of(0L)

        override fun diff(bgn: BatteryCurrentSnapshot): Delta<BatteryCurrentSnapshot> {
            return object : Delta<BatteryCurrentSnapshot>(bgn, this) {
                override fun computeDelta(): BatteryCurrentSnapshot {
                    val delta = BatteryCurrentSnapshot()
                    delta.stat = Differ.DigitDiffer.globalDiff(bgn.stat, end.stat)
                    return delta
                }
            }
        }
    }

    class DevStatSnapshot internal constructor() : Snapshot<DevStatSnapshot>() {
        @JvmField
        var uptime: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var chargingRatio: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var unChargingRatio: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var screenOff: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var lowEnergyRatio: DigitEntry<Long> = DigitEntry.of(0L)

        override fun diff(bgn: DevStatSnapshot): Delta<DevStatSnapshot> {
            return object : Delta<DevStatSnapshot>(bgn, this) {
                override fun computeDelta(): DevStatSnapshot {
                    val delta = DevStatSnapshot()
                    delta.uptime = Differ.DigitDiffer.globalDiff(bgn.uptime, end.uptime)
                    delta.chargingRatio = Differ.DigitDiffer.globalDiff(bgn.chargingRatio, end.chargingRatio)
                    delta.unChargingRatio = Differ.DigitDiffer.globalDiff(bgn.unChargingRatio, end.unChargingRatio)
                    delta.screenOff = Differ.DigitDiffer.globalDiff(bgn.screenOff, end.screenOff)
                    delta.lowEnergyRatio = Differ.DigitDiffer.globalDiff(bgn.lowEnergyRatio, end.lowEnergyRatio)
                    return delta
                }
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.DeviceStatusMonitorFeature"
        private val EMPTY_STAMP_LIST: MutableList<TimeBreaker.Stamp> = Collections.emptyList()
    }
}
