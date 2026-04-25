package com.kernelflux.traceharbor.trace

import android.app.Application
import android.os.Build
import android.os.Looper
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener
import com.kernelflux.traceharbor.trace.config.SharePluginInfo
import com.kernelflux.traceharbor.trace.config.TraceConfig
import com.kernelflux.traceharbor.trace.core.AppMethodBeat
import com.kernelflux.traceharbor.trace.core.UIThreadMonitor
import com.kernelflux.traceharbor.trace.tracer.EvilMethodTracer
import com.kernelflux.traceharbor.trace.tracer.FrameTracer
import com.kernelflux.traceharbor.trace.tracer.IdleHandlerLagTracer
import com.kernelflux.traceharbor.trace.tracer.LooperAnrTracer
import com.kernelflux.traceharbor.trace.tracer.SignalAnrTracer
import com.kernelflux.traceharbor.trace.tracer.StartupTracer
import com.kernelflux.traceharbor.trace.tracer.TouchEventLagTracer
import com.kernelflux.traceharbor.util.TraceHarborHandlerThread
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * The `trace-canary` module's [Plugin] entry-point. Owns the lifecycle
 * of all eight tracers + the two core monitors, gated by the matching
 * `traceConfig.isXxxEnable()` flags.
 *
 * Public Java API kept byte-for-byte:
 *  - `new TracePlugin(traceConfig)` constructor
 *  - 4 lifecycle overrides (`init`, `start`, `stop`, `destroy`,
 *    `onForeground`) + `getTag()`
 *  - 7 accessor methods: `getFrameTracer()`, `getAppMethodBeat()`,
 *    `getLooperAnrTracer()`, `getEvilMethodTracer()`,
 *    `getStartupTracer()`, `getUIThreadMonitor()`, `getTraceConfig()`
 *
 * Kotlin properties auto-generate the corresponding `getXxx()` getters
 * so existing Java callers (FrameDecorator.kt synthetic property
 * access + samples/sample-android/TraceTestsActivity.java) keep
 * working unchanged.
 */
class TracePlugin(private val traceConfig: TraceConfig) : Plugin() {

    private var evilMethodTracer: EvilMethodTracer? = null
    private var startupTracer: StartupTracer? = null
    private var frameTracer: FrameTracer? = null
    private var looperAnrTracer: LooperAnrTracer? = null
    private var signalAnrTracer: SignalAnrTracer? = null
    private var idleHandlerLagTracer: IdleHandlerLagTracer? = null
    private var touchEventLagTracer: TouchEventLagTracer? = null
    private val sdkInt: Int = Build.VERSION.SDK_INT

    override fun init(app: Application, listener: PluginListener) {
        super.init(app, listener)
        TraceHarborLog.i(TAG, "trace plugin init, trace config: %s", traceConfig.toString())
        if (sdkInt < Build.VERSION_CODES.JELLY_BEAN) {
            TraceHarborLog.e(
                TAG,
                "[FrameBeat] API is low Build.VERSION_CODES.JELLY_BEAN(16), TracePlugin is not supported",
            )
            unSupportPlugin()
            return
        }

        looperAnrTracer = LooperAnrTracer(traceConfig)
        frameTracer = FrameTracer(traceConfig)
        evilMethodTracer = EvilMethodTracer(traceConfig)
        startupTracer = StartupTracer(traceConfig)
    }

