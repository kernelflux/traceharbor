package io.traceharbor.batterycanary.monitor.feature;

import android.content.pm.ApplicationInfo;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import io.traceharbor.batterycanary.monitor.BatteryMonitorCore;
import io.traceharbor.util.TraceHarborLog;

/**
 * @author Kaede
 * @since 2020/12/24
 */
public abstract class AbsMonitorFeature implements MonitorFeature {
    private static final String TAG = "TraceHarbor.battery.MonitorFeature";

    protected String getTag() {
        return TAG;
    }

    @SuppressWarnings("NotNullFieldNotInitialized")
    @NonNull
    protected BatteryMonitorCore mCore;

    @CallSuper
    @Override
    public void configure(BatteryMonitorCore monitor) {
        TraceHarborLog.i(getTag(), "#configure");
        this.mCore = monitor;
    }

    @CallSuper
    @Override
    public void onTurnOn() {
        TraceHarborLog.i(getTag(), "#onTurnOn");
    }

    @CallSuper
    @Override
    public void onTurnOff() {
        TraceHarborLog.i(getTag(), "#onTurnOff");
    }

    @CallSuper
    @Override
    public void onForeground(boolean isForeground) {
        TraceHarborLog.i(getTag(), "#onForeground, foreground = " + isForeground);
    }

    @CallSuper
    @WorkerThread
    @Override
    public void onBackgroundCheck(long duringMillis) {
        TraceHarborLog.i(getTag(), "#onBackgroundCheck, since background started millis = " + duringMillis);
    }

    protected boolean shouldTracing() {
        if (mCore.getConfig().isAggressiveMode) return true;
        return  0 != (mCore.getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
    }

    @Override
    public String toString() {
        return getTag();
    }
}

