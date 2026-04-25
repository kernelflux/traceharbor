package com.kernelflux.traceharbor.trace.core

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUILifecycleOwner
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.hacker.ActivityThreadHacker
import com.kernelflux.traceharbor.trace.listeners.IAppMethodBeatListener
import com.kernelflux.traceharbor.trace.listeners.ILooperListener
import com.kernelflux.traceharbor.trace.util.Utils
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * Heart of the trace-canary call-stack tracking. The Gradle plugin
 * bytecode-instruments every Java method body to call
 * `AppMethodBeat.i(int methodId)` on entry and
 * `AppMethodBeat.o(int methodId)` on exit, so the static `i`/`o`
 * signatures and `METHOD_ID_DISPATCH` constant MUST stay byte-for-byte
 * compatible with the existing instrumented bytecode.
 *
 * Buffers per-method enter/exit + relative timestamp into a single
 * 64-bit long via [mergeData]:
 *   - bit 63    : 1 = enter, 0 = exit
 *   - bits 62-43: methodId (20 bits, max [METHOD_ID_MAX] = 0xFFFFF)
 *   - bits 42-0 : relative time (`SystemClock.uptimeMillis - sDiffTime`)
 *
 * Singleton (`getInstance()`); singleton-state lives in the companion
 * object so the bytecode shape matches the Java original.
 *
 * Implements [BeatLifecycle] — `onStart` arms the timer thread and
 * the looper hook; `onStop` halts dispatch but keeps the buffer.
 */
class AppMethodBeat private constructor() : BeatLifecycle {

    fun interface MethodEnterListener {
        fun enter(method: Int, threadId: Long)
    }

    override fun onStart() {
        synchronized(statusLock) {
            if (status < STATUS_STARTED && status >= STATUS_EXPIRED_START) {
                checkStartExpiredRunnable?.let { sHandler.removeCallbacks(it) }
                TraceHarborHandlerThread.getDefaultHandler()
                    .removeCallbacks(realReleaseRunnable)
                if (sBuffer == null) {
                    throw RuntimeException("$TAG sBuffer == null")
                }
                TraceHarborLog.i(TAG, "[onStart] preStatus:%s", status, Utils.getStack())
                status = STATUS_STARTED
            } else {
                TraceHarborLog.w(TAG, "[onStart] current status:%s", status)
            }
        }
    }

    override fun onStop() {
        synchronized(statusLock) {
            if (status == STATUS_STARTED) {
                TraceHarborLog.i(TAG, "[onStop] %s", Utils.getStack())
                status = STATUS_STOPPED
            } else {
                TraceHarborLog.w(TAG, "[onStop] current status:%s", status)
            }
        }
    }

    fun forceStop() {
        synchronized(statusLock) {
            status = STATUS_STOPPED
        }
    }

    override fun isAlive(): Boolean = status >= STATUS_STARTED

