package com.kernelflux.traceharbor.trace.tracer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.trace.TracePlugin
import com.kernelflux.traceharbor.trace.config.SharePluginInfo
import com.kernelflux.traceharbor.trace.config.TraceConfig
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.core.UIThreadMonitor
import com.kernelflux.traceharbor.trace.listeners.IDoFrameListener
import com.kernelflux.traceharbor.trace.listeners.IDropFrameListener
import com.kernelflux.traceharbor.trace.listeners.IFrameListener
import com.kernelflux.traceharbor.trace.listeners.ISceneFrameListener
import com.kernelflux.traceharbor.trace.listeners.LooperObserver
import com.kernelflux.traceharbor.util.DeviceUtil
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import org.json.JSONException
import org.json.JSONObject
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap

/**
 * Frames-per-second / dropped-frames tracer. On API ≥ N hooks
 * `Window.addOnFrameMetricsAvailableListener` per resumed Activity to
 * collect [FrameMetrics] directly from the framework; on older APIs
 * falls back to the legacy [LooperObserver] timing inferred from
 * `Choreographer` timestamps via [UIThreadMonitor].
 *
 * Flow:
 * - `onAlive` → `forceEnable` → API ≥ N: register an Activity
 *   lifecycle callback so each `onActivityResumed` attaches a per-
 *   window [Window.OnFrameMetricsAvailableListener]; API < N:
 *   register `looperObserver` with `UIThreadMonitor`.
 * - Each frame: invoke
 *   [SceneFrameCollector.onFrameMetricsAvailable], aggregating into
 *   per-scene `SceneFrameCollectItem`s. After
 *   [ISceneFrameListener.getIntervalMs], the item averages and emits
 *   via `ISceneFrameListener.onFrameMetricsAvailable`.
 * - The default [AllSceneFrameListener] turns each emit into a
 *   `Constants.Type.FPS` Issue.
 */
