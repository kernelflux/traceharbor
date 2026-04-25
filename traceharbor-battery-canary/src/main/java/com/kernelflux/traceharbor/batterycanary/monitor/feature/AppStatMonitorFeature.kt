package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.kernelflux.traceharbor.batterycanary.BatteryEventDelegate
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.APP_STAT_BACKGROUND
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.APP_STAT_FLOAT_WINDOW
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.APP_STAT_FOREGROUND
import com.kernelflux.traceharbor.batterycanary.monitor.AppStats.APP_STAT_FOREGROUND_SERVICE
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Differ
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.stats.BatteryRecord
import com.kernelflux.traceharbor.batterycanary.stats.BatteryStatsFeature
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.TimeBreaker
import com.kernelflux.traceharbor.lifecycle.IStateObserver
import com.kernelflux.traceharbor.lifecycle.owners.ForegroundServiceLifecycleOwner
import com.kernelflux.traceharbor.lifecycle.owners.OverlayWindowLifecycleOwner
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Collections
import kotlin.math.max

/**
 * @author Kaede
 * @since 2020/12/8
 */
@Suppress("MemberVisibilityCanBePrivate", "PropertyName", "DEPRECATION")
class AppStatMonitorFeature : AbsMonitorFeature() {
    interface AppStatListener {
        fun onForegroundServiceLeak(
            isMyself: Boolean,
            appImportance: Int,
            globalAppImportance: Int,
            componentName: ComponentName,
            millis: Long
        )

        fun onAppSateLeak(
            isMyself: Boolean,
            appImportance: Int,
            componentName: ComponentName,
            millis: Long
        )
    }

    @JvmField
    var mAppImportance: Int = IMPORTANCE_LEAST

    @JvmField
    var mGlobalAppImportance: Int = IMPORTANCE_LEAST

    @JvmField
    var mForegroundServiceImportanceLimit: Int = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    @JvmField
    var mStampList: MutableList<TimeBreaker.Stamp> = EMPTY_STAMP_LIST

    @JvmField
    var mSceneStampList: MutableList<TimeBreaker.Stamp> = EMPTY_STAMP_LIST

    @JvmField
    val coolingTask: Runnable = Runnable {
        if (mStampList.size >= mCore.getConfig().overHeatCount) {
            synchronized(TAG) {
                TimeBreaker.gcList(mStampList)
            }
        }
        if (mSceneStampList.size >= mCore.getConfig().overHeatCount) {
            synchronized(TAG) {
                TimeBreaker.gcList(mSceneStampList)
            }
        }
    }

    private val mFgSrvObserver: IStateObserver = object : IStateObserver {
        override fun on() {
            TraceHarborLog.i(TAG, "fgSrv >> on")
            val foreground = mCore.isForeground()
            val appStat = BatteryCanaryUtil.getAppStatImmediately(mCore.getContext(), foreground)
            if (appStat != APP_STAT_FOREGROUND) {
                TraceHarborLog.i(TAG, "statAppStat: $APP_STAT_FOREGROUND_SERVICE")
                onStatAppStat(APP_STAT_FOREGROUND_SERVICE)
            } else {
                TraceHarborLog.i(TAG, "skip statAppStat, fg = $foreground, currAppStat = $appStat")
            }
        }

        override fun off() {
            TraceHarborLog.i(TAG, "fgSrv >> off")
            val foreground = mCore.isForeground()
            val appStat = BatteryCanaryUtil.getAppStatImmediately(mCore.getContext(), foreground)
            if (appStat != APP_STAT_FOREGROUND &&
                appStat != APP_STAT_FOREGROUND_SERVICE &&
                appStat != APP_STAT_FLOAT_WINDOW
            ) {
                TraceHarborLog.i(TAG, "statAppStat: $APP_STAT_BACKGROUND")
                onStatAppStat(APP_STAT_BACKGROUND)
            } else {
                TraceHarborLog.i(TAG, "skip statAppStat, fg = $foreground, currAppStat = $appStat")
            }
        }
    }

