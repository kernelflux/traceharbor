package com.kernelflux.traceharbor.trace.config

import com.kernelflux.traceharbor.dynamicconfig.IDynamicConfig
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.listeners.IDefaultConfig
import java.util.Arrays
import java.util.HashSet

/**
 * The config about TracePlugin setting.
 *
 * Notes on Java interop:
 * - All `public` mutable fields are exposed via `@JvmField` so existing
 *   Java callers keep direct field access (`config.touchEventLagThreshold`,
 *   `config.anrTraceFilePath`, etc.) byte-for-byte unchanged.
 * - Trivial getters that simply returned the same field
 *   (`getIdleHandlerLagThreshold`, `getTouchEventLagThreshold`,
 *   `getTimeSliceMs`) are intentionally dropped — they were redundant
 *   with the public field and have no callers in this codebase.
 *   `getEvilThresholdMs`, `getColdStartupThresholdMs`, etc. are kept as
 *   real methods because they compute values via [IDynamicConfig].
 * - Boolean accessors required by [IDefaultConfig] follow the original
 *   `is...()` and `get...()` Java naming exactly.
 */
class TraceConfig private constructor() : IDefaultConfig {

    @JvmField var dynamicConfig: IDynamicConfig? = null
    @JvmField var defaultFpsEnable: Boolean = false
    @JvmField var defaultMethodTraceEnable: Boolean = false
    @JvmField var defaultStartupEnable: Boolean = false
    @JvmField var defaultAppMethodBeatEnable: Boolean = true
    @JvmField var defaultAnrEnable: Boolean = false
    @JvmField var defaultIdleHandlerTraceEnable: Boolean = false
    @JvmField var idleHandlerLagThreshold: Int = Constants.DEFAULT_IDLE_HANDLER_LAG
    @JvmField var touchEventLagThreshold: Int = Constants.DEFAULT_TOUCH_EVENT_LAG
    @JvmField var defaultTouchEventTraceEnable: Boolean = false
    @JvmField var isDebug: Boolean = false
    @JvmField var isDevEnv: Boolean = false
    @JvmField var defaultSignalAnrEnable: Boolean = false
    @JvmField var stackStyle: Int = STACK_STYLE_SIMPLE
    @JvmField var splashActivities: String? = null
    @JvmField var splashActivitiesSet: MutableSet<String>? = null
    @JvmField var anrTraceFilePath: String = ""
    @JvmField var printTraceFilePath: String = ""
    @JvmField var isHasActivity: Boolean = true
    @JvmField var historyMsgRecorder: Boolean = false
    @JvmField var denseMsgTracer: Boolean = false

    override fun toString(): String = buildString {
        append(" \n")
        append("# TraceConfig\n")
        append("* isDebug:\t").append(isDebug).append("\n")
        append("* isDevEnv:\t").append(isDevEnv).append("\n")
        append("* isHasActivity:\t").append(isHasActivity).append("\n")
        append("* defaultFpsEnable:\t").append(defaultFpsEnable).append("\n")
        append("* defaultMethodTraceEnable:\t").append(defaultMethodTraceEnable).append("\n")
        append("* defaultStartupEnable:\t").append(defaultStartupEnable).append("\n")
        append("* defaultAnrEnable:\t").append(defaultAnrEnable).append("\n")
        append("* splashActivities:\t").append(splashActivities).append("\n")
        append("* historyMsgRecorder:\t").append(historyMsgRecorder).append("\n")
        append("* denseMsgTracer:\t").append(denseMsgTracer).append("\n")
    }

    override fun isAppMethodBeatEnable(): Boolean =
        defaultMethodTraceEnable || defaultStartupEnable

    override fun isFPSEnable(): Boolean = defaultFpsEnable

    override fun isDebug(): Boolean = isDebug

    override fun isDevEnv(): Boolean = isDevEnv

    override fun getLooperPrinterStackStyle(): Int = stackStyle

    override fun isEvilMethodTraceEnable(): Boolean = defaultMethodTraceEnable

    fun isStartupEnable(): Boolean = defaultStartupEnable

    fun isHasActivity(): Boolean = isHasActivity

    override fun isAnrTraceEnable(): Boolean = defaultAnrEnable

    override fun isIdleHandlerTraceEnable(): Boolean = defaultIdleHandlerTraceEnable

    override fun isTouchEventTraceEnable(): Boolean = defaultTouchEventTraceEnable

    override fun isSignalAnrTraceEnable(): Boolean = defaultSignalAnrEnable

    override fun getAnrTraceFilePath(): String = anrTraceFilePath

    override fun getPrintTraceFilePath(): String = printTraceFilePath

    override fun isHistoryMsgRecorderEnable(): Boolean = historyMsgRecorder

    override fun isDenseMsgTracerEnable(): Boolean = denseMsgTracer

    fun getSplashActivities(): MutableSet<String> {
        val existing = splashActivitiesSet
        if (existing != null) return existing

        val set: MutableSet<String> = HashSet()
        splashActivitiesSet = set

        val dyn = dynamicConfig
        if (dyn == null) {
            val raw = splashActivities ?: return set
            set.addAll(Arrays.asList(*raw.split(";").toTypedArray()))
        } else {
            val dySplash = dyn.get(
                IDynamicConfig.ExptEnum.clicfg_traceharbor_trace_care_scene_set.name,
                splashActivities.orEmpty(),
            )
            // Original Java preserved a `null` over an empty string here —
            // mirror that semantics so callers passing no splash activity
            // still get an empty splashActivitiesSet rather than a "" entry.
            if (dySplash.isNotEmpty()) {
                splashActivities = dySplash
            }
            val raw = splashActivities
            if (raw != null) {
                set.addAll(Arrays.asList(*raw.split(";").toTypedArray()))
            }
        }
        return set
    }

