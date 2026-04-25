package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import androidx.annotation.CallSuper
import androidx.core.util.Pair
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AbsTaskMonitorFeature.TaskJiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot.ThreadJiffiesEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.ListEntry
import com.kernelflux.traceharbor.batterycanary.stats.HealthStatsFeature
import com.kernelflux.traceharbor.batterycanary.stats.HealthStatsFeature.HealthStatsSnapshot
import com.kernelflux.traceharbor.batterycanary.stats.HealthStatsHelper
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.Consumer
import com.kernelflux.traceharbor.batterycanary.utils.Function
import com.kernelflux.traceharbor.batterycanary.utils.PowerProfile
import com.kernelflux.traceharbor.batterycanary.utils.RadioStatUtil
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Collections
import java.util.LinkedHashMap

/**
 * @author Kaede
 * @since 2021/9/18
 */
open class CompositeMonitors {
    @JvmField
    protected val mMetrics: MutableList<Class<out Snapshot<*>>> = ArrayList()

    @JvmField
    protected val mBgnSnapshots: MutableMap<Class<out Snapshot<*>>, Snapshot<*>> = HashMap()

    @JvmField
    protected val mDeltas: MutableMap<Class<out Snapshot<*>>, Delta<*>> = HashMap()

    @JvmField
    protected val mSampleRegs: MutableMap<Class<out Snapshot<*>>, Long> = HashMap()

    @JvmField
    protected val mSamplers: MutableMap<Class<out Snapshot<*>>, Snapshot.Sampler> = HashMap()

    @JvmField
    protected val mSampleResults: MutableMap<Class<out Snapshot<*>>, Snapshot.Sampler.Result> = HashMap()

    @JvmField
    protected val mTaskDeltas: MutableMap<Class<out AbsTaskMonitorFeature>, List<Delta<TaskJiffiesSnapshot>>> = HashMap()

    @JvmField
    protected val mTaskDeltasCollect: MutableMap<String, MutableList<Pair<Class<out AbsTaskMonitorFeature>, Delta<TaskJiffiesSnapshot>>>> = HashMap()

    @JvmField
    protected val mExtras: Bundle = Bundle()

    @JvmField
    protected val mStacks: MutableMap<String, String> = HashMap()

    @JvmField
    protected var mMonitor: BatteryMonitorCore? = null

    @JvmField
    protected var mAppStats: AppStats? = null

    @JvmField
    protected var mCpuFreqSampler: CpuFreqSampler? = null

    @JvmField
    protected var mBpsSampler: BpsSampler? = null

    @JvmField
    protected var mBgnMillis: Long = SystemClock.uptimeMillis()

    @JvmField
    protected var mScope: String

    constructor(core: BatteryMonitorCore?) {
        mMonitor = core
        mScope = SCOPE_UNKNOWN
    }

    constructor(core: BatteryMonitorCore?, scope: String) {
        mMonitor = core
        mScope = scope
    }

    open fun getScope(): String = mScope

    @CallSuper
    open fun clear() {
        TraceHarborLog.i(TAG, hashCode().toString() + " #clear: " + mScope)
        mBgnSnapshots.clear()
        mDeltas.clear()
        mSamplers.clear()
        mSampleResults.clear()
        mTaskDeltas.clear()
        mTaskDeltasCollect.clear()
        mExtras.clear()
        mStacks.clear()
        mCpuFreqSampler = null
    }

    open fun fork(): CompositeMonitors = fork(CompositeMonitors(mMonitor, mScope))

    @CallSuper
    protected open fun fork(that: CompositeMonitors): CompositeMonitors {
        TraceHarborLog.i(TAG, hashCode().toString() + " #fork: " + mScope)
        that.clear()
        that.mBgnMillis = mBgnMillis
        that.mAppStats = mAppStats
        that.mMetrics.addAll(mMetrics)
        that.mBgnSnapshots.putAll(mBgnSnapshots)
        that.mDeltas.putAll(mDeltas)

        // Sampler can not be cloned.
        that.mTaskDeltas.putAll(mTaskDeltas)
        that.mTaskDeltasCollect.putAll(mTaskDeltasCollect)
        that.mExtras.putAll(mExtras)
        that.mStacks.putAll(mStacks)
        that.mCpuFreqSampler = mCpuFreqSampler
        return that
    }

    open fun getMonitor(): BatteryMonitorCore? = mMonitor

    @Suppress("UNCHECKED_CAST")
    open fun <T : MonitorFeature> getFeature(clazz: Class<T>): T? {
        val monitor = mMonitor ?: return null
        for (plugin in monitor.getConfig().features) {
            if (clazz.isAssignableFrom(plugin.javaClass)) {
                return plugin as T
            }
        }
        return null
    }

    open fun <T : MonitorFeature> getFeature(clazz: Class<T>, block: Consumer<T>) {
        val feature = getFeature(clazz)
        if (feature != null) {
            block.accept(feature)
        }
    }

    open fun getAppStats(): AppStats? = mAppStats

    open fun getAppStats(block: Consumer<AppStats>) {
        val appStats = getAppStats()
        if (appStats != null) {
            block.accept(appStats)
        }
    }

    open fun setAppStats(appStats: AppStats?) {
        mAppStats = appStats
    }

    open fun getCpuLoad(): Int {
        val appStats = mAppStats
        if (appStats == null) {
            TraceHarborLog.w(TAG, "AppStats should not be null to get CpuLoad")
            return -1
        }
        val appJiffiesDelta: Long
        val uidJiffies = getDelta(JiffiesMonitorFeature.UidJiffiesSnapshot::class.java)
        if (uidJiffies != null) {
            appJiffiesDelta = uidJiffies.dlt.totalUidJiffies.get()
        } else {
            val pidJiffies = getDelta(JiffiesSnapshot::class.java)
            if (pidJiffies == null) {
                TraceHarborLog.w(TAG, JiffiesSnapshot::class.java.toString() + " should be metrics to get CpuLoad")
                return -1
            }
            appJiffiesDelta = pidJiffies.dlt.totalJiffies.get()
        }
        val cpuUptimeDelta = appStats.duringMillis
        val cpuLoad = if (cpuUptimeDelta > 0) (appJiffiesDelta * 10).toFloat() / cpuUptimeDelta else 0f
        return (cpuLoad * 100).toInt()
    }

