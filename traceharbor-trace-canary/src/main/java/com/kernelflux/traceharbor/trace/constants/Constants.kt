package com.kernelflux.traceharbor.trace.constants

/**
 * Constants are exposed via the `companion object` so Java callers keep using
 * `Constants.BUFFER_SIZE`, `Constants.Type.NORMAL`, etc. unchanged.
 *
 * The wrapping `class Constants` (instead of an `object`) is preserved so that
 * `Constants.Type` resolves as a nested enum without an `INSTANCE` field
 * intermediary — same access path Java callers had against the original
 * `public class Constants`.
 */
class Constants {

    enum class Type {
        NORMAL,
        ANR,
        STARTUP,
        LAG,
        SIGNAL_ANR,
        SIGNAL_ANR_NATIVE_BACKTRACE,
        LAG_IDLE_HANDLER,
        LAG_TOUCH,
        PRIORITY_MODIFIED,
        TIMERSLACK_MODIFIED,
    }

    companion object {
        const val BUFFER_SIZE: Int = 100 * 10000 // 7.6M
        const val TIME_UPDATE_CYCLE_MS: Int = 5
        const val FILTER_STACK_MAX_COUNT: Int = 60
        const val FILTER_STACK_KEY_ALL_PERCENT: Float = .3F
        const val FILTER_STACK_KEY_PATENT_PERCENT: Float = .8F
        const val DEFAULT_EVIL_METHOD_THRESHOLD_MS: Int = 700
        const val DEFAULT_FPS_TIME_SLICE_ALIVE_MS: Int = 10 * 1000
        const val TIME_MILLIS_TO_NANO: Int = 1000000
        const val TIME_SECOND_TO_NANO: Int = 1000000000
        const val DEFAULT_INPUT_EXPIRED_TIME: Int = 500
        const val DEFAULT_ANR: Int = 5 * 1000
        const val DEFAULT_NORMAL_LAG: Int = 2 * 1000
        const val DEFAULT_IDLE_HANDLER_LAG: Int = 2 * 1000
        const val DEFAULT_TOUCH_EVENT_LAG: Int = 2 * 1000
        const val DEFAULT_ANR_INVALID: Int = 6 * 1000
        const val DEFAULT_FRAME_DURATION: Long = 16666667L

        const val DEFAULT_DROPPED_NORMAL: Int = 3
        const val DEFAULT_DROPPED_MIDDLE: Int = 9
        const val DEFAULT_DROPPED_HIGH: Int = 24
        const val DEFAULT_DROPPED_FROZEN: Int = 42

        const val DEFAULT_STARTUP_THRESHOLD_MS_WARM: Int = 4 * 1000
        const val DEFAULT_STARTUP_THRESHOLD_MS_COLD: Int = 10 * 1000

        const val DEFAULT_RELEASE_BUFFER_DELAY: Int = 10 * 1000
        const val TARGET_EVIL_METHOD_STACK: Int = 30
        const val MAX_LIMIT_ANALYSE_STACK_KEY_NUM: Int = 10

        const val LIMIT_WARM_THRESHOLD_MS: Int = 5 * 1000
    }
}
