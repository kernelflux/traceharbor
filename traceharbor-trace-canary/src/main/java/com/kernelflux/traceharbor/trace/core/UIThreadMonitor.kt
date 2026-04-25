package com.kernelflux.traceharbor.trace.core

import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import com.kernelflux.traceharbor.AppActiveTraceHarborDelegate
import com.kernelflux.traceharbor.trace.config.TraceConfig
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.listeners.LooperObserver
import com.kernelflux.traceharbor.trace.util.Utils
import com.kernelflux.traceharbor.util.ReflectUtils
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Method
import java.util.Arrays

/**
 * Reflection-driven main-thread frame timer that splits each
 * [Choreographer] vsync window into the three sub-stages
 * (`CALLBACK_INPUT`, `CALLBACK_ANIMATION`, `CALLBACK_TRAVERSAL`) and
 * publishes per-frame and per-message-dispatch timing to the
 * registered [LooperObserver]s.
 *
 * Singleton — `UIThreadMonitor.getMonitor()` returns the static
 * instance; the constructor is private. Implements [BeatLifecycle]
 * (`onStart`/`onStop`/`isAlive`) and [Runnable] (re-injected as the
 * input-frame head callback every frame).
 *
 * Marked `@Deprecated` to mirror the Java original.
 */
@Deprecated("Kept for compatibility with the original wechat-matrix API")
@Suppress("DEPRECATION") // LooperObserver is also @Deprecated by design
class UIThreadMonitor private constructor() : BeatLifecycle, Runnable {

    @Volatile
    private var isAliveField: Boolean = false

    private val dispatchTimeMs: LongArray = LongArray(4)
    private val observers: HashSet<LooperObserver> = HashSet()

    @Volatile
    private var token: Long = 0L
    private var isVsyncFrame: Boolean = false

    private var config: TraceConfig? = null
    private var callbackQueueLock: Any? = null
    private var callbackQueues: Array<Any?>? = null
    private var addTraversalQueue: Method? = null
    private var addInputQueue: Method? = null
    private var addAnimationQueue: Method? = null
    private var choreographer: Choreographer? = null
    private var vsyncReceiver: Any? = null
    private var frameIntervalNanos: Long = 16666666L
    private var queueStatus: IntArray = IntArray(CALLBACK_LAST + 1)
    private val callbackExist: BooleanArray = BooleanArray(CALLBACK_LAST + 1)
    private var queueCost: LongArray = LongArray(CALLBACK_LAST + 1)

    /**
     * Backed by a Kotlin `val` with a backing field. `is` prefix → Kotlin
     * generates an `isInit()` getter, so existing Java callers like
     * `UIThreadMonitor.getMonitor().isInit()` keep working, and Kotlin
     * callers (`TracePlugin.kt`) can write either `.isInit` or
     * `.isInit()`.
     */
    var isInit: Boolean = false
        private set

