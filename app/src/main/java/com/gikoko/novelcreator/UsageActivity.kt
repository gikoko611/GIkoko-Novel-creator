package com.gikoko.novelcreator

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max

class UsageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        val root = findViewById<LinearLayout>(R.id.rootContainer)

        root.addView(Ui.backBtn(this) { finish() })
        root.addView(Ui.title(this, "📊 USAGE TRACKER"))
        root.addView(Ui.label(this, "AI request သုံးစွဲမှု - နောက်ဆုံး 7 ရက်"))

        val week = Prefs.usageWeek(this)
        val maxV = max(1, week.maxOf { it.second })

        val chartCard = Ui.card(this, true)
        val chart = LinearLayout(this)
        chart.orientation = LinearLayout.HORIZONTAL
        chart.gravity = Gravity.BOTTOM
        val chartLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 170f))
        chartLp.topMargin = Ui.dp(this, 10f)
        chart.layoutParams = chartLp

        for (d in week) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val colLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            col.layoutParams = colLp

            val countTv = TextView(this)
            countTv.text = if (d.second > 0) d.second.toString() else ""
            countTv.textSize = 11f
            countTv.setTextColor(Ui.GOLD_LIGHT)
            countTv.gravity = Gravity.CENTER

            val bar = android.view.View(this)
            val h = Ui.dp(this, 4f) + Math.round(Ui.dp(this, 110f) * d.second.toFloat() / maxV)
            bar.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 18f), h)
            bar.setBackgroundColor(if (d.second > 0) Ui.GOLD else Color.parseColor("#1C2646"))

            val dayTv = TextView(this)
            dayTv.text = d.first
            dayTv.textSize = 11f
            dayTv.setTextColor(Ui.DIM)
            dayTv.gravity = Gravity.CENTER

            col.addView(countTv)
            col.addView(bar)
            col.addView(dayTv)
            chart.addView(col)
        }
        chartCard.addView(chart)
        root.addView(chartCard)

        val today = week[6].second
        val statCard = Ui.card(this)
        statCard.addView(Ui.body(this, "ဒီနေ့: " + today + " requests"))
        statCard.addView(Ui.body(this, "7 ရက် အတွင်း: " + Prefs.usageTotal(this) + " requests (စုစုပေါင်း)"))
        root.addView(statCard)
    }
}
