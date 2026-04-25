package com.kernelflux.traceharbor.trace.core

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.MessageQueue
import android.os.SystemClock
import android.util.Log
import android.util.Printer
import androidx.annotation.CallSuper
import com.kernelflux.traceharbor.trace.listeners.ILooperListener
import com.kernelflux.traceharbor.util.ReflectUtils
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Per-Looper message-dispatch tracker. Hooks into [Looper.setMessageLogging]
 * with a chained [LooperPrinter] that fires `onDispatchBegin` / `onDispatchEnd`
 * to all registered listeners (legacy [LooperDispatchListener] **and** the
 * newer [ILooperListener]).
 *
 * Singleton-per-Looper: instances are interned in `sLooperMonitorMap`; the
 * shared main-looper monitor is `sMainMonitor`. Implements
 * [MessageQueue.IdleHandler] so it can re-arm its printer hook every minute
 * (in case an unrelated framework call replaces the message-logging printer).
 *
 * History recording (last [HISTORY_QUEUE_MAX_SIZE] dispatches) and dense
 * tracing (last [RECENT_QUEUE_MAX_SIZE] dispatches with cumulative duration)
 * are gated by the per-instance `historyMsgRecorder` / `denseMsgTracer`
 * flags — both default `false` and are written via the legacy
 * [LooperDispatchListener] constructor.
 */
class LooperMonitor private constructor(looper: Looper) : MessageQueue.IdleHandler {

    private val anrHistoryMQ: Queue<M> = ConcurrentLinkedQueue()
    private val recentMsgQ: Queue<M> = ConcurrentLinkedQueue()

    private var messageStartTime: Long = 0
    private var latestMsgLog: String = ""

    /**
     * Counter of dispatch-begin events while `historyMsgRecorder` is on.
     * Reset to 0 by [cleanRecentMQ]. Public Java getter `getRecentMCount()`
     * preserved via Kotlin's auto-generated accessor.
     */
    var recentMCount: Long = 0
        private set

    /**
     * Cumulative dispatch duration recorded into [recentMsgQ] (only while
     * dense-message tracing is on). Reset by [cleanRecentMQ]. Java getter
     * preserved.
     */
    var recentMDuration: Long = 0
        private set

    private var denseMsgTracer: Boolean = false
    private var historyMsgRecorder: Boolean = false

    @Suppress("DEPRECATION")
    private val oldListeners: HashSet<LooperDispatchListener> = HashSet()
    private val listeners: MutableMap<ILooperListener, DispatchListenerWrapper> = HashMap()

    private var printer: LooperPrinter? = null

    /**
     * Mutable so [onRelease] can null it out. Java callers reading
     * `getLooper()` after release will get null (same as the Java original
     * NPE-on-use behaviour, just made explicit).
     */
    private var looper: Looper? = looper

    private var lastCheckPrinterTime: Long = 0

    init {
        resetPrinter()
        addIdleHandler(looper)
    }

    fun getLooper(): Looper? = looper

    /**
     * Kept for compatibility — UIThreadMonitor and AppMethodBeat both
     * subclass / register via this listener type. Two constructors mirror
     * the Java original (no-arg + 2-arg).
     */
    @Deprecated("Use ILooperListener instead.")
    abstract class LooperDispatchListener {

        @JvmField
        internal var isHasDispatchStart: Boolean = false

        @JvmField
        var historyMsgRecorder: Boolean = false

        @JvmField
        var denseMsgTracer: Boolean = false

        constructor(historyMsgRecorder: Boolean, denseMsgTracer: Boolean) {
            this.historyMsgRecorder = historyMsgRecorder
            this.denseMsgTracer = denseMsgTracer
        }

        constructor()

        open fun isValid(): Boolean = false

        open fun dispatchStart() {}

        @CallSuper
        open fun onDispatchStart(x: String) {
            if (!isHasDispatchStart) {
                isHasDispatchStart = true
                dispatchStart()
            }
        }

        @CallSuper
        open fun onDispatchEnd(x: String) {
            if (isHasDispatchStart) {
                isHasDispatchStart = false
                dispatchEnd()
            }
        }

        open fun dispatchEnd() {}
    }

    private class DispatchListenerWrapper(private val dispatchListener: ILooperListener) {
        private var isHasDispatchStart: Boolean = false
        private var beginNs: Long = 0

        fun isValid(): Boolean = dispatchListener.isValid()

        fun onDispatchBegin(x: String) {
            if (!isHasDispatchStart) {
                isHasDispatchStart = true
                beginNs = System.nanoTime()
                dispatchListener.onDispatchBegin(x)
            }
        }

        fun onDispatchEnd(x: String) {
            if (isHasDispatchStart) {
                isHasDispatchStart = false
                dispatchListener.onDispatchEnd(x, beginNs, System.nanoTime())
            }
        }
    }

