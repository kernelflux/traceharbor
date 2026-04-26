package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.annotation.SuppressLint
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Pair
import android.util.SparseArray
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorConfig.Companion.DEF_STAMP_OVERHEAT
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.ProcStatUtil
import com.kernelflux.traceharbor.batterycanary.utils.TimeBreaker
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.tencent.mmkv.BuildConfig
import java.util.LinkedList
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Suppress("MemberVisibilityCanBePrivate")
abstract class AbsTaskMonitorFeature : AbsMonitorFeature() {
    @JvmField
    protected val mDeltaList: MutableList<Delta<TaskJiffiesSnapshot>> = ArrayList()

    @JvmField
    protected val mTaskJiffiesTrace: MutableMap<Int, TaskJiffiesSnapshot> = ConcurrentHashMap()

    @JvmField
    protected val mTaskConcurrentTrace: MutableMap<String, Pair<MutableList<Int>, Long>> = ConcurrentHashMap()

    @JvmField
    protected val mTaskStampList: SparseArray<MutableList<TimeBreaker.Stamp>> = SparseArray()

    @JvmField
    protected var mFirstTaskStamp: TimeBreaker.Stamp = TimeBreaker.Stamp(IDLE_TASK, INITIAL_JIFFIES)

    @JvmField
    protected var mAppStatFeat: AppStatMonitorFeature? = null

    @JvmField
    protected var mDevStatFeat: DeviceStatMonitorFeature? = null

    @JvmField
    protected var mOverHeatCount: Int = DEF_STAMP_OVERHEAT

    @JvmField
    protected var mConcurrentLimit: Int = 50

    @JvmField
    protected val coolingTask: Runnable = Runnable { onCoolingDown() }

    protected override fun getTag(): String = TAG

    override fun configure(monitor: BatteryMonitorCore) {
        super.configure(monitor)
        mAppStatFeat = monitor.getMonitorFeature(AppStatMonitorFeature::class.java)
        mDevStatFeat = monitor.getMonitorFeature(DeviceStatMonitorFeature::class.java)
        mFirstTaskStamp = TimeBreaker.Stamp(IDLE_TASK, INITIAL_JIFFIES)
        mOverHeatCount = max(monitor.getConfig().overHeatCount, mOverHeatCount)
    }

    override fun onTurnOn() {
        super.onTurnOn()
    }

    override fun onTurnOff() {
        super.onTurnOff()
        mTaskJiffiesTrace.clear()
        synchronized(mTaskConcurrentTrace) {
            mTaskConcurrentTrace.clear()
        }
        synchronized(mDeltaList) {
            mDeltaList.clear()
        }
        synchronized(mTaskStampList) {
            mTaskStampList.clear()
        }
    }

    fun currentJiffies(windowMsFromNow: Long): List<Delta<TaskJiffiesSnapshot>> {
        val list: MutableList<Delta<TaskJiffiesSnapshot>>
        synchronized(mDeltaList) {
            if (windowMsFromNow <= 0) {
                list = ArrayList(mDeltaList)
            } else {
                list = ArrayList()
                val bgnMillis = SystemClock.uptimeMillis() - windowMsFromNow
                for (item in mDeltaList) {
                    if (item.bgn.time >= bgnMillis) {
                        list.add(item)
                    }
                }
            }
        }

        // Sort by jiffies descending, preserving the original foreground ordering.
        list.sortWith(Comparator sort@{ o1, o2 ->
            val left = o1.dlt
            val right = o2.dlt
            if (left.appStat != 1 || right.appStat != 1) {
                if (left.appStat == 1) {
                    return@sort 1
                }
                if (right.appStat == 1) {
                    return@sort -1
                }
            }
            val minus = left.jiffies.get() - right.jiffies.get()
            when {
                minus == 0L -> 0
                minus > 0L -> -1
                else -> 1
            }
        })
        return list
    }

    fun clearFinishedJiffies() {
        synchronized(mDeltaList) {
            mDeltaList.clear()
        }
    }

