package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.text.TextUtils
import androidx.annotation.NonNull
import com.kernelflux.traceharbor.batterycanary.utils.LocationManagerServiceHooker
import com.kernelflux.traceharbor.util.TraceHarborLog

class LocationMonitorFeature : AbsMonitorFeature() {
    @JvmField
    val mTracing = LocationTracing()

    @JvmField
    var mListener: LocationManagerServiceHooker.IListener? = null

    override fun getTag(): String = TAG

    override fun onTurnOn() {
        super.onTurnOn()
        if (core.getConfig().isAmsHookEnabled) {
            mListener = object : LocationManagerServiceHooker.IListener {
                override fun onRequestLocationUpdates(minTimeMillis: Long, minDistance: Float) {
                    val stack = if (shouldTracing()) core.getConfig().callStackCollector.collectCurr() else ""
                    TraceHarborLog.i(
                        TAG,
                        "#onRequestLocationUpdates, time = $minTimeMillis, distance = $minDistance, stack = $stack"
                    )
                    mTracing.setStack(stack)
                    mTracing.onStartScan()
                }
            }
            LocationManagerServiceHooker.addListener(mListener)
        }
    }

    override fun onTurnOff() {
        super.onTurnOff()
        LocationManagerServiceHooker.removeListener(mListener)
        mTracing.onClear()
    }

    override fun weight(): Int = Int.MIN_VALUE

    @NonNull
    fun getTracing(): LocationTracing = mTracing

    fun currentSnapshot(): LocationSnapshot = mTracing.getSnapshot()

    class LocationTracing {
        private var mScanCount = 0
        private var mLastConfiguredStack = ""

        fun setStack(stack: String?) {
            if (!TextUtils.isEmpty(stack)) {
                mLastConfiguredStack = stack!!
            }
        }

        fun onStartScan() {
            mScanCount++
        }

        fun onClear() {
            mScanCount = 0
        }

        fun getSnapshot(): LocationSnapshot {
            val snapshot = LocationSnapshot()
            snapshot.scanCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mScanCount)
            snapshot.stack = mLastConfiguredStack
            return snapshot
        }
    }

    class LocationSnapshot : MonitorFeature.Snapshot<LocationSnapshot>() {
        @JvmField
        var scanCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var stack: String = ""

        override fun diff(bgn: LocationSnapshot): MonitorFeature.Snapshot.Delta<LocationSnapshot> {
            return object : MonitorFeature.Snapshot.Delta<LocationSnapshot>(bgn, this) {
                override fun computeDelta(): LocationSnapshot {
                    val snapshot = LocationSnapshot()
                    snapshot.scanCount = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.scanCount, end.scanCount)
                    snapshot.stack = end.stack
                    return snapshot
                }
            }
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.LocationMonitorFeature"
    }
}