    @Deprecated("Use addListener(ILooperListener) instead.")
    @Suppress("DEPRECATION")
    fun getOldListeners(): HashSet<LooperDispatchListener> = oldListeners

    @Deprecated("Use addListener(ILooperListener) instead.")
    @Suppress("DEPRECATION")
    fun addListener(listener: LooperDispatchListener) {
        synchronized(oldListeners) {
            oldListeners.add(listener)
        }
    }

    @Deprecated("Use removeListener(ILooperListener) instead.")
    @Suppress("DEPRECATION")
    fun removeListener(listener: LooperDispatchListener) {
        synchronized(oldListeners) {
            oldListeners.remove(listener)
        }
    }

    fun addListener(listener: ILooperListener) {
        synchronized(listeners) {
            listeners[listener] = DispatchListenerWrapper(listener)
        }
    }

    fun removeListener(listener: ILooperListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    override fun queueIdle(): Boolean {
        if (SystemClock.uptimeMillis() - lastCheckPrinterTime >= CHECK_TIME) {
            resetPrinter()
            lastCheckPrinterTime = SystemClock.uptimeMillis()
        }
        return true
    }

    @Synchronized
    fun onRelease() {
        if (printer != null) {
            synchronized(oldListeners) {
                oldListeners.clear()
            }
            synchronized(listeners) {
                listeners.clear()
            }
            val l = looper
            val p = printer
            TraceHarborLog.v(TAG, "[onRelease] %s, origin printer:%s", l?.thread?.name, p?.origin)
            l?.let { removeIdleHandler(it) }
            l?.setMessageLogging(p?.origin)
            looper = null
            printer = null
        }
    }

    @Synchronized
    private fun resetPrinter() {
        val l = looper ?: return
        var originPrinter: Printer? = null
        try {
            if (!isReflectLoggingError) {
                originPrinter = ReflectUtils.get<Printer>(l.javaClass, "mLogging", l)
                val current = printer
                if (originPrinter === current && current != null) {
                    return
                }
                // Fix issues that printer loaded by different classloader
                if (originPrinter != null && current != null) {
                    if (originPrinter.javaClass.name == current.javaClass.name) {
                        TraceHarborLog.w(
                            TAG,
                            "LooperPrinter might be loaded by different classloader" +
                                ", my = " + current.javaClass.classLoader +
                                ", other = " + originPrinter.javaClass.classLoader,
                        )
                        return
                    }
                }
            }
        } catch (e: Exception) {
            isReflectLoggingError = true
            Log.e(TAG, "[resetPrinter] %s".format(e))
        }

        val current = printer
        if (current != null) {
            TraceHarborLog.w(
                TAG,
                "maybe thread:%s printer[%s] was replace other[%s]!",
                l.thread.name,
                current,
                originPrinter,
            )
        }
        val newPrinter = LooperPrinter(originPrinter)
        printer = newPrinter
        l.setMessageLogging(newPrinter)
        if (originPrinter != null) {
            TraceHarborLog.i(
                TAG,
                "reset printer, originPrinter[%s] in %s",
                originPrinter,
                l.thread.name,
            )
        }
    }

    @Synchronized
    private fun removeIdleHandler(looper: Looper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            looper.queue.removeIdleHandler(this)
        } else {
            try {
                val queue = ReflectUtils.get<MessageQueue>(looper.javaClass, "mQueue", looper)
                queue?.removeIdleHandler(this)
            } catch (e: Exception) {
                Log.e(TAG, "[removeIdleHandler] %s".format(e))
            }
        }
    }