    open fun getNorCpuLoad(): Int {
        val cpuLoad = getCpuLoad()
        if (cpuLoad == -1) {
            TraceHarborLog.w(TAG, "cpu is invalid")
            return -1
        }
        val result = getSamplingResult(DeviceStatMonitorFeature.CpuFreqSnapshot::class.java)
        if (result == null) {
            TraceHarborLog.w(TAG, "cpufreq is null")
            return -1
        }
        val cpuFreqSteps = BatteryCanaryUtil.getCpuFreqSteps()
        if (cpuFreqSteps.size != BatteryCanaryUtil.getCpuCoreNum()) {
            TraceHarborLog.w(TAG, "cpuCore is invalid: " + cpuFreqSteps.size + " vs " + BatteryCanaryUtil.getCpuCoreNum())
        }
        var sumMax = 0L
        for (steps in cpuFreqSteps) {
            var max = 0
            for (item in steps) {
                if (item > max) {
                    max = item
                }
            }
            sumMax += max.toLong()
        }
        if (sumMax <= 0) {
            TraceHarborLog.w(TAG, "cpufreq sum is invalid: $sumMax")
            return -1
        }
        if (result.sampleAvg >= sumMax) {
            TraceHarborLog.w(TAG, "NorCpuLoad err: sampling = $result")
            for (item in cpuFreqSteps) {
                TraceHarborLog.w(TAG, "NorCpuLoad err: freqs = " + item.contentToString())
            }
        }
        return (cpuLoad * result.sampleAvg / sumMax).toInt()
    }

    /**
     * Work in progress
     */
    open fun getDevCpuLoad(): Int {
        val appStats = mAppStats
        if (appStats == null) {
            TraceHarborLog.w(TAG, "AppStats should not be null to get CpuLoad")
            return -1
        }
        val cpuJiffies = getDelta(CpuStatFeature.CpuStateSnapshot::class.java)
        if (cpuJiffies == null) {
            TraceHarborLog.w(TAG, "Configure CpuLoad by uptime")
            return -1
        }
        val cpuJiffiesDelta = cpuJiffies.dlt.totalCpuJiffies()
        val devJiffiesDelta = appStats.duringMillis
        val cpuLoad = if (devJiffiesDelta > 0) (cpuJiffiesDelta * 10).toFloat() / devJiffiesDelta else 0f
        return (cpuLoad * 100).toInt()
    }

    open fun computeAvgJiffies(jiffies: Long): Long {
        val appStats = mAppStats
        if (appStats == null) {
            TraceHarborLog.w(TAG, "AppStats should not be null to computeAvgJiffies")
            return -1
        }
        return computeAvgJiffies(jiffies, appStats.duringMillis)
    }

