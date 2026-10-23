package com.gikoko.novelcreator

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fl = FrameLayout(this)
        fl.addView(CyberSplashView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val center = LinearLayout(this)
        center.orientation = LinearLayout.VERTICAL
        center.gravity = Gravity.CENTER

        val resId = resources.getIdentifier("img_logo", "drawable", packageName)
        if (resId != 0) {
            val iv = ImageView(this)
            iv.setImageResource(resId)
            iv.adjustViewBounds = true
            center.addView(iv, LinearLayout.LayoutParams(Ui.dp(this, 260f), LinearLayout.LayoutParams.WRAP_CONTENT))
        } else {
            center.addView(GoldLogoView(this), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 160f)))
        }

        val powered = TextView(this)
        powered.text = "POWERED BY I KO KO"
        powered.textSize = 12f
        powered.setTextColor(Ui.GOLD_LIGHT)
        powered.typeface = Typeface.DEFAULT_BOLD
        powered.letterSpacing = 0.3f
        powered.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Ui.dp(this, 18f)
        center.addView(powered, lp)

        fl.addView(center, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(fl)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
        }, 1600)
    }
}
