package com.kernelflux.traceharbor.batterycanary.stats

import android.text.TextUtils
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats
import com.kernelflux.traceharbor.batterycanary.monitor.feature.BlueToothMonitorFeature.BlueToothSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CompositeMonitors
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature.CpuStateSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.DeviceStatMonitorFeature.BatteryTmpSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot.ThreadJiffiesEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.LocationMonitorFeature.LocationSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.WifiMonitorFeature.WifiSnapshot
import com.kernelflux.traceharbor.batterycanary.stats.BatteryRecord.ReportRecord.EntryInfo
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.PowerProfile
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Locale

/**
 * @author Kaede
 * @since 2021/12/15
 */
interface BatteryStats {
    fun statsAppStat(appStat: Int): BatteryRecord.AppStatRecord

    fun statsDevStat(devStat: Int): BatteryRecord.DevStatRecord

    fun statsScene(scene: String): BatteryRecord.SceneStatRecord

    fun statsEvent(event: String, eventId: Int, extras: Map<String, Any>): BatteryRecord.EventStatRecord

    fun statsMonitors(monitors: CompositeMonitors): BatteryRecord.ReportRecord

    class BatteryStatsImpl : BatteryStats {
        override fun statsAppStat(appStat: Int): BatteryRecord.AppStatRecord {
            val statRecord = BatteryRecord.AppStatRecord()
            statRecord.appStat = appStat
            return statRecord
        }

        override fun statsDevStat(devStat: Int): BatteryRecord.DevStatRecord {
            val statRecord = BatteryRecord.DevStatRecord()
            statRecord.devStat = devStat
            return statRecord
        }

        override fun statsScene(scene: String): BatteryRecord.SceneStatRecord {
            val statRecord = BatteryRecord.SceneStatRecord()
            statRecord.scene = scene
            return statRecord
        }

        override fun statsEvent(event: String, eventId: Int, extras: Map<String, Any>): BatteryRecord.EventStatRecord {
            val statRecord = BatteryRecord.EventStatRecord()
            statRecord.id = eventId.toLong()
            statRecord.event = event
            if (extras.isNotEmpty()) {
                statRecord.extras = HashMap(extras)
            }
            return statRecord
        }

