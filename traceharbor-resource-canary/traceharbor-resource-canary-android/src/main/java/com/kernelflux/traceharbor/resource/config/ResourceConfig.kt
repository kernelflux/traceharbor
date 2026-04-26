package com.kernelflux.traceharbor.resource.config

import com.kernelflux.traceharbor.dynamicconfig.IDynamicConfig
import java.util.concurrent.TimeUnit

class ResourceConfig private constructor(
    private val mDynamicConfig: IDynamicConfig?,
    private val mDumpHprofMode: DumpMode,
    private val mDetectDebugger: Boolean,
    private val mTargetActivity: String?,
    private val mManufacture: String?,
    private val enableManualDumpNotification: Boolean,
) {
    enum class DumpMode {
        NO_DUMP,
        AUTO_DUMP,
        MANUAL_DUMP,
        SILENCE_ANALYSE,
        FORK_DUMP,
        FORK_ANALYSE,
        LAZY_FORK_ANALYZE,
    }

    fun getScanIntervalMillis(): Long {
        return mDynamicConfig!!.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_resource_detect_interval_millis.name,
            DEFAULT_DETECT_INTERVAL_MILLIS,
        )
    }

    fun getBgScanIntervalMillis(): Long {
        return mDynamicConfig!!.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_resource_detect_interval_millis_bg.name,
            DEFAULT_DETECT_INTERVAL_MILLIS_BG,
        )
    }

    fun getMaxRedetectTimes(): Int {
        return mDynamicConfig!!.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_resource_max_detect_times.name,
            DEFAULT_MAX_REDETECT_TIMES,
        )
    }

    fun getDumpHprofMode(): DumpMode = mDumpHprofMode

    fun getTargetActivity(): String? = mTargetActivity

    fun getDetectDebugger(): Boolean = mDetectDebugger

    fun getManufacture(): String? = mManufacture

    fun isManualDumpNotificationEnabled(): Boolean = enableManualDumpNotification

    class Builder {
        private var mDefaultDumpHprofMode: DumpMode = DEFAULT_DUMP_HPROF_MODE
        private var dynamicConfig: IDynamicConfig? = null
        private var mTargetActivity: String? = null
        private var enableManualDumpNotification: Boolean = true
        private var mDetectDebugger: Boolean = false
        private var mManufacture: String? = null

        fun dynamicConfig(dynamicConfig: IDynamicConfig): Builder {
            this.dynamicConfig = dynamicConfig
            return this
        }

        fun setAutoDumpHprofMode(mode: DumpMode): Builder {
            mDefaultDumpHprofMode = mode
            return this
        }

        fun setDetectDebuger(enabled: Boolean): Builder {
            mDetectDebugger = true
            return this
        }

        fun enableManualDumpNotification(enable: Boolean): Builder {
            enableManualDumpNotification = enable
            return this
        }

        fun setManualDumpTargetActivity(targetActivity: String?): Builder {
            mTargetActivity = targetActivity
            return this
        }

        fun setManufacture(manufacture: String?): Builder {
            mManufacture = manufacture
            return this
        }

        fun build(): ResourceConfig {
            return ResourceConfig(
                dynamicConfig,
                mDefaultDumpHprofMode,
                mDetectDebugger,
                mTargetActivity,
                mManufacture,
                enableManualDumpNotification,
            )
        }
    }

    companion object {
        const val TAG: String = "TraceHarbor.ResourceConfig"
        const val FORK_DUMP_SUPPORTED_API_GUARD: Int = 31
        private val DEFAULT_DETECT_INTERVAL_MILLIS: Long = TimeUnit.MINUTES.toMillis(1)
        private val DEFAULT_DETECT_INTERVAL_MILLIS_BG: Long = TimeUnit.MINUTES.toMillis(20)
        private const val DEFAULT_MAX_REDETECT_TIMES: Int = 10
        private val DEFAULT_DUMP_HPROF_MODE: DumpMode = DumpMode.MANUAL_DUMP
    }
}

