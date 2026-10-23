package com.gikoko.novelcreator

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.novel.NovelStore
import com.gikoko.novelcreator.util.BackupUtil
import com.gikoko.novelcreator.util.DownloadsUtil
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

class LibraryActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) doRestore(uri)
    }

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
        while (root.childCount > 2) {
            root.removeViewAt(root.childCount - 1)
        }
        val novels = NovelStore.list(this)
        if (novels.isEmpty()) {
            val empty = Ui.statusLine(this, "ဝတ္ထု မရှိသေးပါ - NOVEL STUDIO ကနေ အရင်ရေးပါ")
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Ui.dp(this, 40f)
            empty.layoutParams = lp
            root.addView(empty)
            addBackupSection()
            return
        }
        for (n in novels) {
            val card = Ui.card(this, true)
            val row = Ui.row(this)
            row.gravity = Gravity.CENTER_VERTICAL
            val cover = File(n.dir, "cover.jpg")
            if (cover.exists()) {
                try {
                    val bmp = BitmapFactory.decodeFile(cover.absolutePath)
                    if (bmp != null) {
                        val iv = ImageView(this)
                        iv.setImageBitmap(bmp)
                        row.addView(iv, LinearLayout.LayoutParams(Ui.dp(this, 56f), Ui.dp(this, 72f)))
                    }
                } catch (e: Exception) {
                }
            }
            val mid = LinearLayout(this)
            mid.orientation = LinearLayout.VERTICAL
            val title = TextView(this)
            title.text = n.title
            title.textSize = 17f
            title.typeface = android.graphics.Typeface.DEFAULT_BOLD
            title.setTextColor(Ui.GOLD)
            mid.addView(title)
            val sub = Ui.label(this, n.genre + " • " + (if (n.mode == "outline") "OUTLINE" else "DIRECT") + " • အခန်း " + n.chapterCount + " ခန်း • " + n.created)
            mid.addView(sub)
            val midLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            midLp.leftMargin = Ui.dp(this, 12f)
            mid.layoutParams = midLp
            row.addView(mid)
            card.addView(row)
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
            root.addView(card)
        }
        addBackupSection()
    }

    private fun addBackupSection() {
        val card = Ui.card(this)
        card.addView(Ui.section(this, "BACKUP & RESTORE"))
        val row = Ui.row(this)
        row.addView(Ui.chip(this, "💾 BACKUP", false) { doBackup() })
        row.addView(Ui.chip(this, "📂 RESTORE", false) { openBackup.launch(arrayOf("application/zip")) }))
        card.addView(row)
        card.addView(Ui.label(this, "ဝတ္ထုအားလုံးကို ZIP file အဖြစ် Download folder မှာ သိမ်း/ပြန်ထည့်နိုင်ပါသည်"))
        root.addView(card)
    }

    private fun doBackup() {
        val dialog = AlertDialog.Builder(this)
            .setMessage("Backup ဖွင့်နေပါသည်... ခဏစောင့်ပါ")
            .setCancelable(false)
            .show()
        thread(start = true) {
            try {
                val f = BackupUtil.backup(this)
                val path = DownloadsUtil.saveBytes(this, f.name, "application/zip", f.readBytes())
                runOnUiThread {
                    Toast.makeText(this, "Backup သိမ်းပြီးပါပြီ: " + path, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "backup error: " + (e.message ?: ""))
                runOnUiThread {
                    Toast.makeText(this, "Backup မအောင်မြင်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { dialog.dismiss() }
            }
        }
    }

    private fun doRestore(uri: Uri) {
        val dialog = AlertDialog.Builder(this)
            .setMessage("Restore လုပ်နေပါသည်... ခဏစောင့်ပါ")
            .setCancelable(false)
            .show()
        thread(start = true) {
            try {
                val ins = contentResolver.openInputStream(uri) ?: throw IOException("ဖွင့်မရပါ")
                val n = BackupUtil.restore(this, ins)
                runOnUiThread {
                    rebuild()
                    Toast.makeText(this, "ဝတ္ထု " + n + " ပုဒ် ပြန်ထည့်ပြီးပါပြီ", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "restore error: " + (e.message ?: ""))
                runOnUiThread {
                    Toast.makeText(this, "Restore မအောင်မြင်ပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { dialog.dismiss() }
            }
        }
    }
}
