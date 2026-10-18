package com.zitzhen.powertv.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.zitzhen.powertv.R

/**
 * A lightweight, fixed-window sparkline. The most recent sample is always on
 * the right edge, so the graph scrolls instead of rescaling horizontally.
 */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var values: List<Float> = emptyList()
    private var maxValue: Float = 100f

    private val lineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
    private val gridColor = ContextCompat.getColor(context, R.color.chart_grid)
    private val fillColor = ContextCompat.getColor(context, R.color.chart_fill)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.25f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = lineColor
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = gridColor
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = lineColor
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorUtils.setAlphaComponent(lineColor, 56)
    }

    private val linePath = Path()
    private val fillPath = Path()

    fun setValues(newValues: List<Float>, newMaxValue: Float = 100f) {
        values = newValues.takeLast(WINDOW)
        maxValue = newMaxValue.coerceAtLeast(1f)
        contentDescription = values.lastOrNull()?.let { "${it.toInt()}%" }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val top = dp(4f)
        val bottom = h - dp(4f)
        fillPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            ColorUtils.setAlphaComponent(lineColor, 96),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = dp(4f)
        val bottom = height - dp(4f)
        val left = dp(1f)
        val right = width - dp(1f)
        if (right <= left || bottom <= top) return

        // Horizontal guides keep the sparkline readable without a chart frame.
        for (fraction in floatArrayOf(0.25f, 0.5f, 0.75f)) {
            val y = bottom - (bottom - top) * fraction
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (values.isEmpty()) return

        val step = (right - left) / (WINDOW - 1)
        val startIndex = WINDOW - values.size
        var lastX = right
        var lastY = bottom

        linePath.reset()
        fillPath.reset()
        values.forEachIndexed { index, value ->
            val x = left + step * (startIndex + index)
            val normalized = (value.coerceIn(0f, maxValue) / maxValue)
            val y = bottom - normalized * (bottom - top)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }
        fillPath.lineTo(lastX, bottom)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
        canvas.drawCircle(lastX, lastY, dp(3f), dotPaint)
        canvas.drawCircle(lastX, lastY, dp(5.5f), haloPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val WINDOW = 60
    }
}