    open fun <T : Snapshot<T>> isOverHeat(snapshotClass: Class<T>): Boolean {
        val appStats = getAppStats()
        val delta = getDelta(snapshotClass)
        if (appStats == null || delta == null) {
            return false
        }
        if (snapshotClass == JiffiesSnapshot::class.java) {
            @Suppress("UNCHECKED_CAST")
            val jiffiesDelta = delta as Delta<JiffiesSnapshot>
            val minute = appStats.minute
            val avgJiffies = jiffiesDelta.dlt.totalJiffies.get() / minute
            return minute >= 5 && avgJiffies >= 1000
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    open fun <T : Snapshot<T>> getDelta(snapshotClass: Class<T>): Delta<T>? {
        return mDeltas[snapshotClass] as? Delta<T>
    }

    open fun <T : Snapshot<T>> getDelta(snapshotClass: Class<T>, block: Consumer<Delta<T>>) {
        val delta = getDelta(snapshotClass)
        if (delta != null) {
            block.accept(delta)
        }
    }

    open fun getDeltaRaw(snapshotClass: Class<out Snapshot<*>>): Delta<*>? = mDeltas[snapshotClass]

    open fun getDeltaRaw(snapshotClass: Class<out Snapshot<*>>, block: Consumer<Delta<*>>) {
        val delta = getDeltaRaw(snapshotClass)
        if (delta != null) {
            block.accept(delta)
        }
    }

    open fun putDelta(snapshotClass: Class<out Snapshot<*>>, delta: Delta<out Snapshot<*>>) {
        mDeltas[snapshotClass] = delta
    }

    open fun getSamplingResult(snapshotClass: Class<out Snapshot<*>>): Snapshot.Sampler.Result? = mSampleResults[snapshotClass]

    open fun getSamplingResult(snapshotClass: Class<out Snapshot<*>>, block: Consumer<Snapshot.Sampler.Result>) {
        val result = getSamplingResult(snapshotClass)
        if (result != null) {
            block.accept(result)
        }
    }

    @CallSuper
    open fun metricAll(): CompositeMonitors {
        metric(JiffiesSnapshot::class.java)
        metric(AlarmMonitorFeature.AlarmSnapshot::class.java)
        metric(WakeLockMonitorFeature.WakeLockSnapshot::class.java)
        metric(CpuStatFeature.CpuStateSnapshot::class.java)
        metric(AppStatMonitorFeature.AppStatSnapshot::class.java)
        metric(DeviceStatMonitorFeature.CpuFreqSnapshot::class.java)
        metric(DeviceStatMonitorFeature.BatteryTmpSnapshot::class.java)
        metric(TrafficMonitorFeature.RadioStatSnapshot::class.java)
        metric(BlueToothMonitorFeature.BlueToothSnapshot::class.java)
        metric(WifiMonitorFeature.WifiSnapshot::class.java)
        metric(LocationMonitorFeature.LocationSnapshot::class.java)
        return this
    }

    open fun metricCpuLoad(): CompositeMonitors {
        if (!mMetrics.contains(JiffiesSnapshot::class.java)) {
            metric(JiffiesSnapshot::class.java)
        }
        if (!mMetrics.contains(CpuStatFeature.CpuStateSnapshot::class.java)) {
            metric(CpuStatFeature.CpuStateSnapshot::class.java)
        }
        return this
    }

    open fun metric(snapshotClass: Class<out Snapshot<*>>): CompositeMonitors {
        if (!mMetrics.contains(snapshotClass)) {
            mMetrics.add(snapshotClass)
        }
        return this
    }

    open fun sample(snapshotClass: Class<out Snapshot<*>>): CompositeMonitors {
        return sample(snapshotClass, BatteryCanaryUtil.ONE_MIN.toLong())
    }

    open fun sample(snapshotClass: Class<out Snapshot<*>>, interval: Long): CompositeMonitors {
        mSampleRegs[snapshotClass] = interval
        return this
    }

    open fun start() {
        TraceHarborLog.i(TAG, hashCode().toString() + " #start: " + mScope)
        mAppStats = null
        mBgnMillis = SystemClock.uptimeMillis()
        configureBgnSnapshots()
        configureSamplers()
    }

    open fun finish() {
        TraceHarborLog.i(TAG, hashCode().toString() + " #finish: " + mScope)
        configureEndDeltas()
        collectStacks()
        configureSampleResults()
        mAppStats = AppStats.current(SystemClock.uptimeMillis() - mBgnMillis)
        polishEstimatedPower()
    }

    protected open fun configureBgnSnapshots() {
        for (item in mMetrics) {
            val currSnapshot = statCurrSnapshot(item)
            if (currSnapshot != null) {
                mBgnSnapshots[item] = currSnapshot
                if (currSnapshot is HealthStatsSnapshot && mSampleRegs.containsKey(HealthStatsSnapshot::class.java)) {
                    currSnapshot.startAccCollecting()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST", "rawtypes")
    protected open fun configureEndDeltas() {
        for ((snapshotClass, lastSnapshot) in mBgnSnapshots) {
            val currSnapshot = statCurrSnapshot(snapshotClass)
            if (currSnapshot != null && currSnapshot.javaClass == lastSnapshot.javaClass) {
                val delta: Delta<out Snapshot<*>>
                delta = if (lastSnapshot is HealthStatsSnapshot && lastSnapshot.accCollector != null) {
                    (currSnapshot as HealthStatsSnapshot).diffByAccCollector(lastSnapshot)
                } else {
                    (currSnapshot as Snapshot<Snapshot<*>>).diff(lastSnapshot)
                }
                putDelta(snapshotClass, delta)
            }
        }
    }

    protected open fun collectStacks() {
        val monitor = mMonitor ?: return
        if (SCOPE_CANARY == getScope()) {
            val appStats = getAppStats()
            if (appStats != null && !appStats.isForeground) {
                getDelta(JiffiesSnapshot::class.java, Consumer { delta ->
                    val minute = kotlin.math.max(1L, delta.during / BatteryCanaryUtil.ONE_MIN)
                    if (minute < 5) {
                        return@Consumer
                    }
                    for (threadEntry : ThreadJiffiesEntry in delta.dlt.threadEntries.list) {
                        val topThreadAvgJiffies = threadEntry.get() / minute
                        if (topThreadAvgJiffies < 3000L) {
                            break
                        }
                        val stack = monitor.getConfig().callStackCollector.collect(threadEntry.tid)
                        if (!TextUtils.isEmpty(stack)) {
                            mStacks[threadEntry.tid.toString()] = stack
                        }
                    }
                })
            }
        }
    }

    @CallSuper
    protected open fun statCurrSnapshot(snapshotClass: Class<out Snapshot<*>>): Snapshot<*>? {
        var snapshot: Snapshot<*>? = null
        if (snapshotClass == AlarmMonitorFeature.AlarmSnapshot::class.java) {
            val feature = getFeature(AlarmMonitorFeature::class.java)
            if (feature != null) snapshot = feature.currentAlarms()
            return snapshot
        }
        if (snapshotClass == BlueToothMonitorFeature.BlueToothSnapshot::class.java) {
            val feature = getFeature(BlueToothMonitorFeature::class.java)
            if (feature != null) snapshot = feature.currentSnapshot()
            return snapshot
        }
        if (snapshotClass == DeviceStatMonitorFeature.CpuFreqSnapshot::class.java) {
            val feature = getFeature(DeviceStatMonitorFeature::class.java)
            if (feature != null) snapshot = feature.currentCpuFreq()
            return snapshot
        }
        if (snapshotClass == DeviceStatMonitorFeature.BatteryTmpSnapshot::class.java) {
            val feature = getFeature(DeviceStatMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) snapshot = feature.currentBatteryTemperature(monitor.getContext())
            return snapshot
        }
        if (snapshotClass == JiffiesSnapshot::class.java) {
            val feature = getFeature(JiffiesMonitorFeature::class.java)
            if (feature != null) snapshot = feature.currentJiffiesSnapshot()
            return snapshot
        }
        if (snapshotClass == JiffiesMonitorFeature.UidJiffiesSnapshot::class.java) {
            val feat = getFeature(JiffiesMonitorFeature::class.java)
            if (feat != null) return feat.currentUidJiffiesSnapshot()
        }
        if (snapshotClass == LocationMonitorFeature.LocationSnapshot::class.java) {
            val feature = getFeature(LocationMonitorFeature::class.java)
            if (feature != null) snapshot = feature.currentSnapshot()
            return snapshot
        }
        if (snapshotClass == TrafficMonitorFeature.RadioStatSnapshot::class.java) {
            val feature = getFeature(TrafficMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) snapshot = feature.currentRadioSnapshot(monitor.getContext())
            return snapshot
        }
        if (snapshotClass == WakeLockMonitorFeature.WakeLockSnapshot::class.java) {
            val feature = getFeature(WakeLockMonitorFeature::class.java)
            if (feature != null) snapshot = feature.currentWakeLocks()
            return snapshot
        }
        if (snapshotClass == WifiMonitorFeature.WifiSnapshot::class.java) {
            val feature = getFeature(WifiMonitorFeature::class.java)
            if (feature != null) snapshot = feature.currentSnapshot()
            return snapshot
        }
        if (snapshotClass == CpuStatFeature.CpuStateSnapshot::class.java) {
            val feature = getFeature(CpuStatFeature::class.java)
            if (feature != null && feature.isSupported) snapshot = feature.currentCpuStateSnapshot()
            return snapshot
        }
        if (snapshotClass == CpuStatFeature.UidCpuStateSnapshot::class.java) {
            val feature = getFeature(CpuStatFeature::class.java)
            if (feature != null && feature.isSupported) snapshot = feature.currentUidCpuStateSnapshot()
            return snapshot
        }
        if (snapshotClass == AppStatMonitorFeature.AppStatSnapshot::class.java) {
            val feature = getFeature(AppStatMonitorFeature::class.java)
            if (feature != null) snapshot = feature.currentAppStatSnapshot()
            return snapshot
        }
        if (snapshotClass == HealthStatsSnapshot::class.java) {
            val feature = getFeature(HealthStatsFeature::class.java)
            if (feature != null) snapshot = feature.currHealthStatsSnapshot()
            return snapshot
        }
        return null
    }

    protected open fun configureSamplers() {
        for ((snapshotClass, interval) in mSampleRegs) {
            val sampler = statSampler(snapshotClass)
            if (sampler != null) {
                sampler.setInterval(interval)
                sampler.start()
            }
        }
    }

    protected open fun configureSampleResults() {
        for ((snapshotClass, sampler) in mSamplers) {
            TraceHarborLog.i(TAG, hashCode().toString() + " " + sampler.getTag() + " #pause: " + mScope)
            sampler.pause()
            val result = sampler.getResult()
            if (result != null) {
                mSampleResults[snapshotClass] = result
            }
        }
    }

    @CallSuper
    protected open fun statSampler(snapshotClass: Class<out Snapshot<*>>): Snapshot.Sampler? {
        var sampler: Snapshot.Sampler? = null
        if (snapshotClass == DeviceStatMonitorFeature.CpuFreqSnapshot::class.java) {
            val feature = getFeature(DeviceStatMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) {
                val cpuStatsFeat = getFeature(CpuStatFeature::class.java)
                if (cpuStatsFeat != null && cpuStatsFeat.isSupported) {
                    mCpuFreqSampler = CpuFreqSampler(BatteryCanaryUtil.getCpuFreqSteps())
                }
                sampler = Snapshot.Sampler("cpufreq", monitor.getHandler(), Function { sampleTick ->
                    val cpuFreqs = BatteryCanaryUtil.getCpuCurrentFreq()
                    val powerProfile = cpuStatsFeat?.powerProfile
                    if (powerProfile != null && cpuStatsFeat.isSupported) {
                        val cpuFreqSampler = mCpuFreqSampler
                        if (cpuFreqSampler != null && cpuFreqSampler.isCompat(powerProfile)) {
                            cpuFreqSampler.count(cpuFreqs)
                        }
                    }
                    val snapshot = feature.currentCpuFreq(cpuFreqs)
                    val list = snapshot.cpuFreqs.list
                    TraceHarborLog.i(TAG, hashCode().toString() + " #onSampling: " + mScope)
                    TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + ", val = " + list)
                    if (list.isEmpty()) {
                        return@Function Snapshot.Sampler.INVALID
                    }
                    var sum = 0L
                    for (item : DigitEntry<Int> in list) {
                        sum += item.get().toLong()
                    }
                    sum
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == DeviceStatMonitorFeature.BatteryTmpSnapshot::class.java) {
            val feature = getFeature(DeviceStatMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) {
                sampler = Snapshot.Sampler("batt-temp", monitor.getHandler(), Function { sampleTick ->
                    val snapshot = feature.currentBatteryTemperature(monitor.getContext())
                    val value = snapshot.temp.get()
                    TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + ", val = " + value)
                    if (value == -1) Snapshot.Sampler.INVALID else value
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == DeviceStatMonitorFeature.ThermalStatSnapshot::class.java) {
            val feature = getFeature(DeviceStatMonitorFeature::class.java)
            val monitor = mMonitor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && feature != null && monitor != null) {
                sampler = Snapshot.Sampler("thermal-stat", monitor.getHandler(), Function { sampleTick ->
                    val value = BatteryCanaryUtil.getThermalStat(monitor.getContext())
                    TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + ", val = " + value)
                    if (value == -1) Snapshot.Sampler.INVALID else value
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == DeviceStatMonitorFeature.ThermalHeadroomSnapshot::class.java) {
            val feature = getFeature(DeviceStatMonitorFeature::class.java)
            val monitor = mMonitor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && feature != null && monitor != null) {
                val interval = mSampleRegs[snapshotClass]
                if (interval != null && interval >= 1000L) {
                    sampler = Snapshot.Sampler("thermal-headroom", monitor.getHandler(), Function { sampleTick ->
                        val value = BatteryCanaryUtil.getThermalHeadroom(monitor.getContext(), (interval / 1000L).toInt())
                        TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + ", val = " + value)
                        if (value == -1f) Snapshot.Sampler.INVALID else value
                    })
                    mSamplers[snapshotClass] = sampler
                }
            }
            return sampler
        }
        if (snapshotClass == DeviceStatMonitorFeature.ChargeWattageSnapshot::class.java) {
            val feature = getFeature(DeviceStatMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) {
                sampler = Snapshot.Sampler("batt-watt", monitor.getHandler(), Function { sampleTick ->
                    val value = BatteryCanaryUtil.getChargingWatt(monitor.getContext())
                    TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + ", val = " + value)
                    if (value == -1) Snapshot.Sampler.INVALID else value
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == CpuStatFeature.CpuStateSnapshot::class.java) {
            val feature = getFeature(CpuStatFeature::class.java)
            val monitor = mMonitor
            if (feature != null && feature.isSupported && monitor != null) {
                sampler = Snapshot.Sampler("cpu-stat", monitor.getHandler(), Function { sampleTick ->
                    val snapshot = feature.currentCpuStateSnapshot()
                    for (i in 0 until snapshot.cpuCoreStates.size) {
                        val item: ListEntry<DigitEntry<Long>> = snapshot.cpuCoreStates[i]
                        TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + " cpuCore" + i + ", val = " + item.list)
                    }
                    for (i in 0 until snapshot.procCpuCoreStates.size) {
                        val item: ListEntry<DigitEntry<Long>> = snapshot.procCpuCoreStates[i]
                        TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + " procCpuCluster" + i + ", val = " + item.list)
                    }
                    0
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == JiffiesMonitorFeature.UidJiffiesSnapshot::class.java) {
            val feature = getFeature(JiffiesMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) {
                var lastSnapshot: JiffiesMonitorFeature.UidJiffiesSnapshot? = null
                sampler = Snapshot.Sampler("uid-jiffies", monitor.getHandler(), Function { sampleTick ->
                    val curr = feature.currentUidJiffiesSnapshot()
                    val last = lastSnapshot
                    if (last != null) {
                        val delta = curr.diff(last)
                        val minute = kotlin.math.max(1L, delta.during / BatteryCanaryUtil.ONE_MIN)
                        val avgUidJiffies = computeAvgJiffies(delta.dlt.totalUidJiffies.get(), delta.during)
                        TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + " avgUidJiffies, val = " + avgUidJiffies + ", minute = " + minute)
                        for (item in delta.dlt.pidDeltaJiffiesList) {
                            val avgPidJiffies = computeAvgJiffies(item.dlt.totalJiffies.get(), delta.during)
                            TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + " avgPidJiffies, val = " + avgPidJiffies + ", minute = " + minute + ", name = " + item.dlt.name)
                        }
                        lastSnapshot = curr
                        avgUidJiffies
                    } else {
                        lastSnapshot = curr
                        0
                    }
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == TrafficMonitorFeature.RadioStatSnapshot::class.java) {
            val feature = getFeature(TrafficMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) {
                sampler = Snapshot.Sampler("traffic", monitor.getHandler(), Function { sampleTick ->
                    val snapshot = feature.currentRadioSnapshot(monitor.getContext())
                    if (snapshot != null) {
                        TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + " wifiRx, val = " + snapshot.wifiRxBytes)
                        TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + " wifiTx, val = " + snapshot.wifiTxBytes)
                        TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + " mobileRx, val = " + snapshot.mobileRxBytes)
                        TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + " mobileTx, val = " + snapshot.mobileTxBytes)
                    }
                    0
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == TrafficMonitorFeature.RadioBpsSnapshot::class.java) {
            val feature = getFeature(TrafficMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) {
                mBpsSampler = BpsSampler()
                sampler = Snapshot.Sampler("trafficBps", monitor.getHandler(), Function {
                    val snapshot = feature.currentRadioBpsSnapshot(monitor.getContext())
                    if (snapshot != null) {
                        mBpsSampler?.count(snapshot)
                    }
                    0
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == DeviceStatMonitorFeature.BatteryCurrentSnapshot::class.java) {
            val feature = getFeature(DeviceStatMonitorFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) {
                sampler = Snapshot.Sampler("batt-curr", monitor.getHandler(), Function { sampleTick ->
                    if (BatteryCanaryUtil.isDeviceCharging(monitor.getContext())) {
                        return@Function Snapshot.Sampler.INVALID
                    }
                    val value = BatteryCanaryUtil.getBatteryCurrencyImmediately(monitor.getContext())
                    TraceHarborLog.i(TAG, "onSampling " + sampleTick.mCount + " " + sampleTick.mTag + ", val = " + value)
                    if (value == -1L) Snapshot.Sampler.INVALID else value
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        if (snapshotClass == HealthStatsSnapshot::class.java) {
            val feature = getFeature(HealthStatsFeature::class.java)
            val monitor = mMonitor
            if (feature != null && monitor != null) {
                sampler = Snapshot.Sampler("health-stats", monitor.getHandler(), Function { sampleTick ->
                    val snapshot = mBgnSnapshots[HealthStatsSnapshot::class.java]
                    if (snapshot is HealthStatsSnapshot) {
                        TraceHarborLog.i(TAG, "onAcc " + sampleTick.mCount + " " + sampleTick.mTag)
                        snapshot.accCollect(feature.currHealthStatsSnapshot())
                    }
                    Snapshot.Sampler.INVALID
                })
                mSamplers[snapshotClass] = sampler
            }
            return sampler
        }
        return null
    }

    protected open fun configureTaskDeltas(featClass: Class<out AbsTaskMonitorFeature>) {
        val appStats = mAppStats
        if (appStats != null) {
            val taskFeat = getFeature(featClass)
            if (taskFeat != null) {
                val deltas = taskFeat.currentJiffies(appStats.duringMillis)
                putTaskDeltas(featClass, deltas)
            }
        }
    }

    protected open fun collectTaskDeltas() {
        if (mTaskDeltas.isNotEmpty()) {
            for ((key, value) in mTaskDeltas) {
                for (taskDelta in value) {
                    if (taskDelta.bgn.time >= mBgnMillis) {
                        var pairList = mTaskDeltasCollect[taskDelta.dlt.name]
                        if (pairList == null) {
                            pairList = ArrayList()
                            mTaskDeltasCollect[taskDelta.dlt.name] = pairList
                        }
                        pairList.add(Pair(key, taskDelta))
                    }
                }
            }
        }
    }

    open fun putTaskDeltas(key: Class<out AbsTaskMonitorFeature>, deltas: List<Delta<TaskJiffiesSnapshot>>) {
        mTaskDeltas[key] = deltas
    }

    open fun getTaskDeltas(key: Class<out AbsTaskMonitorFeature>): List<Delta<TaskJiffiesSnapshot>> {
        return mTaskDeltas[key] ?: Collections.emptyList()
    }

    open fun getTaskDeltas(key: Class<out AbsTaskMonitorFeature>, block: Consumer<List<Delta<TaskJiffiesSnapshot>>>) {
        val deltas = mTaskDeltas[key]
        if (deltas != null) {
            block.accept(deltas)
        }
    }

    open fun getCollectedTaskDeltas(): Map<String, List<Pair<Class<out AbsTaskMonitorFeature>, Delta<TaskJiffiesSnapshot>>>> {
        if (mTaskDeltasCollect.size <= 1) {
            return mTaskDeltasCollect
        }
        return BatteryCanaryUtil.sortMapByValue(mTaskDeltasCollect, Comparator { o1, o2 ->
            var sumLeft = 0L
            var sumRight = 0L
            for (item in o1.value) {
                sumLeft += item.second!!.dlt.jiffies.get()
            }
            for (item in o2.value) {
                sumRight += item.second!!.dlt.jiffies.get()
            }
            val minus = sumLeft - sumRight
            when {
                minus == 0L -> 0
                minus > 0L -> -1
                else -> 1
            }
        })
    }

    open fun getCollectedTaskDeltas(block: Consumer<Map<String, List<Pair<Class<out AbsTaskMonitorFeature>, Delta<TaskJiffiesSnapshot>>>>>) {
        block.accept(getCollectedTaskDeltas())
    }

    open fun getAllPidDeltaList(block: Consumer<List<Delta<JiffiesSnapshot>>>) {
        block.accept(getAllPidDeltaList())
    }

    open fun getAllPidDeltaList(): List<Delta<JiffiesSnapshot>> {
        val delta = getDelta(JiffiesMonitorFeature.UidJiffiesSnapshot::class.java)
        if (delta == null) {
            val pidDelta = getDelta(JiffiesSnapshot::class.java)
            if (pidDelta != null) {
                return Collections.singletonList(pidDelta)
            }
            return Collections.emptyList()
        }
        return delta.dlt.pidDeltaJiffiesList
    }

    open fun getStacks(): Map<String, String> = mStacks

    open fun getExtras(): Bundle = mExtras

    open fun getCpuFreqSampler(): CpuFreqSampler? = mCpuFreqSampler

    open fun getBpsSampler(): BpsSampler? = mBpsSampler

    override fun toString(): String {
        return "CompositeMonitors{\n" +
            "Metrics=" + mMetrics + "\n" +
            ", BgnSnapshots=" + mBgnSnapshots + "\n" +
            ", Deltas=" + mDeltas + "\n" +
            ", SampleRegs=" + mSampleRegs + "\n" +
            ", Samplers=" + mSamplers + "\n" +
            ", SampleResults=" + mSampleResults + "\n" +
            ", TaskDeltas=" + mTaskDeltas + "\n" +
            ", AppStats=" + mAppStats + "\n" +
            ", Stacks=" + mStacks + "\n" +
            ", Extras =" + mExtras + "\n" +
            '}'
    }

    open class CpuFreqSampler(
        @JvmField val cpuFreqSteps: List<IntArray>,
    ) {
        @JvmField
        var cpuCurrentFreq: IntArray? = null

        @JvmField
        val cpuFreqCounters: MutableList<IntArray> = ArrayList(cpuFreqSteps.size)

        init {
            for (item in cpuFreqSteps) {
                cpuFreqCounters.add(IntArray(item.size))
            }
        }

        open fun isCompat(powerProfile: PowerProfile): Boolean {
            if (cpuFreqSteps.size == powerProfile.cpuCoreNum) {
                for (i in cpuFreqSteps.indices) {
                    val clusterByCpuNum = powerProfile.getClusterByCpuNum(i)
                    val steps = powerProfile.getNumSpeedStepsInCpuCluster(clusterByCpuNum)
                    if (cpuFreqSteps[i].size != steps) {
                        return false
                    }
                }
                return true
            }
            return false
        }

        open fun count(cpuCurrentFreq: IntArray) {
            this.cpuCurrentFreq = cpuCurrentFreq
            for (i in cpuCurrentFreq.indices) {
                val speed = cpuCurrentFreq[i]
                val steps = cpuFreqSteps[i]
                if (speed < steps[0]) {
                    cpuFreqCounters[i][0]++
                    continue
                }
                var found = false
                for (j in steps.indices) {
                    if (speed <= steps[j]) {
                        cpuFreqCounters[i][j]++
                        found = true
                        break
                    }
                }
                if (!found && speed > steps[steps.size - 1]) {
                    cpuFreqCounters[i][steps.size - 1]++
                }
            }
        }
    }

    open class BpsSampler {
        @JvmField
        var count: Int = 0

        @JvmField
        var wifiRxBps: Long = 0

        @JvmField
        var wifiTxBps: Long = 0

        @JvmField
        var mobileRxBps: Long = 0

        @JvmField
        var mobileTxBps: Long = 0

        open fun count(snapshot: TrafficMonitorFeature.RadioBpsSnapshot) {
            count++
            wifiRxBps += snapshot.wifiRxBps.get()
            wifiTxBps += snapshot.wifiTxBps.get()
            mobileRxBps += snapshot.mobileRxBps.get()
            mobileTxBps += snapshot.mobileTxBps.get()
        }

        open fun getAverage(input: Long): Double {
            return if (count != 0) input.toDouble() / count else 0.0
        }
    }

    protected open fun polishEstimatedPower() {
        getDelta(HealthStatsSnapshot::class.java, Consumer { healthStatsDelta ->
            tuningPowers(healthStatsDelta.dlt)
            run {
                var power = 0.0
                val powers = healthStatsDelta.dlt.extras["JiffyUid"]
                if (powers is Map<*, *>) {
                    val value = powers["power-cpu-uidDiff"]
                    if (value is Double) power = value
                }
                healthStatsDelta.dlt.cpuPower = DigitEntry.of(power)
            }
            run {
                if (healthStatsDelta.dlt.mobilePower.get() <= 0) {
                    var power = 0.0
                    val value = healthStatsDelta.dlt.extras["power-mobile-statByte"]
                    if (value is Double) power = value
                    healthStatsDelta.dlt.mobilePower = DigitEntry.of(power)
                }
            }
            run {
                if (healthStatsDelta.dlt.wifiPower.get() <= 0) {
                    var power = 0.0
                    val value = healthStatsDelta.dlt.extras["power-wifi-statByte"]
                    if (value is Double) power = value
                    healthStatsDelta.dlt.wifiPower = DigitEntry.of(power)
                }
            }
        })
    }

    protected open fun tuningPowers(snapshot: HealthStatsSnapshot) {
        if (!snapshot.isDelta) {
            throw IllegalStateException("Only support delta snapshot")
        }
        val monitor = getMonitor() ?: return
        snapshot.extras = HashMap()
        val tunning = monitor.getConfig().isTuningPowers
        val tuner = Tuner()

        getFeature(CpuStatFeature::class.java, Consumer { feat ->
            if (feat.isSupported) {
                val powerProfile = feat.powerProfile ?: return@Consumer
                getDelta(CpuStatFeature.CpuStateSnapshot::class.java, Consumer { _ ->
                    if (tunning) {
                        getDelta(HealthStatsSnapshot::class.java, Consumer { healthStats ->
                            healthStats.dlt.extras["TimeUid"] = tuner.tuningCpuPowers(
                                powerProfile,
                                this,
                                object : Tuner.CpuTime {
                                    override fun getBgnMs(procSuffix: String?): Long {
                                        return getCpuTimeMs(procSuffix, healthStats.bgn)
                                    }

                                    override fun getEndMs(procSuffix: String?): Long {
                                        return getCpuTimeMs(procSuffix, healthStats.end)
                                    }

                                    override fun getDltMs(procSuffix: String?): Long {
                                        return getCpuTimeMs(procSuffix, healthStats.dlt)
                                    }

                                    private fun getCpuTimeMs(procSuffix: String?, healthStatsSnapshot: HealthStatsSnapshot): Long {
                                        if (procSuffix == null) {
                                            return healthStatsSnapshot.cpuUsrTimeMs.get() + healthStatsSnapshot.cpuSysTimeMs.get()
                                        }
                                        val currentMonitor = mMonitor ?: return 0L
                                        var procName = currentMonitor.getContext().packageName
                                        if ("main" == procSuffix) {
                                            procName = currentMonitor.getContext().packageName + ":" + procSuffix
                                        }
                                        val usrTime = healthStatsSnapshot.procStatsCpuUsrTimeMs[procName]
                                        val sysTime = healthStatsSnapshot.procStatsCpuSysTimeMs[procName]
                                        return (usrTime?.get() ?: 0L) + (sysTime?.get() ?: 0L)
                                    }
                                },
                            )
                        })
                    }

                    getDelta(JiffiesMonitorFeature.UidJiffiesSnapshot::class.java, Consumer { delta ->
                        snapshot.extras["JiffyUid"] = tuner.tuningCpuPowers(
                            powerProfile,
                            this,
                            object : Tuner.CpuTime {
                                override fun getBgnMs(procSuffix: String?): Long {
                                    if (procSuffix == null) {
                                        return delta.bgn.totalUidJiffies.get() * 10L
                                    }
                                    for (item in delta.bgn.pidCurrJiffiesList) {
                                        if (item.name == procSuffix) {
                                            return item.totalJiffies.get() * 10L
                                        }
                                    }
                                    return 0L
                                }

                                override fun getEndMs(procSuffix: String?): Long {
                                    if (procSuffix == null) {
                                        return delta.end.totalUidJiffies.get() * 10L
                                    }
                                    for (item in delta.end.pidCurrJiffiesList) {
                                        if (item.name == procSuffix) {
                                            return item.totalJiffies.get() * 10L
                                        }
                                    }
                                    return 0L
                                }

                                override fun getDltMs(procSuffix: String?): Long {
                                    if (procSuffix == null) {
                                        return delta.dlt.totalUidJiffies.get() * 10L
                                    }
                                    for (item in delta.dlt.pidDeltaJiffiesList) {
                                        if (item.dlt.name == procSuffix) {
                                            return item.dlt.totalJiffies.get() * 10L
                                        }
                                    }
                                    return 0L
                                }
                            },
                        )
                    })
                })

                run {
                    var mobileRxBps = 0.0
                    var mobileTxBps = 0.0
                    var wifiRxBps = 0.0
                    var wifiTxBps = 0.0
                    val bpsSampler = getBpsSampler()
                    if (bpsSampler != null) {
                        mobileRxBps = bpsSampler.getAverage(bpsSampler.mobileRxBps)
                        mobileTxBps = bpsSampler.getAverage(bpsSampler.mobileTxBps)
                        wifiRxBps = bpsSampler.getAverage(bpsSampler.wifiRxBps)
                        wifiTxBps = bpsSampler.getAverage(bpsSampler.wifiTxBps)
                    } else {
                        val currentMonitor = mMonitor
                        if (currentMonitor != null) {
                            val bpsStat = RadioStatUtil.getCurrentBps(currentMonitor.getContext())
                            if (bpsStat != null) {
                                mobileRxBps = bpsStat.mobileRxBps.toDouble()
                                mobileTxBps = bpsStat.mobileTxBps.toDouble()
                                wifiRxBps = bpsStat.wifiRxBps.toDouble()
                                wifiTxBps = bpsStat.wifiTxBps.toDouble()
                            }
                        }
                    }
                    val finalMobileRxBps = mobileRxBps
                    val finalMobileTxBps = mobileTxBps
                    val finalWifiRxBps = wifiRxBps
                    val finalWifiTxBps = wifiTxBps

                    getDelta(HealthStatsSnapshot::class.java, Consumer { delta ->
                        @SuppressLint("VisibleForTests")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            run {
                                var power = 0.0
                                if (delta.bgn.healthStats != null && delta.end.healthStats != null) {
                                    val powerBgn = HealthStatsHelper.calcMobilePowerByRadioActive(powerProfile, delta.bgn.healthStats)
                                    val powerEnd = HealthStatsHelper.calcMobilePowerByRadioActive(powerProfile, delta.end.healthStats)
                                    power = powerEnd - powerBgn
                                }
                                snapshot.extras["power-mobile-radio"] = power
                            }
                            run {
                                var power = 0.0
                                if (delta.bgn.healthStats != null && delta.end.healthStats != null) {
                                    val powerBgn = HealthStatsHelper.calcMobilePowerByController(powerProfile, delta.bgn.healthStats)
                                    val powerEnd = HealthStatsHelper.calcMobilePowerByController(powerProfile, delta.end.healthStats)
                                    power = powerEnd - powerBgn
                                }
                                snapshot.extras["power-mobile-controller"] = power
                            }
                            run {
                                var power = 0.0
                                if (delta.bgn.healthStats != null && delta.end.healthStats != null) {
                                    val powerBgn = HealthStatsHelper.calcMobilePowerByPackets(powerProfile, delta.bgn.healthStats, finalMobileRxBps, finalMobileTxBps)
                                    val powerEnd = HealthStatsHelper.calcMobilePowerByPackets(powerProfile, delta.end.healthStats, finalMobileRxBps, finalMobileTxBps)
                                    power = powerEnd - powerBgn
                                }
                                snapshot.extras["power-mobile-packet"] = power
                            }
                            run {
                                var power = 0.0
                                if (delta.bgn.healthStats != null && delta.end.healthStats != null) {
                                    val powerBgn = HealthStatsHelper.calcWifiPowerByController(powerProfile, delta.bgn.healthStats)
                                    val powerEnd = HealthStatsHelper.calcWifiPowerByController(powerProfile, delta.end.healthStats)
                                    power = powerEnd - powerBgn
                                }
                                snapshot.extras["power-wifi-controller"] = power
                            }
                            run {
                                var power = 0.0
                                if (delta.bgn.healthStats != null && delta.end.healthStats != null) {
                                    val powerBgn = HealthStatsHelper.calcWifiPowerByPackets(powerProfile, delta.bgn.healthStats, finalWifiRxBps, finalWifiTxBps)
                                    val powerEnd = HealthStatsHelper.calcWifiPowerByPackets(powerProfile, delta.end.healthStats, finalWifiRxBps, finalWifiTxBps)
                                    power = powerEnd - powerBgn
                                }
                                snapshot.extras["power-wifi-packet"] = power
                            }
                        }
                    })

                    getDelta(TrafficMonitorFeature.RadioStatSnapshot::class.java, Consumer { delta ->
                        @SuppressLint("VisibleForTests")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mMonitor != null) {
                            val currentBpsSampler = getBpsSampler()
                            if (currentBpsSampler != null) {
                                var power = HealthStatsHelper.calcMobilePowerByNetworkStatBytes(powerProfile, delta.dlt, finalMobileRxBps, finalMobileTxBps)
                                snapshot.extras["power-mobile-statByte"] = power
                                power = HealthStatsHelper.calcMobilePowerByNetworkStatPackets(powerProfile, delta.dlt, finalMobileRxBps, finalMobileTxBps)
                                snapshot.extras["power-mobile-statPacket"] = power
                                power = HealthStatsHelper.calcWifiPowerByNetworkStatBytes(powerProfile, delta.dlt, finalWifiRxBps, finalWifiTxBps)
                                snapshot.extras["power-wifi-statByte"] = power
                                power = HealthStatsHelper.calcWifiPowerByNetworkStatPackets(powerProfile, delta.dlt, finalWifiRxBps, finalWifiTxBps)
                                snapshot.extras["power-wifi-statPacket"] = power
                            }
                        }
                    })
                }
            }
        })
    }

    @SuppressLint("RestrictedApi")
    protected open class Tuner {
        interface CpuTime {
            fun getBgnMs(procSuffix: String?): Long
            fun getEndMs(procSuffix: String?): Long
            fun getDltMs(procSuffix: String?): Long
        }

        @SuppressLint("VisibleForTests")
        open fun tuningCpuPowers(powerProfile: PowerProfile, monitors: CompositeMonitors, cpuTime: CpuTime): Map<String, Any> {
            val dict: MutableMap<String, Any> = LinkedHashMap()
            monitors.getDelta(CpuStatFeature.UidCpuStateSnapshot::class.java, Consumer { uidCpuStatDelta ->
                val monitor = monitors.getMonitor() ?: return@Consumer
                run {
                    var cpuPower = 0.0
                    val scaled = false
                    for (cpuStateDelta in uidCpuStatDelta.dlt.pidDeltaCpuSateList) {
                        val cpuStatsSnapshot = cpuStateDelta.dlt
                        val cpuTimeMs = cpuTime.getDltMs(cpuStatsSnapshot.name)
                        cpuPower += HealthStatsHelper.estimateCpuActivePower(powerProfile, cpuTimeMs) +
                            HealthStatsHelper.estimateCpuClustersPowerByUidStats(powerProfile, cpuStatsSnapshot, cpuTimeMs, scaled) +
                            HealthStatsHelper.estimateCpuCoresPowerByUidStats(powerProfile, cpuStatsSnapshot, cpuTimeMs, scaled)
                    }
                    dict["power-cpu-uidDiff"] = cpuPower
                }

                val tunning = monitor.getConfig().isTuningPowers
                if (!tunning) {
                    return@Consumer
                }

                run {
                    var cpuPower = 0.0
                    val scaled = true
                    for (cpuStateDelta in uidCpuStatDelta.dlt.pidDeltaCpuSateList) {
                        val cpuStatsSnapshot = cpuStateDelta.dlt
                        val cpuTimeMs = cpuTime.getDltMs(cpuStatsSnapshot.name)
                        cpuPower += HealthStatsHelper.estimateCpuActivePower(powerProfile, cpuTimeMs) +
                            HealthStatsHelper.estimateCpuClustersPowerByUidStats(powerProfile, cpuStatsSnapshot, cpuTimeMs, scaled) +
                            HealthStatsHelper.estimateCpuCoresPowerByUidStats(powerProfile, cpuStatsSnapshot, cpuTimeMs, scaled)
                    }
                    dict["power-cpu-uidDiffScale"] = cpuPower
                }

                monitors.getDelta(CpuStatFeature.CpuStateSnapshot::class.java, Consumer { pidCpuStatDelta ->
                    run {
                        val cpuStatsSnapshot = pidCpuStatDelta.dlt
                        val cpuTimeMs = cpuTime.getDltMs(null)
                        val cpuPower = HealthStatsHelper.estimateCpuActivePower(powerProfile, cpuTimeMs) +
                            HealthStatsHelper.estimateCpuClustersPowerByDevStats(powerProfile, cpuStatsSnapshot, cpuTimeMs) +
                            HealthStatsHelper.estimateCpuCoresPowerByDevStats(powerProfile, cpuStatsSnapshot, cpuTimeMs)
                        dict["power-cpu-devDiff"] = cpuPower
                    }
                    run {
                        val cpuFreqSampler = monitors.getCpuFreqSampler()
                        if (cpuFreqSampler != null && cpuFreqSampler.isCompat(powerProfile)) {
                            val cpuTimeMs = cpuTime.getDltMs(null)
                            val cpuPower = HealthStatsHelper.estimateCpuActivePower(powerProfile, cpuTimeMs) +
                                estimateCpuPowerByCpuFreqStats(powerProfile, cpuFreqSampler, cpuTimeMs)
                            dict["power-cpu-cpuFreq"] = cpuPower
                        }
                    }
                })
            })
            return dict
        }

        companion object {
            @JvmStatic
            private fun estimateCpuPowerByCpuFreqStats(powerProfile: PowerProfile, sampler: CpuFreqSampler, cpuTimeMs: Long): Double {
                var powerMah = 0.0
                if (cpuTimeMs > 0) {
                    var totalSum = 0L
                    for (i in sampler.cpuFreqCounters.indices) {
                        for (j in sampler.cpuFreqCounters[i].indices) {
                            totalSum += sampler.cpuFreqCounters[i][j].toLong()
                        }
                    }
                    if (totalSum > 0) {
                        for (i in sampler.cpuFreqCounters.indices) {
                            val clusterNum = powerProfile.getClusterByCpuNum(i)
                            var coreSum = 0L
                            for (j in sampler.cpuFreqCounters[i].indices) {
                                val step = sampler.cpuFreqCounters[i][j]
                                if (step > 0) {
                                    val figuredCoreTimeMs = ((step * 1.0f / totalSum) * cpuTimeMs).toLong()
                                    val powerMa = powerProfile.getAveragePowerForCpuCore(clusterNum, j)
                                    powerMah += HealthStatsHelper.UsageBasedPowerEstimator(powerMa).calculatePower(figuredCoreTimeMs)
                                }
                                coreSum += step.toLong()
                            }
                            if (coreSum > 0) {
                                val figuredClusterTimeMs = ((coreSum * 1.0f / totalSum) * cpuTimeMs).toLong()
                                val powerMa = powerProfile.getAveragePowerForCpuCluster(clusterNum)
                                powerMah += HealthStatsHelper.UsageBasedPowerEstimator(powerMa).calculatePower(figuredClusterTimeMs)
                            }
                        }
                    }
                }
                return powerMah
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.CompositeMonitors"

        const val SCOPE_UNKNOWN: String = "unknown"
        const val SCOPE_CANARY: String = "canary"
        const val SCOPE_INTERNAL: String = "internal"
        const val SCOPE_OVERHEAT: String = "overheat"
        const val SCOPE_TOP_SHELL: String = "topShell"
        const val SCOPE_TOP_INDICATOR: String = "topIndicator"

        @JvmStatic
        fun computeAvgJiffies(jiffies: Long, millis: Long): Long {
            if (millis <= 0) {
                throw IllegalArgumentException("Illegal millis: $millis")
            }
            return (jiffies / (millis / 60000f)).toLong()
        }
    }
}
