package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Handler
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.utils.AlarmManagerServiceHooker
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.util.TraceHarborLog

@Suppress("NotNullFieldNotInitialized")
class AlarmMonitorFeature : AbsMonitorFeature() {
    interface AlarmListener {
        fun onAlarmDuplicated(duplicatedCount: Int, record: AlarmRecord)
    }

    @JvmField
    @VisibleForTesting
    var handler: Handler? = null

    @JvmField
    val mAlarmTracing: AlarmTracing = AlarmTracing()

    @JvmField
    @Nullable
    var mListener: AlarmManagerServiceHooker.IListener? = null

    private fun getListener(): AlarmListener = core

    override fun getTag(): String = TAG

    override fun configure(monitor: BatteryMonitorCore) {
        super.configure(monitor)
        handler = monitor.getHandler()
    }

    override fun onTurnOn() {
        super.onTurnOn()
        if (core.getConfig().isAmsHookEnabled) {
            mListener = object : AlarmManagerServiceHooker.IListener {
                override fun onAlarmSet(
                    type: Int,
                    triggerAtMillis: Long,
                    windowMillis: Long,
                    intervalMillis: Long,
                    flags: Int,
                    operation: PendingIntent?,
                    onAlarmListener: AlarmManager.OnAlarmListener?,
                ) {
                    var stack = ""
                    if (core.getConfig().isStatAsSample) {
                        stack = BatteryCanaryUtil.polishStack(
                            Log.getStackTraceString(Throwable()),
                            "at android.app.AlarmManager",
                        )
                    }

                    val alarmRecord = AlarmRecord(type, triggerAtMillis, windowMillis, intervalMillis, flags, stack)
                    TraceHarborLog.i(TAG, "#onAlarmSet, target = $alarmRecord")

                    if (operation != null || onAlarmListener != null) {
                        val traceKey = operation?.hashCode() ?: onAlarmListener!!.hashCode()
                        mAlarmTracing.onSet(traceKey, alarmRecord)
                    }
                }

                override fun onAlarmRemove(operation: PendingIntent?, onAlarmListener: AlarmManager.OnAlarmListener?) {
                    if (operation != null || onAlarmListener != null) {
                        val traceKey = operation?.hashCode() ?: onAlarmListener!!.hashCode()
                        mAlarmTracing.onRemove(traceKey)
                    }
                }
            }
            AlarmManagerServiceHooker.addListener(mListener)
        }
    }

    override fun onTurnOff() {
        super.onTurnOff()
        AlarmManagerServiceHooker.removeListener(mListener)
        handler?.removeCallbacksAndMessages(null)
        mAlarmTracing.onClear()
    }

    override fun weight(): Int = Int.MIN_VALUE

    @VisibleForTesting
    fun onAlarmSet(
        type: Int,
        triggerAtMillis: Long,
        windowMillis: Long,
        intervalMillis: Long,
        flags: Int,
        operation: PendingIntent?,
        onAlarmListener: AlarmManager.OnAlarmListener?,
    ) {
        mListener?.onAlarmSet(type, triggerAtMillis, windowMillis, intervalMillis, flags, operation, onAlarmListener)
    }

    @VisibleForTesting
    fun onAlarmRemove(operation: PendingIntent?, onAlarmListener: AlarmManager.OnAlarmListener?) {
        mListener?.onAlarmRemove(operation, onAlarmListener)
    }

    @NonNull
    fun getTracing(): AlarmTracing = mAlarmTracing

    fun currentAlarms(): AlarmSnapshot = mAlarmTracing.getSnapshot()

    class AlarmRecord(
        @JvmField val type: Int,
        @JvmField val triggerAtMillis: Long,
        @JvmField val windowMillis: Long,
        @JvmField val intervalMillis: Long,
        @JvmField val flag: Int,
        @JvmField val stack: String,
    ) {
        @JvmField
        val timeBgn: Long = System.currentTimeMillis()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            return hashCode() == other.hashCode()
        }

        @NonNull
        override fun toString(): String {
            return "AlarmRecord{" +
                "type=$type" +
                ", triggerAtMillis=$triggerAtMillis" +
                ", windowMillis=$windowMillis" +
                ", intervalMillis=$intervalMillis" +
                ", flag=$flag" +
                ", timeBgn=$timeBgn" +
                ", stack='$stack'" +
                '}'
        }
    }

    class AlarmTracing {
        private val mLock = ByteArray(0)
        private var mTotalCount = 0
        private var mTracingCount = 0
        private var mDuplicatedGroups = 0
        private var mDuplicatedCounts = 0

        fun onSet(key: Int, record: AlarmRecord) {
            synchronized(mLock) {
                mTotalCount++
                mTracingCount++
            }
        }

        fun onSet() {
            synchronized(mLock) {
                mTotalCount++
                mTracingCount++
            }
        }

        fun onRemove(key: Int) {
            synchronized(mLock) {
                mTracingCount--
            }
        }

        fun onRemove() {
            synchronized(mLock) {
                mTracingCount--
            }
        }

        fun onClear() {
            mTotalCount = 0
            mTracingCount = 0
            mDuplicatedGroups = 0
            mDuplicatedCounts = 0
        }

        fun getSnapshot(): AlarmSnapshot {
            val snapshot = AlarmSnapshot()
            synchronized(mLock) {
                snapshot.totalCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mTotalCount)
                snapshot.tracingCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mTracingCount)
                snapshot.duplicatedGroup = MonitorFeature.Snapshot.Entry.DigitEntry.of(mDuplicatedGroups)
                snapshot.duplicatedCount = MonitorFeature.Snapshot.Entry.DigitEntry.of(mDuplicatedCounts)
                snapshot.records = MonitorFeature.Snapshot.Entry.ListEntry.ofEmpty()
            }
            return snapshot
        }
    }

    class AlarmSnapshot : MonitorFeature.Snapshot<AlarmSnapshot>() {
        @JvmField
        var totalCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var tracingCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var duplicatedGroup: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var duplicatedCount: MonitorFeature.Snapshot.Entry.DigitEntry<Int> = MonitorFeature.Snapshot.Entry.DigitEntry.of(0)

        @JvmField
        var records: MonitorFeature.Snapshot.Entry.ListEntry<MonitorFeature.Snapshot.Entry.BeanEntry<AlarmRecord>> =
            MonitorFeature.Snapshot.Entry.ListEntry.ofEmpty()

        override fun diff(bgn: AlarmSnapshot): MonitorFeature.Snapshot.Delta<AlarmSnapshot> {
            return object : MonitorFeature.Snapshot.Delta<AlarmSnapshot>(bgn, this) {
                override fun computeDelta(): AlarmSnapshot {
                    val snapshot = AlarmSnapshot()
                    snapshot.totalCount = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.totalCount, end.totalCount)
                    snapshot.tracingCount = MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.tracingCount, end.tracingCount)
                    snapshot.duplicatedGroup =
                        MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.duplicatedGroup, end.duplicatedGroup)
                    snapshot.duplicatedCount =
                        MonitorFeature.Snapshot.Differ.DigitDiffer.globalDiff(bgn.duplicatedCount, end.duplicatedCount)
                    snapshot.records = MonitorFeature.Snapshot.Differ.ListDiffer.globalDiff(bgn.records, end.records)
                    return snapshot
                }
            }
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.AlarmMonitorFeature"
    }
}

