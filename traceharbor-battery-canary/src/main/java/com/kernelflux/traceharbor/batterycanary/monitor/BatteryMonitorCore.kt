package com.kernelflux.traceharbor.batterycanary.monitor

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.batterycanary.BatteryEventDelegate
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AbsTaskMonitorFeature.TaskJiffiesSnapshot
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AlarmMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.AppStatMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.JiffiesMonitorFeature.JiffiesSnapshot.ThreadJiffiesEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.LooperTaskMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Delta
import com.kernelflux.traceharbor.batterycanary.monitor.feature.MonitorFeature.Snapshot.Entry.ListEntry
import com.kernelflux.traceharbor.batterycanary.monitor.feature.NotificationMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.NotificationMonitorFeature.BadNotification
import com.kernelflux.traceharbor.batterycanary.monitor.feature.WakeLockMonitorFeature
import com.kernelflux.traceharbor.batterycanary.monitor.feature.WakeLockMonitorFeature.WakeLockTrace.WakeLockRecord
import com.kernelflux.traceharbor.batterycanary.utils.BatteryCanaryUtil
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.concurrent.Callable

open class BatteryMonitorCore @SuppressLint("VisibleForTests") constructor(
    private val mConfig: BatteryMonitorConfig,
) : LooperTaskMonitorFeature.LooperTaskListener,
    WakeLockMonitorFeature.WakeLockListener,
    AlarmMonitorFeature.AlarmListener,
    JiffiesMonitorFeature.JiffiesListener,
    AppStatMonitorFeature.AppStatListener,
    NotificationMonitorFeature.NotificationListener,
    Handler.Callback {

    interface Callback<T : MonitorFeature.Snapshot<T>> {
        fun onGetJiffies(snapshot: T)
    }

    interface JiffiesListener {
        fun onTraceBegin()
        fun onTraceEnd(isForeground: Boolean) // TODO: configurable status support
    }

    private inner class ForegroundLoopCheckTask : Runnable {
        var lastWhat: Int = MSG_ID_JIFFIES_START

        override fun run() {
            if (mForegroundModeEnabled) {
                val message = Message.obtain(mHandler)
                message.what = lastWhat
                message.arg1 = MSG_ARG_FOREGROUND
                mHandler.sendMessageAtFrontOfQueue(message)
                lastWhat = if (lastWhat == MSG_ID_JIFFIES_END) MSG_ID_JIFFIES_START else MSG_ID_JIFFIES_END
                mHandler.postDelayed(this, mFgLooperMillis)
            }
        }
    }

    private inner class BackgroundLoopCheckTask : Runnable {
        var round: Int = 0

        override fun run() {
            round++
            TraceHarborLog.i(TAG, "#onBackgroundLoopCheck, round = $round")
            if (!isForeground()) {
                synchronized(BatteryMonitorCore::class.java) {
                    for (plugin in mConfig.features) {
                        plugin.onBackgroundCheck(mBgLooperMillis * round)
                    }
                }
            }
            if (!isForeground()) {
                mHandler.postDelayed(this, mBgLooperMillis)
            }
        }
    }

    private val mHandler: Handler
    private val mCanaryHandler: Handler
    private var mFgLooperTask: ForegroundLoopCheckTask? = null
    private var mBgLooperTask: BackgroundLoopCheckTask? = null

    private var mSupplier: Callable<String> = Callable { "unknown" }

    @Volatile
    private var mTurnOn: Boolean = false
    private var mAppForeground: Boolean = ProcessUILifecycleOwner.isProcessForeground
    private var mForegroundModeEnabled: Boolean = false
    private var mBackgroundModeEnabled: Boolean = false
    private val mMonitorDelayMillis: Long
    private val mFgLooperMillis: Long
    private val mBgLooperMillis: Long

    init {
        if (mConfig.callback is BatteryMonitorCallback.BatteryPrinter) {
            (mConfig.callback as BatteryMonitorCallback.BatteryPrinter).attach(this)
        }
        val sceneSupplier = mConfig.onSceneSupplier
        if (sceneSupplier != null) {
            mSupplier = sceneSupplier
        }

        val canaryThread = mConfig.canaryThread
        if (canaryThread != null) {
            val thread: HandlerThread = canaryThread
            mHandler = Handler(thread.looper, this) // For BatteryMonitorCore only
            mCanaryHandler = Handler(thread.looper, this) // For BatteryCanary
        } else {
            val thread = TraceHarborHandlerThread.getDefaultHandlerThread()
            mHandler = Handler(thread.looper, this) // For BatteryMonitorCore only
            mCanaryHandler = mHandler // For BatteryCanary as legacy logic
        }

        enableForegroundLoopCheck(mConfig.isForegroundModeEnabled)
        enableBackgroundLoopCheck(mConfig.isBackgroundModeEnabled)
        mMonitorDelayMillis = mConfig.greyTime
        mFgLooperMillis = mConfig.foregroundLoopCheckTime
        mBgLooperMillis = mConfig.backgroundLoopCheckTime

        for (plugin in mConfig.features) {
            plugin.configure(this)
        }
    }

    @VisibleForTesting
    open fun enableForegroundLoopCheck(bool: Boolean) {
        mForegroundModeEnabled = bool
        if (mForegroundModeEnabled) {
            mFgLooperTask = ForegroundLoopCheckTask()
        }
    }

    @VisibleForTesting
    open fun enableBackgroundLoopCheck(bool: Boolean) {
        mBackgroundModeEnabled = bool
    }

    override fun handleMessage(msg: Message): Boolean {
        if (msg.what == MSG_ID_JIFFIES_START) {
            notifyTraceBegin()
            return true
        }
        if (msg.what == MSG_ID_JIFFIES_END) {
            notifyTraceEnd(msg.arg1 == MSG_ARG_FOREGROUND)
            return true
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    open fun <T : MonitorFeature> getMonitorFeature(clazz: Class<T>): T? {
        for (plugin in mConfig.features) {
            if (clazz.isAssignableFrom(plugin.javaClass)) {
                return plugin as T
            }
        }
        return null
    }

    open fun getConfig(): BatteryMonitorConfig = mConfig

    open fun isTurnOn(): Boolean {
        synchronized(BatteryMonitorCore::class.java) {
            return mTurnOn
        }
    }

    open fun start() {
        synchronized(BatteryMonitorCore::class.java) {
            if (!mTurnOn) {
                for (plugin in mConfig.features) {
                    plugin.onTurnOn()
                }
                mTurnOn = true
            }
            if (BatteryEventDelegate.isInit()) {
                BatteryEventDelegate.getInstance().attach(this).startListening()
            }
        }
    }

    open fun stop() {
        synchronized(BatteryMonitorCore::class.java) {
            if (mTurnOn) {
                mHandler.removeCallbacksAndMessages(null)
                for (plugin in mConfig.features) {
                    plugin.onTurnOff()
                }
                mTurnOn = false
            }
        }
    }

    open fun onForeground(isForeground: Boolean) {
        if (!TraceHarbor.isInstalled()) {
            TraceHarborLog.e(TAG, "TraceHarbor was not installed yet, just ignore the event")
            return
        }
        mAppForeground = isForeground

        if (BatteryEventDelegate.isInit()) {
            BatteryEventDelegate.getInstance().onForeground(isForeground)
        }

        if (!isForeground) {
            // 1. remove all checks
            mHandler.removeCallbacksAndMessages(null)

            // 2. start background jiffies check
            val message = Message.obtain(mHandler)
            message.what = MSG_ID_JIFFIES_START
            mHandler.sendMessageDelayed(message, mMonitorDelayMillis)

            // 3. start background loop check task
            if (mBackgroundModeEnabled) {
                if (mBgLooperTask != null) {
                    mHandler.removeCallbacks(mBgLooperTask!!)
                    mBgLooperTask = null
                }
                mBgLooperTask = BackgroundLoopCheckTask()
                mHandler.postDelayed(mBgLooperTask!!, mBgLooperMillis)
            }
        } else if (!mHandler.hasMessages(MSG_ID_JIFFIES_START)) {
            // 1. remove background loop task
            if (mBgLooperTask != null) {
                mHandler.removeCallbacks(mBgLooperTask!!)
                mBgLooperTask = null
            }

            // 2. finish background jiffies check
            val message = Message.obtain(mHandler)
            message.what = MSG_ID_JIFFIES_END
            mHandler.sendMessageAtFrontOfQueue(message)

            // 3. start foreground jiffies loop check
            if (mForegroundModeEnabled && mFgLooperTask != null) {
                mHandler.removeCallbacks(mFgLooperTask!!)
                mFgLooperTask!!.lastWhat = MSG_ID_JIFFIES_START
                mHandler.post(mFgLooperTask!!)
            }
        }

        for (plugin in mConfig.features) {
            plugin.onForeground(isForeground)
        }
    }

    open fun getHandler(): Handler = mCanaryHandler

    open fun getContext(): Context {
        // FIXME: context api configs
        return TraceHarbor.with().application
    }

    open fun getScene(): String {
        return try {
            mSupplier.call()
        } catch (e: Exception) {
            "unknown"
        }
    }

    open fun isForeground(): Boolean = mAppForeground

    open fun getCurrentBatteryTemperature(context: Context): Int {
        return try {
            val tmp = BatteryCanaryUtil.getBatteryTemperature(context)
            TraceHarborLog.i(TAG, "onGetTemperature, battery = $tmp")
            tmp
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "#currentBatteryTemperature error")
            -1
        }
    }

    private fun notifyTraceBegin() {
        TraceHarborLog.d(TAG, "#onTraceBegin")
        getConfig().callback.onTraceBegin()
    }

    private fun notifyTraceEnd(isForeground: Boolean) {
        TraceHarborLog.d(TAG, "#onTraceEnd")
        getConfig().callback.onTraceEnd(isForeground)
    }

    @Deprecated("")
    override fun onTaskTrace(thread: Thread, sortList: List<LooperTaskMonitorFeature.TaskTraceInfo>) {
        TraceHarborLog.d(TAG, "#onTaskTrace, thread = " + thread.name)
        getConfig().callback.onTaskTrace(thread, sortList)
    }

    override fun onLooperTaskOverHeat(deltas: List<Delta<TaskJiffiesSnapshot>>) {
        getConfig().callback.onLooperTaskOverHeat(deltas)
    }

    override fun onLooperConcurrentOverHeat(key: String, concurrentCount: Int, duringMillis: Long) {
        getConfig().callback.onLooperConcurrentOverHeat(key, concurrentCount, duringMillis)
    }

    @Deprecated("")
    override fun onWakeLockTimeout(warningCount: Int, record: WakeLockRecord) {
        getConfig().callback.onWakeLockTimeout(warningCount, record)
    }

    override fun onWakeLockTimeout(record: WakeLockRecord, backgroundMillis: Long) {
        getConfig().callback.onWakeLockTimeout(record, backgroundMillis)
    }

    override fun onAlarmDuplicated(duplicatedCount: Int, record: AlarmMonitorFeature.AlarmRecord) {
        getConfig().callback.onAlarmDuplicated(duplicatedCount, record)
    }

    @Deprecated("")
    override fun onParseError(pid: Int, tid: Int) {
        getConfig().callback.onParseError(pid, tid)
    }

    override fun onWatchingThreads(threadJiffiesList: ListEntry<out ThreadJiffiesEntry>) {
        getConfig().callback.onWatchingThreads(threadJiffiesList)
    }

    override fun onForegroundServiceLeak(
        isMyself: Boolean,
        appImportance: Int,
        globalAppImportance: Int,
        componentName: ComponentName,
        millis: Long,
    ) {
        getConfig().callback.onForegroundServiceLeak(isMyself, appImportance, globalAppImportance, componentName, millis)
    }

    override fun onAppSateLeak(isMyself: Boolean, appImportance: Int, componentName: ComponentName, millis: Long) {
        getConfig().callback.onAppSateLeak(isMyself, appImportance, componentName, millis)
    }

    override fun onNotify(notification: BadNotification) {
        getConfig().callback.onNotify(notification)
    }

    companion object {
        private const val TAG = "TraceHarbor.battery.BatteryMonitorCore"
        private const val MSG_ID_JIFFIES_START = 0x1
        private const val MSG_ID_JIFFIES_END = 0x2
        private const val MSG_ARG_FOREGROUND = 0x3
    }
}
