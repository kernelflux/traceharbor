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

    override fun init(app: Application, listener: PluginListener) {
        super.init(app, listener)
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

    fun getProcessName(): String {
        if (processName == null) {
            synchronized(this) {
                if (processName == null) {
                    var app = application
                    if (app == null) {
                        if (!TraceHarbor.isInstalled()) {
                            throw IllegalStateException(tag + " is not yet init!")
                        }
                        app = TraceHarbor.with().application
                    }
                    processName = TraceHarborUtil.getProcessName(app)
                }
            }
        }
        return processName!!
    }

    fun getPackageName(): String {
        if (packageNameCache == null) {
            synchronized(this) {
                if (packageNameCache == null) {
                    var app = application
                    if (app == null) {
                        if (!TraceHarbor.isInstalled()) {
                            throw IllegalStateException(tag + " is not yet init!")
                        }
                        app = TraceHarbor.with().application
                    }
                    packageNameCache = app.packageName
                }
            }
        }
        return packageNameCache!!
    }

    private companion object {
        private const val TAG = "TraceHarbor.battery.BatteryMonitorPlugin"
        private var packageNameCache: String? = null
        private var processName: String? = null
    }
}

