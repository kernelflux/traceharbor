package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.content.Context
import androidx.annotation.Nullable
import com.kernelflux.traceharbor.batterycanary.utils.RadioStatUtil

/**
 * Device Status Monitoring:
 * - CpuFreq
 * - Battery Status
 * - Temperatures
 *
 * @author Kaede
 * @since 2020/11/1
 */
@Suppress("NotNullFieldNotInitialized")
class TrafficMonitorFeature : AbsMonitorFeature() {
    override fun getTag(): String = TAG

    override fun weight(): Int = Int.MIN_VALUE

    @Nullable
    fun currentRadioSnapshot(context: Context): RadioStatSnapshot? {
        val stat = RadioStatUtil.getCurrentStat(context) ?: return null

        val snapshot = RadioStatSnapshot()
        snapshot.wifiRxBytes = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.wifiRxBytes)
        snapshot.wifiTxBytes = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.wifiTxBytes)
        snapshot.wifiRxPackets = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.wifiRxPackets)
        snapshot.wifiTxPackets = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.wifiTxPackets)

        snapshot.mobileRxBytes = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.mobileRxBytes)
        snapshot.mobileTxBytes = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.mobileTxBytes)
        snapshot.mobileRxPackets = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.mobileRxPackets)
        snapshot.mobileTxPackets = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.mobileTxPackets)
        return snapshot
    }

    @Nullable
    fun currentRadioBpsSnapshot(context: Context): RadioBpsSnapshot? {
        val stat = RadioStatUtil.getCurrentBps(context) ?: return null

        val snapshot = RadioBpsSnapshot()
        snapshot.wifiRxBps = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.wifiRxBps)
        snapshot.wifiTxBps = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.wifiTxBps)
        snapshot.mobileRxBps = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.mobileRxBps)
        snapshot.mobileTxBps = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.mobileTxBps)
        return snapshot
    }

    class RadioStatSnapshot : MonitorFeature.Snapshot<RadioStatSnapshot>() {
        @JvmField
        var wifiRxBytes: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var wifiTxBytes: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var wifiRxPackets: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var wifiTxPackets: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var mobileRxBytes: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var mobileTxBytes: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var mobileRxPackets: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var mobileTxPackets: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        override fun diff(bgn: RadioStatSnapshot): MonitorFeature.Snapshot.Delta<RadioStatSnapshot> {
            return object : MonitorFeature.Snapshot.Delta<RadioStatSnapshot>(bgn, this) {
                override fun computeDelta(): RadioStatSnapshot {
                    val delta = RadioStatSnapshot()
                    delta.wifiRxBytes = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.wifiRxBytes, end.wifiRxBytes)
                    delta.wifiTxBytes = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.wifiTxBytes, end.wifiTxBytes)
                    delta.wifiRxPackets = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.wifiRxPackets, end.wifiRxPackets)
                    delta.wifiTxPackets = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.wifiTxPackets, end.wifiTxPackets)

                    delta.mobileRxBytes = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.mobileRxBytes, end.mobileRxBytes)
                    delta.mobileTxBytes = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.mobileTxBytes, end.mobileTxBytes)
                    delta.mobileRxPackets = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.mobileRxPackets, end.mobileRxPackets)
                    delta.mobileTxPackets = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.mobileTxPackets, end.mobileTxPackets)
                    return delta
                }
            }
        }
    }

    class RadioBpsSnapshot : MonitorFeature.Snapshot<RadioBpsSnapshot>() {
        @JvmField
        var wifiRxBps: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var wifiTxBps: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var mobileRxBps: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        @JvmField
        var mobileTxBps: MonitorFeature.Snapshot.Entry.DigitEntry<Long> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0L)

        override fun diff(bgn: RadioBpsSnapshot): MonitorFeature.Snapshot.Delta<RadioBpsSnapshot> {
            return object : MonitorFeature.Snapshot.Delta<RadioBpsSnapshot>(bgn, this) {
                override fun computeDelta(): RadioBpsSnapshot {
                    val delta = RadioBpsSnapshot()
                    delta.wifiRxBps = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.wifiRxBps, end.wifiRxBps)
                    delta.wifiTxBps = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.wifiTxBps, end.wifiTxBps)
                    delta.mobileRxBps = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.mobileRxBps, end.mobileRxBps)
                    delta.mobileTxBps = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.mobileTxBps, end.mobileTxBps)
                    return delta
                }
            }
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.TrafficMonitorFeature"
    }
}

