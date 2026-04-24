package com.kernelflux.traceharbor.trace.hacker

import android.os.Build
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.kernelflux.traceharbor.trace.config.IssueFixConfig
import com.kernelflux.traceharbor.trace.core.AppMethodBeat
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.HashSet
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Reflective hook into `ActivityThread.mH` (the system Handler) to time
 * application/launcher cold start phases.
 *
 * The original Java was a pure utility — `class ActivityThreadHacker` with
 * static fields/methods. We model it as a `class private constructor()` +
 * `companion object`, exposing every public API via `@JvmStatic` /
 * `@JvmField` so existing callers (`AppMethodBeat`, `StartupTracer`) keep
 * `ActivityThreadHacker.hackSysHandlerCallback()` and field-access patterns
 * (e.g. `ActivityThreadHacker.sApplicationCreateScene`) byte-for-byte
 * unchanged.
 *
 * `sLastLaunchActivityMethodIndex` and `sApplicationCreateBeginMethodIndex`
 * are reassignable from `StartupTracer` (see `release()` calls) so they
 * remain `var` not `val`.
 */
class ActivityThreadHacker private constructor() {

    fun interface IApplicationCreateListener {
        fun onApplicationCreateEnd()
    }

    companion object {
        private const val TAG = "TraceHarbor.ActivityThreadHacker"

        private var sApplicationCreateBeginTime: Long = 0L
        private var sApplicationCreateEndTime: Long = 0L

        @JvmField
        var sLastLaunchActivityMethodIndex: AppMethodBeat.IndexRecord =
            AppMethodBeat.IndexRecord()

        @JvmField
        var sApplicationCreateBeginMethodIndex: AppMethodBeat.IndexRecord =
            AppMethodBeat.IndexRecord()

        @JvmField
        var sApplicationCreateScene: Int = Int.MIN_VALUE

        private val listeners: HashSet<IApplicationCreateListener> = HashSet()
        private var sIsCreatedByLaunchActivity: Boolean = false

        @JvmStatic
        fun addListener(listener: IApplicationCreateListener) {
            synchronized(listeners) {
                listeners.add(listener)
            }
        }

        @JvmStatic
        fun removeListener(listener: IApplicationCreateListener) {
            synchronized(listeners) {
                listeners.remove(listener)
            }
        }

        @JvmStatic
        fun hackSysHandlerCallback() {
            try {
                sApplicationCreateBeginTime = SystemClock.uptimeMillis()
                sApplicationCreateBeginMethodIndex =
                    AppMethodBeat.getInstance().maskIndex("ApplicationCreateBeginMethodIndex")
                val forName = Class.forName("android.app.ActivityThread")
                val field = forName.getDeclaredField("sCurrentActivityThread")
                field.isAccessible = true
                val activityThreadValue = field.get(forName)
                val mH = forName.getDeclaredField("mH")
                mH.isAccessible = true
                val handler = mH.get(activityThreadValue)
                val handlerClass: Class<*>? = handler.javaClass.superclass
                if (handlerClass != null) {
                    val callbackField = handlerClass.getDeclaredField("mCallback")
                    callbackField.isAccessible = true
                    val originalCallback = callbackField.get(handler) as Handler.Callback?
                    val callback = HackCallback(originalCallback)
                    callbackField.set(handler, callback)
                }

                TraceHarborLog.i(
                    TAG,
                    "hook system handler completed. start:%s SDK_INT:%s",
                    sApplicationCreateBeginTime,
                    Build.VERSION.SDK_INT,
                )
            } catch (e: Exception) {
                TraceHarborLog.e(TAG, "hook system handler err! %s", e.cause.toString())
            }
        }

        @JvmStatic
        fun getApplicationCost(): Long =
            sApplicationCreateEndTime - sApplicationCreateBeginTime

        @JvmStatic
        fun getEggBrokenTime(): Long = sApplicationCreateBeginTime

        @JvmStatic
        fun isCreatedByLaunchActivity(): Boolean = sIsCreatedByLaunchActivity

        // Internal setters used only by the inner HackCallback (which lives
        // in the same companion-scope and so can mutate these freely).
    }

