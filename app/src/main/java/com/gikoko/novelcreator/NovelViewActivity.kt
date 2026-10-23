package com.gikoko.novelcreator

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.net.AiClient
import com.gikoko.novelcreator.novel.NovelEngine
import com.gikoko.novelcreator.novel.NovelStore
import com.gikoko.novelcreator.util.DownloadsUtil
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class NovelViewActivity : AppCompatActivity() {

    private var novelId = 0L
    private lateinit var dir: java.io.File
    private var current = 1
    private var count = 0

    private lateinit var titleTv: TextView
    private lateinit var chapterLabel: TextView
    private lateinit var bodyTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        novelId = intent.getLongExtra("id", 0L)
        dir = NovelStore.dir(this, novelId)
        if (!dir.exists()) {
            finish()
            return
        }
        val root = findViewById<LinearLayout>(R.id.rootContainer)

        root.addView(Ui.backBtn(this) { finish() })
        titleTv = Ui.title(this, "")
        root.addView(titleTv)

        val meta = NovelStore.meta(dir)
        val genre = meta.optString("genre", "")
        val mode = meta.optString("mode", "")
        count = meta.optInt("chapterCount", 0)
        current = if (count > 0) 1 else 0

        val sub = Ui.label(this, genre + " • " + if (mode == "outline") "OUTLINE MODE" else "DIRECT MODE" + " • အခန်း " + count + " ခန်း")
        root.addView(sub)

        val navCard = Ui.card(this)
        val navRow = Ui.row(this)
        navRow.gravity = Gravity.CENTER_VERTICAL
        val prev = Ui.chip(this, "◀", false) { nav(-1) }
        chapterLabel = TextView(this)
        chapterLabel.gravity = Gravity.CENTER
        chapterLabel.textSize = 14f
        chapterLabel.typeface = android.graphics.Typeface.DEFAULT_BOLD
        chapterLabel.setTextColor(Ui.GOLD)
        val navLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        navLp.gravity = Gravity.CENTER
        chapterLabel.layoutParams = navLp
        val next = Ui.chip(this, "▶", false) { nav(1) }
        navRow.addView(prev)
        navRow.addView(chapterLabel)
        navRow.addView(next)
        navCard.addView(navRow)
        root.addView(navCard)

        bodyTv = Ui.body(this, "")
        bodyTv.textSize = 16f
        val bodyCard = Ui.card(this, true)
        bodyCard.addView(bodyTv)
        root.addView(bodyCard)

        val toolsCard = Ui.card(this)
        toolsCard.addView(Ui.section(this, "CHAPTER TOOLS (AI)"))
        val toolsRow = Ui.row(this)
        toolsRow.addView(Ui.chip(this, "➕ ဆက်ရေး", false) { chapterTool("continue") })
        toolsRow.addView(Ui.chip(this, "🔄 ပြန်ရေး", false) { chapterTool("rewrite") })
        toolsRow.addView(Ui.chip(this, "📖 အရှည်တိုး", false) { chapterTool("expand") })
        toolsCard.addView(toolsRow)
        root.addView(toolsCard)

        root.addView(Ui.goldBtn(this, "🔊 AUDIOBOOK ထုတ်မယ်") {
            val i = Intent(this, AudioActivity::class.java)
            i.putExtra("id", novelId)
            startActivity(i)
        })

        val exportRow = Ui.row(this)
        exportRow.addView(Ui.chip(this, "💾 EXPORT TXT", false) { exportTxt() })
        exportRow.addView(Ui.chip(this, "📤 SHARE TXT", false) { shareTxt() })
        root.addView(exportRow)

        refresh()
    }

    private fun nav(delta: Int) {
        val newIdx = current + delta
        if (newIdx < 1 || newIdx > count) {
            Toast.makeText(this, "အခန်း ထပ်မရှိပါ", Toast.LENGTH_SHORT).show()
            return
        }
        current = newIdx
        refresh()
    }

    private fun refresh() {
        val meta = NovelStore.meta(dir)
        count = meta.optInt("chapterCount", 0)
        if (current < 1) current = if (count > 0) 1 else 0
        if (current > count) current = count
        titleTv.text = meta.optString("title", "")
        chapterLabel.text = if (count > 0) "အခန်း " + current + " / " + count else "အခန်း မရှိပါ"
        bodyTv.text = if (current >= 1) NovelStore.readChapter(dir, current) else "ဒီလိုင်းအောက်မှာ tools တွေသုံးပြီး အခန်းရေးနိုင်ပါသည်။"
    }

    private fun chapterTool(tool: String) {
        if (current < 1 && tool != "continue") {
            Toast.makeText(this, "အရင်ဆုံး အခန်းရှိမှ ဖြစ်ပါမည်", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setMessage("AI ရေးနေပါသည်... ခဏစောင့်ပါ")
            .setCancelable(false)
            .show()
        thread(start = true) {
            try {
                val meta = NovelStore.meta(dir)
                val genre = meta.optString("genre", "Romance")
                val outline = NovelStore.outline(dir)
                val chaptersJson = outline?.optJSONArray("chapters")
                when (tool) {
                    "continue" -> {
                        val n = count + 1
                        var brief = "ဇာတ်လမ်းကို ဆက်လက် တိုးချဲ့ရေးပါ"
                        var chTitle = "အခန်း " + n
                        if (chaptersJson != null && n - 1 < chaptersJson.length()) {
                            val o = chaptersJson.getJSONObject(n - 1)
                            brief = o.optString("brief", brief)
                            chTitle = o.optString("title", chTitle)
                        }
                        val prevTail = if (count > 0) tailOf(count) else ""
                        val raw = AiClient.generate(this, NovelEngine.chapterPrompt("", genre, brief, chTitle, n, n, "စာပိုဒ် ၈ မှ ၁၂ ပိုဒ်", prevTail), false)
                        Prefs.addUsage(this)
                        val text = NovelEngine.cleanText(raw)
                        NovelStore.saveChapter(dir, n, text)
                        NovelStore.saveMeta(dir, JSONObject().put("chapterCount", n))
                        runOnUiThread {
                            current = n
                            refresh()
                            Toast.makeText(this, "အခန်း " + n + " ထပ်ရေးပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "rewrite" -> {
                        val chText = NovelStore.readChapter(dir, current)
                        val prevTail = if (current > 1) tailOf(current - 1) else ""
                        val raw = AiClient.generate(this, NovelEngine.rewritePrompt(prevTail, chText), false)
                        Prefs.addUsage(this)
                        NovelStore.saveChapter(dir, current, NovelEngine.cleanText(raw))
                        runOnUiThread {
                            refresh()
                            Toast.makeText(this, "ပြန်ရေးပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "expand" -> {
                        val chText = NovelStore.readChapter(dir, current)
                        val raw = AiClient.generate(this, NovelEngine.expandPrompt(chText, "စာပိုဒ် ၁၄ မှ ၂၀ ပိုဒ်"), false)
                        Prefs.addUsage(this)
                        NovelStore.saveChapter(dir, current, NovelEngine.cleanText(raw))
                        runOnUiThread {
                            refresh()
                            Toast.makeText(this, "အရှည်တိုးပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.log(this, "chapterTool error: " + (e.message ?: ""))
                runOnUiThread {
                    Toast.makeText(this, "အမှား: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { dialog.dismiss() }
            }
        }
    }

    private fun tailOf(idx: Int): String {
        val t = NovelStore.readChapter(dir, idx)
        return if (t.length > 400) t.substring(t.length - 400) else t
    }

    private fun exportTxt() {
        try {
            val meta = NovelStore.meta(dir)
            val name = (meta.optString("title", "novel") + ".txt").replace(" ", "_")
            val path = DownloadsUtil.saveText(this, name, NovelStore.fullText(dir))
            Toast.makeText(this, "သိမ်းပြီးပါပြီ: " + path, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export မအောင်မြင်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareTxt() {
        try {
            val meta = NovelStore.meta(dir)
            val f = java.io.File(cacheDir, "novel_share.txt")
            f.writeText(NovelStore.fullText(dir))
            DownloadsUtil.shareFile(this, f, "text/plain")
        } catch (e: Exception) {
            Toast.makeText(this, "Share မအောင်မြင်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }
}
