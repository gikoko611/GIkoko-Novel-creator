package com.gikoko.novelcreator

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.novel.NovelStore

class LibraryActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        root = findViewById(R.id.rootContainer)
        root.addView(Ui.backBtn(this) { finish() })
        root.addView(Ui.title(this, "📚 LIBRARY"))
    }

    override fun onStart() {
        super.onStart()
        rebuild()
    }

    private fun rebuild() {
        val list = findViewById<LinearLayout>(R.id.rootContainer)
        while (list.childCount > 2) {
            list.removeViewAt(list.childCount - 1)
        }
        val novels = NovelStore.list(this)
        if (novels.isEmpty()) {
            val empty = Ui.statusLine(this, "ဝတ္ထု မရှိသေးပါ - NOVEL STUDIO ကနေ အရင်ရေးပါ")
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Ui.dp(this, 40f)
            empty.layoutParams = lp
            list.addView(empty)
            return
        }
        for (n in novels) {
            val card = Ui.card(this, true)
            val title = TextView(this)
            title.text = n.title
            title.textSize = 17f
            title.typeface = android.graphics.Typeface.DEFAULT_BOLD
            title.setTextColor(Ui.GOLD)
            card.addView(title)
            val sub = Ui.label(this, n.genre + " • " + if (n.mode == "outline") "OUTLINE" else "DIRECT" + " • အခန်း " + n.chapterCount + " ခန်း • " + n.created)
            sub.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            card.addView(sub)
            card.setOnClickListener {
                val i = Intent(this, NovelViewActivity::class.java)
                i.putExtra("id", n.id)
                startActivity(i)
            }
            card.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("ဖျက်မလား")
                    .setMessage(n.title + " ကို အပြီးအပိုင် ဖျက်မှာလား")
                    .setPositiveButton("ဖျက်မယ်") { _, _ ->
                        NovelStore.delete(this, n.id)
                        rebuild()
                    }
                    .setNegativeButton("မဖျက်ပါနဲ့", null)
                    .show()
                true
            }
            list.addView(card)
        }
    }
}
