package com.gikoko.novelcreator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.net.EdgeTtsClient
import com.gikoko.novelcreator.novel.NovelEngine

class StudioActivity : AppCompatActivity() {

    private var genreIdx = 0
    private var modeIdx = 0
    private var chaptersIdx = 0
    private var lenIdx = 1
    private var voiceIdx = 0
    private var templateIdx = -1

    private lateinit var genreRow: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var chaptersRow: LinearLayout
    private lateinit var lenRow: LinearLayout
    private lateinit var voiceRow: LinearLayout
    private lateinit var templateRow: LinearLayout
    private lateinit var ideaInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        val root = findViewById<LinearLayout>(R.id.rootContainer)

        root.addView(Ui.backBtn(this) { finish() })
        val titleLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        titleLp.topMargin = Ui.dp(this, 10f)
        val t = Ui.title(this, "✍️ NOVEL STUDIO")
        t.layoutParams = titleLp
        root.addView(t)

        root.addView(section("ဇာတ်အမျိုးအစား ရွေးပါ"))
        val hsv = HorizontalScrollView(this)
        hsv.isHorizontalScrollBarEnabled = false
        genreRow = Ui.row(this)
        hsv.addView(genreRow)
        root.addView(hsv)
        buildChips()

        root.addView(section("ဇာတ်လမ်းအိုင်ဒီယာ (စိတ်ကူးယဉ် အကြောင်းအရာ)"))
        ideaInput = Ui.input(this, "ဥပမာ - ရန်ကုန်မြို့ရဲ့ မလင်းသော ညတစ်ညမှာ ပင်ပင်းပင်လေးက လျှို့ဝှက်တဲ့ စာအုပ်တစ်အုပ် တွေ့ရှိခဲ့သည်...", "", true)
        root.addView(ideaInput)

        root.addView(section("PROMPT TEMPLATE (စာရေးရမှုဟန် မှာကြားချက်)"))
        val thsv = HorizontalScrollView(this)
        thsv.isHorizontalScrollBarEnabled = false
        templateRow = Ui.row(this)
        thsv.addView(templateRow)
        root.addView(thsv)

        root.addView(section("ရေးပုံစံ"))
        modeRow = Ui.row(this)
        root.addView(modeRow)

        root.addView(section("အခန်းအရေအတွက်"))
        chaptersRow = Ui.row(this)
        root.addView(chaptersRow)

        root.addView(section("အခန်းတစ်ခန်းရဲ့ အရှည်"))
        lenRow = Ui.row(this)
        root.addView(lenRow)

        root.addView(section("Audiobook အသံ"))
        voiceRow = Ui.row(this)
        root.addView(voiceRow)

        val btnLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        btnLp.topMargin = Ui.dp(this, 18f)
        val createBtn = Ui.goldBtn(this, "🚀 ဝတ္ထု ရေးမယ်") { createNovel() }
        createBtn.layoutParams = btnLp
        root.addView(createBtn)

