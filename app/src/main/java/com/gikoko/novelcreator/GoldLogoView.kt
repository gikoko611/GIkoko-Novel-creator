package com.gikoko.novelcreator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

class GoldLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var t = 0L
    private val dp = resources.displayMetrics.density
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        mainPaint.textAlign = Paint.Align.CENTER
        mainPaint.typeface = Typeface.DEFAULT_BOLD
        subPaint.textAlign = Paint.Align.CENTER
        subPaint.typeface = Typeface.DEFAULT_BOLD
        subPaint.letterSpacing = 0.25f
        subPaint.setShadowLayer(dp * 7f, 0f, 0f, Color.parseColor("#663DE8F8"))

        val anim = ValueAnimator.ofInt(0, 1)
        anim.duration = 16
        anim.repeatCount = ValueAnimator.INFINITE
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener {
            t += 16
            invalidate()
        }
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val mainSize = (w * 0.16f).coerceIn(dp * 20f, dp * 44f)
        val mainY = h * 0.42f

        glowPaint.shader = RadialGradient(
            cx, mainY, w * 0.55f,
            Color.parseColor("#2BFFC940"),
            Color.parseColor("#0005070E"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        glowPaint.shader = null

        mainPaint.textSize = mainSize

        val extrude = (dp * 2.4f).toInt()
        mainPaint.clearShadowLayer()
        mainPaint.shader = null
        for (i in extrude downTo 1) {
            mainPaint.color = if (i > extrude / 2) Color.parseColor("#4A3100") else Color.parseColor("#6B4700")
            canvas.drawText("G.I KO KO", cx, mainY + i * dp * 0.8f, mainPaint)
        }

        mainPaint.shader = LinearGradient(
            0f, mainY - mainSize, 0f, mainY + mainSize * 0.35f,
            Color.parseColor("#FFF3C9"),
            Color.parseColor("#FFC940"),
            Shader.TileMode.CLAMP
        )
        canvas.drawText("G.I KO KO", cx, mainY, mainPaint)
        mainPaint.shader = null

        val pulse = 0.55f + 0.45f * abs(sin(t * 0.0022))
        subPaint.textSize = mainSize * 0.34f
        subPaint.color = Color.parseColor("#3DE8F8")
        subPaint.alpha = (140 + 100 * pulse).toInt().coerceAtMost(255)
        canvas.drawText("NOVEL CREATOR", cx, mainY + mainSize * 1.05f, subPaint)
        subPaint.alpha = 255
    }
}
