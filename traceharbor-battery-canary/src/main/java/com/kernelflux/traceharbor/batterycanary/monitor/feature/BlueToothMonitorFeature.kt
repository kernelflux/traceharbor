package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.bluetooth.le.ScanSettings
import android.os.Build
import android.text.TextUtils
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorConfig
import com.kernelflux.traceharbor.batterycanary.utils.BluetoothManagerServiceHooker
import com.kernelflux.traceharbor.util.TraceHarborLog

class BlueToothMonitorFeature : AbsMonitorFeature() {
    @JvmField
    val mTracing = BlueToothTracing()

    @JvmField
    var mListener: BluetoothManagerServiceHooker.IListener? = null

    override fun getTag(): String = TAG

    override fun onTurnOn() {
        super.onTurnOn()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            TraceHarborLog.w(TAG, "only support >= android 8.0 for the moment")
            return
        }
        if (core.getConfig().isAmsHookEnabled ||
            ((core.getConfig().amsHookEnableFlag and BatteryMonitorConfig.AMS_HOOK_FLAG_BT) == BatteryMonitorConfig.AMS_HOOK_FLAG_BT)
        ) {
            mListener = object : BluetoothManagerServiceHooker.IListener {
                override fun onRegisterScanner() {
                    val stack = if (shouldTracing()) core.getConfig().callStackCollector.collectCurr() else ""
                    TraceHarborLog.i(TAG, "#onRegisterScanner, stack = $stack")
                    mTracing.setStack(stack)
                    mTracing.onRegisterScanner()
                }

                override fun onStartDiscovery() {
                    val stack = if (shouldTracing()) core.getConfig().callStackCollector.collectCurr() else ""
                    TraceHarborLog.i(TAG, "#onStartDiscovery, stack = $stack")
                    mTracing.setStack(stack)
                    mTracing.onStartDiscovery()
                }

                override fun onStartScan(scanId: Int, @Nullable scanSettings: ScanSettings?) {
                    // callback from H handler
                    TraceHarborLog.i(TAG, "#onStartScan, id = $scanId")
                    mTracing.onStartScan()
                }

                override fun onStartScanForIntent(@Nullable scanSettings: ScanSettings?) {
                    // callback from H handler
                    TraceHarborLog.i(TAG, "#onStartScanForIntent")
                    mTracing.onStartScan()
                }
            }
            BluetoothManagerServiceHooker.addListener(mListener)
        }
    }

    override fun onTurnOff() {
        super.onTurnOff()
        BluetoothManagerServiceHooker.removeListener(mListener)
        mTracing.onClear()
    }

    override fun weight(): Int = Int.MIN_VALUE

    @NonNull
    fun getTracing(): BlueToothTracing = mTracing

    fun currentSnapshot(): BlueToothSnapshot = mTracing.getSnapshot()

    class BlueToothTracing {
        private var mRegsCount = 0
        private var mDiscCount = 0
        private var mScanCount = 0
        private var mLastConfiguredStack = ""

        fun setStack(stack: String?) {
            if (!TextUtils.isEmpty(stack)) {
                mLastConfiguredStack = stack!!
            }
        }

        fun onRegisterScanner() {
            mRegsCount++
        }

        fun onStartDiscovery() {
            mDiscCount++
        }

        fun onStartScan() {
            mScanCount++
        }

        fun onClear() {
            mRegsCount = 0
            mDiscCount = 0
            mScanCount = 0
        }

        fun getSnapshot(): BlueToothSnapshot {
            val snapshot = BlueToothSnapshot()
            snapshot.regsCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mRegsCount)
            snapshot.discCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mDiscCount)
            snapshot.scanCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mScanCount)
            snapshot.stack = mLastConfiguredStack
            return snapshot
        }
    }

    class BlueToothSnapshot : MonitorFeature.Snapshot<BlueToothSnapshot>() {
        @JvmField
        var regsCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var discCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var scanCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var stack: String = ""

        override fun diff(bgn: BlueToothSnapshot): MonitorFeature.Snapshot.Delta<BlueToothSnapshot> {
            return object : MonitorFeature.Snapshot.Delta<BlueToothSnapshot>(bgn, this) {
                override fun computeDelta(): BlueToothSnapshot {
                    val snapshot = BlueToothSnapshot()
                    snapshot.regsCount = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.regsCount, end.regsCount)
                    snapshot.discCount = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.discCount, end.discCount)
                    snapshot.scanCount = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.scanCount, end.scanCount)
                    snapshot.stack = end.stack
                    return snapshot
                }
            }
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.BlueToothMonitorFeature"
    }
}

