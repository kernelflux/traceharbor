package com.kernelflux.traceharbor.batterycanary

import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore.Callback
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature
import com.kernelflux.traceharbor.batterycanary.utils.Consumer

/**
 * TraceHarbor Battery Canary Plugin Facades.
 *
 * @author Kaede
 * @since 2021/1/27
 */
object BatteryCanary {

    @JvmStatic
    fun <T : MonitorFeature> getMonitorFeature(clazz: Class<T>): T? {
        if (TraceHarbor.isInstalled()) {
            val plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin::class.java)
            if (plugin != null) {
                return plugin.core().getMonitorFeature(clazz)
            }
        }
        return null
    }

    @JvmStatic
    fun <T : MonitorFeature> getMonitorFeature(clazz: Class<T>, block: Consumer<T>) {
        if (TraceHarbor.isInstalled()) {
            val plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin::class.java)
            if (plugin != null) {
                val feat = plugin.core().getMonitorFeature(clazz)
                if (feat != null) {
                    block.accept(feat)
                }
            }
        }
    }

    @JvmStatic
    fun currentJiffies(callback: Callback<JiffiesSnapshot>) {
        if (TraceHarbor.isInstalled()) {
            val plugin = TraceHarbor.with().getPluginByClass(BatteryMonitorPlugin::class.java)
            if (plugin != null) {
                val jiffiesFeat = plugin.core().getMonitorFeature(JiffiesMonitorFeature::class.java)
                jiffiesFeat?.currentJiffiesSnapshot(callback)
            }
        }
    }

    @JvmStatic
    fun addBatteryStateListener(listener: BatteryEventDelegate.Listener?) {
        if (listener != null) {
            if (BatteryEventDelegate.isInit()) {
                BatteryEventDelegate.getInstance().addListener(listener)
            }
        }
    }

    @JvmStatic
    fun removeBatteryStateListener(listener: BatteryEventDelegate.Listener?) {
        if (listener != null) {
            if (BatteryEventDelegate.isInit()) {
                BatteryEventDelegate.getInstance().removeListener(listener)
            }
        }
    }
}

