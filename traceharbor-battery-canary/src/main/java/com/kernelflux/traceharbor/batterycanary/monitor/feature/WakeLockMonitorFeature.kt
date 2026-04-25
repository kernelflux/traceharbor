package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.os.WorkSource
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Differ
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.BeanEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.ListEntry
import com.kernelflux.traceharbor.batterycanary.utils.PowerManagerServiceHooker
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.concurrent.ConcurrentHashMap

@Suppress("MemberVisibilityCanBePrivate", "NotNullFieldNotInitialized", "DEPRECATION")
class WakeLockMonitorFeature : AbsMonitorFeature() {
    interface WakeLockListener {
        @Deprecated("")
        fun onWakeLockTimeout(warningCount: Int, record: WakeLockTrace.WakeLockRecord)

        fun onWakeLockTimeout(record: WakeLockTrace.WakeLockRecord, backgroundMillis: Long)
    }

    @VisibleForTesting
    @JvmField
    var mOverTimeMillis: Long = 0L

    @JvmField
    val mWorkingWakeLocks: MutableMap<IBinder, WakeLockTrace> = ConcurrentHashMap(2)

    @JvmField
    val mWakeLockTracing: WakeLockTracing = WakeLockTracing()

    @JvmField
    var mListener: PowerManagerServiceHooker.IListener? = null

    private fun getListener(): WakeLockListener = mCore

    protected override fun getTag(): String = TAG

    override fun configure(monitor: BatteryMonitorCore) {
        super.configure(monitor)
        mOverTimeMillis = monitor.getConfig().wakelockTimeout
    }

    override fun onTurnOn() {
        super.onTurnOn()
        if (mCore.getConfig().isAmsHookEnabled) {
            mListener = object : PowerManagerServiceHooker.IListener {
                override fun onAcquireWakeLock(
                    token: IBinder,
                    flags: Int,
                    tag: String?,
                    packageName: String?,
                    workSource: WorkSource?,
                    historyTag: String?,
                ) {
                    val stack = if (shouldTracing(tag)) mCore.getConfig().callStackCollector.collectCurr() else ""
                    TraceHarborLog.i(
                        TAG,
                        "[onAcquireWakeLock] token=%s flags=%s tag=%s historyTag=%s packageName=%s workSource=%s stack=%s",
                        token.toString(),
                        flags,
                        tag,
                        historyTag,
                        packageName,
                        workSource,
                        stack,
                    )

                    // remove duplicated old trace
                    val existingTrace = mWorkingWakeLocks[token]
                    if (existingTrace != null) {
                        existingTrace.finish(mCore.getHandler())
                    }

                    val wakeLockTrace = WakeLockTrace(token, tag, flags, packageName, stack)
                    wakeLockTrace.setListener(
                        object : WakeLockTrace.OverTimeListener {
                            override fun onWakeLockOvertime(
                                warningCount: Int,
                                record: WakeLockTrace.WakeLockRecord,
                            ) {
                                getListener().onWakeLockTimeout(warningCount, record)
                                if (wakeLockTrace.isExpired()) {
                                    wakeLockTrace.finish(mCore.getHandler())
                                    val iterator = mWorkingWakeLocks.entries.iterator()
                                    while (iterator.hasNext()) {
                                        val next = iterator.next()
                                        if (next.value === wakeLockTrace) {
                                            iterator.remove()
                                            break
                                        }
                                    }
                                }
                            }
                        },
                    )
                    wakeLockTrace.start(mCore.getHandler(), mOverTimeMillis)
                    mWorkingWakeLocks[token] = wakeLockTrace

                    // dump tracing info
                    dumpTracingForTag(wakeLockTrace.record.tag)
                }

                override fun onReleaseWakeLock(token: IBinder, flags: Int) {
                    TraceHarborLog.i(TAG, "[onReleaseWakeLock] token=%s flags=%s", token.hashCode(), flags)
                    val iterator = mWorkingWakeLocks.entries.iterator()
                    var wakeLockTrace: WakeLockTrace? = null
                    while (iterator.hasNext()) {
                        val next = iterator.next()
                        if (next.key === token) {
                            wakeLockTrace = next.value
                            iterator.remove()
                            break
                        }
                    }

                    if (wakeLockTrace != null) {
                        wakeLockTrace.finish(mCore.getHandler())
                        mWakeLockTracing.add(wakeLockTrace.record)
                        val tag = wakeLockTrace.record.tag
                        val stack = if (shouldTracing(tag)) mCore.getConfig().callStackCollector.collectCurr() else ""
                        TraceHarborLog.i(TAG, "[onReleaseWakeLock] tag = $tag, stack = $stack")

                        // dump tracing info
                        dumpTracingForTag(tag)
                    } else {
                        TraceHarborLog.w(TAG, "missing tracking, token = $token")
                    }
                }
            }
            PowerManagerServiceHooker.addListener(mListener)
        }
    }