    fun getEvilThresholdMs(): Int =
        dynamicConfig?.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_trace_evil_method_threshold.name,
            Constants.DEFAULT_EVIL_METHOD_THRESHOLD_MS,
        ) ?: Constants.DEFAULT_EVIL_METHOD_THRESHOLD_MS

    fun getTimeSliceMs(): Int =
        dynamicConfig?.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_trace_fps_time_slice.name,
            Constants.DEFAULT_FPS_TIME_SLICE_ALIVE_MS,
        ) ?: Constants.DEFAULT_FPS_TIME_SLICE_ALIVE_MS

    fun getColdStartupThresholdMs(): Int =
        dynamicConfig?.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_trace_app_start_up_threshold.name,
            Constants.DEFAULT_STARTUP_THRESHOLD_MS_COLD,
        ) ?: Constants.DEFAULT_STARTUP_THRESHOLD_MS_COLD

    fun getWarmStartupThresholdMs(): Int =
        dynamicConfig?.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_trace_warm_app_start_up_threshold.name,
            Constants.DEFAULT_STARTUP_THRESHOLD_MS_WARM,
        ) ?: Constants.DEFAULT_STARTUP_THRESHOLD_MS_WARM

    fun getFrozenThreshold(): Int =
        dynamicConfig?.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_fps_dropped_frozen.name,
            Constants.DEFAULT_DROPPED_FROZEN,
        ) ?: Constants.DEFAULT_DROPPED_FROZEN

    fun getHighThreshold(): Int =
        dynamicConfig?.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_fps_dropped_high.name,
            Constants.DEFAULT_DROPPED_HIGH,
        ) ?: Constants.DEFAULT_DROPPED_HIGH

    fun getMiddleThreshold(): Int =
        dynamicConfig?.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_fps_dropped_middle.name,
            Constants.DEFAULT_DROPPED_MIDDLE,
        ) ?: Constants.DEFAULT_DROPPED_MIDDLE

    fun getNormalThreshold(): Int =
        dynamicConfig?.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_fps_dropped_normal.name,
            Constants.DEFAULT_DROPPED_NORMAL,
        ) ?: Constants.DEFAULT_DROPPED_NORMAL

    class Builder {
        private val config: TraceConfig = TraceConfig()

        fun dynamicConfig(dynamicConfig: IDynamicConfig?): Builder = apply {
            config.dynamicConfig = dynamicConfig
        }

        fun enableAppMethodBeat(enable: Boolean): Builder = apply {
            config.defaultAppMethodBeatEnable = enable
        }

        fun enableFPS(enable: Boolean): Builder = apply {
            config.defaultFpsEnable = enable
        }

        fun enableEvilMethodTrace(enable: Boolean): Builder = apply {
            config.defaultMethodTraceEnable = enable
        }

        fun enableAnrTrace(enable: Boolean): Builder = apply {
            config.defaultAnrEnable = enable
        }

        fun looperPrinterStackStyle(stackStyle: Int): Builder = apply {
            config.stackStyle = stackStyle
        }

        fun enableSignalAnrTrace(enable: Boolean): Builder = apply {
            config.defaultSignalAnrEnable = enable
        }

        fun enableStartup(enable: Boolean): Builder = apply {
            config.defaultStartupEnable = enable
        }

        fun isDebug(isDebug: Boolean): Builder = apply {
            config.isDebug = isDebug
        }

        fun isDevEnv(isDevEnv: Boolean): Builder = apply {
            config.isDevEnv = isDevEnv
        }

        fun isHasActivity(isHasActivity: Boolean): Builder = apply {
            config.isHasActivity = isHasActivity
        }

        fun splashActivities(activities: String?): Builder = apply {
            config.splashActivities = activities
        }

        fun anrTracePath(anrTraceFilePath: String): Builder = apply {
            config.anrTraceFilePath = anrTraceFilePath
        }

        fun printTracePath(anrTraceFilePath: String): Builder = apply {
            config.printTraceFilePath = anrTraceFilePath
        }

        fun enableIdleHandlerTrace(enable: Boolean): Builder = apply {
            config.defaultIdleHandlerTraceEnable = enable
        }

        fun setIdleHandlerThreshold(threshold: Int): Builder = apply {
            config.idleHandlerLagThreshold = threshold
        }

        fun enableTouchEventTrace(enable: Boolean): Builder = apply {
            config.defaultTouchEventTraceEnable = enable
        }

        fun setTouchEventThreshold(threshold: Int): Builder = apply {
            config.touchEventLagThreshold = threshold
        }

        fun enableHistoryMsgRecorder(enable: Boolean): Builder = apply {
            config.historyMsgRecorder = enable
        }

        fun enableDenseMsgTracer(enable: Boolean): Builder = apply {
            config.denseMsgTracer = enable
        }

        fun build(): TraceConfig = config
    }

    companion object {
        private const val TAG = "TraceHarbor.TraceConfig"
        const val STACK_STYLE_SIMPLE: Int = 0
        const val STACK_STYLE_WHOLE: Int = 1
        const val STACK_STYLE_RAW: Int = 2
    }
}