    private val mFloatViewObserver: IStateObserver = object : IStateObserver {
        override fun on() {
            TraceHarborLog.i(TAG, "floatView >> on")
            val foreground = mCore.isForeground()
            val appStat = BatteryCanaryUtil.getAppStatImmediately(mCore.getContext(), foreground)
            if (appStat != APP_STAT_FOREGROUND && appStat != APP_STAT_FOREGROUND_SERVICE) {
                TraceHarborLog.i(TAG, "statAppStat: $APP_STAT_FLOAT_WINDOW")
                onStatAppStat(APP_STAT_FLOAT_WINDOW)
            } else {
                TraceHarborLog.i(TAG, "skip statAppStat, fg = $foreground, currAppStat = $appStat")
            }
        }

        override fun off() {
            TraceHarborLog.i(TAG, "floatView >> off")
            val foreground = mCore.isForeground()
            val appStat = BatteryCanaryUtil.getAppStatImmediately(mCore.getContext(), foreground)
            if (appStat != APP_STAT_FOREGROUND &&
                appStat != APP_STAT_FOREGROUND_SERVICE &&
                appStat != APP_STAT_FLOAT_WINDOW
            ) {
                TraceHarborLog.i(TAG, "statAppStat: $APP_STAT_BACKGROUND")
                onStatAppStat(APP_STAT_BACKGROUND)
            } else {
                TraceHarborLog.i(TAG, "skip statAppStat, fg = $foreground, currAppStat = $appStat")
            }
        }
    }

    protected override fun getTag(): String = TAG

    override fun configure(monitor: BatteryMonitorCore) {
        super.configure(monitor)
        mForegroundServiceImportanceLimit = max(
            monitor.getConfig().foregroundServiceLeakLimit,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        )
    }

    override fun onTurnOn() {
        super.onTurnOn()
        val firstStamp = TimeBreaker.Stamp(APP_STAT_FOREGROUND.toString())
        val firstSceneStamp = TimeBreaker.Stamp(mCore.getScene())
        synchronized(TAG) {
            mStampList = ArrayList()
            mStampList.add(0, firstStamp)
            mSceneStampList = ArrayList()
            mSceneStampList.add(0, firstSceneStamp)
        }

        ForegroundServiceLifecycleOwner.observeForever(mFgSrvObserver)
        OverlayWindowLifecycleOwner.observeForever(mFloatViewObserver)
    }

    override fun onTurnOff() {
        super.onTurnOff()
        ForegroundServiceLifecycleOwner.removeObserver(mFgSrvObserver)
        OverlayWindowLifecycleOwner.removeObserver(mFloatViewObserver)
        synchronized(TAG) {
            mStampList.clear()
            mSceneStampList.clear()
        }
    }

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
        val appStat = BatteryCanaryUtil.getAppStatImmediately(mCore.getContext(), isForeground)
        BatteryCanaryUtil.getProxy().updateAppStat(appStat)
        onStatAppStat(appStat)

