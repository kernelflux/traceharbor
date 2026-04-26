package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.text.TextUtils
import androidx.annotation.NonNull
import com.kernelflux.traceharbor.batterycanary.utils.WifiManagerServiceHooker
import com.kernelflux.traceharbor.util.TraceHarborLog

class WifiMonitorFeature : AbsMonitorFeature() {
    @JvmField
    val mTracing = WifiTracing()

    @JvmField
    var mListener: WifiManagerServiceHooker.IListener? = null

    override fun getTag(): String = TAG

    override fun onTurnOn() {
        super.onTurnOn()
        if (core.getConfig().isAmsHookEnabled) {
            mListener = object : WifiManagerServiceHooker.IListener {
                override fun onStartScan() {
                    val stack = if (shouldTracing()) core.getConfig().callStackCollector.collectCurr() else ""
                    TraceHarborLog.i(TAG, "#onStartScan, stack = $stack")
                    mTracing.setStack(stack)
                    mTracing.onStartScan()
                }

                override fun onGetScanResults() {
                    val stack = if (shouldTracing()) core.getConfig().callStackCollector.collectCurr() else ""
                    TraceHarborLog.i(TAG, "#onGetScanResults, stack = $stack")
                    mTracing.setStack(stack)
                    mTracing.onGetScanResults()
                }
            }
            WifiManagerServiceHooker.addListener(mListener)
        }
    }

    override fun onTurnOff() {
        super.onTurnOff()
        WifiManagerServiceHooker.removeListener(mListener)
        mTracing.onClear()
    }

    override fun weight(): Int = Int.MIN_VALUE

    @NonNull
    fun getTracing(): WifiTracing = mTracing

    fun currentSnapshot(): WifiSnapshot = mTracing.getSnapshot()

    class WifiTracing {
        private var mScanCount = 0
        private var mQueryCount = 0
        private var mLastConfiguredStack = ""

        fun setStack(stack: String?) {
            if (!TextUtils.isEmpty(stack)) {
                mLastConfiguredStack = stack!!
            }
        }

        fun onStartScan() {
            mScanCount++
        }

        fun onGetScanResults() {
            mQueryCount++
        }

        fun onClear() {
            mScanCount = 0
            mQueryCount = 0
        }

        fun getSnapshot(): WifiSnapshot {
            val snapshot = WifiSnapshot()
            snapshot.scanCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mScanCount)
            snapshot.queryCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mQueryCount)
            snapshot.stack = mLastConfiguredStack
            return snapshot
        }
    }

    class WifiSnapshot : MonitorFeature.Snapshot<WifiSnapshot>() {
        @JvmField
        var scanCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var queryCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var stack: String = ""

        override fun diff(bgn: WifiSnapshot): MonitorFeature.Snapshot.Delta<WifiSnapshot> {
            return object : MonitorFeature.Snapshot.Delta<WifiSnapshot>(bgn, this) {
                override fun computeDelta(): WifiSnapshot {
                    val snapshot = WifiSnapshot()
                    snapshot.scanCount = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.scanCount, end.scanCount)
                    snapshot.queryCount = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.queryCount, end.queryCount)
                    snapshot.stack = end.stack
                    return snapshot
                }
            }
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.WifiMonitorFeature"
    }
}

