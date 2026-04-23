package io.traceharbor.memory.canary

import io.traceharbor.lifecycle.owners.ProcessDeepBackgroundOwner
import io.traceharbor.lifecycle.owners.ProcessStagedBackgroundOwner
import io.traceharbor.lifecycle.supervisor.AppDeepBackgroundOwner
import io.traceharbor.lifecycle.supervisor.AppStagedBackgroundOwner
import io.traceharbor.lifecycle.supervisor.ProcessSupervisor
import io.traceharbor.memory.canary.monitor.AppBgSumPssMonitor
import io.traceharbor.memory.canary.monitor.AppBgSumPssMonitorConfig
import io.traceharbor.memory.canary.monitor.ProcessBgMemoryMonitor
import io.traceharbor.memory.canary.monitor.ProcessBgMemoryMonitorConfig
import io.traceharbor.memory.canary.trim.TrimMemoryConfig
import io.traceharbor.memory.canary.trim.TrimMemoryNotifier
import io.traceharbor.plugin.Plugin
import io.traceharbor.util.TraceHarborLog
import io.traceharbor.util.TraceHarborUtil
import io.traceharbor.util.safeLet

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

    override fun getTag(): String {
        return "TraceHarbor.MemoryCanaryPlugin"
    }
}