        TraceHarborLog.i(TAG, "updateAppImportance when app " + if (isForeground) "foreground" else "background")
        updateAppImportance()
    }

    @WorkerThread
    override fun onBackgroundCheck(duringMillis: Long) {
        super.onBackgroundCheck(duringMillis)
        TraceHarborLog.i(TAG, "#onBackgroundCheck, during = $duringMillis")

        if (mGlobalAppImportance > mForegroundServiceImportanceLimit || mAppImportance > mForegroundServiceImportanceLimit) {
            val context = mCore.getContext()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val runningServices = am.getRunningServices(Int.MAX_VALUE) ?: return

            for (item in runningServices) {
                if (!TextUtils.isEmpty(item.process) && item.process.startsWith(context.packageName)) {
                    if (item.foreground) {
                        TraceHarborLog.i(TAG, "checkForegroundService whether app importance is low, during = $duringMillis")
                        if (mGlobalAppImportance > mForegroundServiceImportanceLimit) {
                            TraceHarborLog.w(
                                TAG,
                                "foreground service detected with low global importance: " +
                                    "$mAppImportance, $mGlobalAppImportance, ${item.service}"
                            )
                            mCore.onForegroundServiceLeak(
                                false,
                                mAppImportance,
                                mGlobalAppImportance,
                                item.service,
                                duringMillis
                            )
                        }

                        if (mAppImportance > mForegroundServiceImportanceLimit) {
                            if (item.process == context.packageName) {
                                TraceHarborLog.w(
                                    TAG,
                                    "foreground service detected with low app importance: " +
                                        "$mAppImportance, $mGlobalAppImportance, ${item.service}"
                                )
                                mCore.onForegroundServiceLeak(
                                    true,
                                    mAppImportance,
                                    mGlobalAppImportance,
                                    item.service,
                                    duringMillis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onStatAppStat(appStat: Int) {
        synchronized(TAG) {
            if (mStampList !== EMPTY_STAMP_LIST) {
                TraceHarborLog.i(BatteryEventDelegate.TAG, "onStat >> " + BatteryCanaryUtil.convertAppStat(appStat))
                mStampList.add(0, TimeBreaker.Stamp(appStat.toString()))
                checkOverHeat()
            }
        }
    }

    @Suppress("unused")
    fun onStatScene(scene: String) {
        val statsFeature = mCore.getMonitorFeature(BatteryStatsFeature::class.java)
        if (statsFeature != null) {
            val statRecord = BatteryRecord.SceneStatRecord()
            statRecord.scene = scene
            statsFeature.writeRecord(statRecord)
        }

        synchronized(TAG) {
            if (mSceneStampList !== EMPTY_STAMP_LIST) {
                mSceneStampList.add(0, TimeBreaker.Stamp(scene))
                checkOverHeat()
            }
        }

        TraceHarborLog.i(TAG, "updateAppImportance when launch: $scene")
        updateAppImportance()
    }

    private fun checkOverHeat() {
        mCore.getHandler().removeCallbacks(coolingTask)
        mCore.getHandler().postDelayed(coolingTask, 1000L)
    }

    private fun updateAppImportance() {
        if (mAppImportance <= mForegroundServiceImportanceLimit &&
            mGlobalAppImportance <= mForegroundServiceImportanceLimit
        ) {
            return
        }

        val runnable = Runnable {
            val context = mCore.getContext()
            var mainProc = context.packageName
            if (mainProc.contains(":")) {
                mainProc = mainProc.substring(0, mainProc.indexOf(":"))
            }

            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@Runnable
            val processes = am.runningAppProcesses ?: return@Runnable

            for (item in processes) {
                if (item.processName.startsWith(mainProc)) {
                    if (mGlobalAppImportance > item.importance) {
                        TraceHarborLog.i(
                            TAG,
                            "update global importance: $mGlobalAppImportance > ${item.importance}" +
                                ", reason = ${item.importanceReasonComponent}"
                        )
                        mGlobalAppImportance = item.importance
                    }
                    if (item.processName == context.packageName) {
                        if (mAppImportance > item.importance) {
                            TraceHarborLog.i(
                                TAG,
                                "update app importance: $mAppImportance > ${item.importance}" +
                                    ", reason = ${item.importanceReasonComponent}"
                            )
                            mAppImportance = item.importance
                        }
                    }
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            mCore.getHandler().post(runnable)
        } else {
            runnable.run()
        }
    }

    @Suppress("unused")
    private fun checkBackgroundAppState(duringMillis: Long) {
        val runnable = Runnable {
            val context = mCore.getContext()
            var mainProc = context.packageName
            if (mainProc.contains(":")) {
                mainProc = mainProc.substring(0, mainProc.indexOf(":"))
            }

            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@Runnable
            val processes = am.runningAppProcesses ?: return@Runnable

            TraceHarborLog.i(TAG, "Dump backgroud app sate:")
            for (item in processes) {
                if (item.processName.startsWith(mainProc)) {
                    if (item.importance <= mForegroundServiceImportanceLimit) {
                        TraceHarborLog.w(
                            TAG,
                            " + ${item.processName}, proc = ${item.importance}, reason = ${item.importanceReasonComponent}"
                        )
                        mCore.onAppSateLeak(
                            item.processName == context.packageName,
                            item.importance,
                            item.importanceReasonComponent,
                            duringMillis
                        )
                    } else {
                        TraceHarborLog.i(
                            TAG,
                            " - ${item.processName}, proc = ${item.importance}, reason = ${item.importanceReasonComponent}"
                        )
                    }
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            mCore.getHandler().post(runnable)
        } else {
            runnable.run()
        }
    }

    override fun weight(): Int = Int.MAX_VALUE

    fun currentAppStatSnapshot(): AppStatSnapshot = currentAppStatSnapshot(0L)

    fun currentAppStatSnapshot(windowMillis: Long): AppStatSnapshot {
        return try {
            val timePortions = TimeBreaker.configurePortions(
                mStampList,
                windowMillis,
                10L
            ) {
                val appStat = BatteryCanaryUtil.getAppStat(mCore.getContext(), mCore.isForeground())
                TimeBreaker.Stamp(appStat.toString())
            }
            val snapshot = AppStatSnapshot()
            snapshot.setValid(timePortions.isValid)
            snapshot.uptime = DigitEntry.of(timePortions.totalUptime)
            snapshot.fgRatio = DigitEntry.of(timePortions.getRatio(APP_STAT_FOREGROUND.toString()).toLong())
            snapshot.bgRatio = DigitEntry.of(timePortions.getRatio(APP_STAT_BACKGROUND.toString()).toLong())
            snapshot.fgSrvRatio = DigitEntry.of(timePortions.getRatio(APP_STAT_FOREGROUND_SERVICE.toString()).toLong())
            snapshot.floatRatio = DigitEntry.of(timePortions.getRatio(APP_STAT_FLOAT_WINDOW.toString()).toLong())
            snapshot
        } catch (e: Throwable) {
            TraceHarborLog.w(TAG, "configureSnapshot fail: " + e.message)
            val snapshot = AppStatSnapshot()
            snapshot.setValid(false)
            snapshot
        }
    }

    fun currentSceneSnapshot(): TimeBreaker.TimePortions = currentSceneSnapshot(0L)

    fun currentSceneSnapshot(windowMillis: Long): TimeBreaker.TimePortions {
        return try {
            TimeBreaker.configurePortions(
                mSceneStampList,
                windowMillis,
                10L
            ) {
                TimeBreaker.Stamp(mCore.getScene())
            }
        } catch (e: Throwable) {
            TraceHarborLog.w(TAG, "currentSceneSnapshot fail: " + e.message)
            TimeBreaker.TimePortions.ofInvalid()
        }
    }

    fun getAppStatStampList(): List<TimeBreaker.Stamp> {
        if (mStampList.isEmpty()) return Collections.emptyList()
        return ArrayList(mStampList)
    }

    fun getSceneStampList(): List<TimeBreaker.Stamp> {
        if (mSceneStampList.isEmpty()) return Collections.emptyList()
        return ArrayList(mSceneStampList)
    }

    class AppStatSnapshot internal constructor() : Snapshot<AppStatSnapshot>() {
        @JvmField
        var uptime: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var fgRatio: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var bgRatio: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var fgSrvRatio: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var floatRatio: DigitEntry<Long> = DigitEntry.of(0L)

        override fun diff(bgn: AppStatSnapshot): Delta<AppStatSnapshot> {
            return object : Delta<AppStatSnapshot>(bgn, this) {
                override fun computeDelta(): AppStatSnapshot {
                    val delta = AppStatSnapshot()
                    delta.uptime = Differ.DigitDiffer.globalDiff(bgn.uptime, end.uptime)
                    delta.fgRatio = Differ.DigitDiffer.globalDiff(bgn.fgRatio, end.fgRatio)
                    delta.bgRatio = Differ.DigitDiffer.globalDiff(bgn.bgRatio, end.bgRatio)
                    delta.fgSrvRatio = Differ.DigitDiffer.globalDiff(bgn.fgSrvRatio, end.fgSrvRatio)
                    return delta
                }
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.AppStatMonitorFeature"

        /**
         * Less important than [ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE].
         */
        const val IMPORTANCE_LEAST: Int = 1024

        private val EMPTY_STAMP_LIST: MutableList<TimeBreaker.Stamp> = Collections.emptyList()
    }
}
