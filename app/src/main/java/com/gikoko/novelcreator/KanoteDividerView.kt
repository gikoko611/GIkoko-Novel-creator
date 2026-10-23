package com.gikoko.novelcreator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class KanoteDividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dp = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        linePaint.color = Color.parseColor("#CCFFC940")
        linePaint.strokeWidth = dp * 1.1f
        fillPaint.color = Color.parseColor("#FFC940")
        fillPaint.setShadowLayer(dp * 4f, 0f, 0f, Color.parseColor("#66FFC940"))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cy = h / 2f
        val d = dp * 4.5f
        val gap = d * 2.4f

        canvas.drawLine(0f, cy, w * 0.5f - gap, cy, linePaint)
        canvas.drawLine(w * 0.5f + gap, cy, w, cy, linePaint)

        val p = Path()
        p.moveTo(w / 2f, cy - d)
        p.lineTo(w / 2f + d, cy)
        p.lineTo(w / 2f, cy + d)
        p.lineTo(w / 2f - d, cy)
        p.close()
        canvas.drawPath(p, fillPaint)

        drawMini(canvas, w * 0.5f - gap, cy, dp * 2.2f)
        drawMini(canvas, w * 0.5f + gap, cy, dp * 2.2f)
        drawMini(canvas, dp * 2f, cy, dp * 1.6f)
        drawMini(canvas, w - dp * 2f, cy, dp * 1.6f)
    }

    private fun drawMini(canvas: Canvas, x: Float, y: Float, half: Float) {
        val p = Path()
        p.moveTo(x, y - half)
        p.lineTo(x + half, y)
        p.lineTo(x, y + half)
        p.lineTo(x - half, y)
        p.close()
        canvas.drawPath(p, fillPaint)
    }
}