@Suppress("DEPRECATION")
class FrameTracer(private val config: TraceConfig) :
    Tracer(), Application.ActivityLifecycleCallbacks {

    private var droppedSum: Double = 0.0

    @Deprecated("Kept for compatibility")
    private var durationSum: Long = 0

    @Deprecated("Kept for compatibility")
    private val oldListeners: HashSet<IDoFrameListener> = HashSet()

    @Deprecated("Kept for compatibility")
    private var frameIntervalNs: Long = UIThreadMonitor.getMonitor().getFrameIntervalNanos()

    @Deprecated("Kept for compatibility")
    private val looperObserver: LooperObserver = object : LooperObserver() {
        override fun doFrame(
            focusedActivity: String,
            startNs: Long,
            endNs: Long,
            isVsyncFrame: Boolean,
            intendedFrameTimeNs: Long,
            inputCostNs: Long,
            animationCostNs: Long,
            traversalCostNs: Long,
        ) {
            if (isForeground()) {
                notifyListener(
                    focusedActivity,
                    startNs,
                    endNs,
                    isVsyncFrame,
                    intendedFrameTimeNs,
                    inputCostNs,
                    animationCostNs,
                    traversalCostNs,
                )
            }
        }

        @Suppress("DEPRECATION")
        private fun notifyListener(
            focusedActivity: String,
            startNs: Long,
            endNs: Long,
            isVsyncFrame: Boolean,
            intendedFrameTimeNs: Long,
            inputCostNs: Long,
            animationCostNs: Long,
            traversalCostNs: Long,
        ) {
            val traceBegin = System.currentTimeMillis()
            try {
                val jitter = endNs - intendedFrameTimeNs
                val dropFrame = (jitter / frameIntervalNs).toInt()
                if (oldDropFrameListener != null && dropFrame > dropFrameListenerThreshold) {
                    try {
                        val top = TraceHarborUtil.getTopActivityName()
                        if (top != null) {
                            oldDropFrameListener?.dropFrame(dropFrame, jitter, top)
                        }
                    } catch (e: Exception) {
                        TraceHarborLog.e(TAG, "dropFrameListener error e:" + e.message)
                    }
                }

                droppedSum += dropFrame
                durationSum += Math.max(jitter, frameIntervalNs)

                synchronized(oldListeners) {
                    for (listener in oldListeners) {
                        if (config.isDevEnv()) {
                            listener.time = SystemClock.uptimeMillis()
                        }
                        if (null != listener.getExecutor()) {
                            if (listener.getIntervalFrameReplay() > 0) {
                                listener.collect(
                                    focusedActivity,
                                    startNs,
                                    endNs,
                                    dropFrame,
                                    isVsyncFrame,
                                    intendedFrameTimeNs,
                                    inputCostNs,
                                    animationCostNs,
                                    traversalCostNs,
                                )
                            } else {
                                listener.getExecutor()?.execute {
                                    listener.doFrameAsync(
                                        focusedActivity,
                                        startNs,
                                        endNs,
                                        dropFrame,
                                        isVsyncFrame,
                                        intendedFrameTimeNs,
                                        inputCostNs,
                                        animationCostNs,
                                        traversalCostNs,
                                    )
                                }
                            }
                        } else {
                            listener.doFrameSync(
                                focusedActivity,
                                startNs,
                                endNs,
                                dropFrame,
                                isVsyncFrame,
                                intendedFrameTimeNs,
                                inputCostNs,
                                animationCostNs,
                                traversalCostNs,
                            )
                        }

                        if (config.isDevEnv()) {
                            listener.time = SystemClock.uptimeMillis() - listener.time
                            TraceHarborLog.d(
                                TAG,
                                "[notifyListener] cost:%sms listener:%s",
                                listener.time,
                                listener,
                            )
                        }
                    }
                }
            } finally {
                val cost = System.currentTimeMillis() - traceBegin
                if (config.isDebug() && cost > frameIntervalNs) {
                    TraceHarborLog.w(
                        TAG,
                        "[notifyListener] warm! maybe do heavy work in doFrameSync! size:%s cost:%sms",
                        oldListeners.size,
                        cost,
                    )
                }
            }
        }
    }

    @Deprecated("Kept for compatibility")
    private var oldDropFrameListener: DropFrameListener? = null
    private var dropFrameListener: IDropFrameListener? = null
    private var dropFrameListenerThreshold: Int = 0

    private val listeners: HashSet<IFrameListener> = HashSet()
    private val frozenThreshold: Long = config.getFrozenThreshold().toLong()
    private val highThreshold: Long = config.getHighThreshold().toLong()
    private val middleThreshold: Long = config.getMiddleThreshold().toLong()
    private val normalThreshold: Long = config.getNormalThreshold().toLong()

    @JvmField
    internal var sceneFrameCollector: SceneFrameCollector? = null

    private val frameListenerMap: MutableMap<Int, Window.OnFrameMetricsAvailableListener> =
        ConcurrentHashMap()

    init {
        TraceHarborLog.i(
            TAG,
            "[init] frameIntervalMs:%s isFPSEnable:%s",
            frameIntervalNs,
            config.isFPSEnable(),
        )
    }

    @Deprecated("Use addListener(IFrameListener) instead")
    fun addListener(listener: IDoFrameListener) {
        synchronized(oldListeners) {
            oldListeners.add(listener)
        }
    }

    @Deprecated("Use removeListener(IFrameListener) instead")
    fun removeListener(listener: IDoFrameListener) {
        synchronized(oldListeners) {
            oldListeners.remove(listener)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun addListener(listener: IFrameListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun removeListener(listener: IFrameListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun register(listener: ISceneFrameListener) {
        sceneFrameCollector?.register(listener)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun unregister(listener: ISceneFrameListener, isCallbackRestAfterUnregister: Boolean) {
        sceneFrameCollector?.unregister(listener, isCallbackRestAfterUnregister)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun unregister(listener: ISceneFrameListener) {
        unregister(listener, false)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun reset(listener: ISceneFrameListener, isCallbackRestBeforeReset: Boolean) {
        sceneFrameCollector?.reset(listener, isCallbackRestBeforeReset)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun reset(listener: ISceneFrameListener) {
        unregister(listener, false)
    }

    override fun onAlive() {
        super.onAlive()
        if (config.isFPSEnable()) {
            forceEnable()
        }
    }

    @SuppressLint("NewApi")
    fun forceEnable() {
        TraceHarborLog.i(TAG, "forceEnable")
        if (sdkInt >= Build.VERSION_CODES.N) {
            TraceHarbor.with().getApplication()?.registerActivityLifecycleCallbacks(this)
            val collector = SceneFrameCollector()
            sceneFrameCollector = collector
            addListener(collector)
            register(AllSceneFrameListener())
        } else {
            UIThreadMonitor.getMonitor().addObserver(looperObserver)
        }
    }

    fun forceDisable() {
        TraceHarborLog.i(TAG, "forceDisable")
        removeDropFrameListener()
        if (sdkInt >= Build.VERSION_CODES.N) {
            TraceHarbor.with().getApplication()?.unregisterActivityLifecycleCallbacks(this)
            listeners.clear()
            frameListenerMap.clear()
        } else {
            UIThreadMonitor.getMonitor().removeObserver(looperObserver)
            oldListeners.clear()
        }
    }

    override fun onDead() {
        super.onDead()
        if (config.isFPSEnable()) {
            forceDisable()
        }
    }

    fun getDroppedSum(): Int = droppedSum.toInt()

    @Deprecated("Kept for compatibility")
    fun getDurationSum(): Long = durationSum

    /**
     * Public final enum of dropped-frame severity levels. Each level
     * acts as both a logical bucket and an array index (via `ordinal`)
     * into the `dropLevel` / `dropSum` companion arrays.
     */
    enum class DropStatus {
        DROPPED_BEST,
        DROPPED_NORMAL,
        DROPPED_MIDDLE,
        DROPPED_HIGH,
        DROPPED_FROZEN;

        companion object {
            @JvmStatic
            fun stringify(level: IntArray, sum: IntArray): String {
                val sb = StringBuilder()
                sb.append('{')

                for (item in values()) {
                    sb.append('(').append(item.name).append("_LEVEL=")
                        .append(level[item.ordinal]).append(" ")
                    sb.append(item.name).append("_SUM=")
                        .append(sum[item.ordinal]).append("); ")
                }
                sb.setLength(sb.length - 2) // remove the last "; "
                sb.append("}")

                return sb.toString()
            }
        }
    }

    /**
     * Public final enum mirroring [FrameMetrics] duration buckets.
     * The companion `indices` array maps each ordinal to the
     * corresponding `FrameMetrics.*_DURATION` int constant so callers
     * can `frameMetrics.getMetric(FrameDuration.indices[i])`.
     */
    enum class FrameDuration {
        UNKNOWN_DELAY_DURATION,
        INPUT_HANDLING_DURATION,
        ANIMATION_DURATION,
        LAYOUT_MEASURE_DURATION,
        DRAW_DURATION,
        SYNC_DURATION,
        COMMAND_ISSUE_DURATION,
        SWAP_BUFFERS_DURATION,
        TOTAL_DURATION,
        GPU_DURATION;

        companion object {
            @SuppressLint("InlinedApi")
            @JvmField
            val indices: IntArray = intArrayOf(
                FrameMetrics.UNKNOWN_DELAY_DURATION,
                FrameMetrics.INPUT_HANDLING_DURATION,
                FrameMetrics.ANIMATION_DURATION,
                FrameMetrics.LAYOUT_MEASURE_DURATION,
                FrameMetrics.DRAW_DURATION,
                FrameMetrics.SYNC_DURATION,
                FrameMetrics.COMMAND_ISSUE_DURATION,
                FrameMetrics.SWAP_BUFFERS_DURATION,
                FrameMetrics.TOTAL_DURATION,
                FrameMetrics.GPU_DURATION,
            )

            @JvmStatic
            fun stringify(durations: LongArray): String {
                val sb = StringBuilder()
                sb.append('{')

                for (item in values()) {
                    sb.append(item.name).append('=').append(durations[item.ordinal]).append("; ")
                }
                sb.setLength(sb.length - 2) // remove the last "; "
                sb.append("}")

                return sb.toString()
            }
        }
    }

    /**
     * Per-scene aggregator. Inner class so it can read the outer
     * tracer's threshold fields (`frozenThreshold`, `highThreshold`,
     * `middleThreshold`, `normalThreshold`) directly.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    internal inner class SceneFrameCollector : IFrameListener {

        private val frameHandler =
            Handler(TraceHarborHandlerThread.getDefaultHandlerThread().looper)

        private val specifiedSceneMap: HashMap<String, SceneFrameCollectItem> = HashMap()
        private val unspecifiedSceneMap: HashMap<ISceneFrameListener, SceneFrameCollectItem> =
            HashMap()

        @Synchronized
        fun register(listener: ISceneFrameListener) {
            if (listener.getIntervalMs() < 1 || listener.getThreshold() < 0) {
                TraceHarborLog.e(
                    TAG,
                    "Illegal value, intervalMs=%d, threshold=%d, activity=%s",
                    listener.getIntervalMs(),
                    listener.getThreshold(),
                    listener.javaClass.name,
                )
                return
            }
            val scene = listener.getName()
            val collectItem = SceneFrameCollectItem(listener)
            if (scene == null || scene.isEmpty()) {
                unspecifiedSceneMap[listener] = collectItem
            } else {
                specifiedSceneMap[scene] = collectItem
            }
        }

        @Synchronized
        fun unregister(listener: ISceneFrameListener, isCallbackRestAfterUnregister: Boolean) {
            val scene = listener.getName()
            val target: SceneFrameCollectItem? = if (scene == null || scene.isEmpty()) {
                unspecifiedSceneMap.remove(listener)
            } else {
                specifiedSceneMap.remove(scene)
            }

            if (target != null && isCallbackRestAfterUnregister) {
                frameHandler.post {
                    target.tryCallBackAndReset()
                }
            }
        }

        @Synchronized
        fun reset(listener: ISceneFrameListener, isCallbackRestBeforeReset: Boolean) {
            val scene = listener.getName()
            val target: SceneFrameCollectItem? = if (scene == null || scene.isEmpty()) {
                unspecifiedSceneMap[listener]
            } else {
                specifiedSceneMap[scene]
            }
            if (target != null && isCallbackRestBeforeReset) {
                target.tryCallBackAndReset()
            }
        }

        @Synchronized
        fun resetAllAndCallBack() {
            for (value in unspecifiedSceneMap.values) {
                value.tryCallBackAndReset()
            }
            for (value in specifiedSceneMap.values) {
                value.tryCallBackAndReset()
            }
        }

        override fun onFrameMetricsAvailable(
            sceneName: String,
            frameMetrics: FrameMetrics,
            droppedFrames: Float,
            refreshRate: Float,
        ) {
            frameHandler.post {
                // NOTE: this preserves the Java original's surprising
                // `sceneName.getClass().getName()` lookup — it keys
                // the specified-scene map by the runtime class name
                // of the *string* (always `java.lang.String`) rather
                // than the scene name itself. Likely a long-standing
                // bug in the upstream wechat-matrix code, but kept
                // for behavioural parity.
                val scene = sceneName.javaClass.name
                synchronized(this@SceneFrameCollector) {
                    val collectItem = specifiedSceneMap[scene]
                    collectItem?.append(sceneName, frameMetrics, droppedFrames, refreshRate)
                    for (frameCollectItem in unspecifiedSceneMap.values) {
                        frameCollectItem.append(sceneName, frameMetrics, droppedFrames, refreshRate)
                    }
                }
            }
        }
    }

    /**
     * Per-scene running aggregate of frame metrics. Inner class so it
     * can read the outer tracer's threshold fields when classifying
     * frames into [DropStatus] buckets.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private inner class SceneFrameCollectItem(@JvmField val listener: ISceneFrameListener) {
        private val durations: LongArray = LongArray(FrameDuration.values().size)
        private val dropLevel: IntArray = IntArray(DropStatus.values().size)
        private val dropSum: IntArray = IntArray(DropStatus.values().size)
        private var dropCount: Float = 0f
        private var refreshRate: Float = 0f
        private var totalDuration: Float = 0f
        private var beginMs: Long = 0
        private var lastScene: String = ""
        private var count: Int = 0

        fun append(
            scene: String,
            frameMetrics: FrameMetrics,
            droppedFrames: Float,
            refreshRate: Float,
        ) {
            if ((listener.skipFirstFrame() && frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) ||
                droppedFrames < (refreshRate / 60) * listener.getThreshold()
            ) {
                return
            }
            if (count == 0) {
                beginMs = SystemClock.uptimeMillis()
            }
            for (i in FrameDuration.UNKNOWN_DELAY_DURATION.ordinal..FrameDuration.TOTAL_DURATION.ordinal) {
                durations[i] += frameMetrics.getMetric(FrameDuration.indices[i])
            }
            if (sdkInt >= Build.VERSION_CODES.S) {
                durations[FrameDuration.GPU_DURATION.ordinal] +=
                    frameMetrics.getMetric(FrameMetrics.GPU_DURATION)
            }

            dropCount += droppedFrames
            collect(Math.round(droppedFrames))
            this.refreshRate += refreshRate
            val frameIntervalNanos = Constants.TIME_SECOND_TO_NANO / refreshRate
            totalDuration += Math.max(
                frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION).toFloat(),
                frameIntervalNanos,
            )
            ++count

            lastScene = scene
            if (SystemClock.uptimeMillis() - beginMs >= listener.getIntervalMs()) {
                tryCallBackAndReset()
            }
        }

        fun tryCallBackAndReset() {
            if (count > 20) {
                dropCount /= count
                this.refreshRate /= count
                totalDuration /= count
                for (i in durations.indices) {
                    durations[i] = durations[i] / count
                }
                listener.onFrameMetricsAvailable(
                    lastScene,
                    durations,
                    dropLevel,
                    dropSum,
                    dropCount,
                    this.refreshRate,
                    Constants.TIME_SECOND_TO_NANO / totalDuration,
                )
            }
            reset()
        }

        private fun collect(droppedFrames: Int) {
            if (droppedFrames >= frozenThreshold) {
                dropLevel[DropStatus.DROPPED_FROZEN.ordinal]++
                dropSum[DropStatus.DROPPED_FROZEN.ordinal] += droppedFrames
            } else if (droppedFrames >= highThreshold) {
                dropLevel[DropStatus.DROPPED_HIGH.ordinal]++
                dropSum[DropStatus.DROPPED_HIGH.ordinal] += droppedFrames
            } else if (droppedFrames >= middleThreshold) {
                dropLevel[DropStatus.DROPPED_MIDDLE.ordinal]++
                dropSum[DropStatus.DROPPED_MIDDLE.ordinal] += droppedFrames
            } else if (droppedFrames >= normalThreshold) {
                dropLevel[DropStatus.DROPPED_NORMAL.ordinal]++
                dropSum[DropStatus.DROPPED_NORMAL.ordinal] += droppedFrames
            } else {
                dropLevel[DropStatus.DROPPED_BEST.ordinal]++
                dropSum[DropStatus.DROPPED_BEST.ordinal] += Math.max(droppedFrames, 0)
            }
        }

        private fun reset() {
            dropCount = 0f
            refreshRate = 0f
            totalDuration = 0f
            count = 0

            Arrays.fill(durations, 0)
            Arrays.fill(dropLevel, 0)
            Arrays.fill(dropSum, 0)
        }
    }

    /**
     * Reserved for compatibility — use [setDropFrameListener]
     * (`IDropFrameListener` overload) for API ≥ N.
     */
    @Deprecated("Use setDropFrameListener instead")
    fun addDropFrameListener(
        dropFrameListenerThreshold: Int,
        dropFrameListener: DropFrameListener?,
    ) {
        this.oldDropFrameListener = dropFrameListener
        this.dropFrameListenerThreshold = dropFrameListenerThreshold
    }

    fun setDropFrameListener(
        dropFrameListenerThreshold: Int,
        dropFrameListener: IDropFrameListener?,
    ) {
        this.dropFrameListener = dropFrameListener
        this.dropFrameListenerThreshold = dropFrameListenerThreshold
    }

    fun removeDropFrameListener() {
        this.oldDropFrameListener = null
        this.dropFrameListener = null
    }

    @Deprecated("Use IDropFrameListener instead")
    fun interface DropFrameListener {
        fun dropFrame(droppedFrame: Int, jitter: Long, scene: String)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    private fun getRefreshRate(window: Window): Float {
        if (sdkInt >= Build.VERSION_CODES.R) {
            return window.context.display!!.refreshRate
        }
        @Suppress("DEPRECATION")
        return window.windowManager.defaultDisplay.refreshRate
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onActivityResumed(activity: Activity) {
        if (frameListenerMap.containsKey(activity.hashCode())) {
            return
        }

        defaultRefreshRate = getRefreshRate(activity.window)
        TraceHarborLog.i(TAG, "default refresh rate is %dHz", defaultRefreshRate.toInt())

        val onFrameMetricsAvailableListener = object : Window.OnFrameMetricsAvailableListener {
            private var cachedRefreshRate: Float = defaultRefreshRate
            private var cachedThreshold: Float =
                dropFrameListenerThreshold / 60f * cachedRefreshRate
            private var lastModeId: Int = -1
            private var lastThreshold: Int = -1
            private var attributes: WindowManager.LayoutParams? = null

            private fun updateRefreshRate(window: Window) {
                if (attributes == null) {
                    attributes = window.attributes
                }
                val attrs = attributes ?: return
                if (attrs.preferredDisplayModeId != lastModeId ||
                    lastThreshold != dropFrameListenerThreshold
                ) {
                    lastModeId = attrs.preferredDisplayModeId
                    lastThreshold = dropFrameListenerThreshold
                    cachedRefreshRate = getRefreshRate(window)
                    cachedThreshold = dropFrameListenerThreshold / 60f * cachedRefreshRate
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            override fun onFrameMetricsAvailable(
                window: Window,
                frameMetrics: FrameMetrics,
                dropCountSinceLastInvocation: Int,
            ) {
                if (isForeground()) {
                    // skip not-available metrics. Some devices (e.g.
                    // Honor NTH-AN00, ANY-AN00) emit huge outliers
                    // here that would skew the running average.
                    for (i in FrameDuration.UNKNOWN_DELAY_DURATION.ordinal..FrameDuration.TOTAL_DURATION.ordinal) {
                        val v = frameMetrics.getMetric(FrameDuration.indices[i])
                        if (v < 0 || v >= HALF_MAX) {
                            return
                        }
                    }
                    val frameMetricsCopy = FrameMetrics(frameMetrics)

                    updateRefreshRate(window)

                    val totalDuration = frameMetricsCopy.getMetric(FrameMetrics.TOTAL_DURATION)
                    val frameIntervalNanos = Constants.TIME_SECOND_TO_NANO / cachedRefreshRate
                    val droppedFrames = Math.max(
                        0f,
                        (totalDuration - frameIntervalNanos) / frameIntervalNanos,
                    )

                    droppedSum += droppedFrames

                    val dfl = dropFrameListener
                    if (dfl != null && droppedFrames >= cachedThreshold) {
                        dfl.onFrameMetricsAvailable(
                            ProcessUILifecycleOwner.visibleScene,
                            frameMetricsCopy,
                            droppedFrames,
                            cachedRefreshRate,
                        )
                    }
                    synchronized(listeners) {
                        for (observer in listeners) {
                            observer.onFrameMetricsAvailable(
                                ProcessUILifecycleOwner.visibleScene,
                                frameMetricsCopy,
                                droppedFrames,
                                cachedRefreshRate,
                            )
                        }
                    }
                }
            }
        }

        this.frameListenerMap[activity.hashCode()] = onFrameMetricsAvailableListener
        activity.window.addOnFrameMetricsAvailableListener(
            onFrameMetricsAvailableListener,
            TraceHarborHandlerThread.getDefaultHandler(),
        )
        TraceHarborLog.i(TAG, "onActivityResumed addOnFrameMetricsAvailableListener")
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onActivityPaused(activity: Activity) {
        sceneFrameCollector?.resetAllAndCallBack()
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onActivityDestroyed(activity: Activity) {
        try {
            val removed = frameListenerMap.remove(activity.hashCode())
            if (removed != null) {
                activity.window.removeOnFrameMetricsAvailableListener(removed)
            }
        } catch (t: Throwable) {
            TraceHarborLog.e(
                TAG,
                "removeOnFrameMetricsAvailableListener error : " + t.message,
            )
        }
    }

    /**
     * Default emitter — turns each per-scene aggregate into a
     * `Constants.Type.FPS` Issue. Static nested (no outer-tracer
     * reference needed).
     */
    @RequiresApi(Build.VERSION_CODES.N)
    internal class AllSceneFrameListener : ISceneFrameListener {
        override fun getIntervalMs(): Int = Constants.DEFAULT_FPS_TIME_SLICE_ALIVE_MS

        override fun getName(): String? = null

        override fun skipFirstFrame(): Boolean = false

        override fun getThreshold(): Int = 0

        override fun onFrameMetricsAvailable(
            sceneName: String,
            avgDurations: LongArray,
            dropLevel: IntArray,
            dropSum: IntArray,
            avgDroppedFrame: Float,
            avgRefreshRate: Float,
            avgFps: Float,
        ) {
            TraceHarborLog.i(LISTENER_TAG, "[report] FPS:%s %s", avgFps, toString())
            try {
                val plugin: TracePlugin? =
                    TraceHarbor.with().getPluginByClass(TracePlugin::class.java)
                if (null == plugin) {
                    return
                }
                val dropLevelObject = JSONObject()
                val dropSumObject = JSONObject()
                for (dropStatus in DropStatus.values()) {
                    dropLevelObject.put(dropStatus.name, dropLevel[dropStatus.ordinal])
                    dropSumObject.put(dropStatus.name, dropSum[dropStatus.ordinal])
                }

                val resultObject = JSONObject()
                val app = plugin.application ?: return
                DeviceUtil.getDeviceInfo(resultObject, app)

                resultObject.put(SharePluginInfo.ISSUE_SCENE, sceneName)
                resultObject.put(SharePluginInfo.ISSUE_DROP_LEVEL, dropLevelObject)
                resultObject.put(SharePluginInfo.ISSUE_DROP_SUM, dropSumObject)
                resultObject.put(SharePluginInfo.ISSUE_FPS, avgFps)

                for (frameDuration in FrameDuration.values()) {
                    resultObject.put(
                        frameDuration.name,
                        avgDurations[frameDuration.ordinal],
                    )
                    if (frameDuration == FrameDuration.TOTAL_DURATION) {
                        break
                    }
                }
                if (sdkInt >= Build.VERSION_CODES.S) {
                    resultObject.put(
                        "GPU_DURATION",
                        avgDurations[FrameDuration.GPU_DURATION.ordinal],
                    )
                }
                resultObject.put("DROP_COUNT", Math.round(avgDroppedFrame))
                resultObject.put("REFRESH_RATE", avgRefreshRate.toInt())

                val issue = Issue()
                issue.tag = SharePluginInfo.TAG_PLUGIN_FPS
                issue.content = resultObject
                plugin.onDetectIssue(issue)
            } catch (e: JSONException) {
                TraceHarborLog.e(LISTENER_TAG, "json error", e)
            }
        }

        companion object {
            private const val LISTENER_TAG = "AllSceneFrameListener"
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.FrameTracer"

        private const val HALF_MAX: Long = Long.MAX_VALUE ushr 1

        @JvmField val sdkInt: Int = Build.VERSION.SDK_INT

        @JvmField var defaultRefreshRate: Float = 60f

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.N)
        fun metricsToString(frameMetrics: FrameMetrics): String {
            val sb = StringBuilder()

            sb.append("{unknown_delay_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION))
            sb.append("; input_handling_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION))
            sb.append("; animation_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION))
            sb.append("; layout_measure_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
            sb.append("; draw_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.DRAW_DURATION))
            sb.append("; sync_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.SYNC_DURATION))
            sb.append("; command_issue_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION))
            sb.append("; swap_buffers_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))
            sb.append("; total_duration=")
                .append(frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION))
            sb.append("; first_draw_frame=")
                .append(frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME))
            if (sdkInt >= Build.VERSION_CODES.S) {
                sb.append("; gpu_duration=")
                    .append(frameMetrics.getMetric(FrameMetrics.GPU_DURATION))
            }
            sb.append("}")

            return sb.toString()
        }
    }
}
