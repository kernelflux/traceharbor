package com.kernelflux.traceharbor.trace.tracer

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.MessageQueue
import com.kernelflux.traceharbor.AppActiveTraceHarborDelegate
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.report.Issue
import com.kernelflux.traceharbor.trace.TracePlugin
import com.kernelflux.traceharbor.trace.config.SharePluginInfo
import com.kernelflux.traceharbor.trace.config.TraceConfig
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.util.AppForegroundUtil
import com.kernelflux.traceharbor.trace.util.Utils
import com.kernelflux.traceharbor.util.DeviceUtil
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.json.JSONObject
import java.util.ArrayList
import java.util.HashMap

/**
 * Detects long-running `MessageQueue.IdleHandler.queueIdle()` callbacks
 * on the main looper.
 *
 * Implementation overview (preserved from the Java original):
 *  1. Reflectively swap `MessageQueue.mIdleHandlers` from the framework
 *     `ArrayList<IdleHandler>` into a [MyArrayList] subclass that wraps
 *     every freshly-added `IdleHandler` with a [MyIdleHandler] proxy.
 *  2. Each `MyIdleHandler.queueIdle()` invocation posts a watchdog
 *     `Runnable` to a background `HandlerThread`. If the wrapped
 *     `queueIdle` returns within `traceConfig.idleHandlerLagThreshold`
 *     ms the watchdog is cancelled; otherwise the watchdog reports a
 *     `LAG_IDLE_HANDLER` issue with the main-thread Java stack.
 *
 * Constructor and overrides keep their byte-for-byte Java surface so
 * `TracePlugin.java` continues to instantiate via
 * `new IdleHandlerLagTracer(traceConfig)`.
 */
class IdleHandlerLagTracer(config: TraceConfig) : Tracer() {

    init {
        traceConfig = config
    }

    override fun onAlive() {
        super.onAlive()
        traceConfig?.let {
            if (it.isIdleHandlerTraceEnable()) {
                idleHandlerLagHandlerThread = HandlerThread("IdleHandlerLagThread")
                idleHandlerLagRunnable = IdleHandlerLagRunable()
                detectIdleHandler()
            }
        }
    }

    override fun onDead() {
        super.onDead()
        traceConfig?.let {
            if (it.isIdleHandlerTraceEnable()) {
                idleHandlerLagHandler?.removeCallbacksAndMessages(null)
            }
        }
    }

    internal class IdleHandlerLagRunable : Runnable {
        override fun run() {
            try {
                val plugin = TraceHarbor.with()
                    .getPluginByClass(TracePlugin::class.java) ?: return

                val stackTrace = Utils.getMainThreadJavaStackTrace()
                val currentForeground = AppForegroundUtil.isInterestingToUser()
                val scene = AppActiveTraceHarborDelegate.INSTANCE.getVisibleScene()

                val jsonObject = JSONObject()
                DeviceUtil.getDeviceInfo(jsonObject, TraceHarbor.with().getApplication())
                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.LAG_IDLE_HANDLER)
                jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene)
                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, stackTrace)
                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, currentForeground)

                val issue = Issue()
                issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
                issue.content = jsonObject
                plugin.onDetectIssue(issue)
                TraceHarborLog.e(
                    TAG,
                    "happens idle handler Lag : %s ",
                    jsonObject.toString(),
                )
            } catch (t: Throwable) {
                TraceHarborLog.e(TAG, "TraceHarbor error, error = ${t.message}")
            }
        }
    }

    /**
     * Wraps a single `MessageQueue.IdleHandler`. The watchdog runnable
     * is posted *before* the delegate runs and removed *after* it
     * returns — if it doesn't return within the configured threshold,
     * the watchdog fires on the IdleHandlerLagThread and emits an
     * issue.
     */
    internal class MyIdleHandler(
        @JvmField val idleHandler: MessageQueue.IdleHandler,
    ) : MessageQueue.IdleHandler {
        override fun queueIdle(): Boolean {
            val cfg = traceConfig
            val handler = idleHandlerLagHandler
            val runnable = idleHandlerLagRunnable
            if (cfg != null && handler != null && runnable != null) {
                handler.postDelayed(runnable, cfg.idleHandlerLagThreshold.toLong())
                val ret = idleHandler.queueIdle()
                handler.removeCallbacks(runnable)
                return ret
            }
            return idleHandler.queueIdle()
        }
    }

    /**
     * Replacement `ArrayList` reflectively installed into
     * `MessageQueue.mIdleHandlers`. Wraps every freshly-added
     * `IdleHandler` with a [MyIdleHandler] proxy and remembers the
     * mapping so `removeIdleHandler` (which calls `remove(handler)`)
     * still finds the right wrapper.
     */
    internal class MyArrayList : ArrayList<MessageQueue.IdleHandler>() {
        private val map: MutableMap<MessageQueue.IdleHandler, MyIdleHandler> = HashMap()

        override fun add(element: MessageQueue.IdleHandler): Boolean {
            val wrapper = MyIdleHandler(element)
            map[element] = wrapper
            return super.add(wrapper)
        }

        override fun remove(element: MessageQueue.IdleHandler): Boolean {
            return if (element is MyIdleHandler) {
                val original = element.idleHandler
                map.remove(original)
                super.remove(element)
            } else {
                val wrapper = map.remove(element)
                if (wrapper != null) {
                    super.remove(wrapper)
                } else {
                    super.remove(element)
                }
            }
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.IdleHandlerLagTracer"

        @JvmStatic
        private var traceConfig: TraceConfig? = null

        @JvmStatic
        private var idleHandlerLagHandlerThread: HandlerThread? = null

        @JvmStatic
        private var idleHandlerLagHandler: Handler? = null

        @JvmStatic
        private var idleHandlerLagRunnable: Runnable? = null

        private fun detectIdleHandler() {
            try {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
                    return
                }
                val mainQueue = Looper.getMainLooper().queue
                val field = MessageQueue::class.java.getDeclaredField("mIdleHandlers")
                field.isAccessible = true
                val myIdleHandlerArrayList = MyArrayList()
                field.set(mainQueue, myIdleHandlerArrayList)
                idleHandlerLagHandlerThread?.start()
                idleHandlerLagHandler = Handler(idleHandlerLagHandlerThread!!.looper)
            } catch (t: Throwable) {
                TraceHarborLog.e(TAG, "reflect idle handler error = ${t.message}")
            }
        }
    }
}
