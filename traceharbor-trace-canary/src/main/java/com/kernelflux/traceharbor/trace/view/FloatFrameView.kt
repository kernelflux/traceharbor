package com.kernelflux.traceharbor.trace.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.kernelflux.traceharbor.trace.R
import com.kernelflux.traceharbor.trace.constants.Constants
import java.util.LinkedList

/**
 * Floating debug HUD that renders FPS / per-frame phase durations and a
 * 50-sample line chart on top of every Activity.
 *
 * The 22 child `TextView` fields are mutated directly by [FrameDecorator]
 * (Java) on every callback, so each one is exposed via `@JvmField` to
 * preserve the `view.fpsView.setText(…)` field-access pattern verbatim.
 */
@RequiresApi(Build.VERSION_CODES.N)
class FloatFrameView : LinearLayout {

    @JvmField var fpsView: TextView? = null
    @JvmField var sceneView: TextView? = null
    @JvmField var chartView: LineChartView? = null

    @JvmField var extraInfoView: TextView? = null
    @JvmField var unknownDelayDurationView: TextView? = null
    @JvmField var inputHandlingDurationView: TextView? = null
    @JvmField var animationDurationView: TextView? = null
    @JvmField var layoutMeasureDurationView: TextView? = null
    @JvmField var drawDurationView: TextView? = null
    @JvmField var syncDurationView: TextView? = null
    @JvmField var commandIssueDurationView: TextView? = null
    @JvmField var swapBuffersDurationView: TextView? = null
    @JvmField var gpuDurationView: TextView? = null
    @JvmField var totalDurationView: TextView? = null

    @JvmField var sumNormalView: TextView? = null
    @JvmField var sumMiddleView: TextView? = null
    @JvmField var sumHighView: TextView? = null
    @JvmField var sumFrozenView: TextView? = null
    @JvmField var levelNormalView: TextView? = null
    @JvmField var levelMiddleView: TextView? = null
    @JvmField var levelHighView: TextView? = null
    @JvmField var levelFrozenView: TextView? = null

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    private fun initView(context: Context) {
        layoutParams = ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        LayoutInflater.from(context).inflate(R.layout.float_frame_view, this)
        fpsView = findViewById(R.id.fps_view)
        extraInfoView = findViewById(R.id.extra_info)
        sceneView = findViewById(R.id.scene_view)
        extraInfoView?.text = "{other info}"

        unknownDelayDurationView = findViewById(R.id.unknown_delay_duration_tv)
        inputHandlingDurationView = findViewById(R.id.input_handling_duration_tv)
        animationDurationView = findViewById(R.id.animation_duration_tv)
        layoutMeasureDurationView = findViewById(R.id.layout_measure_duration_tv)
        drawDurationView = findViewById(R.id.draw_duration_tv)
        syncDurationView = findViewById(R.id.sync_duration_tv)
        commandIssueDurationView = findViewById(R.id.command_issue_duration_tv)
        swapBuffersDurationView = findViewById(R.id.swap_buffers_duration_tv)
        gpuDurationView = findViewById(R.id.gpu_duration_tv)
        totalDurationView = findViewById(R.id.total_duration_tv)

        sumNormalView = findViewById(R.id.sum_normal)
        sumMiddleView = findViewById(R.id.sum_middle)
        sumHighView = findViewById(R.id.sum_high)
        sumFrozenView = findViewById(R.id.sum_frozen)
        levelNormalView = findViewById(R.id.level_normal)
        levelMiddleView = findViewById(R.id.level_middle)
        levelHighView = findViewById(R.id.level_high)
        levelFrozenView = findViewById(R.id.level_frozen)

        chartView = findViewById(R.id.chart)
    }

    /**
     * Custom 50-sample rolling FPS chart. Drawn directly via Canvas to
     * avoid the overhead of nested ViewGroups.
     */
    class LineChartView : View {

        private val paint: Paint
        private val tipPaint: TextPaint
        private val levelLinePaint: Paint
        private val tipLinePaint: Paint
        private val lines: LinkedList<LineInfo>

        var linePadding: Float = 0f
        var lineStrokeWidth: Float = 0f

        private val topPath: Path = Path()
        private val middlePath: Path = Path()
        private val topTip: FloatArray = FloatArray(2)
        private val middleTip: FloatArray = FloatArray(2)

        private val bestColor: Int = context.resources.getColor(R.color.level_best_color)
        private val normalColor: Int = context.resources.getColor(R.color.level_normal_color)
        private val middleColor: Int = context.resources.getColor(R.color.level_middle_color)
        private val highColor: Int = context.resources.getColor(R.color.level_high_color)
        private val frozenColor: Int = context.resources.getColor(R.color.level_frozen_color)
        private val grayColor: Int = context.resources.getColor(R.color.dark_text)

        var padding: Float = dip2px(context, 8f).toFloat()
        var width: Float = 0f
        var lineContentWidth: Float = 0f
        var height: Float = 0f
        var textSize: Float = 0f

