package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.os.Looper
import android.os.Process
import androidx.annotation.Nullable
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AbsTaskMonitorFeature.TaskJiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.ProcStatUtil
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * TraceHarbor Internal Thread Status Monitoring.
 *
 * @author Kaede
 * @since 2020/11/1
 */
@Suppress("NotNullFieldNotInitialized")
class InternalMonitorFeature : AbsMonitorFeature() {
    interface InternalListener {
        fun onReportInternalJiffies(delta: Delta<TaskJiffiesSnapshot>) // Watching myself
    }

    @VisibleForTesting
    @JvmField
    var mWorkerTid: Int = -1

    @Nullable
    private var mLastInternalSnapshot: TaskJiffiesSnapshot? = null

    override fun onTurnOn() {
        super.onTurnOn()
        core.getHandler().post { mWorkerTid = Process.myTid() }
    }

    override fun getTag(): String = TAG

    override fun weight(): Int = Int.MIN_VALUE

    @WorkerThread
    @Nullable
    fun configureMonitorConsuming(): TaskJiffiesSnapshot? {
        if (Looper.myLooper() == Looper.getMainLooper() || Looper.myLooper() == core.getHandler().looper) {
            throw IllegalStateException("'#configureMonitorConsuming' should work within worker thread except matrix thread!")
        }

        if (mWorkerTid > 0) {
            TraceHarborLog.i(TAG, "#configureMonitorConsuming, tid = $mWorkerTid")
            val snapshot = createSnapshot(mWorkerTid)
            if (snapshot != null) {
                val lastSnapshot = mLastInternalSnapshot
                if (lastSnapshot != null) {
                    val delta: Delta<TaskJiffiesSnapshot> = snapshot.diff(lastSnapshot)
                    core.getConfig().callback.onReportInternalJiffies(delta)
                }
                mLastInternalSnapshot = snapshot
                return snapshot
            }
        }
        return null
    }

    @Nullable
    protected fun createSnapshot(tid: Int): InternalSnapshot? {
        val snapshot = InternalSnapshot()
        snapshot.tid = tid
        snapshot.appStat = BatteryCanaryUtil.getAppStat(core.getContext(), core.isForeground())
        snapshot.devStat = BatteryCanaryUtil.getDeviceStat(core.getContext())
        try {
            val supplier = core.getConfig().onSceneSupplier
            snapshot.scene = supplier?.call().orEmpty()
        } catch (_: Exception) {
            snapshot.scene = ""
        }

        val stat = ProcStatUtil.of(Process.myPid(), tid) ?: return null
        snapshot.jiffies = MonitorFeature.Snapshot.Entry.DigitEntry.of(stat.jiffies)
        snapshot.name = stat.comm
        return snapshot
    }

    class InternalSnapshot : TaskJiffiesSnapshot()

    private companion object {
        private const val TAG = "TraceHarbor.battery.InternalMonitorFeature"
    }
}