    override fun start() {
        super.start()
        if (!isSupported) {
            TraceHarborLog.w(TAG, "[start] Plugin is unSupported!")
            return
        }
        TraceHarborLog.w(TAG, "start!")
        val runnable = Runnable {
            if (sdkInt < Build.VERSION_CODES.N && willUiThreadMonitorRunning(traceConfig)) {
                if (!UIThreadMonitor.getMonitor().isInit) {
                    try {
                        UIThreadMonitor.getMonitor().init(traceConfig)
                    } catch (e: RuntimeException) {
                        TraceHarborLog.e(TAG, "[start] RuntimeException:%s", e)
                        return@Runnable
                    }
                }
            }

            if (traceConfig.isAppMethodBeatEnable()) {
                AppMethodBeat.getInstance().onStart()
            } else {
                AppMethodBeat.getInstance().forceStop()
            }

            UIThreadMonitor.getMonitor().onStart()

            if (traceConfig.isAnrTraceEnable()) {
                looperAnrTracer?.onStartTrace()
            }

            if (traceConfig.isIdleHandlerTraceEnable()) {
                idleHandlerLagTracer = IdleHandlerLagTracer(traceConfig).also {
                    it.onStartTrace()
                }
            }

            if (traceConfig.isTouchEventTraceEnable()) {
                touchEventLagTracer = TouchEventLagTracer(traceConfig).also {
                    it.onStartTrace()
                }
            }

            if (traceConfig.isSignalAnrTraceEnable()) {
                if (!SignalAnrTracer.hasInstance) {
                    signalAnrTracer = SignalAnrTracer(traceConfig).also {
                        it.onStartTrace()
                    }
                }
            }

            if (traceConfig.isFPSEnable()) {
                frameTracer?.onStartTrace()
            }

            if (traceConfig.isEvilMethodTraceEnable()) {
                evilMethodTracer?.onStartTrace()
            }

            if (traceConfig.isStartupEnable()) {
                startupTracer?.onStartTrace()
            }
        }

        if (Thread.currentThread() === Looper.getMainLooper().thread) {
            runnable.run()
        } else {
            TraceHarborLog.w(
                TAG,
                "start TracePlugin in Thread[%s] but not in mainThread!",
                Thread.currentThread().id,
            )
            TraceHarborHandlerThread.getDefaultMainHandler().post(runnable)
        }
    }

    override fun stop() {
        super.stop()
        if (!isSupported) {
            TraceHarborLog.w(TAG, "[stop] Plugin is unSupported!")
            return
        }
        TraceHarborLog.w(TAG, "stop!")
        val runnable = Runnable {
            AppMethodBeat.getInstance().onStop()
            UIThreadMonitor.getMonitor().onStop()
            looperAnrTracer?.onCloseTrace()
            frameTracer?.onCloseTrace()
            evilMethodTracer?.onCloseTrace()
            startupTracer?.onCloseTrace()
            signalAnrTracer?.onCloseTrace()
            idleHandlerLagTracer?.onCloseTrace()
        }

        if (Thread.currentThread() === Looper.getMainLooper().thread) {
            runnable.run()
        } else {
            TraceHarborLog.w(
                TAG,
                "stop TracePlugin in Thread[%s] but not in mainThread!",
                Thread.currentThread().id,
            )
            TraceHarborHandlerThread.getDefaultMainHandler().post(runnable)
        }
    }

    override fun onForeground(isForeground: Boolean) {
        super.onForeground(isForeground)
        if (!isSupported) {
            return
        }
        frameTracer?.onForeground(isForeground)
        looperAnrTracer?.onForeground(isForeground)
        evilMethodTracer?.onForeground(isForeground)
        startupTracer?.onForeground(isForeground)
    }

    private fun willUiThreadMonitorRunning(traceConfig: TraceConfig): Boolean =
        traceConfig.isEvilMethodTraceEnable() ||
            traceConfig.isAnrTraceEnable() ||
            traceConfig.isFPSEnable()

    override fun destroy() {
        super.destroy()
    }

    /**
     * Override of [com.kernelflux.traceharbor.plugin.IPlugin.tag] —
     * Java callers continue to invoke `tracePlugin.getTag()` thanks to
     * the auto-generated `getTag()` accessor.
     */
    override val tag: String
        get() = SharePluginInfo.TAG_PLUGIN

    /**
     * Java callers (FrameDecorator.kt synthetic property access etc.)
     * keep `tracePlugin.getFrameTracer()` working — Kotlin auto-emits
     * the accessor.
     */
    fun getFrameTracer(): FrameTracer? = frameTracer

    fun getAppMethodBeat(): AppMethodBeat = AppMethodBeat.getInstance()

    fun getLooperAnrTracer(): LooperAnrTracer? = looperAnrTracer

    fun getEvilMethodTracer(): EvilMethodTracer? = evilMethodTracer

    fun getStartupTracer(): StartupTracer? = startupTracer

    /**
     * Method (not property) — has conditional logic so it can't be a
     * trivial getter.
     */
    fun getUIThreadMonitor(): UIThreadMonitor? =
        if (UIThreadMonitor.getMonitor().isInit) UIThreadMonitor.getMonitor() else null

    fun getTraceConfig(): TraceConfig = traceConfig

    private companion object {
        private const val TAG = "TraceHarbor.TracePlugin"
    }
}