    override fun onTurnOff() {
        super.onTurnOff()
        PowerManagerServiceHooker.removeListener(mListener)
        mCore.getHandler().removeCallbacksAndMessages(null)
        mWorkingWakeLocks.clear()
        mWakeLockTracing.onClear()
    }

    override fun onBackgroundCheck(duringMillis: Long) {
        super.onBackgroundCheck(duringMillis)
        if (mWorkingWakeLocks.isNotEmpty()) {
            for (item in mWorkingWakeLocks.values) {
                if (!item.isFinished()) {
                    if (shouldTracing(item.record.tag)) {
                        // wakelock not released in background
                        getListener().onWakeLockTimeout(item.record, duringMillis)
                    }
                }
            }
        }
    }

    override fun weight(): Int = Int.MIN_VALUE

    @VisibleForTesting
    fun onAcquireWakeLock(
        token: IBinder,
        flags: Int,
        tag: String?,
        packageName: String?,
        workSource: WorkSource?,
        historyTag: String?,
    ) {
        mListener?.onAcquireWakeLock(token, flags, tag, packageName, workSource, historyTag)
    }

    @VisibleForTesting
    fun onReleaseWakeLock(token: IBinder, flags: Int) {
        mListener?.onReleaseWakeLock(token, flags)
    }

    private fun shouldTracing(tag: String?): Boolean {
        return shouldTracing() || !mCore.getConfig().tagWhiteList.contains(tag) || mCore.getConfig().tagBlackList.contains(tag)
    }

    private fun dumpTracingForTag(tag: String?) {
        if (mCore.getConfig().tagBlackList.contains(tag)) {
            // dump trace of wakelocks within blacklist
            TraceHarborLog.w(TAG, "dump wakelocks tracing for tag '$tag':")
            for (item in mWorkingWakeLocks.values) {
                if (item.record.tag.equals(tag, ignoreCase = true)) {
                    TraceHarborLog.w(TAG, " - " + item.record)
                }
            }
        }
    }

    fun getTracing(): WakeLockTracing = mWakeLockTracing

    fun currentWakeLocks(): WakeLockSnapshot = mWakeLockTracing.getSnapshot()

    open class WakeLockTrace internal constructor(
        @JvmField
        val token: IBinder,
        tag: String?,
        flags: Int,
        packageName: String?,
        stack: String?,
    ) {
        interface OverTimeListener {
            fun onWakeLockOvertime(warningCount: Int, record: WakeLockRecord)
        }

        @JvmField
        val record: WakeLockRecord = WakeLockRecord(tag, flags, packageName, stack)

        @JvmField
        var warningCount: Int = 0

        @JvmField
        var warningCountLimit: Int = 30

        private var loopTask: Runnable? = null
        private var mListener: OverTimeListener? = null

        fun attach(warningCountLimit: Int): WakeLockTrace {
            this.warningCountLimit = warningCountLimit
            return this
        }

        fun setListener(listener: OverTimeListener?) {
            mListener = listener
        }

        fun start(handler: Handler, intervalMillis: Long) {
            if (loopTask != null || isFinished()) {
                TraceHarborLog.w(TAG, "cant not start tracing of wakelock, target = $record")
                return
            }
            warningCount = 0
            val task = object : Runnable {
                override fun run() {
                    warningCount++
                    mListener?.onWakeLockOvertime(warningCount, record)
                    if (warningCount < warningCountLimit) {
                        handler.postDelayed(this, intervalMillis)
                    }
                }
            }
            loopTask = task
            handler.postDelayed(task, intervalMillis)
        }

        fun finish(handle: Handler) {
            val task = loopTask
            if (task != null) {
                handle.removeCallbacks(task)
                loopTask = null
            }
            record.finish()
        }

        fun isFinished(): Boolean = record.isFinished()

        fun isExpired(): Boolean = warningCount >= warningCountLimit

        override fun hashCode(): Int = token.hashCode()

        override fun equals(other: Any?): Boolean {
            if (other !is WakeLockTrace) return false
            return token == other
        }

        open class WakeLockRecord(
            @JvmField
            val tag: String?,
            @JvmField
            val flags: Int,
            @JvmField
            val packageName: String?,
            @JvmField
            val stack: String?,
        ) {
            @JvmField
            val timeBgn: Long = SystemClock.uptimeMillis()

            @JvmField
            var timeEnd: Long = 0L

            fun finish() {
                timeEnd = SystemClock.uptimeMillis()
            }

            fun isFinished(): Boolean = timeEnd >= timeBgn

            fun getLockingTimeMillis(): Long {
                val during = if (isFinished()) timeEnd - timeBgn else SystemClock.uptimeMillis() - timeBgn
                return if (during > 0L) during else 0L
            }

            override fun toString(): String {
                return "WakeLockRecord{" +
                    "flags=" + flags +
                    ", tag='" + tag + '\'' +
                    ", packageName='" + packageName + '\'' +
                    ", stack='" + stack + '\'' +
                    ", timeBgn=" + timeBgn +
                    ", timeEnd=" + timeEnd +
                    '}'
            }
        }
    }

