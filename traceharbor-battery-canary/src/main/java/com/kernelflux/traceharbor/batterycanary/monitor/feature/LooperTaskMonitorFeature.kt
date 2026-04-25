package com.kernelflux.traceharbor.batterycanary.monitor.feature

import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.kernelflux.traceharbor.batterycanary.BuildConfig
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.trace.core.LooperMonitor
import com.kernelflux.traceharbor.trace.listeners.ILooperListener
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Collections

class LooperTaskMonitorFeature : AbsTaskMonitorFeature() {
    interface LooperTaskListener {
        @Deprecated("")
        fun onTaskTrace(thread: Thread, sortList: List<TaskTraceInfo>)

        fun onLooperTaskOverHeat(deltas: List<@JvmSuppressWildcards Delta<TaskJiffiesSnapshot>>)

        fun onLooperConcurrentOverHeat(key: String, concurrentCount: Int, duringMillis: Long)
    }

    @JvmField
    val mWatchingList: MutableList<String> = ArrayList()

    @JvmField
    val mLooperMonitorTrace: MutableMap<Looper, LooperMonitor> = HashMap()

    @JvmField
    var mLooperTaskListener: ILooperListener? = null

    @JvmField
    var mDelayWatchingTask: Runnable? = null

    override fun getTag(): String = TAG

    fun getListener(): LooperTaskListener = mCore

