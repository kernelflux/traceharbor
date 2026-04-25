package com.kernelflux.traceharbor.memory.canary

import com.kernelflux.traceharbor.lifecycle.owners.ProcessDeepBackgroundOwner
import com.kernelflux.traceharbor.lifecycle.owners.ProcessStagedBackgroundOwner
import com.kernelflux.traceharbor.lifecycle.supervisor.AppDeepBackgroundOwner
import com.kernelflux.traceharbor.lifecycle.supervisor.AppStagedBackgroundOwner
import com.kernelflux.traceharbor.lifecycle.supervisor.ProcessSupervisor
import com.kernelflux.traceharbor.memory.canary.monitor.AppBgSumPssMonitor
import com.kernelflux.traceharbor.memory.canary.monitor.AppBgSumPssMonitorConfig
import com.kernelflux.traceharbor.memory.canary.monitor.ProcessBgMemoryMonitor
import com.kernelflux.traceharbor.memory.canary.monitor.ProcessBgMemoryMonitorConfig
import com.kernelflux.traceharbor.memory.canary.trim.TrimMemoryConfig
import com.kernelflux.traceharbor.memory.canary.trim.TrimMemoryNotifier
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import com.kernelflux.traceharbor.util.safeLet

@Suppress("ArrayInDataClass")
data class MemoryCanaryConfig(
    val appBgSumPssMonitorConfigs: Array<AppBgSumPssMonitorConfig> = arrayOf(
        AppBgSumPssMonitorConfig(bgStatefulOwner = AppStagedBackgroundOwner),
        AppBgSumPssMonitorConfig(bgStatefulOwner = AppDeepBackgroundOwner)
    ),
    val processBgMemoryMonitorConfigs: Array<ProcessBgMemoryMonitorConfig> = arrayOf(
        ProcessBgMemoryMonitorConfig(bgStatefulOwner = ProcessStagedBackgroundOwner),
        ProcessBgMemoryMonitorConfig(bgStatefulOwner = ProcessDeepBackgroundOwner)
    ),
    val trimMemoryConfig: TrimMemoryConfig = TrimMemoryConfig()
)

class MemoryCanaryPlugin(
    private val memoryCanaryConfig: MemoryCanaryConfig = MemoryCanaryConfig()
) : Plugin() {

    override fun start() {
        if (status == PLUGIN_STARTED) {
            TraceHarborLog.e(tag, "already started")
            return
        }
        super.start()

        memoryCanaryConfig.apply {
            val isSupervisor = safeLet(tag, defVal = false) {
                ProcessSupervisor.isSupervisor // throws Exception when Supervisor disabled
            }
            if (isSupervisor) {
                TraceHarborLog.d(tag, "supervisor is ${TraceHarborUtil.getProcessName(application)}")

                AppBgSumPssMonitor.init(appBgSumPssMonitorConfigs)
            }
            processBgMemoryMonitorConfigs.forEach {
                ProcessBgMemoryMonitor(it).init()
            }
            TrimMemoryNotifier.init(trimMemoryConfig)
        }
    }

    override val tag: String
        get() = "TraceHarbor.MemoryCanaryPlugin"
}