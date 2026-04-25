package com.kernelflux.traceharbor.iocanary.config

import com.kernelflux.traceharbor.dynamicconfig.IDynamicConfig

class IOConfig private constructor(private val mDynamicConfig: IDynamicConfig) {

    fun isDetectFileIOInMainThread(): Boolean =
        mDynamicConfig.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_io_file_io_main_thread_enable.name,
            DEFAULT_DETECT_MAIN_THREAD_FILE_IO,
        )

    fun isDetectFileIORepeatReadSameFile(): Boolean =
        mDynamicConfig.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_io_repeated_read_enable.name,
            DEFAULT_DETECT_REPEAT_READ_SAME_FILE,
        )

    fun isDetectFileIOBufferTooSmall(): Boolean =
        mDynamicConfig.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_io_small_buffer_enable.name,
            DEFAULT_DETECT_SMALL_BUFFER,
        )

    fun isDetectIOClosableLeak(): Boolean =
        mDynamicConfig.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_io_closeable_leak_enable.name,
            DEFAULT_DETECT_CLOSABLE_LEAK,
        )

    fun getFileMainThreadTriggerThreshold(): Int =
        mDynamicConfig.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_io_main_thread_enable_threshold.name,
            DEFAULT_FILE_MAIN_THREAD_TRIGGER_THRESHOLD,
        )

    fun getFileBufferSmallThreshold(): Int =
        mDynamicConfig.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_io_small_buffer_threshold.name,
            DEFAULT_FILE_BUFFER_SMALL_THRESHOLD,
        )

    fun getFilBufferSmallOpTimes(): Int =
        mDynamicConfig.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_io_small_buffer_operator_times.name,
            DEFAULT_FILE_BUFFER_SMALL_OP_TIMES,
        )

    fun getFileRepeatReadThreshold(): Int =
        mDynamicConfig.get(
            IDynamicConfig.ExptEnum.clicfg_traceharbor_io_repeated_read_threshold.name,
            DEFAULT_FILE_REPEAT_READ_TIMES_THRESHOLD,
        )

    override fun toString(): String =
        String.format(
            "[IOCanary.IOConfig], main_thread:%b, small_buffer:%b, repeat_read:%b, closeable_leak:%b",
            isDetectFileIOInMainThread(),
            isDetectFileIOBufferTooSmall(),
            isDetectFileIORepeatReadSameFile(),
            isDetectIOClosableLeak(),
        )

    class Builder {
        private var mDynamicConfig: IDynamicConfig? = null

        fun dynamicConfig(dynamicConfig: IDynamicConfig): Builder {
            this.mDynamicConfig = dynamicConfig
            return this
        }

        fun build(): IOConfig = IOConfig(mDynamicConfig!!)
    }

    companion object {
        private const val DEFAULT_FILE_MAIN_THREAD_TRIGGER_THRESHOLD = 500
        private const val DEFAULT_FILE_BUFFER_SMALL_THRESHOLD = 4096
        private const val DEFAULT_FILE_BUFFER_SMALL_OP_TIMES = 20
        private const val DEFAULT_FILE_REPEAT_READ_TIMES_THRESHOLD = 5

        private const val DEFAULT_DETECT_MAIN_THREAD_FILE_IO = true
        private const val DEFAULT_DETECT_SMALL_BUFFER = true
        private const val DEFAULT_DETECT_REPEAT_READ_SAME_FILE = true
        private const val DEFAULT_DETECT_CLOSABLE_LEAK = true
    }
}