        @JvmOverloads
        constructor(
            context: Context,
            attrs: AttributeSet?,
            defStyleAttr: Int = 0,
        ) : super(context, attrs, defStyleAttr) {
            paint = Paint()
            tipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            textSize = dip2px(context, 8f).toFloat()
            tipPaint.textSize = textSize
            tipPaint.strokeWidth = dip2px(context, 1f).toFloat()
            tipPaint.color = grayColor

            levelLinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            levelLinePaint.strokeWidth = dip2px(context, 1f).toFloat()
            levelLinePaint.style = Paint.Style.STROKE
            levelLinePaint.pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)

            tipLinePaint = Paint(tipPaint)
            tipLinePaint.strokeWidth = dip2px(context, 1f).toFloat()
            tipLinePaint.color = grayColor
            tipLinePaint.style = Paint.Style.STROKE
            tipLinePaint.pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
            lines = LinkedList()
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.onLayout(changed, left, top, right, bottom)
            if (changed) {
                width = measuredWidth.toFloat()
                height = measuredHeight.toFloat()

                lineContentWidth = width - 3 * padding

                lineStrokeWidth = dip2px(context, 1f).toFloat()
                paint.strokeWidth = lineStrokeWidth
                linePadding = lineStrokeWidth * 2

                val rate = lineContentWidth / 60
                topTip[0] = 10 * rate + (width - lineContentWidth)
                topTip[1] = LINE_COUNT * linePadding + padding
                topPath.moveTo(topTip[0], topTip[1])
                topPath.lineTo(topTip[0], 0f)

                middleTip[0] = 30 * rate + (width - lineContentWidth)
                middleTip[1] = LINE_COUNT * linePadding + padding
                middlePath.moveTo(middleTip[0], middleTip[1])
                middlePath.lineTo(middleTip[0], 0f)
            }
        }

        fun addFps(fps: Int, color: Int) {
            val linePoint = LineInfo(fps, color)
            if (lines.size >= LINE_COUNT) {
                lines.removeLast()
            }
            lines.addFirst(linePoint)
            invalidate()
        }

        @SuppressLint("DefaultLocale")
        override fun draw(canvas: Canvas) {
            super.draw(canvas)
            var index = 1
            var sumFps = 0
            for (lineInfo in lines) {
                sumFps += lineInfo.fps
                lineInfo.draw(canvas, index)
                if (index % 25 == 0) {
                    val path = Path()
                    val pathY = lineInfo.linePoint[1]
                    path.moveTo(0f, pathY)
                    path.lineTo(measuredHeight.toFloat(), pathY)
                    canvas.drawPath(path, tipLinePaint)
                    tipPaint.color = grayColor
                    canvas.drawText("${index / 5}s", 0f, pathY + textSize, tipPaint)
                    if (index > 0) {
                        val aver = sumFps / index
                        tipPaint.color = getColor(aver)
                        canvas.drawText("${aver}FPS", 0f, pathY - textSize / 2, tipPaint)
                    }
                }
                index++
            }
            tipPaint.color = grayColor
            levelLinePaint.color = normalColor
            canvas.drawPath(topPath, levelLinePaint)
            canvas.drawText("50", topTip[0] - textSize / 2, topTip[1] + textSize, tipPaint)
            levelLinePaint.color = middleColor
            canvas.drawPath(middlePath, levelLinePaint)
            canvas.drawText("30", middleTip[0] - textSize / 2, middleTip[1] + textSize, tipPaint)
        }

        private fun getColor(fps: Int): Int = when {
            fps > 60 - Constants.DEFAULT_DROPPED_NORMAL -> bestColor
            fps > 60 - Constants.DEFAULT_DROPPED_MIDDLE -> normalColor
            fps > 60 - Constants.DEFAULT_DROPPED_HIGH -> middleColor
            fps > 60 - Constants.DEFAULT_DROPPED_FROZEN -> highColor
            else -> frozenColor
        }

        /**
         * Inner class — references outer [width], [lineContentWidth],
         * [linePadding], [paint] from the enclosing [LineChartView]. Kept
         * as `inner` to retain the same enclosing-instance reference Java
         * gave us.
         */
        inner class LineInfo internal constructor(
            @JvmField val fps: Int,
            @JvmField val color: Int,
        ) {
            internal val linePoint: FloatArray = FloatArray(4)

            init {
                linePoint[0] = width // startX
                linePoint[2] = (60 - fps) * lineContentWidth / 60 + (this@LineChartView.width - lineContentWidth) // endX
            }

            internal fun draw(canvas: Canvas, index: Int) {
                if (paint.color != color) {
                    paint.color = color
                }
                linePoint[1] = (1 + index) * linePadding // startY
                linePoint[3] = linePoint[1] // endY
                canvas.drawLine(linePoint[0], linePoint[1], linePoint[2], linePoint[3], paint)
            }
        }

        companion object {
            private const val LINE_COUNT = 50

            @JvmStatic
            fun dip2px(context: Context, dpValue: Float): Int {
                val scale = context.resources.displayMetrics.density
                return (dpValue * scale + 0.5f).toInt()
            }
        }
    }
}
