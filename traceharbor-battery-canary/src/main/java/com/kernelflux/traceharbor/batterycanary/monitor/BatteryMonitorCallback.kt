package com.kernelflux.traceharbor.batterycanary.monitor

import android.content.ComponentName
import android.os.SystemClock
import android.text.TextUtils
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AbsTaskMonitorFeature.TaskJiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AlarmMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AlarmMonitorFeature.AlarmSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AppStatMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.BlueToothMonitorFeature.BlueToothSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CompositeMonitors
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.CpuStatFeature.CpuStateSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.DeviceStatMonitorFeature.BatteryTmpSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.DeviceStatMonitorFeature.CpuFreqSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.InternalMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot.ThreadJiffiesEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.LocationMonitorFeature.LocationSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.LooperTaskMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.BeanEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.ListEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Sampler
import com.kernelflux.traceharbor.batterycanary.monitor.feature.NotificationMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.NotificationMonitorFeature.BadNotification
import com.kernelflux.traceharbor.batterycanary.monitor.feature.WakeLockMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.WakeLockMonitorFeature.WakeLockSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.WakeLockMonitorFeature.WakeLockTrace.WakeLockRecord
import com.kernelflux.traceharbor.batterycanary.monitor.feature.WifiMonitorFeature.WifiSnapshot
import com.kernelflux.traceharbor.batterycanary.stats.BatteryStatsFeature
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.Consumer
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Arrays
import java.util.Locale
import kotlin.math.max

/**
 * @author Kaede
 * @since 2020/10/27
 */
