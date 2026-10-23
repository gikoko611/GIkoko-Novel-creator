package com.gikoko.novelcreator

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.novel.NovelStore

class HomeActivity : AppCompatActivity() {

    private lateinit var librarySub: TextView
    private lateinit var providerSub: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        val root = findViewById<LinearLayout>(R.id.rootContainer)

        val logoWrap = LinearLayout(this)
        logoWrap.orientation = LinearLayout.VERTICAL
        logoWrap.gravity = Gravity.CENTER
        val resId = resources.getIdentifier("img_logo", "drawable", packageName)
        if (resId != 0) {
            val iv = ImageView(this)
            iv.setImageResource(resId)
            iv.adjustViewBounds = true
            logoWrap.addView(iv, LinearLayout.LayoutParams(Ui.dp(this, 240f), LinearLayout.LayoutParams.WRAP_CONTENT))
        } else {
            logoWrap.addView(GoldLogoView(this), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 150f)))
        }
        val powered = TextView(this)
        powered.text = "POWERED BY I KO KO"
        powered.textSize = 11f
        powered.setTextColor(Ui.GOLD_LIGHT)
        powered.typeface = Typeface.DEFAULT_BOLD
        powered.letterSpacing = 0.3f
        powered.gravity = Gravity.CENTER
        logoWrap.addView(powered)
        root.addView(logoWrap)

        root.addView(KanoteDividerView(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 14f)))

        val libCard = Ui.menuCard(this, "📚", "LIBRARY", "") { openLibrary() }
        root.addView(libCard)
        librarySub = libCard.findViewWithTag<TextView>("sub")
        val provCard = Ui.menuCard(this, "🔑", "AI PROVIDER & KEY", "") { startActivity(Intent(this, ProviderActivity::class.java)) }
        root.addView(provCard)
        providerSub = provCard.findViewWithTag<TextView>("sub")

        root.addView(Ui.menuCard(this, "✍️", "NOVEL STUDIO", "AI နဲ့ ဝတ္ထုအသစ် ရေးမယ်") {
            startActivity(Intent(this, StudioActivity::class.java))
        })
        root.addView(Ui.menuCard(this, "🔊", "AUDIOBOOK STUDIO", "နောက်ဆုံး ဝတ္ထုကို audiobook ထုတ်မယ်") { openAudioLatest() })
        root.addView(Ui.menuCard(this, "📊", "USAGE TRACKER", "7 ရက် AI သုံးစွဲမှု") {
            startActivity(Intent(this, UsageActivity::class.java))
        })
        root.addView(Ui.menuCard(this, "ℹ️", "ABOUT", "G.I KO KO NOVEL CREATOR v0.0.1") { showAbout() })
    }

    override fun onStart() {
        super.onStart()
        if (!::librarySub.isInitialized) return
        val count = NovelStore.list(this).size
        librarySub.text = "ဝတ္ထု " + count + " ပုဒ် သိမ်းထားပါသည်"
        val status = Prefs.connStatus(this)
        val model = Prefs.model(this)
        val provider = Prefs.provider(this)
        val sb = StringBuilder(provider.uppercase())
        if (model.isNotEmpty()) sb.append(" • ").append(model)
        if (status.isNotEmpty()) sb.append(" • ").append(status)
        providerSub.text = sb.toString()
    }

    private fun openLibrary() {
        startActivity(Intent(this, LibraryActivity::class.java))
    }

    private fun openAudioLatest() {
        val list = NovelStore.list(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "ဝတ္ထု မရှိသေးပါ - NOVEL STUDIO ကနေ အရင်ရေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(this, AudioActivity::class.java)
        i.putExtra("id", list[0].id)
        startActivity(i)
    }

    private fun showAbout() {
        val msg = "G.I KO KO NOVEL CREATOR v0.0.1\n\n" +
            "AI နဲ့ မြန်မာဝတ္ထုဇာတ်လမ်းတွေ ရေးပြီး\n" +
            "မြန်မာအသံ audiobook အဖြစ် ထုတ်ပေးသည်။\n\n" +
            "• Custom Provider + Free models\n" +
            "• Outline / Direct mode + Prompt templates\n" +
            "• AUTO ဆက်ရေး + Chapter tools\n" +
            "• Edge TTS မြန်မာအသံ + အသံနှစ်မျိုး (ဇာတ်စကား)\n" +
            "• Background player + Sleep timer\n" +
            "• AI Cover + EPUB / PDF export\n" +
            "• Full audiobook + Backup & Restore\n" +
            "• Cyber 3D + ရွှေပန်းကနုတ် theme\n\n" +
            "POWERED BY I KO KO"
        AlertDialog.Builder(this)
            .setTitle("ABOUT")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}
