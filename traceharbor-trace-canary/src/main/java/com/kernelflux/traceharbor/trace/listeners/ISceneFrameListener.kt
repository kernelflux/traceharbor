package com.kernelflux.traceharbor.trace.listeners

import androidx.annotation.IntRange

interface ISceneFrameListener {

    /**
     * The interval returned indicates how long to call back `onFrameMetricsAvailable`.
     * Usually this value should not be less than 17. For 60Hz displays it takes at least
     * 16.6ms to generate a frame.
     */
    @IntRange(from = 1)
    fun getIntervalMs(): Int

    /**
     * The name returned will be used to match the specified scene; null or empty matches all scenes.
     */
    fun getName(): String?

    /**
     * Whether to skip the first frame.
     */
    fun skipFirstFrame(): Boolean

    /**
     * Frame metrics whose dropped frames are below this threshold are skipped.
     * Threshold assumes a 60Hz refresh rate.
     */
    @IntRange(from = 0)
    fun getThreshold(): Int

    fun onFrameMetricsAvailable(
        sceneName: String,
        avgDurations: LongArray,
        dropLevel: IntArray,
        dropSum: IntArray,
        avgDroppedFrame: Float,
        avgRefreshRate: Float,
        avgFps: Float,
    )
}