    fun addListener(listener: IAppMethodBeatListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: IAppMethodBeatListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /**
     * Mark the current ring-buffer position as the start of a slice the
     * caller plans to copy out later via [copyData]. Insertion-sorted
     * into a singly-linked list keyed by `index` so [checkPileup] can
     * efficiently invalidate records once the buffer wraps over them.
     */
    fun maskIndex(source: String): IndexRecord {
        if (sIndexRecordHead == null) {
            val head = IndexRecord(sIndex - 1)
            head.source = source
            sIndexRecordHead = head
            return head
        } else {
            val indexRecord = IndexRecord(sIndex - 1)
            indexRecord.source = source
            var record: IndexRecord? = sIndexRecordHead
            var last: IndexRecord? = null

            while (record != null) {
                if (indexRecord.index <= record.index) {
                    if (null == last) {
                        val tmp = sIndexRecordHead
                        sIndexRecordHead = indexRecord
                        indexRecord.next = tmp
                    } else {
                        val tmp = last.next
                        last.next = indexRecord
                        indexRecord.next = tmp
                    }
                    return indexRecord
                }
                last = record
                record = record.next
            }
            // `last` is guaranteed non-null because the while loop ran
            // at least once (the outer if's null-check just confirmed
            // sIndexRecordHead != null).
            last!!.next = indexRecord
            return indexRecord
        }
    }

    fun copyData(startRecord: IndexRecord): LongArray =
        copyData(startRecord, IndexRecord(sIndex - 1))

    private fun copyData(startRecord: IndexRecord, endRecord: IndexRecord): LongArray {
        val current = System.currentTimeMillis()
        var data: LongArray = LongArray(0)
        try {
            if (startRecord.isValid && endRecord.isValid) {
                val length: Int
                val start = Math.max(0, startRecord.index)
                val end = Math.max(0, endRecord.index)

                val buffer = sBuffer ?: return data

                if (end > start) {
                    length = end - start + 1
                    data = LongArray(length)
                    System.arraycopy(buffer, start, data, 0, length)
                } else if (end < start) {
                    length = 1 + end + (buffer.size - start)
                    data = LongArray(length)
                    System.arraycopy(buffer, start, data, 0, buffer.size - start)
                    System.arraycopy(buffer, 0, data, buffer.size - start, end + 1)
                }
                return data
            }
            return data
        } catch (t: Throwable) {
            TraceHarborLog.e(TAG, t.toString())
            return data
        } finally {
            TraceHarborLog.i(
                TAG,
                "[copyData] [%s:%s] length:%s cost:%sms",
                Math.max(0, startRecord.index),
                endRecord.index,
                data.size,
                System.currentTimeMillis() - current,
            )
        }
    }

    fun printIndexRecord() {
        val ss = StringBuilder(" \n")
        var record: IndexRecord? = sIndexRecordHead
        while (null != record) {
            ss.append(record).append("\n")
            record = record.next
        }
        TraceHarborLog.i(TAG, "[printIndexRecord] %s", ss.toString())
    }

    /**
     * Public final nested class. Two constructors mirror the Java
     * original. Public mutable `index`, `isValid`, `source` fields
     * preserved with `@JvmField` so existing Java callers keep direct
     * field access; `next` stays private.
     */
    class IndexRecord {
        @JvmField var index: Int = 0
        internal var next: IndexRecord? = null

        @JvmField var isValid: Boolean = true

        @JvmField var source: String? = null

        constructor(index: Int) {
            this.index = index
        }

        constructor() {
            this.isValid = false
        }

        fun release() {
            isValid = false
            var record: IndexRecord? = sIndexRecordHead
            var last: IndexRecord? = null
            while (null != record) {
                if (record === this) {
                    if (null != last) {
                        last.next = record.next
                    } else {
                        sIndexRecordHead = record.next
                    }
                    record.next = null
                    break
                }
                last = record
                record = record.next
            }
        }

        override fun toString(): String =
            "index:$index,\tisValid:$isValid source:$source"
    }

    companion object {
        private const val TAG = "TraceHarbor.AppMethodBeat"

        @JvmField
        var isDev: Boolean = false

        @JvmStatic
        private val sInstance: AppMethodBeat = AppMethodBeat()

        // STATUS_DEFAULT is `Integer.MAX_VALUE` in the Java original.
        // Kept as a regular `const val` (not `@JvmField`) since it's
        // private/internal and only consumed from inside this class.
        internal const val STATUS_DEFAULT: Int = Int.MAX_VALUE
        internal const val STATUS_STARTED: Int = 2
        internal const val STATUS_READY: Int = 1
        internal const val STATUS_STOPPED: Int = -1
        internal const val STATUS_EXPIRED_START: Int = -2
        internal const val STATUS_OUT_RELEASE: Int = -3

        @JvmStatic
        @Volatile
        internal var status: Int = STATUS_DEFAULT

        private val statusLock = Any()

        @JvmField
        var sMethodEnterListener: MethodEnterListener? = null

        @JvmStatic
        internal var sBuffer: LongArray? = LongArray(Constants.BUFFER_SIZE)

        @JvmStatic
        internal var sIndex: Int = 0

        @JvmStatic
        internal var sLastIndex: Int = -1

        @JvmStatic
        private var assertIn: Boolean = false

        @JvmStatic
        @Volatile
        internal var sCurrentDiffTime: Long = SystemClock.uptimeMillis()

        @JvmStatic
        @Volatile
        internal var sDiffTime: Long = sCurrentDiffTime

        @JvmStatic
        private val sMainThreadId: Long = Looper.getMainLooper().thread.id

        @JvmStatic
        private val sTimerUpdateThread: HandlerThread =
            TraceHarborHandlerThread.getNewHandlerThread(
                "traceharbor_time_update_thread",
                Thread.MIN_PRIORITY + 2,
            )

        @JvmStatic
        private val sHandler: Handler = Handler(sTimerUpdateThread.looper)

        internal const val METHOD_ID_MAX: Int = 0xFFFFF

        // Public — accessed from instrumented bytecode as
        // `AppMethodBeat.METHOD_ID_DISPATCH`. `const val` already
        // generates a public static final field.
        const val METHOD_ID_DISPATCH: Int = METHOD_ID_MAX - 1

        @JvmStatic
        private val sFocusActivitySet: MutableSet<String> = HashSet()

        @JvmStatic
        private val listeners: HashSet<IAppMethodBeatListener> = HashSet()

        private val updateTimeLock = Any()

        @JvmStatic
        @Volatile
        internal var isPauseUpdateTime: Boolean = false

        @JvmStatic
        internal var checkStartExpiredRunnable: Runnable? = null

        @JvmStatic
        internal var sIndexRecordHead: IndexRecord? = null

        @JvmStatic
        private val looperMonitorListener: ILooperListener = object : ILooperListener {
            override fun isValid(): Boolean = status >= STATUS_READY
            override fun onDispatchBegin(log: String) { dispatchBegin() }
            override fun onDispatchEnd(log: String, beginNs: Long, endNs: Long) {
                dispatchEnd()
            }
        }

        @JvmStatic
        private val realReleaseRunnable: Runnable = Runnable { realRelease() }

        /**
         * Update-diff-time runnable. Polls `SystemClock.uptimeMillis()`
         * every [Constants.TIME_UPDATE_CYCLE_MS] while the buffer is
         * actively being written to; parks on `updateTimeLock` when
         * the main looper enters its idle section to avoid spinning
         * the CPU between dispatches.
         *
         * `Object.wait()` / `notify()` aren't exposed by Kotlin's
         * `Any`, so cast to `java.lang.Object` first (same trick as
         * Round 15 FrameDecorator).
         */
        @JvmStatic
        private val sUpdateDiffTimeRunnable: Runnable = Runnable {
            try {
                while (true) {
                    while (!isPauseUpdateTime && status > STATUS_STOPPED) {
                        sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime
                        SystemClock.sleep(Constants.TIME_UPDATE_CYCLE_MS.toLong())
                    }
                    synchronized(updateTimeLock) {
                        (updateTimeLock as java.lang.Object).wait()
                    }
                }
            } catch (e: Exception) {
                TraceHarborLog.e(TAG, "" + e.toString())
            }
        }

        init {
            TraceHarborHandlerThread.getDefaultHandler().postDelayed(
                realReleaseRunnable,
                Constants.DEFAULT_RELEASE_BUFFER_DELAY.toLong(),
            )
        }

        @JvmStatic
        fun getInstance(): AppMethodBeat = sInstance

        @JvmStatic
        fun isRealTrace(): Boolean = status >= STATUS_READY

        @JvmStatic
        private fun realRelease() {
            synchronized(statusLock) {
                if (status == STATUS_DEFAULT || status <= STATUS_READY) {
                    TraceHarborLog.i(
                        TAG,
                        "[realRelease] timestamp:%s",
                        System.currentTimeMillis(),
                    )
                    sHandler.removeCallbacksAndMessages(null)
                    LooperMonitor.unregister(looperMonitorListener)
                    sTimerUpdateThread.quit()
                    sBuffer = null
                    status = STATUS_OUT_RELEASE
                }
            }
        }

        @JvmStatic
        private fun realExecute() {
            TraceHarborLog.i(TAG, "[realExecute] timestamp:%s", System.currentTimeMillis())
            sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime

            sHandler.removeCallbacksAndMessages(null)
            sHandler.postDelayed(sUpdateDiffTimeRunnable, Constants.TIME_UPDATE_CYCLE_MS.toLong())
            val expired = Runnable {
                synchronized(statusLock) {
                    TraceHarborLog.i(
                        TAG,
                        "[startExpired] timestamp:%s status:%s",
                        System.currentTimeMillis(),
                        status,
                    )
                    if (status == STATUS_DEFAULT || status == STATUS_READY) {
                        status = STATUS_EXPIRED_START
                    }
                }
            }
            checkStartExpiredRunnable = expired
            sHandler.postDelayed(expired, Constants.DEFAULT_RELEASE_BUFFER_DELAY.toLong())

            ActivityThreadHacker.hackSysHandlerCallback()
            LooperMonitor.register(looperMonitorListener)
        }

        @JvmStatic
        private fun dispatchBegin() {
            sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime
            isPauseUpdateTime = false

            synchronized(updateTimeLock) {
                (updateTimeLock as java.lang.Object).notify()
            }
        }

        @JvmStatic
        private fun dispatchEnd() {
            isPauseUpdateTime = true
        }

        /**
         * Method-enter hook. Called from instrumented bytecode on every
         * method entry — performance-critical; do not introduce
         * allocations on the hot path.
         */
        @JvmStatic
        fun i(methodId: Int) {
            if (status <= STATUS_STOPPED) {
                return
            }
            if (methodId >= METHOD_ID_MAX) {
                return
            }

            if (status == STATUS_DEFAULT) {
                synchronized(statusLock) {
                    if (status == STATUS_DEFAULT) {
                        realExecute()
                        status = STATUS_READY
                    }
                }
            }

            val threadId = Thread.currentThread().id
            sMethodEnterListener?.enter(methodId, threadId)

            if (threadId == sMainThreadId) {
                if (assertIn) {
                    android.util.Log.e(
                        TAG,
                        "ERROR!!! AppMethodBeat.i Recursive calls!!!",
                    )
                    return
                }
                assertIn = true
                if (sIndex < Constants.BUFFER_SIZE) {
                    mergeData(methodId, sIndex, true)
                } else {
                    sIndex = 0
                    mergeData(methodId, sIndex, true)
                }
                ++sIndex
                assertIn = false
            }
        }

        /**
         * Method-exit hook. Called from instrumented bytecode on every
         * method return.
         */
        @JvmStatic
        fun o(methodId: Int) {
            if (status <= STATUS_STOPPED) {
                return
            }
            if (methodId >= METHOD_ID_MAX) {
                return
            }
            if (Thread.currentThread().id == sMainThreadId) {
                if (sIndex < Constants.BUFFER_SIZE) {
                    mergeData(methodId, sIndex, false)
                } else {
                    sIndex = 0
                    mergeData(methodId, sIndex, false)
                }
                ++sIndex
            }
        }

        /**
         * Activity-focus hook. Called from instrumented bytecode at the
         * end of `Activity.onWindowFocusChanged(boolean)`.
         */
        @JvmStatic
        fun at(activity: Activity, isFocus: Boolean) {
            val activityName = activity.javaClass.name
            if (isFocus) {
                if (sFocusActivitySet.add(activityName)) {
                    synchronized(listeners) {
                        for (listener in listeners) {
                            listener.onActivityFocused(activity)
                        }
                    }
                    TraceHarborLog.i(
                        TAG,
                        "[at] visibleScene[%s] has %s focus!",
                        getVisibleScene(),
                        "attach",
                    )
                }
            } else {
                if (sFocusActivitySet.remove(activityName)) {
                    TraceHarborLog.i(
                        TAG,
                        "[at] visibleScene[%s] has %s focus!",
                        getVisibleScene(),
                        "detach",
                    )
                }
            }
        }

        @JvmStatic
        fun getVisibleScene(): String? = ProcessUILifecycleOwner.visibleScene

        /**
         * Pack `methodId`, the entry/exit bit, and the current
         * relative timestamp into a single 64-bit long inside
         * [sBuffer]. Bit-layout:
         *   - bit 63    : isIn flag (1 = enter)
         *   - bits 62-43: methodId (20 bits → max 0xFFFFF)
         *   - bits 42-0 : `sCurrentDiffTime` (43 bits)
         */
        @JvmStatic
        private fun mergeData(methodId: Int, index: Int, isIn: Boolean) {
            if (methodId == METHOD_ID_DISPATCH) {
                sCurrentDiffTime = SystemClock.uptimeMillis() - sDiffTime
            }

            try {
                var trueId = 0L
                if (isIn) {
                    trueId = trueId or (1L shl 63)
                }
                trueId = trueId or (methodId.toLong() shl 43)
                trueId = trueId or (sCurrentDiffTime and 0x7FFFFFFFFFFL)
                val buffer = sBuffer ?: return
                buffer[index] = trueId
                checkPileup(index)
                sLastIndex = index
            } catch (t: Throwable) {
                TraceHarborLog.e(TAG, t.message ?: "")
            }
        }

        @JvmStatic
        private fun checkPileup(index: Int) {
            var indexRecord: IndexRecord? = sIndexRecordHead
            while (indexRecord != null) {
                if (indexRecord.index == index ||
                    (indexRecord.index == -1 && sLastIndex == Constants.BUFFER_SIZE - 1)
                ) {
                    indexRecord.isValid = false
                    TraceHarborLog.w(TAG, "[checkPileup] %s", indexRecord.toString())
                    indexRecord = indexRecord.next
                    sIndexRecordHead = indexRecord
                } else {
                    break
                }
            }
        }

        @JvmStatic
        fun getDiffTime(): Long = sDiffTime
    }
}