        refreshChips()
        refreshTemplateChips()
    }

    private fun section(text: String): TextView {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Ui.dp(this, 16f)
        lp.bottomMargin = Ui.dp(this, 8f)
        val tv = Ui.section(this, text)
        tv.layoutParams = lp
        return tv
    }

    private fun buildChips() {
        genreRow.addView(Ui.chip(this, "placeholder", false) { })
    }

    private fun refreshChips() {
        refreshRow(genreRow, NovelEngine.GENRES.toList(), genreIdx) { idx -> genreIdx = idx; refreshChips() }
        val modes = listOf("🧩 OUTLINE FIRST (ဇာတ်ကောင်+plot အရင်)", "⚡ DIRECT (တိုက်ရိုက်ရေး)")
        refreshRow(modeRow, modes, modeIdx) { idx -> modeIdx = idx; refreshChips() }
        val chapters = NovelEngine.CHAPTER_OPTIONS.map { it.toString() + " ခန်း" }
        refreshRow(chaptersRow, chapters, chaptersIdx) { idx -> chaptersIdx = idx; refreshChips() }
        refreshRow(lenRow, NovelEngine.LENGTH_LABELS.toList(), lenIdx) { idx -> lenIdx = idx; refreshChips() }
        val voices = listOf("👩 မိန်းကလေး (Nilar)", "👨 ယောက်ျားလေး (Thiha)")
        refreshRow(voiceRow, voices, voiceIdx) { idx -> voiceIdx = idx; refreshChips() }
    }

    private fun refreshRow(row: LinearLayout, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        row.removeAllViews()
        for (i in items.indices) {
            row.addView(Ui.chip(this, items[i], i == selected) { onPick(i) })
        }
    }

    private fun refreshTemplateChips() {
        templateRow.removeAllViews()
        val list = Prefs.templates(this)
        for (i in list.indices) {
            val chip = Ui.chip(this, list[i], templateIdx == i) {
                templateIdx = if (templateIdx == i) -1 else i
                refreshTemplateChips()
            }
            chip.setOnLongClickListener {
                confirmDeleteTemplate(i)
                true
            }
            templateRow.addView(chip)
        }
        templateRow.addView(Ui.chip(this, "＋ ထည့်မယ်", false) { addTemplateDialog() })
    }

    private fun addTemplateDialog() {
        val input = Ui.input(this, "ဥပမာ - ရုံးသုံးစကားလုံးတွေနဲ့ တရားဝင် စာပေါင်းရေးပါ", "", true)
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(this, 16)
        wrap.setPadding(pad, pad, pad, pad)
        wrap.addView(input)
        AlertDialog.Builder(this)
            .setTitle("PROMPT TEMPLATE အသစ်")
            .setView(wrap)
            .setPositiveButton("သိမ်းမယ်") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    Prefs.addTemplate(this, t)
                    templateIdx = Prefs.templates(this).size - 1
                    refreshTemplateChips()
                } else {
                    Toast.makeText(this, "စာသား ဗလာဖြစ်နေပါတယ်", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("မသိမ်းပါနဲ့", null)
            .show()
    }

    private fun confirmDeleteTemplate(idx: Int) {
        val list = Prefs.templates(this)
        if (idx !in list.indices) return
        AlertDialog.Builder(this)
            .setTitle("ဖျက်မလား")
            .setMessage("Template:\n" + list[idx] + "\nကို ဖျက်မှာလား")
            .setPositiveButton("ဖျက်မယ်") { _, _ ->
                Prefs.removeTemplate(this, idx)
                if (templateIdx == idx) {
                    templateIdx = -1
                } else if (templateIdx > idx) {
                    templateIdx--
                }
                refreshTemplateChips()
            }
            .setNegativeButton("မဖျက်ပါနဲ့", null)
            .show()
    }

    private fun createNovel() {
        if (ideaInput.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "ဇာတ်လမ်းအိုင်ဒီယာ အရင်ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        if (Prefs.apiKey(this).isEmpty() || Prefs.model(this).isEmpty()) {
            Toast.makeText(this, "API key နဲ့ model အရင် ထည့်/ရွေးပါ", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ProviderActivity::class.java))
            return
        }
        val voice = if (voiceIdx == 0) EdgeTtsClient.VOICE_MY_FEMALE else EdgeTtsClient.VOICE_MY_MALE
        val i = Intent(this, ProcessingActivity::class.java)
        i.putExtra("genre", NovelEngine.GENRES[genreIdx])
        i.putExtra("idea", ideaInput.text.toString().trim())
        i.putExtra("mode", if (modeIdx == 0) "outline" else "direct")
        i.putExtra("chapters", NovelEngine.CHAPTER_OPTIONS[chaptersIdx])
        i.putExtra("lenIdx", lenIdx)
        i.putExtra("voice", voice)
        val templates = Prefs.templates(this)
        if (templateIdx in templates.indices) {
            i.putExtra("template", templates[templateIdx])
        }
        startActivity(i)
    }
}