    fun init(config: TraceConfig) {
        this.config = config

        if (Thread.currentThread() !== Looper.getMainLooper().thread) {
            throw AssertionError("must be init in main thread!")
        }

        val historyMsgRecorder = config.historyMsgRecorder
        val denseMsgTracer = config.denseMsgTracer

        LooperMonitor.register(
            object : LooperMonitor.LooperDispatchListener(historyMsgRecorder, denseMsgTracer) {
                override fun isValid(): Boolean = isAliveField

                override fun dispatchStart() {
                    super.dispatchStart()
                    this@UIThreadMonitor.dispatchBegin()
                }

                override fun dispatchEnd() {
                    super.dispatchEnd()
                    this@UIThreadMonitor.dispatchEnd()
                }
            },
        )
        this.isInit = true
        choreographer = Choreographer.getInstance()
        frameIntervalNanos = ReflectUtils.reflectObject(
            choreographer,
            "mFrameIntervalNanos",
            Constants.DEFAULT_FRAME_DURATION,
        ) ?: Constants.DEFAULT_FRAME_DURATION
        callbackQueueLock = ReflectUtils.reflectObject<Any>(choreographer, "mLock", Any())
        @Suppress("UNCHECKED_CAST")
        callbackQueues =
            ReflectUtils.reflectObject<Array<Any?>>(choreographer, "mCallbackQueues", null)
        callbackQueues?.let { queues ->
            addInputQueue = ReflectUtils.reflectMethod(
                queues[CALLBACK_INPUT],
                ADD_CALLBACK,
                java.lang.Long.TYPE,
                Any::class.java,
                Any::class.java,
            )
            addAnimationQueue = ReflectUtils.reflectMethod(
                queues[CALLBACK_ANIMATION],
                ADD_CALLBACK,
                java.lang.Long.TYPE,
                Any::class.java,
                Any::class.java,
            )
            addTraversalQueue = ReflectUtils.reflectMethod(
                queues[CALLBACK_TRAVERSAL],
                ADD_CALLBACK,
                java.lang.Long.TYPE,
                Any::class.java,
                Any::class.java,
            )
        }
        vsyncReceiver = ReflectUtils.reflectObject<Any>(choreographer, "mDisplayEventReceiver", null)

        TraceHarborLog.i(
            TAG,
            "[UIThreadMonitor] %s %s %s %s %s %s frameIntervalNanos:%s",
            callbackQueueLock == null,
            callbackQueues == null,
            addInputQueue == null,
            addTraversalQueue == null,
            addAnimationQueue == null,
            vsyncReceiver == null,
            frameIntervalNanos,
        )

        if (config.isDevEnv()) {
            addObserver(object : LooperObserver() {
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
                    TraceHarborLog.i(
                        TAG,
                        "focusedActivity[%s] frame cost:%sms isVsyncFrame=%s intendedFrameTimeNs=%s [%s|%s|%s]ns",
                        focusedActivity,
                        (endNs - startNs) / Constants.TIME_MILLIS_TO_NANO,
                        isVsyncFrame,
                        intendedFrameTimeNs,
                        inputCostNs,
                        animationCostNs,
                        traversalCostNs,
                    )
                }
            })
        }
    }

    @Synchronized
    private fun addFrameCallback(type: Int, callback: Runnable, isAddHeader: Boolean) {
        if (callbackExist[type]) {
            TraceHarborLog.w(
                TAG,
                "[addFrameCallback] this type %s callback has exist! isAddHeader:%s",
                type,
                isAddHeader,
            )
            return
        }

        if (!isAliveField && type == CALLBACK_INPUT) {
            TraceHarborLog.w(TAG, "[addFrameCallback] UIThreadMonitor is not alive!")
            return
        }
        try {
            val lock = callbackQueueLock ?: return
            val queues = callbackQueues ?: return
            synchronized(lock) {
                val method: Method? = when (type) {
                    CALLBACK_INPUT -> addInputQueue
                    CALLBACK_ANIMATION -> addAnimationQueue
                    CALLBACK_TRAVERSAL -> addTraversalQueue
                    else -> null
                }
                if (method != null) {
                    method.invoke(
                        queues[type],
                        if (!isAddHeader) SystemClock.uptimeMillis() else -1L,
                        callback,
                        null,
                    )
                    callbackExist[type] = true
                }
            }
        } catch (e: Exception) {
            TraceHarborLog.e(TAG, e.toString())
        }
    }

    fun getFrameIntervalNanos(): Long = frameIntervalNanos

    fun addObserver(observer: LooperObserver) {
        if (!isAliveField) {
            onStart()
        }
        synchronized(observers) {
            observers.add(observer)
        }
    }

    fun removeObserver(observer: LooperObserver) {
        synchronized(observers) {
            observers.remove(observer)
            if (observers.isEmpty()) {
                onStop()
            }
        }
    }

    private fun dispatchBegin() {
        token = System.nanoTime()
        dispatchTimeMs[0] = token
        dispatchTimeMs[2] = SystemClock.currentThreadTimeMillis()
        val cfg = config
        if (cfg?.isAppMethodBeatEnable() == true) {
            AppMethodBeat.i(AppMethodBeat.METHOD_ID_DISPATCH)
        }
        synchronized(observers) {
            for (observer in observers) {
                if (!observer.isDispatchBegin()) {
                    observer.dispatchBegin(dispatchTimeMs[0], dispatchTimeMs[2], token)
                }
            }
        }
        if (cfg?.isDevEnv() == true) {
            TraceHarborLog.d(TAG, "[dispatchBegin#run] inner cost:%sns", System.nanoTime() - token)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun doFrameBegin(token: Long) {
        this.isVsyncFrame = true
    }

    @Suppress("UNUSED_PARAMETER")
    private fun doFrameEnd(token: Long) {
        doQueueEnd(CALLBACK_TRAVERSAL)

        // Java original iterates VALUES of queueStatus (not indices) and
        // writes `queueCost[value] = DO_QUEUE_END_ERROR`. Preserved
        // verbatim — the surface is a tracker for "any non-end status"
        // and the side-effect is intentional.
        for (i in queueStatus) {
            if (i != DO_QUEUE_END) {
                queueCost[i] = DO_QUEUE_END_ERROR.toLong()
                if (config?.isDevEnv == true) {
                    throw RuntimeException(
                        String.format("UIThreadMonitor happens type[%s] != DO_QUEUE_END", i),
                    )
                }
            }
        }
        queueStatus = IntArray(CALLBACK_LAST + 1)

        addFrameCallback(CALLBACK_INPUT, this, true)
    }

    private fun dispatchEnd() {
        var traceBegin: Long = 0
        val cfg = config
        if (cfg?.isDevEnv() == true) {
            traceBegin = System.nanoTime()
        }

        if (cfg?.isFPSEnable() == true) {
            val startNs = token
            var intendedFrameTimeNs = startNs
            if (isVsyncFrame) {
                doFrameEnd(token)
                intendedFrameTimeNs = getIntendedFrameTimeNs(startNs)
            }

            val endNs = System.nanoTime()

            synchronized(observers) {
                for (observer in observers) {
                    if (observer.isDispatchBegin()) {
                        observer.doFrame(
                            AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene().orEmpty(),
                            startNs,
                            endNs,
                            isVsyncFrame,
                            intendedFrameTimeNs,
                            queueCost[CALLBACK_INPUT],
                            queueCost[CALLBACK_ANIMATION],
                            queueCost[CALLBACK_TRAVERSAL],
                        )
                    }
                }
            }
        }

        if (cfg?.isEvilMethodTraceEnable() == true || cfg?.isDevEnv() == true) {
            dispatchTimeMs[3] = SystemClock.currentThreadTimeMillis()
            dispatchTimeMs[1] = System.nanoTime()
        }

        AppMethodBeat.o(AppMethodBeat.METHOD_ID_DISPATCH)

        synchronized(observers) {
            for (observer in observers) {
                if (observer.isDispatchBegin()) {
                    observer.dispatchEnd(
                        dispatchTimeMs[0],
                        dispatchTimeMs[2],
                        dispatchTimeMs[1],
                        dispatchTimeMs[3],
                        token,
                        isVsyncFrame,
                    )
                }
            }
        }

        this.isVsyncFrame = false

        if (cfg?.isDevEnv() == true) {
            TraceHarborLog.d(
                TAG,
                "[dispatchEnd#run] inner cost:%sns",
                System.nanoTime() - traceBegin,
            )
        }
    }

    private fun doQueueBegin(type: Int) {
        queueStatus[type] = DO_QUEUE_BEGIN
        queueCost[type] = System.nanoTime()
    }

    private fun doQueueEnd(type: Int) {
        queueStatus[type] = DO_QUEUE_END
        queueCost[type] = System.nanoTime() - queueCost[type]
        synchronized(this) {
            callbackExist[type] = false
        }
    }

    @Synchronized
    override fun onStart() {
        if (!isInit) {
            TraceHarborLog.e(TAG, "[onStart] is never init.")
            return
        }
        if (!isAliveField) {
            this.isAliveField = true
            synchronized(this) {
                TraceHarborLog.i(
                    TAG,
                    "[onStart] callbackExist:%s %s",
                    Arrays.toString(callbackExist),
                    Utils.getStack(),
                )
                callbackExist.fill(false)
            }
            queueStatus = IntArray(CALLBACK_LAST + 1)
            queueCost = LongArray(CALLBACK_LAST + 1)
            addFrameCallback(CALLBACK_INPUT, this, true)
        }
    }

    override fun run() {
        val start = System.nanoTime()
        try {
            doFrameBegin(token)
            doQueueBegin(CALLBACK_INPUT)

            addFrameCallback(
                CALLBACK_ANIMATION,
                Runnable {
                    doQueueEnd(CALLBACK_INPUT)
                    doQueueBegin(CALLBACK_ANIMATION)
                },
                true,
            )

            addFrameCallback(
                CALLBACK_TRAVERSAL,
                Runnable {
                    doQueueEnd(CALLBACK_ANIMATION)
                    doQueueBegin(CALLBACK_TRAVERSAL)
                },
                true,
            )
        } finally {
            if (config?.isDevEnv() == true) {
                TraceHarborLog.d(
                    TAG,
                    "[UIThreadMonitor#run] inner cost:%sns",
                    System.nanoTime() - start,
                )
            }
        }
    }

    @Synchronized
    override fun onStop() {
        if (!isInit) {
            TraceHarborLog.e(TAG, "[onStart] is never init.")
            return
        }
        if (isAliveField) {
            this.isAliveField = false
            TraceHarborLog.i(
                TAG,
                "[onStop] callbackExist:%s %s",
                Arrays.toString(callbackExist),
                Utils.getStack(),
            )
        }
    }

    override fun isAlive(): Boolean = isAliveField

    private fun getIntendedFrameTimeNs(defaultValue: Long): Long {
        try {
            return ReflectUtils.reflectObject(vsyncReceiver, "mTimestampNanos", defaultValue)
                ?: defaultValue
        } catch (e: Exception) {
            e.printStackTrace()
            TraceHarborLog.e(TAG, e.toString())
        }
        return defaultValue
    }

    fun getQueueCost(type: Int, token: Long): Long {
        if (token != this.token) {
            return -1
        }
        return if (queueStatus[type] == DO_QUEUE_END) queueCost[type] else 0
    }

    private var frameInfo: LongArray? = null

    fun getInputEventCost(): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val obj = ReflectUtils.reflectObject<Any>(choreographer, "mFrameInfo", null)
            if (frameInfo == null) {
                frameInfo = ReflectUtils.reflectObject<LongArray>(obj, "frameInfo", null)
                if (frameInfo == null) {
                    frameInfo = ReflectUtils.reflectObject<LongArray>(
                        obj,
                        "mFrameInfo",
                        LongArray(9),
                    ) ?: LongArray(9)
                }
            }
            val infos = frameInfo ?: return 0
            val start = infos[OLDEST_INPUT_EVENT]
            val end = infos[NEWEST_INPUT_EVENT]
            return end - start
        }
        return 0
    }

    companion object {
        private const val TAG = "TraceHarbor.UIThreadMonitor"
        private const val ADD_CALLBACK = "addCallbackLocked"

        // The time of the oldest input event
        private const val OLDEST_INPUT_EVENT = 3

        // The time of the newest input event
        private const val NEWEST_INPUT_EVENT = 4

        /** Callback type: Input callback. Runs first.  */
        const val CALLBACK_INPUT = 0

        /** Callback type: Animation callback. Runs before traversals. */
        const val CALLBACK_ANIMATION = 1

        /**
         * Callback type: Commit callback. Handles post-draw operations
         * for the frame. Runs after traversal completes.
         */
        const val CALLBACK_TRAVERSAL = 2

        /** Never do queue end code. */
        const val DO_QUEUE_END_ERROR = -100

        private const val CALLBACK_LAST = CALLBACK_TRAVERSAL

        private const val DO_QUEUE_DEFAULT = 0
        private const val DO_QUEUE_BEGIN = 1
        private const val DO_QUEUE_END = 2

        private val sInstance = UIThreadMonitor()

        @JvmStatic
        fun getMonitor(): UIThreadMonitor = sInstance
    }
}
