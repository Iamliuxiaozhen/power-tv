package com.zitzhen.powertv.ui.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.zitzhen.powertv.data.theme.ThemeColor
import com.zitzhen.powertv.data.theme.ThemeScheme

/** A tiny palette preview used by the theme list. */
class ThemeSwatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var scheme: ThemeScheme? = null

    private val bounds = RectF()
    private val barBounds = RectF()
    private val barColors = IntArray(4)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun bind(scheme: ThemeScheme) {
        this.scheme = scheme
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = scheme ?: return
        val inset = dp(1f)
        bounds.set(inset, inset, width - inset, height - inset)
        val radius = dp(14f)
        backgroundPaint.color = current[ThemeColor.SURFACE]
        canvas.drawRoundRect(bounds, radius, radius, backgroundPaint)
        strokePaint.color = current[ThemeColor.OUTLINE_VARIANT]
        canvas.drawRoundRect(bounds, radius, radius, strokePaint)

        barColors[0] = current[ThemeColor.PRIMARY]
        barColors[1] = current[ThemeColor.PRIMARY_CONTAINER]
        barColors[2] = current[ThemeColor.SECONDARY]
        barColors[3] = current[ThemeColor.TERTIARY]
        val padding = dp(7f)
        val gap = dp(3f)
        val contentWidth = width - padding * 2
        val barWidth = (contentWidth - gap * 3) / 4f
        val top = padding
        val bottom = height - padding
        for (index in barColors.indices) {
            val left = padding + index * (barWidth + gap)
            val fraction = when (index) {
                0 -> 1f
                1 -> 0.82f
                2 -> 0.64f
                else -> 0.46f
            }
            val barTop = bottom - (bottom - top) * fraction
            barPaint.color = barColors[index]
            barBounds.set(left, barTop, left + barWidth, bottom)
            canvas.drawRoundRect(barBounds, barWidth / 2f, barWidth / 2f, barPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
