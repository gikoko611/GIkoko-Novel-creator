package com.gikoko.novelcreator

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.net.AiClient
import com.gikoko.novelcreator.novel.NovelEngine
import com.gikoko.novelcreator.novel.NovelStore
import org.json.JSONObject
import kotlin.concurrent.thread

class ProcessingActivity : AppCompatActivity() {

    @Volatile
    private var cancelled = false

    private lateinit var progressView: NeonProgressView
    private lateinit var statusTv: TextView

    private var genre = ""
    private var idea = ""
    private var mode = "outline"
    private var chapters = 5
    private var lenDesc = ""
    private var voice = ""
    private var template = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        val root = findViewById<LinearLayout>(R.id.rootContainer)

        genre = intent.getStringExtra("genre") ?: "Romance"
        idea = intent.getStringExtra("idea") ?: ""
        mode = intent.getStringExtra("mode") ?: "outline"
        chapters = intent.getIntExtra("chapters", 5)
        val lenIdx = intent.getIntExtra("lenIdx", 1)
        lenDesc = NovelEngine.LENGTH_PARAS[if (lenIdx in NovelEngine.LENGTH_PARAS.indices) lenIdx else 1]
        voice = intent.getStringExtra("voice") ?: "my-MM-NilarNeural"
        template = intent.getStringExtra("template") ?: ""

        val header = Ui.title(this, "🚀 ဝတ္ထု ရေးနေပါပြီ...")
        root.addView(header)

        progressView = NeonProgressView(this)
        val plp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 24f))
        plp.topMargin = Ui.dp(this, 16f)
        plp.bottomMargin = Ui.dp(this, 12f)
        progressView.layoutParams = plp
        root.addView(progressView)

        val logCard = Ui.card(this)
        statusTv = TextView(this)
        statusTv.textSize = 13f
        statusTv.setTextColor(Ui.DIM)
        statusTv.text = "ဇာတ်လမ်းအိုင်ဒီယာ ပြင်ဆင်နေပါပြီ..."
        logCard.addView(statusTv)
        root.addView(logCard)

        val stopBtn = Ui.goldBtn(this, "⏹ ရပ်တန့်မယ်") { cancelled = true }
        root.addView(stopBtn)

        thread(start = true) { generate() }
    }

    private fun ui(r: () -> Unit) {
        runOnUiThread(r)
    }

    private fun generate() {
        try {
            val totalSteps = chapters + 1
            ui { statusTv.text = if (mode == "outline") "ဇာတ်ကောင် + plot စီစဉ်နေပါသည်..." else "ခေါင်းစဉ် ရွေးနေပါသည်..." }
            ui { progressView.setProgress(3) }

            var title = ""
            var outline: JSONObject? = null
            if (mode == "outline") {
                val raw = AiClient.generate(this, NovelEngine.outlinePrompt(idea, genre, chapters, lenDesc, template), true)
                Prefs.addUsage(this)
                outline = AiClient.extractJson(raw)
                title = outline.optString("title", "")
            } else {
                val raw = AiClient.generate(this, NovelEngine.titlePrompt(idea, genre), true)
                Prefs.addUsage(this)
                title = AiClient.extractJson(raw).optString("title", "")
            }
            if (title.isEmpty()) title = "ဂီကိုကို ဝတ္ထု"
            checkCancel()

            val id = NovelStore.create(this, title, genre, mode, voice)
            val dir = NovelStore.dir(this, id)
            NovelStore.saveMeta(dir, JSONObject().put("template", template))
            if (outline != null) NovelStore.saveOutline(dir, outline)
            val chaptersJson = outline?.optJSONArray("chapters")

            ui { statusTv.text = "ခေါင်းစဉ်: " + title }

            var prevTail = ""
            for (n in 1..chapters) {
                checkCancel()
                var brief = "ဇာတ်လမ်းအိုင်ဒီယာအတိုင်း ဆက်လက်ရေးပါ"
                var chTitle = "အခန်း " + n
                if (chaptersJson != null && n - 1 < chaptersJson.length()) {
                    val o = chaptersJson.getJSONObject(n - 1)
                    brief = o.optString("brief", brief)
                    chTitle = o.optString("title", chTitle)
                }
                ui { statusTv.text = "အခန်း " + n + " / " + chapters + " ရေးနေပါသည်...\n(" + chTitle + ")" }
                val raw = AiClient.generate(this, NovelEngine.chapterPrompt(idea, genre, brief, chTitle, n, chapters, lenDesc, prevTail, template), false)
                Prefs.addUsage(this)
                val text = NovelEngine.cleanText(raw)
                NovelStore.saveChapter(dir, n, text)
                NovelStore.saveMeta(dir, JSONObject().put("chapterCount", n))
                prevTail = if (text.length > 600) text.substring(text.length - 600) else text
                val pct = (n + 1) * 100 / totalSteps
                ui { progressView.setProgress(pct) }
            }

            ui {
                progressView.setProgress(100)
                statusTv.text = "ဝတ္ထု ပြီးပါပြီ ✓ - " + title
                AlertDialog.Builder(this)
                    .setTitle("✓ အောင်မြင်ပါသည်")
                    .setMessage(title + "\n\nအခန်း " + chapters + " ခန်း ရေးပြီးပါပြီ။ Audiobook ထုတ်ဖို့ AUDIOBOOK မှာ ဆက်လုပ်နိုင်ပါသည်။")
                    .setCancelable(false)
                    .setPositiveButton("ဖတ်မယ်") { _, _ -> openNovel(id) }
                    .setNegativeButton("နောက်မှ") { _, _ -> finish() }
                    .show()
            }
        } catch (e: Exception) {
            DebugLog.log(this, "generate error: " + (e.message ?: ""))
            val msg = if (e.message?.contains("ရပ်တန့်") == true) "ရပ်တန့်ခဲ့ပါသည်" else ("အမှား: " + (e.message ?: "unknown"))
            ui {
                AlertDialog.Builder(this)
                    .setTitle("ဇာတ်လမ်း မပြီးသေးပါ")
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            }
        }
    }

    private fun checkCancel() {
        if (cancelled) throw IllegalStateException("ရပ်တန့်ခဲ့ပါသည်")
    }

    private fun openNovel(id: Long) {
        val i = Intent(this, NovelViewActivity::class.java)
        i.putExtra("id", id)
        startActivity(i)
        finish()
    }
}
