package com.kernelflux.traceharbor.batterycanary;

import android.app.Application;

import com.kernelflux.traceharbor.TraceHarbor;
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorConfig;
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore;
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner;
import com.kernelflux.traceharbor.plugin.Plugin;
import com.kernelflux.traceharbor.plugin.PluginListener;
import com.kernelflux.traceharbor.util.TraceHarborLog;
import com.kernelflux.traceharbor.util.TraceHarborUtil;

public class BatteryMonitorPlugin extends Plugin {
    private static final String TAG = "TraceHarbor.battery.BatteryMonitorPlugin";

    final BatteryMonitorCore mDelegate;
    private static String sPackageName = null;
    private static String sProcessName = null;

    public BatteryMonitorPlugin(BatteryMonitorConfig config) {
        mDelegate = new BatteryMonitorCore(config);
        TraceHarborLog.i(TAG, "setUp battery monitor plugin with configs: " + config);
    }

    public BatteryMonitorCore core() {
        return mDelegate;
    }

    @Override
    public void init(Application app, PluginListener listener) {
        super.init(app, listener);
        if (!mDelegate.getConfig().isBuiltinForegroundNotifyEnabled) {
            ProcessUILifecycleOwner.INSTANCE.removeListener(this);
        }
    }

    @Override
    public String getTag() {
        return "BatteryMonitorPlugin";
    }

    @Override
    public void start() {
        super.start();
        mDelegate.start();
    }

    @Override
    public void stop() {
        super.stop();
        mDelegate.stop();
    }

    @Override
    public void onForeground(boolean isForeground) {
        mDelegate.onForeground(isForeground);
    }

    @Override
    public boolean isForeground() {
        return mDelegate.isForeground();
    }

    public String getProcessName() {
        if (sProcessName == null) {
            synchronized (this) {
                if (sProcessName == null) {
                    Application application = getApplication();
                    if (application == null) {
                        if (!TraceHarbor.isInstalled()) {
                            throw new IllegalStateException(getTag() + " is not yet init!");
                        }
                        application = TraceHarbor.with().getApplication();
                    }
                    sProcessName = TraceHarborUtil.getProcessName(application);
                }
            }
        }
        return sProcessName;
    }

    public String getPackageName() {
        if (sPackageName == null) {
            synchronized (this) {
                if (sPackageName == null) {
                    Application application = getApplication();
                    if (application == null) {
                        if (!TraceHarbor.isInstalled()) {
                            throw new IllegalStateException(getTag() + " is not yet init!");
                        }
                        application = TraceHarbor.with().getApplication();
                    }
                    sPackageName = application.getPackageName();
                }
            }
        }
        return sPackageName;
    }
}
