package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.content.pm.ApplicationInfo
import androidx.annotation.CallSuper
import androidx.annotation.WorkerThread
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * @author Kaede
 * @since 2020/12/24
 */
abstract class AbsMonitorFeature : MonitorFeature {
    protected open fun getTag(): String = TAG

    @JvmField
    protected var mCore: BatteryMonitorCore? = null

    protected val core: BatteryMonitorCore
        get() = checkNotNull(mCore) { "Monitor core is not configured yet" }

    @CallSuper
    override fun configure(monitor: BatteryMonitorCore) {
        TraceHarborLog.i(getTag(), "#configure")
        mCore = monitor
    }

    @CallSuper
    override fun onTurnOn() {
        TraceHarborLog.i(getTag(), "#onTurnOn")
    }

    @CallSuper
    override fun onTurnOff() {
        TraceHarborLog.i(getTag(), "#onTurnOff")
    }

    @CallSuper
    override fun onForeground(isForeground: Boolean) {
        TraceHarborLog.i(getTag(), "#onForeground, foreground = $isForeground")
    }

    @CallSuper
    @WorkerThread
    override fun onBackgroundCheck(duringMillis: Long) {
        TraceHarborLog.i(getTag(), "#onBackgroundCheck, since background started millis = $duringMillis")
    }

    protected fun shouldTracing(): Boolean {
        if (core.getConfig().isAggressiveMode) return true
        return 0 != (core.getContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
    }

    override fun toString(): String = getTag()

    private companion object {
        private const val TAG = "TraceHarbor.battery.MonitorFeature"
    }
}

