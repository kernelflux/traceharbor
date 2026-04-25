package com.kernelflux.traceharbor.batterycanary.monitor.feature;

import android.os.Looper;
import android.os.Process;

import com.kernelflux.traceharbor.batterycanary.monitor.feature.AbsTaskMonitorFeature.TaskJiffiesSnapshot;
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta;
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil;
import com.kernelflux.traceharbor.batterycanary.utils.ProcStatUtil;
import com.kernelflux.traceharbor.util.TraceHarborLog;

import java.util.concurrent.Callable;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * TraceHarbor Internal Thread Status Monitoring.
 *
 * @author Kaede
 * @since 2020/11/1
 */
@SuppressWarnings("NotNullFieldNotInitialized")
public final class InternalMonitorFeature extends AbsMonitorFeature {
    private static final String TAG = "TraceHarbor.battery.InternalMonitorFeature";

    public interface InternalListener {
        void onReportInternalJiffies(Delta<TaskJiffiesSnapshot> delta); // Watching myself
    }

    @VisibleForTesting public int mWorkerTid = -1;
    @Nullable private TaskJiffiesSnapshot mLastInternalSnapshot;

    @Override
    public void onTurnOn() {
        super.onTurnOn();
        mCore.getHandler().post(new Runnable() {
            @Override
            public void run() {
                mWorkerTid = Process.myTid();
            }
        });
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    public int weight() {
        return Integer.MIN_VALUE;
    }

    @WorkerThread
    @Nullable
    public TaskJiffiesSnapshot configureMonitorConsuming() {
        if (Looper.myLooper() == Looper.getMainLooper() || Looper.myLooper() == mCore.getHandler().getLooper()) {
            throw new IllegalStateException("'#configureMonitorConsuming' should work within worker thread except matrix thread!");
        }

        if (mWorkerTid > 0) {
            TraceHarborLog.i(TAG, "#configureMonitorConsuming, tid = " + mWorkerTid);
            TaskJiffiesSnapshot snapshot = createSnapshot(mWorkerTid);
            if (snapshot != null) {
                if (mLastInternalSnapshot != null) {
                    Delta<TaskJiffiesSnapshot> delta = snapshot.diff(mLastInternalSnapshot);
                    mCore.getConfig().callback.onReportInternalJiffies(delta);
                }
                mLastInternalSnapshot = snapshot;
                return snapshot;
            }
        }
        return null;
    }

    @Nullable
    protected InternalSnapshot createSnapshot(int tid) {
        InternalSnapshot snapshot = new InternalSnapshot();
        snapshot.tid = tid;
        snapshot.appStat = BatteryCanaryUtil.getAppStat(mCore.getContext(), mCore.isForeground());
        snapshot.devStat = BatteryCanaryUtil.getDeviceStat(mCore.getContext());
        try {
            Callable<String> supplier = mCore.getConfig().onSceneSupplier;
            snapshot.scene = supplier == null ? "" : supplier.call();
        } catch (Exception ignored) {
            snapshot.scene = "";
        }

        ProcStatUtil.ProcStat stat = ProcStatUtil.of(Process.myPid(), tid);
        if (stat == null) {
            return null;
        }
        snapshot.jiffies = Snapshot.Entry.DigitEntry.of(stat.getJiffies());
        snapshot.name = stat.comm;
        return snapshot;
    }

    public static class InternalSnapshot extends TaskJiffiesSnapshot {
    }
}