    @Synchronized
    private fun addIdleHandler(looper: Looper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            looper.queue.addIdleHandler(this)
        } else {
            try {
                val queue = ReflectUtils.get<MessageQueue>(looper.javaClass, "mQueue", looper)
                queue?.addIdleHandler(this)
            } catch (e: Exception) {
                Log.e(TAG, "[removeIdleHandler] %s".format(e))
            }
        }
    }

    /**
     * Inner because [println] calls outer [dispatch].
     */
    inner class LooperPrinter(@JvmField var origin: Printer?) : Printer {

        @JvmField var isHasChecked: Boolean = false

        @JvmField var isValid: Boolean = false

        override fun println(x: String) {
            val o = origin
            if (o != null) {
                o.println(x)
                if (o === this) {
                    throw RuntimeException("$TAG origin == this")
                }
            }

            if (!isHasChecked) {
                isValid = x[0] == '>' || x[0] == '<'
                isHasChecked = true
                if (!isValid) {
                    TraceHarborLog.e(TAG, "[println] Printer is inValid! x:%s", x)
                }
            }

            if (isValid) {
                dispatch(x[0] == '>', x)
            }
        }
    }

    private fun recordMsg(log: String, duration: Long) {
        historyMsgHandler.post {
            enqueueHistoryMQ(M(log, duration))
        }

        if (denseMsgTracer) {
            historyMsgHandler.post {
                enqueueRecentMQ(M(log, duration))
            }
        }
    }

    private fun enqueueRecentMQ(m: M) {
        if (recentMsgQ.size == RECENT_QUEUE_MAX_SIZE) {
            recentMsgQ.poll()
        }
        recentMsgQ.offer(m)

        recentMDuration += m.d
    }

    private fun enqueueHistoryMQ(m: M) {
        if (anrHistoryMQ.size == HISTORY_QUEUE_MAX_SIZE) {
            anrHistoryMQ.poll()
        }
        anrHistoryMQ.offer(m)
    }

    fun getHistoryMQ(): Queue<M> {
        enqueueHistoryMQ(M(latestMsgLog, System.currentTimeMillis() - messageStartTime))
        return anrHistoryMQ
    }

    fun getRecentMsgQ(): Queue<M> = recentMsgQ

    fun cleanRecentMQ() {
        recentMsgQ.clear()
        recentMCount = 0
        recentMDuration = 0
    }

    @Suppress("DEPRECATION")
    private fun dispatch(isBegin: Boolean, log: String) {
        if (isBegin) {
            if (historyMsgRecorder) {
                messageStartTime = System.currentTimeMillis()
                latestMsgLog = log
                recentMCount++
            }
            synchronized(oldListeners) {
                for (listener in oldListeners) {
                    if (listener.isValid()) {
                        listener.onDispatchStart(log)
                    }
                }
            }
            synchronized(listeners) {
                for (listener in listeners.values) {
                    if (listener.isValid()) {
                        listener.onDispatchBegin(log)
                    }
                }
            }
        } else {
            if (historyMsgRecorder) {
                recordMsg(log, System.currentTimeMillis() - messageStartTime)
            }
            synchronized(oldListeners) {
                for (listener in oldListeners) {
                    if (listener.isValid()) {
                        listener.onDispatchEnd(log)
                    }
                }
            }
            synchronized(listeners) {
                for (listener in listeners.values) {
                    if (listener.isValid()) {
                        listener.onDispatchEnd(log)
                    }
                }
            }
        }
    }

    /**
     * Lightweight POJO carrying the printer log + measured duration. Public
     * mutable fields preserved to mirror the Java original — accessed by
     * tooling that introspects [getHistoryMQ] / [getRecentMsgQ].
     */
    class M(@JvmField var l: String, @JvmField var d: Long) {
        override fun toString(): String = "{$l -> $d}"
    }

    @Suppress("DEPRECATION")
    companion object {
        private const val TAG = "TraceHarbor.LooperMonitor"

        // Note: declaration order matters — `sMainMonitor` calls `of()`
        // which reads `sLooperMonitorMap`, so the map must be initialised
        // first.
        private val sLooperMonitorMap: MutableMap<Looper, LooperMonitor> = ConcurrentHashMap()

        @JvmStatic
        private val sMainMonitor: LooperMonitor = of(Looper.getMainLooper())

        private val historyMsgHandlerThread: HandlerThread =
            TraceHarborHandlerThread.getNewHandlerThread(
                "historyMsgHandlerThread",
                Thread.NORM_PRIORITY,
            )

        private val historyMsgHandler: Handler = Handler(historyMsgHandlerThread.looper)

        private const val HISTORY_QUEUE_MAX_SIZE = 200
        private const val RECENT_QUEUE_MAX_SIZE = 5000
        private const val CHECK_TIME: Long = 60 * 1000L

        @JvmStatic
        private var isReflectLoggingError: Boolean = false

        @JvmStatic
        fun getMainMonitor(): LooperMonitor = sMainMonitor

        @JvmStatic
        fun of(looper: Looper): LooperMonitor {
            var looperMonitor = sLooperMonitorMap[looper]
            if (looperMonitor == null) {
                looperMonitor = LooperMonitor(looper)
                sLooperMonitorMap[looper] = looperMonitor
            }
            return looperMonitor
        }

        /**
         * Package-private in the Java original — only callable from the
         * same package. Marked `internal` here to keep it module-scoped
         * (closest Kotlin equivalent).
         */
        @JvmStatic
        @Deprecated("Use register(ILooperListener) instead.")
        internal fun register(listener: LooperDispatchListener) {
            sMainMonitor.addListener(listener)
        }

        @JvmStatic
        @Deprecated("Use unregister(ILooperListener) instead.")
        internal fun unregister(listener: LooperDispatchListener) {
            sMainMonitor.removeListener(listener)
        }

        @JvmStatic
        fun register(listener: ILooperListener) {
            sMainMonitor.addListener(listener)
        }

        @JvmStatic
        fun unregister(listener: ILooperListener) {
            sMainMonitor.removeListener(listener)
        }
    }
}
