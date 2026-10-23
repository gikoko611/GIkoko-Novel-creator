package com.gikoko.novelcreator

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.net.AiClient
import com.gikoko.novelcreator.novel.NovelEngine
import com.gikoko.novelcreator.novel.NovelStore
import com.gikoko.novelcreator.util.DownloadsUtil
import com.gikoko.novelcreator.util.EpubUtil
import com.gikoko.novelcreator.util.PdfExporter
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class NovelViewActivity : AppCompatActivity() {

    private var novelId = 0L
    private lateinit var dir: File
    private var current = 1
    private var count = 0

    private lateinit var titleTv: TextView
    private lateinit var chapterLabel: TextView
    private lateinit var bodyTv: TextView
    private lateinit var coverWrap: LinearLayout

    @Volatile
    private var autoCancelled = false

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

        val sub = Ui.label(this, genre + " • " + (if (mode == "outline") "OUTLINE MODE" else "DIRECT MODE") + " • အခန်း " + count + " ခန်း")
        root.addView(sub)

        coverWrap = LinearLayout(this)
        coverWrap.orientation = LinearLayout.VERTICAL
        coverWrap.gravity = Gravity.CENTER
        root.addView(coverWrap)
        refreshCover()

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
        val coverRow = Ui.row(this)
        coverRow.addView(Ui.chip(this, "🎨 AI COVER ဆွဲမယ်", false) { generateCover() })
        toolsCard.addView(coverRow)
        root.addView(toolsCard)

        root.addView(Ui.goldBtn(this, "♾ AUTO ဆက်ရေးမယ် (အခန်းအသစ်တွေ တန်းရေး)") { autoContinue() })

        root.addView(Ui.goldBtn(this, "🔊 AUDIOBOOK ထုတ်မယ်") {
            val i = Intent(this, AudioActivity::class.java)
            i.putExtra("id", novelId)
            startActivity(i)
        })

        val exportCard = Ui.card(this)
        exportCard.addView(Ui.section(this, "EXPORT"))
        val exportRow = Ui.row(this)
        exportRow.addView(Ui.chip(this, "💾 TXT", false) { exportTxt() })
        exportRow.addView(Ui.chip(this, "📤 SHARE TXT", false) { shareTxt() })
        exportRow.addView(Ui.chip(this, "📗 EPUB", false) { exportEpub() })
        exportRow.addView(Ui.chip(this, "📄 PDF", false) { exportPdf() })
        exportCard.addView(exportRow)
        root.addView(exportCard)

        refresh()
    }

    private fun refreshCover() {
        coverWrap.removeAllViews()
        val cover = File(dir, "cover.jpg")
        if (cover.exists()) {
            try {
                val bmp = BitmapFactory.decodeFile(cover.absolutePath)
                if (bmp != null) {
                    val iv = ImageView(this)
                    iv.setImageBitmap(bmp)
                    iv.adjustViewBounds = true
                    coverWrap.addView(iv, LinearLayout.LayoutParams(Ui.dp(this, 220f), LinearLayout.LayoutParams.WRAP_CONTENT))
                }
            } catch (e: Exception) {
            }
        }
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
                val template = meta.optString("template", "")
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
                        val raw = AiClient.generate(this, NovelEngine.chapterPrompt("", genre, brief, chTitle, n, n, "စာပိုဒ် ၈ မှ ၁၂ ပိုဒ်", prevTail, template), false)
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

    private fun autoContinue() {
        if (count < 1) {
            Toast.makeText(this, "အရင်ဆုံး အခန်းတစ်ခန်းရှိမှ ဆက်ရေးနိုင်ပါသည်", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf("၃ ခန်း", "၅ ခန်း", "၈ ခန်း")
        AlertDialog.Builder(this)
            .setTitle("ဘယ်နှစ်ခန်း ဆက်ရေးမလဲ")
            .setItems(options) { _, which ->
                val extra = if (which == 0) 3 else if (which == 1) 5 else 8
                runAuto(extra)
            }
            .setNegativeButton("မလုပ်ပါနဲ့", null)
            .show()
    }

    private fun runAuto(extra: Int) {
        autoCancelled = false
        val dialog = AlertDialog.Builder(this)
            .setMessage("AI ဆက်ရေးနေပါသည်... (" + extra + " ခန်း)\nခဏစောင့်ပါ")
            .setCancelable(false)
            .setNegativeButton("ရပ်တန့်") { _, _ -> autoCancelled = true }
            .show()
        thread(start = true) {
            try {
                val meta = NovelStore.meta(dir)
                val genre = meta.optString("genre", "Romance")
                val template = meta.optString("template", "")
                val outline = NovelStore.outline(dir)
                val chaptersJson = outline?.optJSONArray("chapters")
                var done = 0
                for (k in 1..extra) {
                    if (autoCancelled) break
                    val m2 = NovelStore.meta(dir)
                    val n = m2.optInt("chapterCount", 0) + 1
                    var brief = "ဇာတ်လမ်းကို ဆက်လက် တိုးချဲ့ရေးပါ"
                    var chTitle = "အခန်း " + n
                    if (chaptersJson != null && n - 1 < chaptersJson.length()) {
                        val o = chaptersJson.getJSONObject(n - 1)
                        brief = o.optString("brief", brief)
                        chTitle = o.optString("title", chTitle)
                    }
                    val prevTail = if (n > 1) tailOf(n - 1) else ""
                    val raw = AiClient.generate(this, NovelEngine.chapterPrompt("", genre, brief, chTitle, n, n, "စာပိုဒ် ၈ မှ ၁၂ ပိုဒ်", prevTail, template), false)
                    Prefs.addUsage(this)
                    NovelStore.saveChapter(dir, n, NovelEngine.cleanText(raw))
                    NovelStore.saveMeta(dir, JSONObject().put("chapterCount", n))
                    done++
                }
                runOnUiThread {
                    refresh()
                    Toast.makeText(this, "အခန်း " + done + " ခန်း ဆက်ရေးပြီးပါပြီ ✓", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "autoContinue error: " + (e.message ?: ""))
                runOnUiThread {
                    Toast.makeText(this, "အမှား: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { dialog.dismiss() }
            }
        }
    }

    private fun generateCover() {
        val meta = NovelStore.meta(dir)
        val title = meta.optString("title", "")
        val genre = meta.optString("genre", "")
        val dialog = AlertDialog.Builder(this)
            .setMessage("AI က cover ဆွဲနေပါသည်...\nခဏစောင့်ပါ (Gemini provider နဲ့ပဲ အလုပ်လုပ်ပါသည်)")
            .setCancelable(false)
            .show()
        thread(start = true) {
            try {
                val bytes = AiClient.generateCover(this, title, genre)
                File(dir, "cover.jpg").writeBytes(bytes)
                runOnUiThread {
                    refreshCover()
                    Toast.makeText(this, "Cover ဆွဲပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "cover error: " + (e.message ?: ""))
                runOnUiThread {
                    Toast.makeText(this, "Cover မဆွဲနိုင်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
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
            val f = File(cacheDir, "novel_share.txt")
            f.writeText(NovelStore.fullText(dir))
            DownloadsUtil.shareFile(this, f, "text/plain")
        } catch (e: Exception) {
            Toast.makeText(this, "Share မအောင်မြင်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun exportEpub() {
        val dialog = AlertDialog.Builder(this)
            .setMessage("EPUB ထုတ်နေပါသည်... ခဏစောင့်ပါ")
            .setCancelable(false)
            .show()
        thread(start = true) {
            try {
                val f = EpubUtil.export(this, novelId)
                val path = DownloadsUtil.saveBytes(this, f.name, "application/epub+zip", f.readBytes())
                runOnUiThread {
                    Toast.makeText(this, "EPUB သိမ်းပြီးပါပြီ: " + path, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "epub error: " + (e.message ?: ""))
                runOnUiThread {
                    Toast.makeText(this, "EPUB မအောင်မြင်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { dialog.dismiss() }
            }
        }
    }

    private fun exportPdf() {
        val dialog = AlertDialog.Builder(this)
            .setMessage("PDF ထုတ်နေပါသည်... ခဏစောင့်ပါ")
            .setCancelable(false)
            .show()
        thread(start = true) {
            try {
                val f = PdfExporter.export(this, novelId)
                val path = DownloadsUtil.saveBytes(this, f.name, "application/pdf", f.readBytes())
                runOnUiThread {
                    Toast.makeText(this, "PDF သိမ်းပြီးပါပြီ: " + path, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "pdf error: " + (e.message ?: ""))
                runOnUiThread {
                    Toast.makeText(this, "PDF မအောင်မြင်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { dialog.dismiss() }
            }
        }
    }
}