    class WakeLockTracing {
        private val mLock = byteArrayOf()
        private var mCount = 0
        private var mMillis = 0L
        private var mTotalCount = 0
        private var mTracingCount = 0

        fun add(record: WakeLockTrace.WakeLockRecord) {
            synchronized(mLock) {
                mCount++
                mMillis += record.getLockingTimeMillis()
            }
        }

        fun onAcquire() {
            synchronized(mLock) {
                mTotalCount++
                mTracingCount++
            }
        }

        fun onRelease() {
            synchronized(mLock) {
                mTracingCount--
            }
        }

        fun getTotalCount(): Int = mCount

        fun getTimeMillis(): Long = mMillis

        fun onClear() {
            mCount = 0
            mMillis = 0
            mTotalCount = 0
            mTracingCount = 0
        }

        fun getSnapshot(): WakeLockSnapshot {
            val snapshot = WakeLockSnapshot()
            snapshot.totalWakeLockTime = DigitEntry.of(getTimeMillis())
            snapshot.totalWakeLockCount = DigitEntry.of(getTotalCount())
            snapshot.totalWakeLockRecords = ListEntry.ofEmpty()
            snapshot.totalAcquireCount = DigitEntry.of(mTotalCount)
            snapshot.totalReleaseCount = DigitEntry.of(mTracingCount)
            return snapshot
        }
    }

    open class WakeLockSnapshot internal constructor() : Snapshot<WakeLockSnapshot>() {
        @JvmField
        var totalWakeLockTime: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var totalWakeLockCount: DigitEntry<Int> = DigitEntry.of(0)

        @JvmField
        var totalWakeLockRecords: ListEntry<BeanEntry<WakeLockTrace.WakeLockRecord>> = ListEntry.ofEmpty()

        @JvmField
        var totalAcquireCount: DigitEntry<Int> = DigitEntry.of(0)

        @JvmField
        var totalReleaseCount: DigitEntry<Int> = DigitEntry.of(0)

        override fun diff(bgn: WakeLockSnapshot): Delta<WakeLockSnapshot> {
            return object : Delta<WakeLockSnapshot>(bgn, this) {
                override fun computeDelta(): WakeLockSnapshot {
                    val delta = WakeLockSnapshot()
                    delta.totalWakeLockTime = Differ.DigitDiffer.globalDiff(bgn.totalWakeLockTime, end.totalWakeLockTime)
                    delta.totalWakeLockCount = Differ.DigitDiffer.globalDiff(bgn.totalWakeLockCount, end.totalWakeLockCount)
                    delta.totalWakeLockRecords = Differ.ListDiffer.globalDiff(bgn.totalWakeLockRecords, end.totalWakeLockRecords)
                    delta.totalAcquireCount = Differ.DigitDiffer.globalDiff(bgn.totalAcquireCount, end.totalAcquireCount)
                    delta.totalReleaseCount = Differ.DigitDiffer.globalDiff(bgn.totalReleaseCount, end.totalReleaseCount)
                    return delta
                }
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.WakeLockMonitorFeature"
    }
}