interface BatteryMonitorCallback :
    BatteryMonitorCore.JiffiesListener,
    InternalMonitorFeature.InternalListener,
    LooperTaskMonitorFeature.LooperTaskListener,
    WakeLockMonitorFeature.WakeLockListener,
    AlarmMonitorFeature.AlarmListener,
    JiffiesMonitorFeature.JiffiesListener,
    NotificationMonitorFeature.NotificationListener,
    AppStatMonitorFeature.AppStatListener {

    @Suppress("NotNullFieldNotInitialized", "SpellCheckingInspection", "unused", "DEPRECATION")
    open class BatteryPrinter : BatteryMonitorCallback {
        @JvmField
        protected var mMonitor: BatteryMonitorCore? = null

        @JvmField
        protected var mCompositeMonitors: CompositeMonitors? = null

        @JvmField
        protected var mTraceBgnMillis: Long = 0L

        @JvmField
        protected var mIsForeground: Boolean = false

        @VisibleForTesting
        open fun attach(monitorCore: BatteryMonitorCore): BatteryPrinter {
            mMonitor = monitorCore
            mCompositeMonitors = CompositeMonitors(monitorCore, CompositeMonitors.SCOPE_CANARY)
            mCompositeMonitors!!.metricAll()
            return this
        }

        protected open fun getMonitor(): BatteryMonitorCore = mMonitor!!

        protected open fun isForegroundReport(): Boolean = mIsForeground

        @CallSuper
        override fun onTraceBegin() {
            mTraceBgnMillis = SystemClock.uptimeMillis()
            mCompositeMonitors!!.clear()
            mCompositeMonitors!!.start()
        }

        override fun onTraceEnd(isForeground: Boolean) {
            mIsForeground = isForeground
            val duringMillis = SystemClock.uptimeMillis() - mTraceBgnMillis
            if (mTraceBgnMillis <= 0L || duringMillis <= 0L) {
                TraceHarborLog.w(TAG, "skip invalid battery tracing, bgn = $mTraceBgnMillis, during = $duringMillis")
                return
            }
            mCompositeMonitors!!.finish()
            mCompositeMonitors!!.getAppStats(Consumer { appStats ->
                appStats.setForeground(isForeground)
            })
            onCanaryDump(mCompositeMonitors!!)
        }

        override fun onReportInternalJiffies(delta: Delta<TaskJiffiesSnapshot>) {
            val monitors = CompositeMonitors(getMonitor(), CompositeMonitors.SCOPE_INTERNAL)
            monitors.setAppStats(AppStats.current(delta.during))
            monitors.putDelta(InternalMonitorFeature.InternalSnapshot::class.java, delta)
            onCanaryReport(monitors)
        }

        override fun onTaskTrace(thread: Thread, sortList: List<LooperTaskMonitorFeature.TaskTraceInfo>) {
        }

        override fun onLooperTaskOverHeat(deltas: List<Delta<TaskJiffiesSnapshot>>) {
        }

        override fun onLooperConcurrentOverHeat(key: String, concurrentCount: Int, duringMillis: Long) {
        }

        override fun onWakeLockTimeout(warningCount: Int, record: WakeLockRecord) {
        }

        override fun onWakeLockTimeout(record: WakeLockRecord, backgroundMillis: Long) {
        }

        override fun onAlarmDuplicated(duplicatedCount: Int, record: AlarmMonitorFeature.AlarmRecord) {
        }

        @Deprecated("")
        override fun onParseError(pid: Int, tid: Int) {
        }

        override fun onNotify(notification: BadNotification) {
        }

        override fun onWatchingThreads(threadJiffiesList: ListEntry<out ThreadJiffiesEntry>) {
            val printer = Printer()
            printer.writeTitle()
            printer.append("| Thread WatchDog").append("\n")

            printer.createSection("jiffies(" + threadJiffiesList.getList().size + ")")
            printer.writeLine("desc", "(status)name(tid)\ttotal")
            for (threadJiffies in threadJiffiesList.getList()) {
                val entryJiffies = threadJiffies.get()
                printer.append("|   -> (").append(if (threadJiffies.isNewAdded) "+" else "~").append("/").append(threadJiffies.stat).append(")")
                    .append(threadJiffies.name).append("(").append(threadJiffies.tid).append(")\t")
                    .append(entryJiffies).append("\tjiffies")
                    .append("\n")
            }

            // Dump thread stacks if need.
            printer.createSection("stacks")
            var dumpStacks = getMonitor().getConfig().isAggressiveMode
            if (!dumpStacks || getMonitor().getConfig().threadWatchList.isNotEmpty()) {
                for (threadJiffies in threadJiffiesList.getList()) {
                    for (settingItem in getMonitor().getConfig().threadWatchList) {
                        if (settingItem.equals(threadJiffies.name, ignoreCase = true) || threadJiffies.name.contains(settingItem)) {
                            dumpStacks = true
                            break
                        }
                    }
                    if (dumpStacks) {
                        break
                    }
                }
            }
            if (dumpStacks) {
                val stackTraces = Thread.getAllStackTraces()
                TraceHarborLog.i(TAG, "onWatchingThreads dump stacks, get all threads size = $stackTraces")

                for ((thread, elements) in stackTraces) {
                    val threadName = thread.name

                    for (threadJiffies in threadJiffiesList.getList()) {
                        val targetThreadName = threadJiffies.name
                        if (targetThreadName.equals(threadName, ignoreCase = true) || threadName.contains(targetThreadName)) {
                            printer.append("|   -> ")
                                .append("(").append(thread.state).append(")")
                                .append(threadName).append("(").append(thread.id).append(")")
                                .append("\n")
                            getMonitor().getConfig().callStackCollector.collect(elements)
                            for (item in elements) {
                                printer.append("|      ").append(item).append("\n")
                            }
                        }
                    }
                }
            } else {
                printer.append("|   disabled").append("\n")
            }

            printer.writeEnding()
            printer.dump()
        }

        override fun onForegroundServiceLeak(
            isMyself: Boolean,
            appImportance: Int,
            globalAppImportance: Int,
            componentName: ComponentName,
            millis: Long,
        ) {
        }

        override fun onAppSateLeak(isMyself: Boolean, appImportance: Int, componentName: ComponentName, millis: Long) {
        }

        protected open fun checkBadThreads(monitors: CompositeMonitors) {
            monitors.getDelta(JiffiesSnapshot::class.java, Consumer { delta: Delta<JiffiesSnapshot> ->
                monitors.getAppStats(Consumer { appStats: AppStats ->
                    val minute = appStats.getMinute()
                    for (threadJiffies in delta.dlt.threadEntries.getList()) {
                        if (!threadJiffies.stat.uppercase(Locale.getDefault()).contains("R")) {
                            continue
                        }
                        monitors.getFeature(JiffiesMonitorFeature::class.java, Consumer { feature: JiffiesMonitorFeature ->
                            val avgJiffies = threadJiffies.get() / minute
                            if (appStats.isForeground()) {
                                if (minute > 10 && avgJiffies > getMonitor().getConfig().fgThreadWatchingLimit) {
                                    TraceHarborLog.i(
                                        TAG,
                                        "threadWatchDog fg set, name = " + delta.dlt.name +
                                            ", pid = " + delta.dlt.pid +
                                            ", tid = " + threadJiffies.tid,
                                    )
                                    feature.watchBackThreadSate(true, delta.dlt.pid, threadJiffies.tid)
                                }
                            } else {
                                if (minute > 10 && avgJiffies > getMonitor().getConfig().bgThreadWatchingLimit) {
                                    TraceHarborLog.i(
                                        TAG,
                                        "threadWatchDog bg set, name = " + delta.dlt.name +
                                            ", pid = " + delta.dlt.pid +
                                            ", tid = " + threadJiffies.tid,
                                    )
                                    feature.watchBackThreadSate(false, delta.dlt.pid, threadJiffies.tid)
                                }
                            }
                        })
                    }
                })
            })
        }

        protected open fun createDumper(): Dumper = Dumper()

        protected open fun createPrinter(): Printer = Printer()

        @CallSuper
        protected open fun onCanaryDump(monitors: CompositeMonitors) {
            val dumper = createDumper()
            val printer = createPrinter()
            printer.writeTitle()
            dumper.dump(monitors, printer)
            printer.writeEnding()
            printer.dump()

            checkBadThreads(monitors)
            onCanaryReport(monitors)
        }

        @CallSuper
        protected open fun onCanaryReport(monitors: CompositeMonitors) {
            monitors.getFeature(BatteryStatsFeature::class.java, Consumer { batteryStatsFeature ->
                batteryStatsFeature.statsMonitors(monitors)
            })
        }

        open class Dumper {
            open fun dump(monitors: CompositeMonitors, printer: Printer) {
                onWritingSections(monitors, printer)
                onWritingAppStatSection(monitors, printer)
            }

            protected open fun onWritingSections(monitors: CompositeMonitors, printer: Printer) {
                if (monitors.getMonitor() == null || monitors.getAppStats() == null) {
                    return
                }

                monitors.getDelta(JiffiesSnapshot::class.java, Consumer { delta: Delta<JiffiesSnapshot> ->
                    onWritingSectionContent(delta, monitors, printer)
                })

                if (
                    monitors.getDelta(AlarmSnapshot::class.java) != null ||
                    monitors.getDelta(WakeLockSnapshot::class.java) != null
                ) {
                    printer.createSection("awake")
                    monitors.getDelta(AlarmSnapshot::class.java, Consumer { delta: Delta<AlarmSnapshot> ->
                        onWritingSectionContent(delta, monitors, printer)
                    })
                    monitors.getDelta(WakeLockSnapshot::class.java, Consumer { delta: Delta<WakeLockSnapshot> ->
                        onWritingSectionContent(delta, monitors, printer)
                    })
                }

                if (
                    monitors.getDelta(BlueToothSnapshot::class.java) != null ||
                    monitors.getDelta(WifiSnapshot::class.java) != null ||
                    monitors.getDelta(LocationSnapshot::class.java) != null
                ) {
                    printer.createSection("scanning")
                    monitors.getDelta(BlueToothSnapshot::class.java, Consumer { delta: Delta<BlueToothSnapshot> ->
                        onWritingSectionContent(delta, monitors, printer)
                    })
                    monitors.getDelta(WifiSnapshot::class.java, Consumer { delta: Delta<WifiSnapshot> ->
                        onWritingSectionContent(delta, monitors, printer)
                    })
                    monitors.getDelta(LocationSnapshot::class.java, Consumer { delta: Delta<LocationSnapshot> ->
                        onWritingSectionContent(delta, monitors, printer)
                    })
                }

                if (
                    monitors.getFeature(AppStatMonitorFeature::class.java) != null ||
                    monitors.getDelta(CpuStateSnapshot::class.java) != null ||
                    monitors.getDelta(CpuFreqSnapshot::class.java) != null ||
                    monitors.getDelta(BatteryTmpSnapshot::class.java) != null
                ) {
                    printer.createSection("dev_stats")
                    monitors.getDelta(CpuStateSnapshot::class.java, Consumer { delta: Delta<CpuStateSnapshot> ->
                        onWritingSectionContent(delta, monitors, printer)
                    })
                    monitors.getDelta(CpuFreqSnapshot::class.java, Consumer { delta: Delta<CpuFreqSnapshot> ->
                        onWritingSectionContent(delta, monitors, printer)
                    })
                    monitors.getDelta(BatteryTmpSnapshot::class.java, Consumer { delta: Delta<BatteryTmpSnapshot> ->
                        onWritingSectionContent(delta, monitors, printer)
                    })
                }
            }

            @Suppress("UNCHECKED_CAST")
            protected open fun onWritingSectionContent(sessionDelta: Delta<*>, monitors: CompositeMonitors, printer: Printer): Boolean {
                if (monitors.getMonitor() == null || monitors.getAppStats() == null) {
                    return false
                }
                val appStats = monitors.getAppStats() ?: return false

                if (sessionDelta.dlt is JiffiesSnapshot) {
                    val delta = sessionDelta as Delta<JiffiesSnapshot>
                    val minute = max(1L, delta.during / ONE_MIN)
                    val avgJiffies = monitors.computeAvgJiffies(delta.dlt.totalJiffies.get())
                    printer.append("| ").append("cpu=").append(monitors.getCpuLoad()).append("/").append(monitors.getNorCpuLoad())
                        .tab().tab().append("fg=").append(BatteryCanaryUtil.convertAppStat(appStats.getAppStat()))
                        .tab().tab().append("during(min)=").append(minute)
                        .tab().tab().append("diff(jiffies)=").append(delta.dlt.totalJiffies.get())
                        .tab().tab().append("avg(jiffies/min)=").append(avgJiffies)
                        .enter()

                    printer.createSection("jiffies(" + delta.dlt.threadEntries.getList().size + ")")
                    printer.writeLine("desc", "(status)name(tid)\tavg/total")
                    printer.writeLine("inc_thread_num", delta.dlt.threadNum.get().toString())
                    printer.writeLine("cur_thread_num", delta.end.threadNum.get().toString())
                    val toppingCount = 8
                    var remainJiffies = 0L
                    for (i in 0 until delta.dlt.threadEntries.getList().size) {
                        val threadJiffies = delta.dlt.threadEntries.getList()[i]
                        val entryJiffies = threadJiffies.get()
                        if (i < toppingCount) {
                            printer.append("|   -> (").append(if (threadJiffies.isNewAdded) "+" else "~").append("/").append(threadJiffies.stat).append(")")
                                .append(threadJiffies.name).append("(").append(threadJiffies.tid).append(")\t")
                                .append(monitors.computeAvgJiffies(entryJiffies)).append("/").append(entryJiffies).append("\tjiffies")
                                .append("\n")
                        } else {
                            remainJiffies += entryJiffies
                        }
                    }
                    printer.append("|\t\t......\n")
                    if (remainJiffies > 0) {
                        printer.append("|   -> R/R)")
                            .append("REMAINS").append("(").append(delta.dlt.threadEntries.getList().size - toppingCount).append(")\t")
                            .append(monitors.computeAvgJiffies(remainJiffies) / minute).append("/").append(remainJiffies).append("\tjiffies")
                            .append("\n")
                    }
                    if (avgJiffies > 1000L || !delta.isValid()) {
                        printer.append("|  ").append(if (avgJiffies > 1000L) " #overHeat" else "").append(if (!delta.isValid()) " #invalid" else "").append("\n")
                    }
                    return true
                }

                if (sessionDelta.dlt is AlarmSnapshot) {
                    val delta = sessionDelta as Delta<AlarmSnapshot>
                    printer.createSubSection("alarm")
                    printer.writeLine(delta.during.toString() + "(mls)\t" + (delta.during / ONE_MIN) + "(min)")
                    printer.writeLine("inc_alarm_count", delta.dlt.totalCount.get().toString())
                    printer.writeLine("inc_trace_count", delta.dlt.tracingCount.get().toString())
                    printer.writeLine("inc_dupli_group", delta.dlt.duplicatedGroup.get().toString())
                    printer.writeLine("inc_dupli_count", delta.dlt.duplicatedCount.get().toString())
                    return true
                }

                if (sessionDelta.dlt is WakeLockSnapshot) {
                    val delta = sessionDelta as Delta<WakeLockSnapshot>
                    printer.createSubSection("wake_lock")
                    printer.writeLine(delta.during.toString() + "(mls)\t" + (delta.during / ONE_MIN) + "(min)")
                    printer.writeLine("inc_lock_count", delta.dlt.totalWakeLockCount.toString())
                    printer.writeLine("inc_time_total", delta.dlt.totalWakeLockTime.toString())

                    val wakeLockRecordsList: List<BeanEntry<WakeLockRecord>> = delta.end.totalWakeLockRecords.getList()
                    if (wakeLockRecordsList.isNotEmpty()) {
                        printer.createSubSection("locking")
                        for (item in wakeLockRecordsList) {
                            if (!item.get().isFinished()) {
                                printer.writeLine(item.get().toString())
                            }
                        }
                    }
                    return true
                }

                if (sessionDelta.dlt is BlueToothSnapshot) {
                    val delta = sessionDelta as Delta<BlueToothSnapshot>
                    printer.createSubSection("bluetooh")
                    printer.writeLine(delta.during.toString() + "(mls)\t" + (delta.during / ONE_MIN) + "(min)")
                    printer.writeLine("inc_regs_count", delta.dlt.regsCount.get().toString())
                    printer.writeLine("inc_dics_count", delta.dlt.discCount.get().toString())
                    printer.writeLine("inc_scan_count", delta.dlt.scanCount.get().toString())
                    return true
                }

                if (sessionDelta.dlt is WifiSnapshot) {
                    val delta = sessionDelta as Delta<WifiSnapshot>
                    printer.createSubSection("wifi")
                    printer.writeLine(delta.during.toString() + "(mls)\t" + (delta.during / ONE_MIN) + "(min)")
                    printer.writeLine("inc_scan_count", delta.dlt.scanCount.get().toString())
                    printer.writeLine("inc_qury_count", delta.dlt.queryCount.get().toString())
                    return true
                }

                if (sessionDelta.dlt is LocationSnapshot) {
                    val delta = sessionDelta as Delta<LocationSnapshot>
                    printer.createSubSection("location")
                    printer.writeLine(delta.during.toString() + "(mls)\t" + (delta.during / ONE_MIN) + "(min)")
                    printer.writeLine("inc_scan_count", delta.dlt.scanCount.get().toString())
                    return true
                }

                if (sessionDelta.dlt is CpuFreqSnapshot) {
                    val delta = sessionDelta as Delta<CpuFreqSnapshot>
                    printer.createSubSection("cpufreq")
                    printer.writeLine(delta.during.toString() + "(mls)\t" + (delta.during / ONE_MIN) + "(min)")
                    printer.writeLine("inc", Arrays.toString(delta.dlt.cpuFreqs.getList().toTypedArray()))
                    printer.writeLine("cur", Arrays.toString(delta.end.cpuFreqs.getList().toTypedArray()))
                    monitors.getSamplingResult(CpuFreqSnapshot::class.java, Consumer { result: Sampler.Result ->
                        printer.createSubSection("cpufreq_sampling")
                        printer.writeLine(result.duringMillis.toString() + "(mls)\t" + result.interval + "(itv)")
                        printer.writeLine("max", result.sampleMax.toString())
                        printer.writeLine("min", result.sampleMin.toString())
                        printer.writeLine("avg", result.sampleAvg.toString())
                        printer.writeLine("cnt", result.count.toString())
                    })
                    return true
                }

                if (sessionDelta.dlt is CpuStateSnapshot) {
                    val delta = sessionDelta as Delta<CpuStateSnapshot>
                    val minute = max(1L, delta.during / ONE_MIN)
                    printer.createSubSection("dev_cpu_load")
                    printer.writeLine(delta.during.toString() + "(mls)\t" + (delta.during / ONE_MIN) + "(min)")
                    val cpuStatFeature = monitors.getFeature(CpuStatFeature::class.java)
                    if (cpuStatFeature != null) {
                        printer.writeLine("usage", monitors.getDevCpuLoad().toString() + "%")
                    }
                    for (i in delta.dlt.cpuCoreStates.indices) {
                        val listEntry: ListEntry<DigitEntry<Long>> = delta.dlt.cpuCoreStates[i]
                        printer.writeLine("cpu$i", Arrays.toString(listEntry.getList().toTypedArray()))
                    }
                    if (cpuStatFeature != null && cpuStatFeature.isSupported) {
                        val powerProfile = cpuStatFeature.powerProfile
                        if (powerProfile != null) {
                            printer.createSubSection("cpu_sip")
                            printer.writeLine("inc_cpu_sip", String.format(Locale.US, "%.2f(mAh)/min", delta.dlt.configureCpuSip(powerProfile) / minute))
                            printer.writeLine("cur_cpu_sip", String.format(Locale.US, "%.2f(mAh)", delta.end.configureCpuSip(powerProfile)))
                            monitors.getDelta(JiffiesSnapshot::class.java, Consumer { jiffiesDelta: Delta<JiffiesSnapshot> ->
                                val procSipBgn = delta.bgn.configureProcSip(powerProfile, jiffiesDelta.bgn.totalJiffies.get())
                                val procSipEnd = delta.end.configureProcSip(powerProfile, jiffiesDelta.end.totalJiffies.get())
                                printer.writeLine("inc_prc_sip", String.format(Locale.US, "%.2f(mAh)/min", (procSipEnd - procSipBgn) / minute))
                                printer.writeLine("cur_prc_sip", String.format(Locale.US, "%.2f(mAh)", procSipEnd))
                            })
                        }
                    }
                    return true
                }

                if (sessionDelta.dlt is BatteryTmpSnapshot) {
                    val delta = sessionDelta as Delta<BatteryTmpSnapshot>
                    printer.createSubSection("batt_temp")
                    printer.writeLine(delta.during.toString() + "(mls)\t" + (delta.during / ONE_MIN) + "(min)")
                    printer.writeLine("inc", delta.dlt.temp.get().toString())
                    printer.writeLine("cur", delta.end.temp.get().toString())
                    monitors.getSamplingResult(BatteryTmpSnapshot::class.java, Consumer { result: Sampler.Result ->
                        printer.createSubSection("batt_temp_sampling")
                        printer.writeLine(result.duringMillis.toString() + "(mls)\t" + result.interval + "(itv)")
                        printer.writeLine("max", result.sampleMax.toString())
                        printer.writeLine("min", result.sampleMin.toString())
                        printer.writeLine("avg", result.sampleAvg.toString())
                        printer.writeLine("cnt", result.count.toString())
                    })
                    return true
                }

                return false
            }

            protected open fun onWritingAppStatSection(monitors: CompositeMonitors, printer: Printer) {
                if (monitors.getMonitor() == null || monitors.getAppStats() == null) {
                    return
                }

                val appStats = monitors.getAppStats() ?: return
                printer.createSection("app_stats")
                printer.createSubSection("stat_time")
                printer.writeLine("time", appStats.getMinute().toString() + "(min)")
                printer.writeLine("fg", appStats.appFgRatio.toString())
                printer.writeLine("bg", appStats.appBgRatio.toString())
                printer.writeLine("fgSrv", appStats.appFgSrvRatio.toString())
                printer.writeLine("float", appStats.appFloatRatio.toString())
                printer.writeLine("devCharging", appStats.devChargingRatio.toString())
                printer.writeLine("devScreenOff", appStats.devSceneOffRatio.toString())
                if (!TextUtils.isEmpty(appStats.sceneTop1)) {
                    printer.writeLine("sceneTop1", appStats.sceneTop1 + "/" + appStats.sceneTop1Ratio)
                }
                if (!TextUtils.isEmpty(appStats.sceneTop2)) {
                    printer.writeLine("sceneTop2", appStats.sceneTop2 + "/" + appStats.sceneTop2Ratio)
                }
                monitors.getFeature(AppStatMonitorFeature::class.java, Consumer { feature: AppStatMonitorFeature ->
                    val currSnapshot = feature.currentAppStatSnapshot()
                    printer.createSubSection("run_time")
                    printer.writeLine("time", (currSnapshot.uptime.get() / ONE_MIN).toString() + "(min)")
                    printer.writeLine("fg", currSnapshot.fgRatio.get().toString())
                    printer.writeLine("bg", currSnapshot.bgRatio.get().toString())
                    printer.writeLine("fgSrv", currSnapshot.fgSrvRatio.get().toString())
                    printer.writeLine("float", currSnapshot.floatRatio.get().toString())
                })
            }
        }

        /**
         * Log Printer
         */
        @Suppress("UnusedReturnValue")
        open class Printer {
            private val sb = StringBuilder()

            open fun append(obj: Any?): Printer {
                sb.append(obj)
                return this
            }

            open fun tab(): Printer {
                sb.append("\t")
                return this
            }

            open fun enter(): Printer {
                sb.append("\n")
                return this
            }

            open fun writeTitle(): Printer {
                sb.append("****************************************** PowerTest *****************************************").append("\n")
                return this
            }

            open fun createSection(sectionName: String): Printer {
                sb.append("+ --------------------------------------------------------------------------------------------").append("\n")
                sb.append("| ").append(sectionName).append(" :").append("\n")
                return this
            }

            open fun writeLine(line: String): Printer {
                sb.append("| ").append("  -> ").append(line).append("\n")
                return this
            }

            open fun writeLine(key: String, value: String): Printer {
                sb.append("| ").append("  -> ").append(key).append("\t= ").append(value).append("\n")
                return this
            }

            open fun createSubSection(name: String): Printer {
                sb.append("| ").append("  <").append(name).append(">\n")
                return this
            }

            open fun writeEnding(): Printer {
                sb.append("**********************************************************************************************")
                return this
            }

            open fun clear() {
                sb.delete(0, sb.length)
            }

            open fun dump() {
                try {
                    TraceHarborLog.i(TAG, "%s", "\t\n" + sb.toString())
                } catch (e: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG, e, "log format error")
                }
            }

            override fun toString(): String = sb.toString()
        }

        companion object {
            private const val TAG = "TraceHarbor.battery.BatteryPrinter"
            private const val ONE_MIN = 60 * 1000
        }
    }
}
