package com.kernelflux.traceharbor.batterycanary

import android.app.Application
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorConfig
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil

class BatteryMonitorPlugin(config: BatteryMonitorConfig) : Plugin() {
    @JvmField
    val mDelegate: BatteryMonitorCore = BatteryMonitorCore(config)

    init {
        TraceHarborLog.i(TAG, "setUp battery monitor plugin with configs: $config")
    }

    fun core(): BatteryMonitorCore = mDelegate

    override fun init(application: Application, pluginListener: PluginListener) {
        super.init(application, pluginListener)
        if (!mDelegate.getConfig().isBuiltinForegroundNotifyEnabled) {
            ProcessUILifecycleOwner.removeListener(this)
        }
    }

    override val tag: String
        get() = "BatteryMonitorPlugin"

    override fun start() {
        super.start()
        mDelegate.start()
    }

    override fun stop() {
        super.stop()
        mDelegate.stop()
    }

    override fun onForeground(isForeground: Boolean) {
        mDelegate.onForeground(isForeground)
    }

    override fun isForeground(): Boolean = mDelegate.isForeground()


    private fun getApp(): Application {
        var app = application
        if (app == null) {
            if (!TraceHarbor.isInstalled()) {
                throw IllegalStateException("$tag is not yet init!")
            }
            app = TraceHarbor.with().application
        }
        return app
    }

    fun getProcessName(): String {
        return processName ?: synchronized(this) {
            processName ?: TraceHarborUtil.getProcessName(getApp()).also {
                processName = it
            }
        }
    }

    fun getPackageName(): String {
        return packageNameCache ?: synchronized(this) {
            packageNameCache ?: getApp().packageName.also {
                packageNameCache = it
            }
        }
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.BatteryMonitorPlugin"
        private var packageNameCache: String? = null
        private var processName: String? = null
    }
}

