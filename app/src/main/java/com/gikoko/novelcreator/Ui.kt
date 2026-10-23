package com.gikoko.novelcreator

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Ui {

    val GOLD = Color.parseColor("#FFC940")
    val GOLD_LIGHT = Color.parseColor("#FFE9A8")
    val CYAN = Color.parseColor("#3DE8F8")
    val TEXT = Color.parseColor("#F2F5FF")
    val DIM = Color.parseColor("#8A93B2")
    val DANGER = Color.parseColor("#FF4D6D")

    fun dp(ctx: Context, v: Float): Int = Math.round(ctx.resources.displayMetrics.density * v)

    fun dp(ctx: Context, v: Int): Int = dp(ctx, v.toFloat())

    fun title(ctx: Context, text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 20f
        tv.setTextColor(GOLD)
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setShadowLayer(dp(ctx, 6f).toFloat(), 0f, 0f, Color.parseColor("#55FFC940"))
        return tv
    }

    fun section(ctx: Context, text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 13f
        tv.setTextColor(CYAN)
        tv.typeface = Typeface.DEFAULT_BOLD
        return tv
    }

    fun label(ctx: Context, text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 12f
        tv.setTextColor(DIM)
        return tv
    }

    fun body(ctx: Context, text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 15f
        tv.setTextColor(TEXT)
        tv.setLineSpacing(dp(ctx, 4f).toFloat(), 1f)
        return tv
    }

    fun statusLine(ctx: Context, text: String): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 13f
        tv.setTextColor(GOLD_LIGHT)
        tv.gravity = Gravity.CENTER
        return tv
    }

    fun backBtn(ctx: Context, onClick: (View) -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = "◀ BACK"
        tv.textSize = 14f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(GOLD)
        tv.setOnClickListener(onClick)
        return tv
    }

    fun card(ctx: Context, kanote: Boolean = false): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        val pad = dp(ctx, 18)
        l.setPadding(pad, pad, pad, pad)
        l.setBackgroundResource(if (kanote) R.drawable.bg_kanote else R.drawable.bg_card)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(ctx, 14)
        l.layoutParams = lp
        return l
    }

    fun chip(ctx: Context, text: String, selected: Boolean, onTap: (View) -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = 14f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(if (selected) GOLD else DIM)
        tv.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
        val h = dp(ctx, 14)
        val v = dp(ctx, 8)
        tv.setPadding(h, v, h, v)
        tv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.rightMargin = dp(ctx, 8) }
        tv.setOnClickListener(onTap)
        return tv
    }

    fun goldBtn(ctx: Context, text: String, onClick: (View) -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.textSize = 16f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(Color.parseColor("#1A1200"))
        tv.setBackgroundResource(R.drawable.bg_gold_btn)
        tv.setPadding(0, dp(ctx, 13), 0, dp(ctx, 13))
        tv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tv.setOnClickListener(onClick)
        return tv
    }

    fun input(ctx: Context, hint: String, text: String, multiline: Boolean): EditText {
        val et = EditText(ctx)
        et.hint = hint
        et.setText(text)
        et.textSize = 14f
        et.setTextColor(TEXT)
        et.setHintTextColor(DIM)
        et.setBackgroundResource(R.drawable.bg_input)
        val p = dp(ctx, 12)
        et.setPadding(p, p, p, p)
        et.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        if (multiline) {
            et.minLines = 3
            et.gravity = Gravity.TOP
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            et.inputType = InputType.TYPE_CLASS_TEXT
        }
        return et
    }

    fun row(ctx: Context): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.HORIZONTAL
        return l
    }

    fun menuCard(ctx: Context, icon: String, title: String, sub: String, onClick: (View) -> Unit): LinearLayout {
        val c = card(ctx, true)
        val row = row(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        val iconTv = TextView(ctx)
        iconTv.text = icon
        iconTv.textSize = 26f
        val tv = TextView(ctx)
        tv.text = title
        tv.textSize = 17f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(GOLD)
        val subTv = TextView(ctx)
        subTv.tag = "sub"
        subTv.text = sub
        subTv.textSize = 12f
        subTv.setTextColor(DIM)
        val mid = LinearLayout(ctx)
        mid.orientation = LinearLayout.VERTICAL
        mid.addView(tv)
        mid.addView(subTv)
        val midLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        midLp.leftMargin = dp(ctx, 14)
        mid.layoutParams = midLp
        row.addView(iconTv)
        row.addView(mid)
        c.addView(row)
        c.setOnClickListener(onClick)
        return c
    }
}
