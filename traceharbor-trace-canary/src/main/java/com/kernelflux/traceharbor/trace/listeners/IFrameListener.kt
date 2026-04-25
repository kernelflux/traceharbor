package com.kernelflux.traceharbor.trace.listeners

import android.os.Build
import android.view.FrameMetrics
import androidx.annotation.RequiresApi

/**
 * Use [ISceneFrameListener] to analyze frame metrics of specified scene, or use
 * [IDropFrameListener] to only analyze dropped frame.
 */
@RequiresApi(Build.VERSION_CODES.N)
fun interface IFrameListener {
    fun onFrameMetricsAvailable(
        sceneName: String,
        frameMetrics: FrameMetrics,
        droppedFrames: Float,
        refreshRate: Float,
    )
}
