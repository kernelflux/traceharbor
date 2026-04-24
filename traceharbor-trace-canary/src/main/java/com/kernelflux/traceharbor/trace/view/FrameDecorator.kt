package com.kernelflux.traceharbor.trace.view

import android.animation.Animator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.lifecycle.IStateObserver
import com.kernelflux.traceharbor.lifecycle.owners.ProcessUIResumedStateOwner
import com.kernelflux.traceharbor.trace.R
import com.kernelflux.traceharbor.trace.TracePlugin
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.listeners.ISceneFrameListener
import com.kernelflux.traceharbor.trace.tracer.FrameTracer
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Arrays

/**
 * Process-singleton overlay decorator that draws a draggable HUD ([view])
 * showing live FPS / per-phase frame durations. Listens to scene-frame
 * callbacks via [ISceneFrameListener] and to process foreground state via
 * [ProcessUIResumedStateOwner].
 *
 * Public API kept byte-for-byte:
 *  - `FrameDecorator.get()` / `FrameDecorator.getInstance(Context)` static
 *    accessors moved to a `companion object` with `@JvmStatic`.
 *  - Java callers (sample app `MainActivity`) keep their existing
 *    `FrameDecorator.getInstance(this)` call site unchanged.
 *
 * Note: `getInstance` is intentionally not double-checked — original Java
 * had a single `if (instance == null)` outside any lock plus a one-shot
 * post-to-main-thread plus `lock.wait()`. Reproduced verbatim to avoid
 * subtle race-condition behavior changes.
 */
