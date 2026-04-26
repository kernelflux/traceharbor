package com.kernelflux.traceharbor.resource.watcher

import android.app.Activity
import android.app.Application
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import com.kernelflux.traceharbor.lifecycle.EmptyActivityLifecycleCallbacks
import com.kernelflux.traceharbor.report.FilePublisher
import com.kernelflux.traceharbor.resource.ResourcePlugin
import com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo
import com.kernelflux.traceharbor.resource.config.ResourceConfig
import com.kernelflux.traceharbor.resource.processor.AutoDumpProcessor
import com.kernelflux.traceharbor.resource.processor.BaseLeakProcessor
import com.kernelflux.traceharbor.resource.processor.ForkDumpProcessor
import com.kernelflux.traceharbor.resource.processor.LazyForkAnalyzeProcessor
import com.kernelflux.traceharbor.resource.processor.ManualDumpProcessor
import com.kernelflux.traceharbor.resource.processor.NativeForkAnalyzeProcessor
import com.kernelflux.traceharbor.resource.processor.NoDumpProcessor
import com.kernelflux.traceharbor.resource.processor.SilenceAnalyseProcessor
import com.kernelflux.traceharbor.resource.watcher.RetryableTaskExecutor.RetryableTask
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Collection
import java.util.Iterator
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class ActivityRefWatcher private constructor(
    app: Application,
    resourcePlugin: ResourcePlugin,
    componentFactory: ComponentFactory,
) : FilePublisher(app, FILE_CONFIG_EXPIRED_TIME_MILLIS, resourcePlugin.tag, resourcePlugin), Watcher {
    class ComponentFactory {
        open fun createDetectExecutor(
            config: ResourceConfig,
            handlerThread: HandlerThread,
        ): RetryableTaskExecutor = RetryableTaskExecutor(config.getScanIntervalMillis(), handlerThread)

        protected open fun createCustomLeakProcessor(
            dumpMode: ResourceConfig.DumpMode,
            watcher: ActivityRefWatcher,
        ): BaseLeakProcessor? = null

        fun createLeakProcess(
            dumpMode: ResourceConfig.DumpMode,
            watcher: ActivityRefWatcher,
        ): BaseLeakProcessor {
            val leakProcessor = createCustomLeakProcessor(dumpMode, watcher)
            if (leakProcessor != null) {
                return leakProcessor
            }
            return when (dumpMode) {
                ResourceConfig.DumpMode.AUTO_DUMP -> AutoDumpProcessor(watcher)
                ResourceConfig.DumpMode.MANUAL_DUMP ->
                    ManualDumpProcessor(watcher, watcher.getResourcePlugin().getConfig().getTargetActivity())
                ResourceConfig.DumpMode.SILENCE_ANALYSE -> SilenceAnalyseProcessor(watcher)
                ResourceConfig.DumpMode.FORK_DUMP -> ForkDumpProcessor(watcher)
                ResourceConfig.DumpMode.FORK_ANALYSE -> NativeForkAnalyzeProcessor(watcher)
                ResourceConfig.DumpMode.LAZY_FORK_ANALYZE -> LazyForkAnalyzeProcessor(watcher)
                ResourceConfig.DumpMode.NO_DUMP -> NoDumpProcessor(watcher)
            }
        }
    }

    private val mResourcePlugin: ResourcePlugin
    private val mDetectExecutor: RetryableTaskExecutor
    private val mMaxRedetectTimes: Int
    private val mBgScanTimes: Long
    private val mFgScanTimes: Long
    private val mHandlerThread: HandlerThread
    private val mHandler: Handler
    private val mDestroyedActivityInfos: ConcurrentLinkedQueue<DestroyedActivityInfo>
    private val mLeakProcessor: BaseLeakProcessor
    private val mDumpHprofMode: ResourceConfig.DumpMode

    constructor(
        app: Application,
        resourcePlugin: ResourcePlugin,
    ) : this(app, resourcePlugin, ComponentFactory()) {
    }

    init {
        mResourcePlugin = resourcePlugin
        val config = resourcePlugin.getConfig()
        mHandlerThread =
            TraceHarborHandlerThread.getNewHandlerThread("traceharbor_res", Thread.NORM_PRIORITY)
        mHandler = Handler(mHandlerThread.looper)
        mDumpHprofMode = config.getDumpHprofMode()
        mBgScanTimes = config.getBgScanIntervalMillis()
        mFgScanTimes = config.getScanIntervalMillis()
        mDetectExecutor = componentFactory.createDetectExecutor(config, mHandlerThread)
        mMaxRedetectTimes = config.getMaxRedetectTimes()
        mLeakProcessor = componentFactory.createLeakProcess(mDumpHprofMode, this)
        mDestroyedActivityInfos = ConcurrentLinkedQueue()
    }

    fun onForeground(isForeground: Boolean) {
        if (isForeground) {
            TraceHarborLog.i(TAG, "we are in foreground, modify scan time[%sms].", mFgScanTimes)
            mDetectExecutor.clearTasks()
            mDetectExecutor.setDelayMillis(mFgScanTimes)
            mDetectExecutor.executeInBackground(mScanDestroyedActivitiesTask)
        } else {
            TraceHarborLog.i(TAG, "we are in background, modify scan time[%sms].", mBgScanTimes)
            mDetectExecutor.setDelayMillis(mBgScanTimes)
        }
    }

    private val mRemovedActivityMonitor =
        object : EmptyActivityLifecycleCallbacks() {
            override fun onActivityDestroyed(activity: Activity) {
                pushDestroyedActivityInfo(activity)
                mHandler.postDelayed(
                    {
                        triggerGc()
                    },
                    2000,
                )
            }
        }

    override fun start() {
        stopDetect()
        val app = mResourcePlugin.application
        if (app != null) {
            app.registerActivityLifecycleCallbacks(mRemovedActivityMonitor)
            scheduleDetectProcedure()
            TraceHarborLog.i(TAG, "watcher is started.")
        }
    }

    override fun stop() {
        stopDetect()
        TraceHarborLog.i(TAG, "watcher is stopped.")
    }

    private fun stopDetect() {
        val app = mResourcePlugin.application
        if (app != null) {
            app.unregisterActivityLifecycleCallbacks(mRemovedActivityMonitor)
            unscheduleDetectProcedure()
        }
    }

    override fun destroy() {
        mDetectExecutor.quit()
        mHandlerThread.quitSafely()
        mLeakProcessor.onDestroy()
        TraceHarborLog.i(TAG, "watcher is destroyed.")
    }

    private fun pushDestroyedActivityInfo(activity: Activity) {
        val activityName = activity.javaClass.name
        if ((mDumpHprofMode == ResourceConfig.DumpMode.NO_DUMP ||
                mDumpHprofMode == ResourceConfig.DumpMode.AUTO_DUMP) &&
            !mResourcePlugin.getConfig().getDetectDebugger() &&
            isPublished(activityName)
        ) {
            TraceHarborLog.i(TAG, "activity leak with name %s had published, just ignore", activityName)
            return
        }

        val uuid = UUID.randomUUID()
        val keyBuilder = StringBuilder()
        keyBuilder
            .append(ACTIVITY_REFKEY_PREFIX)
            .append(activityName)
            .append("@")
            .append(activity.hashCode())
            .append('_')
            .append(java.lang.Long.toHexString(uuid.mostSignificantBits))
            .append(java.lang.Long.toHexString(uuid.leastSignificantBits))
        val key = keyBuilder.toString()
        val destroyedActivityInfo = DestroyedActivityInfo(key, activity, activityName)
        mDestroyedActivityInfos.add(destroyedActivityInfo)
        synchronized(mDestroyedActivityInfos) {
            (mDestroyedActivityInfos as java.lang.Object).notifyAll()
        }
        TraceHarborLog.d(TAG, "mDestroyedActivityInfos add %s", activityName)
    }

    private fun scheduleDetectProcedure() {
        mDetectExecutor.executeInBackground(mScanDestroyedActivitiesTask)
    }

    private fun unscheduleDetectProcedure() {
        mDetectExecutor.clearTasks()
        mDestroyedActivityInfos.clear()
    }

    private val mScanDestroyedActivitiesTask =
        object : RetryableTask {
            override fun execute(): RetryableTask.Status {
                if (mDestroyedActivityInfos.isEmpty()) {
                    TraceHarborLog.i(TAG, "DestroyedActivityInfo is empty! wait...")
                    synchronized(mDestroyedActivityInfos) {
                        try {
                            while (mDestroyedActivityInfos.isEmpty()) {
                                (mDestroyedActivityInfos as java.lang.Object).wait()
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    TraceHarborLog.i(TAG, "DestroyedActivityInfo is NOT empty! resume check")
                    return RetryableTask.Status.RETRY
                }

                if (Debug.isDebuggerConnected() && !mResourcePlugin.getConfig().getDetectDebugger()) {
                    TraceHarborLog.w(
                        TAG,
                        "debugger is connected, to avoid fake result, detection was delayed.",
                    )
                    return RetryableTask.Status.RETRY
                }

                triggerGc()
                val infoIt = mDestroyedActivityInfos.iterator()
                while (infoIt.hasNext()) {
                    val destroyedActivityInfo = infoIt.next()
                    if ((mDumpHprofMode == ResourceConfig.DumpMode.NO_DUMP ||
                            mDumpHprofMode == ResourceConfig.DumpMode.AUTO_DUMP) &&
                        !mResourcePlugin.getConfig().getDetectDebugger() &&
                        isPublished(destroyedActivityInfo.mActivityName)
                    ) {
                        TraceHarborLog.v(
                            TAG,
                            "activity with key [%s] was already published.",
                            destroyedActivityInfo.mActivityName,
                        )
                        infoIt.remove()
                        continue
                    }
                    triggerGc()
                    if (destroyedActivityInfo.mActivityRef.get() == null) {
                        TraceHarborLog.v(
                            TAG,
                            "activity with key [%s] was already recycled.",
                            destroyedActivityInfo.mKey,
                        )
                        infoIt.remove()
                        continue
                    }

                    destroyedActivityInfo.mDetectedCount++
                    if (destroyedActivityInfo.mDetectedCount < mMaxRedetectTimes &&
                        !mResourcePlugin.getConfig().getDetectDebugger()
                    ) {
                        TraceHarborLog.i(
                            TAG,
                            "activity with key [%s] should be recycled but actually still exists in %s times, wait for next detection to confirm.",
                            destroyedActivityInfo.mKey,
                            destroyedActivityInfo.mDetectedCount,
                        )
                        triggerGc()
                        continue
                    }

                    TraceHarborLog.i(
                        TAG,
                        "activity with key [%s] was suspected to be a leaked instance. mode[%s]",
                        destroyedActivityInfo.mKey,
                        mDumpHprofMode,
                    )
                    if (mLeakProcessor.process(destroyedActivityInfo)) {
                        TraceHarborLog.i(
                            TAG,
                            "the leaked activity [%s] with key [%s] has been processed. stop polling",
                            destroyedActivityInfo.mActivityName,
                            destroyedActivityInfo.mKey,
                        )
                        infoIt.remove()
                    }
                }
                return RetryableTask.Status.RETRY
            }
        }

    fun getLeakProcessor(): BaseLeakProcessor = mLeakProcessor

    fun getResourcePlugin(): ResourcePlugin = mResourcePlugin

    fun getDetectHandler(): Handler = mHandler

    fun getDestroyedActivityInfos(): Collection<DestroyedActivityInfo> =
        mDestroyedActivityInfos as Collection<DestroyedActivityInfo>

    private var lastTriggeredTime: Long = 0

    fun triggerGc() {
        val current = System.currentTimeMillis()
        if (mDumpHprofMode == ResourceConfig.DumpMode.NO_DUMP &&
            current - lastTriggeredTime < mResourcePlugin.getConfig().getScanIntervalMillis() / 2 - 100
        ) {
            TraceHarborLog.v(TAG, "skip triggering gc for frequency")
            return
        }
        lastTriggeredTime = current
        TraceHarborLog.v(TAG, "triggering gc...")
        Runtime.getRuntime().gc()
        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }
        Runtime.getRuntime().runFinalization()
        TraceHarborLog.v(TAG, "gc was triggered.")
    }

    companion object {
        private const val TAG = "TraceHarbor.ActivityRefWatcher"
        private val FILE_CONFIG_EXPIRED_TIME_MILLIS: Long = TimeUnit.DAYS.toMillis(1)
        private const val ACTIVITY_REFKEY_PREFIX = "MATRIX_RESCANARY_REFKEY_"
    }
}

