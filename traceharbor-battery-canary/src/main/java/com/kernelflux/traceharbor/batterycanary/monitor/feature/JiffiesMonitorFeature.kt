package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import android.os.SystemClock
import android.text.TextUtils
import androidx.annotation.AnyThread
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import androidx.core.util.Pair
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorConfig
import com.kernelflux.traceharbor.batterycanary.monitor.BatteryMonitorCore.Callback
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Differ
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.DigitEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.ListEntry
import com.kernelflux.traceharbor.batterycanary.shell.TopThreadFeature
import com.kernelflux.traceharbor.batterycanary.shell.ui.TopThreadIndicator
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.batterycanary.utils.ProcStatUtil
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import java.io.File
import java.io.IOException
import java.util.Collections

class JiffiesMonitorFeature : AbsMonitorFeature() {
    private val mFgThreadWatchDog = ThreadWatchDog()
    private val mBgThreadWatchDog = ThreadWatchDog()

    protected override fun getTag(): String = TAG

    override fun onTurnOn() {
        super.onTurnOn()
        sSkipNewAdded = mCore.getConfig().isSkipNewAddedPidTid
    }

    override fun weight(): Int = Int.MAX_VALUE

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
        if (isForeground) {
            mFgThreadWatchDog.start()
            mBgThreadWatchDog.stop()
        } else {
            mBgThreadWatchDog.start()
            mFgThreadWatchDog.stop()
        }
    }

    fun watchBackThreadSate(isForeground: Boolean, pid: Int, tid: Int) {
        if (isForeground) {
            mFgThreadWatchDog.watch(pid, tid)
        } else {
            mBgThreadWatchDog.watch(pid, tid)
        }
    }

    @WorkerThread
    fun currentJiffiesSnapshot(): JiffiesSnapshot {
        return JiffiesSnapshot.currentJiffiesSnapshot(ProcessInfo.getProcessInfo(), mCore.getConfig().isStatPidProc)
    }

    @WorkerThread
    fun currentJiffiesSnapshot(pid: Int): JiffiesSnapshot {
        return JiffiesSnapshot.currentJiffiesSnapshot(ProcessInfo.getProcessInfo(pid), mCore.getConfig().isStatPidProc)
    }

    @WorkerThread
    fun currentUidJiffiesSnapshot(): UidJiffiesSnapshot {
        return UidJiffiesSnapshot.of(mCore.getContext(), mCore.getConfig())
    }

    @AnyThread
    fun currentJiffiesSnapshot(callback: Callback<JiffiesSnapshot>) {
        mCore.getHandler().post {
            callback.onGetJiffies(currentJiffiesSnapshot())
        }
    }

    @AnyThread
    fun currentJiffiesSnapshot(pid: Int, callback: Callback<JiffiesSnapshot>) {
        mCore.getHandler().post {
            callback.onGetJiffies(currentJiffiesSnapshot(pid))
        }
    }

    interface JiffiesListener {
        @Deprecated("")
        fun onParseError(pid: Int, tid: Int)

        fun onWatchingThreads(threadJiffiesList: ListEntry<out JiffiesSnapshot.ThreadJiffiesEntry>)
    }

    @Suppress("SpellCheckingInspection")
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    open class ProcessInfo {
        @JvmField var pid: Int = 0
        @JvmField var name: String = ""
        @JvmField var time: Long = 0L
        @JvmField var upTime: Long = 0L
        @JvmField var jiffies: Long = 0L
        @JvmField var threadInfo: List<ThreadInfo> = Collections.emptyList()

        @Throws(IOException::class)
        open fun loadProcStat() {
            val stat = ProcStatUtil.of(pid)
            if (stat != null) {
                name = stat.comm
                jiffies = stat.jiffies
            } else {
                throw IOException("parse fail: " + BatteryCanaryUtil.cat("/proc/$pid/stat"))
            }
        }

        override fun toString(): String {
            return "process:$name($pid) thread size:" + threadInfo.size
        }

        @Suppress("SpellCheckingInspection")
        open class ThreadInfo {
            @JvmField var pid: Int = 0
            @JvmField var tid: Int = 0
            @JvmField var name: String = ""
            @JvmField var stat: String = ""
            @JvmField var jiffies: Long = 0L

            @Throws(IOException::class)
            open fun loadProcStat() {
                val procStat = ProcStatUtil.of(pid, tid)
                if (procStat != null && !TextUtils.isEmpty(procStat.comm)) {
                    name = procStat.comm
                    stat = procStat.stat
                    jiffies = procStat.jiffies
                } else {
                    throw IOException("parse fail: " + BatteryCanaryUtil.cat("/proc/$pid/task/$tid/stat"))
                }
            }

            override fun toString(): String {
                return "thread:$name($tid) $jiffies"
            }

            companion object {
                @JvmStatic
                fun parseThreadsInfo(pid: Int): List<ThreadInfo> {
                    val rootPath = "/proc/$pid/task/"
                    val taskDir = File(rootPath)
                    try {
                        if (taskDir.isDirectory) {
                            val subDirs = taskDir.listFiles() ?: return Collections.emptyList()
                            val threadInfoList: MutableList<ThreadInfo> = ArrayList(subDirs.size)
                            for (file in subDirs) {
                                if (!file.isDirectory) {
                                    continue
                                }
                                try {
                                    val threadInfo = of(pid, file.name.toInt())
                                    threadInfoList.add(threadInfo)
                                } catch (e: Exception) {
                                    TraceHarborLog.printErrStackTrace(TAG, e, "parse thread error: " + file.name)
                                }
                            }
                            return threadInfoList
                        }
                    } catch (e: Exception) {
                        TraceHarborLog.printErrStackTrace(TAG, e, "list thread dir error")
                    }
                    return Collections.emptyList()
                }

                @JvmStatic
                fun of(pid: Int, tid: Int): ThreadInfo {
                    val threadInfo = ThreadInfo()
                    threadInfo.pid = pid
                    threadInfo.tid = tid
                    return threadInfo
                }
            }
        }

        companion object {
            @JvmStatic
            fun getProcessInfo(): ProcessInfo {
                val processInfo = ProcessInfo()
                processInfo.pid = Process.myPid()
                processInfo.name = if (TraceHarbor.isInstalled()) TraceHarborUtil.getProcessName(TraceHarbor.with().application) else "default"
                processInfo.threadInfo = ThreadInfo.parseThreadsInfo(processInfo.pid)
                processInfo.upTime = SystemClock.uptimeMillis()
                processInfo.time = System.currentTimeMillis()
                return processInfo
            }

            @JvmStatic
            fun getProcessInfo(pid: Int): ProcessInfo {
                if (pid == Process.myPid()) {
                    return getProcessInfo()
                }
                val processInfo = ProcessInfo()
                processInfo.pid = pid
                processInfo.name = if (TraceHarbor.isInstalled()) TraceHarborUtil.getProcessName(TraceHarbor.with().application) else "default"
                processInfo.threadInfo = ThreadInfo.parseThreadsInfo(processInfo.pid)
                processInfo.upTime = SystemClock.uptimeMillis()
                processInfo.time = System.currentTimeMillis()
                return processInfo
            }
        }
    }

    @Suppress("SpellCheckingInspection", "DEPRECATION")
    open class JiffiesSnapshot : Snapshot<JiffiesSnapshot>() {
        @JvmField var pid: Int = 0
        @JvmField var isNewAdded: Boolean = false
        @JvmField var name: String = ""
        @JvmField var totalJiffies: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField var threadEntries: ListEntry<ThreadJiffiesSnapshot> = ListEntry.ofEmpty()
        @JvmField var threadNum: DigitEntry<Int> = DigitEntry.of(0)
        @JvmField var deadThreadEntries: ListEntry<ThreadJiffiesSnapshot> = ListEntry.ofEmpty()

        override fun diff(bgn: JiffiesSnapshot): Delta<JiffiesSnapshot> {
            return object : Delta<JiffiesSnapshot>(bgn, this) {
                override fun computeDelta(): JiffiesSnapshot {
                    val delta = JiffiesSnapshot()
                    delta.pid = end.pid
                    delta.isNewAdded = end.isNewAdded
                    delta.name = end.name
                    delta.totalJiffies = Differ.DigitDiffer.globalDiff(bgn.totalJiffies, end.totalJiffies)
                    delta.threadNum = Differ.DigitDiffer.globalDiff(bgn.threadNum, end.threadNum)
                    delta.threadEntries = ListEntry.ofEmpty()

                    if (end.threadEntries.list.size > 0) {
                        val deltaThreadEntries: MutableList<ThreadJiffiesSnapshot> = ArrayList()
                        for (endRecord in end.threadEntries.list) {
                            var isNewAdded = true
                            var jiffiesConsumed = endRecord.value
                            for (bgnRecord in bgn.threadEntries.list) {
                                if (bgnRecord.name == endRecord.name && bgnRecord.tid == endRecord.tid) {
                                    isNewAdded = false
                                    jiffiesConsumed = Differ.DigitDiffer.globalDiff(bgnRecord, endRecord).value
                                    break
                                }
                            }
                            if (jiffiesConsumed > 0) {
                                val deltaThreadJiffies = ThreadJiffiesSnapshot(jiffiesConsumed)
                                deltaThreadJiffies.tid = endRecord.tid
                                deltaThreadJiffies.name = endRecord.name
                                deltaThreadJiffies.stat = endRecord.stat
                                deltaThreadJiffies.isNewAdded = isNewAdded
                                if (!isNewAdded || !sSkipNewAdded) {
                                    deltaThreadEntries.add(deltaThreadJiffies)
                                }
                            }
                        }
                        if (deltaThreadEntries.size > 0) {
                            deltaThreadEntries.sortWith { o1, o2 ->
                                val minus = o1.get() - o2.get()
                                when {
                                    minus == 0L -> 0
                                    minus > 0L -> -1
                                    else -> 1
                                }
                            }
                            delta.threadEntries = ListEntry.of(deltaThreadEntries)
                        }
                    }

                    if (bgn.threadEntries.list.size > 0) {
                        var deadThreadEntries: MutableList<ThreadJiffiesSnapshot> = Collections.emptyList()
                        for (bgnRecord in bgn.threadEntries.list) {
                            var isDead = true
                            for (exist in delta.threadEntries.list) {
                                if (exist.tid == bgnRecord.tid) {
                                    isDead = false
                                    break
                                }
                            }
                            if (isDead) {
                                if (deadThreadEntries.isEmpty()) {
                                    deadThreadEntries = ArrayList()
                                }
                                deadThreadEntries.add(bgnRecord)
                            }
                        }
                        if (deadThreadEntries.isNotEmpty()) {
                            delta.deadThreadEntries = ListEntry.of(deadThreadEntries)
                        }
                    }

                    return delta
                }
            }
        }

        /**
         * Use [ThreadJiffiesEntry] instead.
         */
        @Deprecated("Use ThreadJiffiesEntry instead")
        open class ThreadJiffiesSnapshot(value: Long) : ThreadJiffiesEntry(value) {
            companion object {
                @JvmStatic
                fun parseThreadJiffies(threadInfo: ProcessInfo.ThreadInfo): ThreadJiffiesSnapshot? {
                    return try {
                        threadInfo.loadProcStat()
                        val snapshot = ThreadJiffiesSnapshot(threadInfo.jiffies)
                        snapshot.name = threadInfo.name
                        snapshot.stat = threadInfo.stat
                        snapshot.tid = threadInfo.tid
                        snapshot.isNewAdded = true
                        snapshot
                    } catch (e: IOException) {
                        TraceHarborLog.printErrStackTrace(TAG, e, "parseThreadJiffies fail")
                        null
                    }
                }
            }
        }

        open class ThreadJiffiesEntry(value: Long) : DigitEntry<Long>(value) {
            @JvmField var tid: Int = 0
            @JvmField var name: String = ""
            @JvmField var isNewAdded: Boolean = false
            @JvmField var stat: String = ""
            @JvmField var stack: String? = null

            override fun diff(right: Long): Long {
                return value - right
            }
        }

        companion object {
            @JvmStatic
            fun currentJiffiesSnapshot(processInfo: ProcessInfo, isStatPidProcInput: Boolean): JiffiesSnapshot {
                var isStatPidProc = isStatPidProcInput
                val snapshot = JiffiesSnapshot()
                snapshot.pid = processInfo.pid
                snapshot.name = processInfo.name

                var totalJiffies = 0L
                if (isStatPidProc) {
                    try {
                        processInfo.loadProcStat()
                        totalJiffies = processInfo.jiffies
                    } catch (e: IOException) {
                        TraceHarborLog.printErrStackTrace(TAG, e, "parseProcJiffies fail")
                        isStatPidProc = false
                        snapshot.setValid(false)
                    }
                }

                var threadJiffiesList: List<ThreadJiffiesSnapshot> = Collections.emptyList()
                var threadNum = 0

                if (processInfo.threadInfo.isNotEmpty()) {
                    threadNum = processInfo.threadInfo.size
                    val mutableThreadJiffiesList: MutableList<ThreadJiffiesSnapshot> = ArrayList(processInfo.threadInfo.size)
                    threadJiffiesList = mutableThreadJiffiesList
                    for (threadInfo in processInfo.threadInfo) {
                        val threadJiffies = ThreadJiffiesSnapshot.parseThreadJiffies(threadInfo)
                        if (threadJiffies != null) {
                            mutableThreadJiffiesList.add(threadJiffies)
                            if (!isStatPidProc) {
                                totalJiffies += threadJiffies.value
                            }
                        } else {
                            snapshot.setValid(false)
                        }
                    }
                }
                snapshot.totalJiffies = DigitEntry.of(totalJiffies)
                snapshot.threadEntries = ListEntry.of(threadJiffiesList)
                snapshot.threadNum = DigitEntry.of(threadNum)
                return snapshot
            }
        }
    }

    inner class ThreadWatchDog : Runnable {
        private var duringMillis: Long = 0L
        private val mWatchingThreads: MutableList<ProcessInfo.ThreadInfo> = ArrayList()
        private var mWatchHandler: Handler? = null

        override fun run() {
            TraceHarborLog.i(
                TAG,
                "threadWatchDog start, size = " + mWatchingThreads.size + ", delayMillis = " + duringMillis,
            )

            val threadJiffiesList: MutableList<JiffiesSnapshot.ThreadJiffiesSnapshot> = ArrayList()
            synchronized(mWatchingThreads) {
                for (item in mWatchingThreads) {
                    val snapshot = JiffiesSnapshot.ThreadJiffiesSnapshot.parseThreadJiffies(item)
                    if (snapshot != null) {
                        snapshot.isNewAdded = false
                        threadJiffiesList.add(snapshot)
                    }
                }
            }
            if (threadJiffiesList.isNotEmpty()) {
                val threadJiffiesListEntry = ListEntry.of(threadJiffiesList)
                mCore.getConfig().callback.onWatchingThreads(threadJiffiesListEntry)
            }

            synchronized(mWatchingThreads) {
                if (duringMillis <= 5 * 60 * 1000L) {
                    mWatchHandler?.postDelayed(this, setNext(5 * 60 * 1000L))
                } else if (duringMillis <= 10 * 60 * 1000L) {
                    mWatchHandler?.postDelayed(this, setNext(10 * 60 * 1000L))
                } else {
                    synchronized(mWatchingThreads) {
                        mWatchingThreads.clear()
                    }
                }
            }
        }

        fun watch(pid: Int, tid: Int) {
            synchronized(mWatchingThreads) {
                for (item in mWatchingThreads) {
                    if (item.pid == pid && item.tid == tid) {
                        return
                    }
                }
                mWatchingThreads.add(ProcessInfo.ThreadInfo.of(pid, tid))
            }
        }

        fun start() {
            synchronized(mWatchingThreads) {
                TraceHarborLog.i(TAG, "ThreadWatchDog start watching, count = " + mWatchingThreads.size)
                if (mWatchingThreads.isNotEmpty()) {
                    val handlerThread = HandlerThread("traceharbor_watchdog")
                    handlerThread.start()
                    mWatchHandler = Handler(handlerThread.looper)
                    mWatchHandler?.postDelayed(this, reset())
                }
            }
        }

        fun stop() {
            synchronized(mWatchingThreads) {
                val watchHandler = mWatchHandler
                if (watchHandler != null) {
                    watchHandler.removeCallbacks(this)
                    watchHandler.looper.quit()
                    mWatchHandler = null
                }
            }
        }

        private fun reset(): Long {
            duringMillis = 0L
            setNext(5 * 60 * 1000L)
            return duringMillis
        }

        private fun setNext(millis: Long): Long {
            duringMillis += millis
            return millis
        }
    }

    open class UidJiffiesSnapshot : Snapshot<UidJiffiesSnapshot>() {
        @JvmField var totalUidJiffies: DigitEntry<Long> = DigitEntry.of(0L)
        @JvmField var pidCurrJiffiesList: List<JiffiesSnapshot> = Collections.emptyList()
        @JvmField var pidDeltaJiffiesList: MutableList<Delta<JiffiesSnapshot>> = Collections.emptyList()

        override fun diff(bgn: UidJiffiesSnapshot): Delta<UidJiffiesSnapshot> {
            return object : Delta<UidJiffiesSnapshot>(bgn, this) {
                override fun computeDelta(): UidJiffiesSnapshot {
                    val delta = UidJiffiesSnapshot()
                    delta.totalUidJiffies = Differ.DigitDiffer.globalDiff(bgn.totalUidJiffies, end.totalUidJiffies)
                    if (end.pidCurrJiffiesList.isNotEmpty()) {
                        delta.pidDeltaJiffiesList = ArrayList()
                        for (endItem in end.pidCurrJiffiesList) {
                            var last: JiffiesSnapshot? = null
                            for (bgnItem in bgn.pidCurrJiffiesList) {
                                if (bgnItem.pid == endItem.pid) {
                                    last = bgnItem
                                    break
                                }
                            }
                            if (last == null) {
                                endItem.isNewAdded = true
                                val empty = JiffiesSnapshot()
                                empty.pid = endItem.pid
                                empty.name = endItem.name
                                empty.totalJiffies = DigitEntry.of(0L)
                                empty.threadEntries = ListEntry.ofEmpty()
                                empty.threadNum = DigitEntry.of(0)
                                last = empty
                            }
                            if (!endItem.isNewAdded || !sSkipNewAdded) {
                                val deltaPidJiffies = endItem.diff(last)
                                delta.pidDeltaJiffiesList.add(deltaPidJiffies)
                            }
                        }

                        delta.pidDeltaJiffiesList.sortWith { o1, o2 ->
                            val minus = o1.dlt.totalJiffies.get() - o2.dlt.totalJiffies.get()
                            when {
                                minus == 0L -> 0
                                minus > 0L -> -1
                                else -> 1
                            }
                        }
                    }
                    return delta
                }
            }
        }

        class IpcJiffies {
            class IpcProcessJiffies : Parcelable {
                @JvmField var pid: Int = 0
                @JvmField var name: String? = null
                @JvmField var totalJiffies: Long = 0L
                @JvmField var threadNum: Int = 0
                @JvmField var threadJiffyList: List<IpcThreadJiffies> = Collections.emptyList()

                protected constructor(parcel: Parcel) {
                    pid = parcel.readInt()
                    name = parcel.readString()
                    totalJiffies = parcel.readLong()
                    threadNum = parcel.readInt()
                    threadJiffyList = parcel.createTypedArrayList(IpcThreadJiffies.CREATOR) ?: Collections.emptyList()
                }

                constructor()

                override fun describeContents(): Int = 0

                override fun writeToParcel(dest: Parcel, flags: Int) {
                    dest.writeInt(pid)
                    dest.writeString(name)
                    dest.writeLong(totalJiffies)
                    dest.writeInt(threadNum)
                    dest.writeTypedList(threadJiffyList)
                }

                class IpcThreadJiffies : Parcelable {
                    @JvmField var tid: Int = 0
                    @JvmField var name: String? = null
                    @JvmField var stat: String? = null
                    @JvmField var jiffies: Long = 0L

                    protected constructor(parcel: Parcel) {
                        tid = parcel.readInt()
                        name = parcel.readString()
                        stat = parcel.readString()
                        jiffies = parcel.readLong()
                    }

                    constructor()

                    override fun describeContents(): Int = 0

                    override fun writeToParcel(dest: Parcel, flags: Int) {
                        dest.writeInt(tid)
                        dest.writeString(name)
                        dest.writeString(stat)
                        dest.writeLong(jiffies)
                    }

                    companion object {
                        @JvmField
                        val CREATOR: Parcelable.Creator<IpcThreadJiffies> = object : Parcelable.Creator<IpcThreadJiffies> {
                            override fun createFromParcel(parcel: Parcel): IpcThreadJiffies {
                                return IpcThreadJiffies(parcel)
                            }

                            override fun newArray(size: Int): Array<IpcThreadJiffies?> {
                                return arrayOfNulls(size)
                            }
                        }
                    }
                }

                companion object {
                    @JvmField
                    val CREATOR: Parcelable.Creator<IpcProcessJiffies> = object : Parcelable.Creator<IpcProcessJiffies> {
                        override fun createFromParcel(parcel: Parcel): IpcProcessJiffies {
                            return IpcProcessJiffies(parcel)
                        }

                        override fun newArray(size: Int): Array<IpcProcessJiffies?> {
                            return arrayOfNulls(size)
                        }
                    }
                }
            }

            companion object {
                @JvmStatic
                fun toIpc(local: JiffiesSnapshot): IpcProcessJiffies {
                    val ipc = IpcProcessJiffies()
                    ipc.pid = local.pid
                    ipc.name = local.name
                    ipc.threadNum = local.threadNum.get()
                    ipc.totalJiffies = local.totalJiffies.get()
                    val threadJiffyList: MutableList<IpcProcessJiffies.IpcThreadJiffies> = ArrayList(local.threadEntries.list.size)
                    ipc.threadJiffyList = threadJiffyList
                    for (item in local.threadEntries.list) {
                        threadJiffyList.add(toIpc(item))
                    }
                    return ipc
                }

                @JvmStatic
                fun toIpc(local: JiffiesSnapshot.ThreadJiffiesSnapshot): IpcProcessJiffies.IpcThreadJiffies {
                    val ipc = IpcProcessJiffies.IpcThreadJiffies()
                    ipc.tid = local.tid
                    ipc.name = local.name
                    ipc.stat = local.stat
                    ipc.jiffies = local.get()
                    return ipc
                }

                @JvmStatic
                fun toLocal(ipc: IpcProcessJiffies): JiffiesSnapshot {
                    val local = JiffiesSnapshot()
                    local.pid = ipc.pid
                    local.name = ipc.name ?: ""
                    local.totalJiffies = DigitEntry.of(ipc.totalJiffies)
                    var threadJiffiesList: List<JiffiesSnapshot.ThreadJiffiesSnapshot> = Collections.emptyList()
                    if (ipc.threadJiffyList.isNotEmpty()) {
                        val mutableThreadJiffiesList: MutableList<JiffiesSnapshot.ThreadJiffiesSnapshot> = ArrayList(ipc.threadJiffyList.size)
                        threadJiffiesList = mutableThreadJiffiesList
                        for (item in ipc.threadJiffyList) {
                            val threadJiffies = toLocal(item)
                            mutableThreadJiffiesList.add(threadJiffies)
                        }
                    }
                    local.threadEntries = ListEntry.of(threadJiffiesList)
                    local.threadNum = DigitEntry.of(threadJiffiesList.size)
                    return local
                }

                @JvmStatic
                fun toLocal(ipc: IpcProcessJiffies.IpcThreadJiffies): JiffiesSnapshot.ThreadJiffiesSnapshot {
                    val local = JiffiesSnapshot.ThreadJiffiesSnapshot(ipc.jiffies)
                    local.name = ipc.name ?: ""
                    local.stat = ipc.stat ?: ""
                    local.tid = ipc.tid
                    local.isNewAdded = true
                    return local
                }
            }
        }

        companion object {
            @JvmStatic
            fun of(context: Context, config: BatteryMonitorConfig): UidJiffiesSnapshot {
                val curr = UidJiffiesSnapshot()
                val procList: List<Pair<Int, String>> = TopThreadFeature.getProcList(context)
                val pidCurrJiffiesList: MutableList<JiffiesSnapshot> = ArrayList(procList.size)
                curr.pidCurrJiffiesList = pidCurrJiffiesList
                var sum = 0L
                TraceHarborLog.i(TAG, "currProcList: $procList")
                for (item in procList) {
                    val pid = item.first!!
                    val procName = java.lang.String.valueOf(item.second)
                    if (ProcStatUtil.exists(pid)) {
                        TraceHarborLog.i(TAG, "proc: $pid")
                        val snapshot = JiffiesSnapshot.currentJiffiesSnapshot(ProcessInfo.getProcessInfo(pid), config.isStatPidProc)
                        snapshot.name = TopThreadIndicator.getProcSuffix(procName)
                        sum += snapshot.totalJiffies.get()
                        pidCurrJiffiesList.add(snapshot)
                    } else {
                        val ipcJiffiesCollector = config.ipcJiffiesCollector
                        if (ipcJiffiesCollector != null) {
                            val ipcProcessJiffies = ipcJiffiesCollector.apply(item)
                            if (ipcProcessJiffies != null) {
                                TraceHarborLog.i(TAG, "ipc: $pid")
                                val snapshot = IpcJiffies.toLocal(ipcProcessJiffies)
                                snapshot.name = TopThreadIndicator.getProcSuffix(procName)
                                sum += snapshot.totalJiffies.get()
                                pidCurrJiffiesList.add(snapshot)
                                continue
                            }
                        }
                        TraceHarborLog.i(TAG, "skip: $pid")
                    }
                }
                curr.totalUidJiffies = DigitEntry.of(sum)
                return curr
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.JiffiesMonitorFeature"

        @JvmField
        var sSkipNewAdded: Boolean = false
    }
}
