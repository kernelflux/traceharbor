package com.kernelflux.traceharbor.batterycanary;

import com.kernelflux.traceharbor.TraceHarbor;
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore.Callback;
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature;
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot;
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature;
import com.kernelflux.traceharbor.batterycanary.utils.Consumer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * TraceHarbor Battery Canary Plugin Facades.
 *
 * @author Kaede
 * @since 2021/1/27
 */
final public class BatteryCanary {

    @Nullable
    public static <T extends MonitorFeature> T getMonitorFeature(Class<T> clazz) {
        if (TraceHarbor.isInstalled()) {
            BatteryMonitorPlugin plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin.class);
            if (plugin != null) {
                return plugin.core().getMonitorFeature(clazz);
            }
        }
        return null;
    }

    public static <T extends MonitorFeature> void getMonitorFeature(Class<T> clazz, Consumer<T> block) {
        if (TraceHarbor.isInstalled()) {
            BatteryMonitorPlugin plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin.class);
            if (plugin != null) {
                T feat = plugin.core().getMonitorFeature(clazz);
                if (feat != null) {
                    block.accept(feat);
                }
            }
        }
    }

    public static void currentJiffies(@NonNull final Callback<JiffiesSnapshot> callback) {
        if (TraceHarbor.isInstalled()) {
            BatteryMonitorPlugin plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin.class);
            if (plugin != null) {
                JiffiesMonitorFeature jiffiesFeat = plugin.core().getMonitorFeature(JiffiesMonitorFeature.class);
                if (jiffiesFeat != null) {
                    jiffiesFeat.currentJiffiesSnapshot(callback);
                }
            }
        }
    }

    public static void addBatteryStateListener(BatteryEventDelegate.Listener listener) {
        if (listener != null) {
            if (BatteryEventDelegate.isInit()) {
                BatteryEventDelegate.getInstance().addListener(listener);
            }
        }
    }

    public static void removeBatteryStateListener(BatteryEventDelegate.Listener listener) {
        if (listener != null) {
            if (BatteryEventDelegate.isInit()) {
                BatteryEventDelegate.getInstance().removeListener(listener);
            }
        }
    }
}