    override fun onTurnOn() {
        super.onTurnOn()
        mLooperTaskListener = object : ILooperListener {
            override fun isValid(): Boolean = mCore.isTurnOn()

            override fun onDispatchBegin(log: String) {
                if (mCore.getConfig().isAggressiveMode) {
                    TraceHarborLog.i(TAG, "[" + Thread.currentThread().name + "]" + log)
                }
                val taskName = computeTaskName(log)
                if (!TextUtils.isEmpty(taskName)) {
                    val hashcode = computeHashcode(log)
                    if (hashcode > 0) {
                        onTaskStarted(taskName!!, hashcode)
                    }
                }
            }

            override fun onDispatchEnd(log: String, beginNs: Long, endNs: Long) {
                if (mCore.getConfig().isAggressiveMode) {
                    TraceHarborLog.i(TAG, "[" + Thread.currentThread().name + "]" + log)
                }
                val taskName = computeTaskName(log)
                if (!TextUtils.isEmpty(taskName)) {
                    val hashcode = computeHashcode(log)
                    if (hashcode > 0) {
                        onTaskFinished(taskName!!, hashcode)
                    }
                }
            }

            // Samples:
            // >>>>> Dispatching to Handler (android.os.Handler) {5774ba9} null: 22
            // <<<<< Finished to Handler (android.os.Handler) {5774ba9} null
            // >>>>> Dispatching to Handler (android.os.Handler) {5774ba9} null: 33
            // <<<<< Finished to Handler (android.os.Handler) {5774ba9} null
            // >>>>> Dispatching to Handler (android.os.Handler) {5774ba9} com.example.Task@a8ee52e: 0
            // <<<<< Finished to Handler (android.os.Handler) {5774ba9} com.example.Task@a8ee52e
            private fun computeTaskName(rawInput: String?): String? {
                if (TextUtils.isEmpty(rawInput)) return null
                val symbolBgn = "} "
                val symbolEnd = "@"
                val indexBgn = rawInput!!.indexOf(symbolBgn)
                val indexEnd = rawInput.lastIndexOf(symbolEnd)
                if (indexBgn >= indexEnd - 1) return null
                return rawInput.substring(indexBgn + symbolBgn.length, indexEnd)
            }

            private fun computeHashcode(rawInput: String?): Int {
                if (TextUtils.isEmpty(rawInput)) return -1
                val symbolBgn = "@"
                val symbolEnd = ": "
                val indexBgn = rawInput!!.indexOf(symbolBgn)
                val indexEnd = if (rawInput.contains(symbolEnd)) rawInput.lastIndexOf(symbolEnd) else Int.MAX_VALUE
                if (indexBgn >= indexEnd - 1) return -1
                val hexString = if (indexEnd == Int.MAX_VALUE) {
                    rawInput.substring(indexBgn + symbolBgn.length)
                } else {
                    rawInput.substring(indexBgn + symbolBgn.length, indexEnd)
                }
                return try {
                    hexString.toInt(16)
                } catch (ignored: NumberFormatException) {
                    -1
                }
            }
        }
    }

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
        if (isForeground) {
            mDelayWatchingTask?.let { mCore.getHandler().removeCallbacks(it) }
        } else {
            mDelayWatchingTask = Runnable { startWatching() }
            mCore.getHandler().postDelayed(mDelayWatchingTask!!, mCore.getConfig().greyTime)
        }
    }

    override fun onTurnOff() {
        super.onTurnOff()
        stopWatching()
    }

    override fun weight(): Int = 0

    fun startWatching() {
        synchronized(mWatchingList) {
            if (mLooperTaskListener == null) {
                return
            }
            TraceHarborLog.i(TAG, "#startWatching")
            if (mCore.getConfig().looperWatchList.contains("all")) {
                val allThreads = getAllThreads()
                for (thread in allThreads) {
                    if (thread is HandlerThread) {
                        val looper = thread.looper
                        if (looper != null && !mLooperMonitorTrace.containsKey(looper)) {
                            watchLooper(thread)
                        }
                    } else if (Looper.getMainLooper().thread === thread) {
                        if (!mLooperMonitorTrace.containsKey(Looper.getMainLooper())) {
                            watchLooper("main", Looper.getMainLooper())
                        }
                    }
                }
            } else {
                var allThreads: Collection<Thread> = Collections.emptyList()
                for (threadToWatch in mCore.getConfig().looperWatchList) {
                    if (TextUtils.isEmpty(threadToWatch)) {
                        continue
                    }
                    if ("main".equals(threadToWatch, ignoreCase = true)) {
                        val mainLooper = Looper.getMainLooper()
                        if (!mLooperMonitorTrace.containsKey(mainLooper)) {
                            watchLooper("main", mainLooper)
                        }
                        continue
                    }

                    if (!mWatchingList.contains(threadToWatch)) {
                        if (allThreads.isEmpty()) {
                            allThreads = getAllThreads()
                        }
                        for (thread in allThreads) {
                            if (Looper.getMainLooper().thread === thread) {
                                continue
                            }
                            if (thread.name.contains(threadToWatch)) {
                                if (thread is HandlerThread) {
                                    val looper = thread.looper
                                    if (looper != null && !mLooperMonitorTrace.containsKey(looper)) {
                                        watchLooper(thread.name, looper)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getAllThreads(): Collection<Thread> {
        val stackTraces: Map<Thread, Array<StackTraceElement>>? = Thread.getAllStackTraces()
        return stackTraces?.keys ?: Collections.emptyList()
    }

    fun stopWatching() {
        synchronized(mWatchingList) {
            mLooperTaskListener = null
            for (item in mLooperMonitorTrace.values) {
                item.onRelease()
            }
            mLooperMonitorTrace.clear()
            mWatchingList.clear()
        }
    }

    fun watchLooper(handlerThread: HandlerThread) {
        val looper = handlerThread.looper
        if (looper != null) {
            watchLooper(handlerThread.name, looper)
        }
    }

    fun watchLooper(name: String?, looper: Looper?) {
        if (TextUtils.isEmpty(name) || looper == null) {
            return
        }

        synchronized(mWatchingList) {
            val listener = mLooperTaskListener
            if (listener != null) {
                mWatchingList.remove(name)
                val remove = mLooperMonitorTrace.remove(looper)
                remove?.onRelease()

                val looperMonitor = LooperMonitor.of(looper)
                looperMonitor.addListener(listener)
                mWatchingList.add(name!!)
                mLooperMonitorTrace[looper] = looperMonitor
            }
        }
    }

    @WorkerThread
    override fun onTaskStarted(key: String, hashcode: Int) {
        val bgn = createSnapshot(key, Process.myTid())
        if (bgn != null) {
            mTaskJiffiesTrace[hashcode] = bgn
            onStatTask(Process.myTid(), key, bgn.jiffies.get())
        }
    }

    @WorkerThread
    override fun onTaskFinished(key: String, hashcode: Int) {
        val bgn = mTaskJiffiesTrace.remove(hashcode)
        if (bgn != null) {
            val end = createSnapshot(key, Process.myTid())
            if (end != null) {
                end.isFinished = true
                updateDeltas(bgn, end)
            }
            onStatTask(Process.myTid(), IDLE_TASK, end?.jiffies?.get() ?: bgn.jiffies.get())
        }
    }

    override fun onTraceOverHeat(deltas: List<@JvmSuppressWildcards Delta<TaskJiffiesSnapshot>>) {
        getListener().onLooperTaskOverHeat(deltas)
    }

    override fun onConcurrentOverHeat(key: String, concurrentCount: Int, duringMillis: Long) {
        getListener().onLooperConcurrentOverHeat(key, concurrentCount, duringMillis)
    }

    override fun onParseTaskJiffiesFail(key: String, pid: Int, tid: Int) {
    }

    override fun shouldTraceTask(delta: Delta<TaskJiffiesSnapshot>): Boolean {
        return BuildConfig.DEBUG || delta.during > 10L
    }

    @Deprecated("")
    class TaskTraceInfo {
        private var count: Int = 0
        private var times: LongArray? = null

        @JvmField
        var helpfulStr: String? = null

        fun increment() {
            if (times == null) {
                times = LongArray(LENGTH)
            }
            val index = count % LENGTH
            times!![index] = System.currentTimeMillis()
            count++
        }

        override fun toString(): String {
            return "$helpfulStr=$count"
        }

        override fun hashCode(): Int {
            return helpfulStr.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (helpfulStr == null) return false
            if (other !is String) return false
            return helpfulStr == other
        }

        companion object {
            private const val LENGTH = 1000
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.LooperTaskMonitorFeature"
    }
}