        override fun statsMonitors(monitors: CompositeMonitors): BatteryRecord.ReportRecord {
            val statRecord = BatteryRecord.ReportRecord()
            val appStats = monitors.getAppStats() ?: return statRecord

            val fg = appStats.isForeground()
            val widowMillis = appStats.duringMillis
            val minute = appStats.getMinute()

            statRecord.scope = monitors.getScope()
            statRecord.windowMillis = widowMillis
            statRecord.extras = HashMap()

            if (appStats.isForeground()) {
                statRecord.extras[BatteryRecord.ReportRecord.EXTRA_APP_FOREGROUND] = true
            }

            // Thread Entry
            val jiffiesDelta = monitors.getDelta(JiffiesSnapshot::class.java)
            if (jiffiesDelta != null) {
                val appJiffiesDelta = jiffiesDelta.dlt.totalJiffies.get()
                statRecord.extras[BatteryRecord.ReportRecord.EXTRA_JIFFY_TOTAL] = appJiffiesDelta
                if (!fg && monitors.isOverHeat(JiffiesSnapshot::class.java)) {
                    // Jiffies overheat
                    statRecord.extras[BatteryRecord.ReportRecord.EXTRA_JIFFY_OVERHEAT] = true
                }

                statRecord.threadInfoList = ArrayList()
                val list = jiffiesDelta.dlt.threadEntries.list
                for (threadJiffies in list.subList(0, minOf(list.size, 5))) {
                    val threadInfo = BatteryRecord.ReportRecord.ThreadInfo()
                    threadInfo.stat = threadJiffies.stat
                    threadInfo.tid = threadJiffies.tid
                    threadInfo.name = threadJiffies.name
                    threadInfo.jiffies = threadJiffies.get()

                    // stack
                    if (!TextUtils.isEmpty(threadJiffies.stack)) {
                        if (!threadInfo.extraInfo.containsKey(BatteryRecord.ReportRecord.EXTRA_THREAD_STACK)) {
                            if (threadInfo.extraInfo.isEmpty()) {
                                threadInfo.extraInfo = HashMap()
                            }
                            threadInfo.extraInfo[BatteryRecord.ReportRecord.EXTRA_THREAD_STACK] = threadJiffies.stack!!
                        }
                    }
                    statRecord.threadInfoList.add(threadInfo)
                }
            }

            statRecord.entryList = ArrayList()

            // DevStat Entry
            createEntryInfo { entryInfo ->
                entryInfo.name = "设备状态"
                entryInfo.entries = LinkedHashMap()

                // cpu load & cpu sip
                monitors.getFeature(CpuStatFeature::class.java) { cpuStatFeature ->
                    monitors.getDelta(CpuStateSnapshot::class.java) { delta ->
                        if (jiffiesDelta != null) {
                            val appJiffiesDelta = jiffiesDelta.dlt.totalJiffies.get()
                            val cpuJiffiesDelta = delta.dlt.totalCpuJiffies()
                            val cpuLoad = appJiffiesDelta.toFloat() / cpuJiffiesDelta
                            val cpuLoadAvg = cpuLoad * BatteryCanaryUtil.getCpuCoreNum()
                            entryInfo.entries["Cpu Load"] = ((kotlin.math.max(cpuLoadAvg, 0f) * 100).toInt().toString() + "%")

                            val powerProfile: PowerProfile = cpuStatFeature.powerProfile ?: return@getDelta
                            val procSipBgn = delta.bgn.configureProcSip(powerProfile, jiffiesDelta.bgn.totalJiffies.get())
                            val procSipEnd = delta.end.configureProcSip(powerProfile, jiffiesDelta.end.totalJiffies.get())
                            entryInfo.entries["Cpu Power"] =
                                String.format(Locale.US, "%.2f mAh", kotlin.math.max(procSipEnd - procSipBgn, 0.0))
                        }
                    }
                }

                // temperature
                monitors.getDelta(BatteryTmpSnapshot::class.java) { delta ->
                    val currTemp = delta.end.temp.get()
                    entryInfo.entries["当前电池温度"] = String.format(Locale.US, "%.1f ℃", currTemp / 10.0f)
                    monitors.getSamplingResult(BatteryTmpSnapshot::class.java) { result ->
                        val maxTemp = result.sampleMax
                        val minTemp = result.sampleMin
                        entryInfo.entries["最大电池温度"] = String.format(Locale.US, "%.1f ℃", maxTemp / 10.0f)
                        entryInfo.entries["电池温度变化"] = String.format(Locale.US, "%.1f ℃", (maxTemp - minTemp) / 10.0f)
                    }
                }

                statRecord.entryList.add(entryInfo)
            }

            // AppStat Entry
            createEntryInfo { entryInfo ->
                entryInfo.name = "App 状态"
                entryInfo.entries = LinkedHashMap()

                entryInfo.entries["前台时间占比"] = appStats.appFgRatio.toString() + "%"
                entryInfo.entries["后台时间占比"] = appStats.appBgRatio.toString() + "%"
                entryInfo.entries["前台服务时间占比"] = appStats.appFgSrvRatio.toString() + "%"
                entryInfo.entries["充电时间占比"] = appStats.devChargingRatio.toString() + "%"
                entryInfo.entries["息屏时间占比 (排除充电)"] = appStats.devSceneOffRatio.toString() + "%"

                statRecord.entryList.add(entryInfo)
            }

            // SystemService Entry
            createEntryInfo { entryInfo ->
                entryInfo.name = "系统服务调用"
                entryInfo.entries = LinkedHashMap()

                monitors.getDelta(BlueToothSnapshot::class.java) { delta ->
                    entryInfo.entries["BlueTooth 扫描"] = String.format(
                        Locale.US,
                        "register %s, discovery %s, scan %s 次",
                        delta.dlt.regsCount.get(),
                        delta.dlt.discCount.get(),
                        delta.dlt.scanCount.get(),
                    )
                }
                monitors.getDelta(WifiSnapshot::class.java) { delta ->
                    entryInfo.entries["Wifi 扫描"] = String.format(
                        Locale.US,
                        "query %s, scan %s 次",
                        delta.dlt.queryCount.get(),
                        delta.dlt.scanCount.get(),
                    )
                }
                monitors.getDelta(LocationSnapshot::class.java) { delta ->
                    entryInfo.entries["GPS 扫描"] =
                        String.format(Locale.US, "scan %s 次", delta.dlt.scanCount.get())
                }

                statRecord.entryList.add(entryInfo)
            }

            return statRecord
        }

        protected fun createEntryInfo(consumer: (EntryInfo) -> Unit) {
            val entryInfo = EntryInfo()
            consumer(entryInfo)
        }
    }
}