    private class HackCallback(
        private val mOriginalCallback: Handler.Callback?,
    ) : Handler.Callback {

        private var method: java.lang.reflect.Method? = null

        override fun handleMessage(msg: Message): Boolean {
            if (IssueFixConfig.getsInstance().isEnableFixSpApply) {
                if (Build.VERSION.SDK_INT in 21..25) {
                    if (msg.what == SERIVCE_ARGS || msg.what == STOP_SERVICE ||
                        msg.what == STOP_ACTIVITY_SHOW || msg.what == STOP_ACTIVITY_HIDE ||
                        msg.what == SLEEPING
                    ) {
                        TraceHarborLog.i(TAG, "Fix SP ANR is enabled")
                        fix()
                    }
                }
            }

            if (!AppMethodBeat.isRealTrace()) {
                return mOriginalCallback != null && mOriginalCallback.handleMessage(msg)
            }

            val isLaunchActivity = isLaunchActivity(msg)

            if (hasPrint > 0) {
                TraceHarborLog.i(
                    TAG,
                    "[handleMessage] msg.what:%s begin:%s isLaunchActivity:%s SDK_INT=%s",
                    msg.what,
                    SystemClock.uptimeMillis(),
                    isLaunchActivity,
                    Build.VERSION.SDK_INT,
                )
                hasPrint--
            }

            if (!isCreated) {
                if (isLaunchActivity || msg.what == CREATE_SERVICE || msg.what == RECEIVER) {
                    sApplicationCreateEndTime = SystemClock.uptimeMillis()
                    sApplicationCreateScene = msg.what
                    isCreated = true
                    sIsCreatedByLaunchActivity = isLaunchActivity
                    TraceHarborLog.i(
                        TAG,
                        "application create end, sApplicationCreateScene:%d, isLaunchActivity:%s",
                        msg.what,
                        isLaunchActivity,
                    )
                    synchronized(listeners) {
                        for (listener in listeners) {
                            listener.onApplicationCreateEnd()
                        }
                    }
                }
            }
            return mOriginalCallback != null && mOriginalCallback.handleMessage(msg)
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private fun fix() {
            try {
                val cls = Class.forName("android.app.QueuedWork")
                val field = cls.getDeclaredField("sPendingWorkFinishers")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val runnables = field.get(null) as ConcurrentLinkedQueue<Runnable>
                runnables.clear()
                TraceHarborLog.i(TAG, "Fix SP ANR sPendingWorkFinishers.clear successful")
            } catch (e: ClassNotFoundException) {
                TraceHarborLog.e(TAG, "Fix SP ANR ClassNotFoundException = " + e.message)
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                TraceHarborLog.e(TAG, "Fix SP ANR IllegalAccessException =" + e.message)
                e.printStackTrace()
            } catch (e: NoSuchFieldException) {
                TraceHarborLog.e(TAG, "Fix SP ANR NoSuchFieldException = " + e.message)
                e.printStackTrace()
            } catch (e: Exception) {
                TraceHarborLog.e(TAG, "Fix SP ANR Exception = " + e.message)
                e.printStackTrace()
            }
        }

        private fun isLaunchActivity(msg: Message): Boolean {
            return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                if (msg.what == EXECUTE_TRANSACTION && msg.obj != null) {
                    try {
                        if (method == null) {
                            val clazz = Class.forName("android.app.servertransaction.ClientTransaction")
                            method = clazz.getDeclaredMethod("getCallbacks").apply {
                                isAccessible = true
                            }
                        }
                        val list = method?.invoke(msg.obj) as List<*>?
                        if (list != null && list.isNotEmpty()) {
                            return list[0]!!.javaClass.name.endsWith(".LaunchActivityItem")
                        }
                    } catch (e: Exception) {
                        TraceHarborLog.e(TAG, "[isLaunchActivity] %s", e)
                    }
                }
                msg.what == LAUNCH_ACTIVITY
            } else {
                msg.what == LAUNCH_ACTIVITY || msg.what == RELAUNCH_ACTIVITY
            }
        }

        companion object {
            private const val LAUNCH_ACTIVITY = 100
            private const val CREATE_SERVICE = 114
            private const val RELAUNCH_ACTIVITY = 126
            private const val RECEIVER = 113
            private const val EXECUTE_TRANSACTION = 159 // for Android 9.0
            private const val SERIVCE_ARGS = 115
            private const val STOP_SERVICE = 116
            private const val STOP_ACTIVITY_SHOW = 103
            private const val STOP_ACTIVITY_HIDE = 104
            private const val SLEEPING = 137

            // Original Java had these as `static` fields on HackCallback
            // (i.e. once-per-process, not once-per-callback) to avoid
            // re-firing the listener when the hook is re-installed. Kept
            // identical here.
            private var isCreated = false
            private var hasPrint = Int.MAX_VALUE
        }
    }
}