@RequiresApi(Build.VERSION_CODES.N)
class FrameDecorator @SuppressLint("ClickableViewAccessibility") private constructor(
    context: Context,
    private val view: FloatFrameView,
) : ISceneFrameListener {

    private var windowManager: WindowManager? = null
    private var layoutParam: WindowManager.LayoutParams? = null
    private var isShowingInternal: Boolean = false
    private var clickListener: View.OnClickListener? = null
    private val displayMetrics: DisplayMetrics = DisplayMetrics()
    private var isEnableInternal: Boolean = true

    private val bestColor: Int
    private val normalColor: Int
    private val middleColor: Int
    private val highColor: Int
    private val frozenColor: Int
    private var belongColor: Int

    private val mProcessForegroundListener: IStateObserver = object : IStateObserver {
        override fun on() {
            onForeground(true)
        }

        override fun off() {
            onForeground(false)
        }
    }

    init {
        bestColor = context.resources.getColor(R.color.level_best_color)
        normalColor = context.resources.getColor(R.color.level_normal_color)
        middleColor = context.resources.getColor(R.color.level_middle_color)
        highColor = context.resources.getColor(R.color.level_high_color)
        frozenColor = context.resources.getColor(R.color.level_frozen_color)
        belongColor = bestColor

        ProcessUIResumedStateOwner.observeForever(mProcessForegroundListener)

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                TraceHarborLog.i(TAG, "onViewAttachedToWindow")
                if (TraceHarbor.isInstalled()) {
                    val tracePlugin = TraceHarbor.with().getPluginByClass(TracePlugin::class.java)
                    if (tracePlugin != null) {
                        val tracer = tracePlugin.frameTracer
                        tracer?.register(this@FrameDecorator)
                    }
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                TraceHarborLog.i(TAG, "onViewDetachedFromWindow")
                if (TraceHarbor.isInstalled()) {
                    val tracePlugin = TraceHarbor.with().getPluginByClass(TracePlugin::class.java)
                    if (tracePlugin != null) {
                        val tracer = tracePlugin.frameTracer
                        tracer?.unregister(this@FrameDecorator)
                    }
                }
            }
        })
        initLayoutParams(context)

        view.setOnTouchListener(object : View.OnTouchListener {
            var downX: Float = 0f
            var downY: Float = 0f
            var downOffsetX: Int = 0
            var downOffsetY: Int = 0

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val lp = layoutParam ?: return true
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        downOffsetX = lp.x
                        downOffsetY = lp.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val moveX = event.x
                        val moveY = event.y
                        lp.x += ((moveX - downX) / 3).toInt()
                        lp.y += ((moveY - downY) / 3).toInt()
                        if (v != null) {
                            windowManager?.updateViewLayout(v, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val targetX = if (lp.x > displayMetrics.widthPixels / 2) {
                            displayMetrics.widthPixels - view.width
                        } else {
                            0
                        }
                        val holder = PropertyValuesHolder.ofInt("trans", lp.x, targetX)
                        val animator: Animator = ValueAnimator.ofPropertyValuesHolder(holder)
                        (animator as ValueAnimator).addUpdateListener { animation ->
                            if (!isShowingInternal) {
                                return@addUpdateListener
                            }
                            lp.x = animation.getAnimatedValue("trans") as Int
                            windowManager?.updateViewLayout(v, lp)
                        }
                        animator.interpolator = AccelerateInterpolator()
                        animator.setDuration(180).start()

                        val upOffsetX = lp.x
                        val upOffsetY = lp.y
                        if (Math.abs(upOffsetX - downOffsetX) <= 20 &&
                            Math.abs(upOffsetY - downOffsetY) <= 20
                        ) {
                            clickListener?.onClick(v)
                        }
                    }
                }
                return true
            }
        })
    }

    fun setClickListener(clickListener: View.OnClickListener?) {
        this.clickListener = clickListener
    }

    fun setExtraInfo(info: String) {
        getView()?.let { v ->
            val textView = v.findViewById<TextView?>(R.id.extra_info)
            textView?.text = info
        }
    }

    fun getView(): FloatFrameView? = view

    private fun initLayoutParams(context: Context) {
        windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
        try {
            val metrics = DisplayMetrics()
            val wm = windowManager
            @Suppress("DEPRECATION")
            if (wm?.defaultDisplay != null) {
                wm.defaultDisplay.getMetrics(displayMetrics)
                wm.defaultDisplay.getMetrics(metrics)
            }

            val lp = WindowManager.LayoutParams()
            lp.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            lp.flags = (
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            lp.gravity = Gravity.START or Gravity.TOP
            val viewLp = view.layoutParams
            if (viewLp != null) {
                lp.x = metrics.widthPixels - viewLp.width * 2
            }
            lp.y = 0
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            lp.format = PixelFormat.TRANSPARENT
            layoutParam = lp
        } catch (e: Exception) {
            // swallow — original Java behavior
        }
    }

    fun show() {
        if (!isEnableInternal) {
            return
        }
        mainHandler.post {
            if (!isShowingInternal) {
                isShowingInternal = true
                windowManager?.addView(view, layoutParam)
            }
        }
    }

    fun isEnable(): Boolean = isEnableInternal

    fun setEnable(enable: Boolean) {
        isEnableInternal = enable
    }

    fun dismiss() {
        if (!isEnableInternal) {
            return
        }
        mainHandler.post {
            if (isShowingInternal) {
                isShowingInternal = false
                windowManager?.removeView(view)
            }
        }
    }

    fun isShowing(): Boolean = isShowingInternal

    private fun onForeground(isForeground: Boolean) {
        TraceHarborLog.i(TAG, "[onForeground] isForeground:%s", isForeground)
        if (!isEnableInternal) {
            return
        }
        mainHandler.post {
            if (isForeground) {
                show()
            } else {
                dismiss()
            }
        }
    }

    override fun getIntervalMs(): Int = 200

    override fun getName(): String? = null

    override fun skipFirstFrame(): Boolean = false

    override fun getThreshold(): Int = 0

    @SuppressLint("DefaultLocale")
    override fun onFrameMetricsAvailable(
        sceneName: String,
        avgDurations: LongArray,
        dropLevel: IntArray,
        dropSum: IntArray,
        avgDroppedFrame: Float,
        avgRefreshRate: Float,
        fps: Float,
    ) {
        val unknownDelay = String.format(
            "unknown delay: %.1fms",
            avgDurations[FrameTracer.FrameDuration.UNKNOWN_DELAY_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val inputHandling = String.format(
            "input handling: %.1fms",
            avgDurations[FrameTracer.FrameDuration.INPUT_HANDLING_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val animation = String.format(
            "animation: %.1fms",
            avgDurations[FrameTracer.FrameDuration.ANIMATION_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val layoutMeasure = String.format(
            "layout measure: %.1fms",
            avgDurations[FrameTracer.FrameDuration.LAYOUT_MEASURE_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val draw = String.format(
            "draw: %.1fms",
            avgDurations[FrameTracer.FrameDuration.DRAW_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val sync = String.format(
            "sync: %.1fms",
            avgDurations[FrameTracer.FrameDuration.SYNC_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val commandIssue = String.format(
            "command issue: %.1fms",
            avgDurations[FrameTracer.FrameDuration.COMMAND_ISSUE_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val swapBuffers = String.format(
            "swap buffers: %.1fms",
            avgDurations[FrameTracer.FrameDuration.SWAP_BUFFERS_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val gpu = String.format(
            "gpu: %.1fms",
            avgDurations[FrameTracer.FrameDuration.GPU_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )
        val total = String.format(
            "total: %.1fms",
            avgDurations[FrameTracer.FrameDuration.TOTAL_DURATION.ordinal].toDouble() / Constants.TIME_MILLIS_TO_NANO,
        )

        belongColor = when {
            fps <= avgRefreshRate - Constants.DEFAULT_DROPPED_FROZEN -> frozenColor
            fps <= avgRefreshRate - Constants.DEFAULT_DROPPED_HIGH -> highColor
            fps <= avgRefreshRate - Constants.DEFAULT_DROPPED_MIDDLE -> middleColor
            fps <= avgRefreshRate - Constants.DEFAULT_DROPPED_NORMAL -> normalColor
            else -> bestColor
        }

        val level = Arrays.copyOf(dropLevel, dropLevel.size)
        val sum = Arrays.copyOf(dropSum, dropSum.size)

        mainHandler.post {
            view.chartView?.addFps(fps.toInt(), belongColor)
            view.fpsView?.text = String.format("%.2f FPS", fps)
            view.fpsView?.setTextColor(belongColor)

            view.unknownDelayDurationView?.text = unknownDelay
            view.inputHandlingDurationView?.text = inputHandling
            view.animationDurationView?.text = animation
            view.layoutMeasureDurationView?.text = layoutMeasure
            view.drawDurationView?.text = draw
            view.syncDurationView?.text = sync
            view.commandIssueDurationView?.text = commandIssue
            view.swapBuffersDurationView?.text = swapBuffers
            if (sdkInt >= Build.VERSION_CODES.S) {
                view.gpuDurationView?.text = gpu
            } else {
                view.gpuDurationView?.text = "gpu: unusable"
            }
            view.totalDurationView?.text = total

            view.sumNormalView?.text = sum[FrameTracer.DropStatus.DROPPED_NORMAL.ordinal].toString()
            view.sumMiddleView?.text = sum[FrameTracer.DropStatus.DROPPED_MIDDLE.ordinal].toString()
            view.sumHighView?.text = sum[FrameTracer.DropStatus.DROPPED_HIGH.ordinal].toString()
            view.sumFrozenView?.text = sum[FrameTracer.DropStatus.DROPPED_FROZEN.ordinal].toString()
            view.levelNormalView?.text = level[FrameTracer.DropStatus.DROPPED_NORMAL.ordinal].toString()
            view.levelMiddleView?.text = level[FrameTracer.DropStatus.DROPPED_MIDDLE.ordinal].toString()
            view.levelHighView?.text = level[FrameTracer.DropStatus.DROPPED_HIGH.ordinal].toString()
            view.levelFrozenView?.text = level[FrameTracer.DropStatus.DROPPED_FROZEN.ordinal].toString()
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.FrameDecorator"
        private val mainHandler = Handler(Looper.getMainLooper())
        private val lock = Object()

        @JvmStatic
        @Volatile
        private var instance: FrameDecorator? = null

        @Suppress("DEPRECATION")
        private val sdkInt: Int = Build.VERSION.SDK_INT

        @JvmStatic
        fun get(): FrameDecorator? = instance

        @JvmStatic
        fun getInstance(context: Context): FrameDecorator? {
            if (instance == null) {
                if (Thread.currentThread() === Looper.getMainLooper().thread) {
                    instance = FrameDecorator(context, FloatFrameView(context))
                } else {
                    try {
                        synchronized(lock) {
                            mainHandler.post {
                                instance = FrameDecorator(context, FloatFrameView(context))
                                synchronized(lock) {
                                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                                    (lock as java.lang.Object).notifyAll()
                                }
                            }
                            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                            (lock as java.lang.Object).wait()
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            return instance
        }
    }
}