    fun getTaskStamps(tid: Int): ArrayList<TimeBreaker.Stamp>? {
        synchronized(mTaskStampList) {
            val stamps = mTaskStampList.get(tid)
            if (stamps != null) {
                return ArrayList(stamps)
            }
            return null
        }
    }

    @SuppressLint("RestrictedApi")
    fun getTaskPortions(tid: Int, jiffiesDelta: Long, jiffiesEnd: Long): TimeBreaker.TimePortions {
        synchronized(mTaskStampList) {
            if (jiffiesDelta < 0L || mTaskStampList.get(tid) == null) {
                return TimeBreaker.TimePortions.ofInvalid()
            }

            val stampList = mTaskStampList.get(tid)
            return TimeBreaker.configurePortions(
                stampList,
                jiffiesDelta,
                JIFFIES_PORTIONING_DELTA
            ) { name -> TimeBreaker.Stamp(name, jiffiesEnd) }
        }
    }

    @WorkerThread
    protected open fun onTaskStarted(key: String, hashcode: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) return

        val bgn = createSnapshot(key, Process.myTid())
        if (bgn != null) {
            mTaskJiffiesTrace[hashcode] = bgn
            onStatTask(Process.myTid(), key, bgn.jiffies.get())
        }
    }

    @WorkerThread
    protected open fun onTaskFinished(key: String, hashcode: Int) {
        val bgn = mTaskJiffiesTrace.remove(hashcode)
        if (Looper.myLooper() != Looper.getMainLooper() && bgn != null) {
            val end = createSnapshot(key, Process.myTid())
            if (end != null) {
                end.isFinished = true
                updateDeltas(bgn, end)
            }
            onStatTask(Process.myTid(), IDLE_TASK, end?.jiffies?.get() ?: bgn.jiffies.get())
        }
    }

    @AnyThread
    protected open fun onTaskRemoved(hashcode: Int) {
        mTaskJiffiesTrace.remove(hashcode)
    }

    protected open fun onTaskConcurrentInc(key: String, hashcode: Int) {
        core.getHandler().post {
            val workingTasks: Pair<MutableList<Int>, Long>
            synchronized(mTaskConcurrentTrace) {
                var current = mTaskConcurrentTrace[key]
                if (current == null) {
                    current = Pair(LinkedList(), SystemClock.uptimeMillis())
                }
                current.first.add(hashcode)
                mTaskConcurrentTrace[key] = current
                workingTasks = current
            }

            if (workingTasks.first.size > mConcurrentLimit) {
                TraceHarborLog.w(
                    TAG,
                    "reach task concurrent limit, count = " + workingTasks.first.size + ", key = " + key
                )
                val duringMillis = SystemClock.uptimeMillis() - workingTasks.second
                TraceHarborLog.w(TAG, "onConcurrentOverHeat, during = $duringMillis")
                onConcurrentOverHeat(key, workingTasks.first.size, duringMillis)
            }
        }
    }

    protected open fun onTaskConcurrentDec(hashcode: Int) {
        core.getHandler().post {
            synchronized(mTaskConcurrentTrace) {
                var found = false
                val entryIterator = mTaskConcurrentTrace.entries.iterator()
                while (entryIterator.hasNext()) {
                    val entry = entryIterator.next()
                    val iterator = entry.value.first.iterator()
                    while (iterator.hasNext()) {
                        val item = iterator.next()
                        if (item == hashcode) {
                            iterator.remove()
                            found = true
                            break
                        }
                    }
                    if (entry.value.first.isEmpty()) {
                        entryIterator.remove()
                    }
                    if (found) {
                        break
                    }
                }
            }
        }
    }

    protected open fun onStatTask(tid: Int, taskName: String, currJiffies: Long) {
        synchronized(mTaskStampList) {
            var stampList = mTaskStampList.get(tid)
            if (stampList == null) {
                stampList = ArrayList()
                stampList.add(0, mFirstTaskStamp)
                mTaskStampList.put(tid, stampList)
            }
            stampList.add(0, TimeBreaker.Stamp(taskName, currJiffies))
        }

        checkOverHeat()
    }

    protected open fun updateDeltas(bgn: TaskJiffiesSnapshot, end: TaskJiffiesSnapshot) {
        if (end.tid != bgn.tid) {
            val message = "task tid mismatch: $bgn vs $end"
            if (BuildConfig.DEBUG) {
                throw RuntimeException(message)
            }
            TraceHarborLog.w(TAG, message)
            return
        }
        if (end.name != bgn.name) {
            val message = "task name mismatch: $bgn vs $end"
            if (BuildConfig.DEBUG) {
                throw RuntimeException(message)
            }
            TraceHarborLog.w(TAG, message)
            return
        }

        val delta = end.diff(bgn)
        if (!shouldTraceTask(delta)) {
            return
        }

        TraceHarborLog.i(
            TAG,
            "onTaskReport: %s, jiffies = %s, millis = %s",
            delta.dlt.name,
            delta.dlt.jiffies.get(),
            delta.during
        )

        mAppStatFeat?.let { appStatFeat ->
            val appStats = appStatFeat.currentAppStatSnapshot(delta.during)
            if (!appStats.isValid()) {
                delta.end.setValid(false)
                delta.dlt.setValid(false)
            }
            var scene = delta.dlt.scene
            var sceneRatio = 100L
            val portions = appStatFeat.currentSceneSnapshot(delta.during)
            val top1 = portions.top1()
            if (top1 != null) {
                scene = top1.key
                sceneRatio = top1.ratio.toLong()
            }
            delta.dlt.bgRatio = appStats.bgRatio.get()
            delta.dlt.scene = scene
            delta.dlt.sceneRatio = sceneRatio
        }
        mDevStatFeat?.let { devStatFeat ->
            val devStat = devStatFeat.currentDevStatSnapshot(delta.during)
            if (!devStat.isValid()) {
                delta.end.setValid(false)
                delta.dlt.setValid(false)
            }
            delta.dlt.chargeRatio = devStat.chargingRatio.get()
        }

        updateDeltas(delta)

        if (mDeltaList.size >= mOverHeatCount) {
            TraceHarborLog.w(TAG, "task list overheat, size = " + mDeltaList.size)
            checkOverHeat()
        }
    }

    protected open fun shouldTraceTask(delta: Delta<TaskJiffiesSnapshot>): Boolean {
        return BuildConfig.DEBUG ||
            delta.during > 1000L &&
            delta.dlt.jiffies.get() / max(1L, delta.during / BatteryCanaryUtil.ONE_MIN) > 100L
    }

    protected open fun updateDeltas(delta: Delta<TaskJiffiesSnapshot>) {
        synchronized(mDeltaList) {
            val iterator = mDeltaList.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.dlt.name == delta.dlt.name && item.dlt.tid == delta.dlt.tid) {
                    if (!item.dlt.isFinished) {
                        iterator.remove()
                    }
                }
            }
            mDeltaList.add(delta)
        }
    }

    protected open fun checkOverHeat() {
        core.getHandler().removeCallbacks(coolingTask)
        core.getHandler().postDelayed(coolingTask, 1000L)
    }

    protected open fun onCoolingDown() {
        synchronized(mTaskStampList) {
            for (i in 0 until mTaskStampList.size()) {
                val stampList = mTaskStampList.valueAt(i)
                if (stampList != null && stampList.size > mOverHeatCount) {
                    TimeBreaker.gcList(stampList)
                }
            }
        }

        if (mDeltaList.size > mOverHeatCount) {
            TraceHarborLog.w(TAG, "cooling task jiffies list, before = " + mDeltaList.size)
            val deltas = currentJiffies(0)
            clearFinishedJiffies()
            TraceHarborLog.w(TAG, "cooling task jiffies list, after = " + mDeltaList.size)

            TraceHarborLog.w(TAG, "report task jiffies list overheat")
            onTraceOverHeat(deltas)
        }
    }

    protected open fun onTraceOverHeat(deltas: List<@JvmSuppressWildcards Delta<TaskJiffiesSnapshot>>) {
    }

    protected open fun onConcurrentOverHeat(key: String, concurrentCount: Int, duringMillis: Long) {
    }

    protected open fun onParseTaskJiffiesFail(key: String, pid: Int, tid: Int) {
    }

    protected open fun createSnapshot(name: String, tid: Int): TaskJiffiesSnapshot? {
        val snapshot = TaskJiffiesSnapshot()
        snapshot.tid = tid
        snapshot.name = name

        snapshot.appStat = BatteryCanaryUtil.getAppStat(core.getContext(), core.isForeground())
        snapshot.devStat = BatteryCanaryUtil.getDeviceStat(core.getContext())

        try {
            val supplier: Callable<String>? = core.getConfig().onSceneSupplier
            snapshot.scene = supplier?.call() ?: ""
        } catch (ignored: Exception) {
            snapshot.scene = ""
        }

        if (core.getConfig().isUseThreadClock) {
            snapshot.jiffies = DigitEntry.of(SystemClock.currentThreadTimeMillis() / BatteryCanaryUtil.JIFFY_MILLIS)
        } else {
            val pid = Process.myPid()
            val stat = ProcStatUtil.of(pid, tid)
            if (stat == null) {
                TraceHarborLog.w(TAG, "parse task procStat fail, name = $name, tid = $tid")
                onParseTaskJiffiesFail(name, pid, tid)
                return null
            }
            snapshot.jiffies = DigitEntry.of(stat.jiffies)
            return snapshot
        }

        return snapshot
    }

    open class TaskJiffiesSnapshot : Snapshot<TaskJiffiesSnapshot>() {
        @JvmField
        var tid: Int = 0

        @JvmField
        var name: String = ""

        @JvmField
        var timeMillis: Long = System.currentTimeMillis()

        @JvmField
        var jiffies: DigitEntry<Long> = DigitEntry.of(0L)

        @JvmField
        var appStat: Int = 0

        @JvmField
        var devStat: Int = 0

        @JvmField
        var scene: String = ""

        @JvmField
        var isFinished: Boolean = false

        @JvmField
        var bgRatio: Long = 100

        @JvmField
        var chargeRatio: Long = 100

        @JvmField
        var sceneRatio: Long = 100

        override fun diff(bgn: TaskJiffiesSnapshot): Delta<TaskJiffiesSnapshot> {
            return object : Delta<TaskJiffiesSnapshot>(bgn, this) {
                override fun computeDelta(): TaskJiffiesSnapshot {
                    val delta = TaskJiffiesSnapshot()
                    delta.tid = end.tid
                    delta.name = end.name
                    delta.timeMillis = end.timeMillis - bgn.timeMillis
                    delta.jiffies = Differ.DigitDiffer.globalDiff(bgn.jiffies, end.jiffies)
                    delta.isFinished = end.isFinished

                    delta.appStat = if (bgn.appStat == 1 || end.appStat == 1) {
                        1
                    } else if (bgn.appStat == 3 && end.appStat == 3) {
                        3
                    } else {
                        2
                    }

                    delta.devStat = if (bgn.devStat == 1 || end.devStat == 1) {
                        1
                    } else if (bgn.devStat == 3 && end.devStat == 3) {
                        3
                    } else if (bgn.devStat == 4 && end.devStat == 4) {
                        3
                    } else {
                        2
                    }

                    delta.scene = end.scene
                    return delta
                }
            }
        }

        override fun toString(): String {
            return "TaskJiffiesSnapshot{" +
                "appStat=" + appStat +
                ", devStat=" + devStat +
                ", tid=" + tid +
                ", name='" + name + '\'' +
                ", jiffies=" + jiffies +
                '}'
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.AbsTaskMonitorFeature"
        private const val INITIAL_JIFFIES = 0L
        private const val JIFFIES_PORTIONING_DELTA = 10L

        const val IDLE_TASK = "thread_pool@idle"
    }
}
