package com.gikoko.novelcreator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.net.EdgeTtsClient
import com.gikoko.novelcreator.novel.NovelStore
import com.gikoko.novelcreator.util.DualVoiceUtil
import com.gikoko.novelcreator.util.DownloadsUtil
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class AudioActivity : AppCompatActivity() {

    private var novelId = 0L
    private lateinit var dir: File
    private var count = 0
    private var current = 1
    private var speed = 1.0f
    private var sleepMinutes = 0

    @Volatile
    private var genCancelled = false

    private val failedChapters = ArrayList<Int>()

    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var progressView: NeonProgressView
    private lateinit var genStatus: TextView
    private lateinit var chapterLabel: TextView
    private lateinit var playBtn: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var speedRow: LinearLayout
    private lateinit var sleepRow: LinearLayout
    private lateinit var retryRow: LinearLayout
    private lateinit var dualBtn: TextView

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startBackground()
    }

    private val seekUpdater = object : Runnable {
        override fun run() {
            val m = mp
            if (m != null && m.isPlaying) {
                val d = m.duration
                if (d > 0) seekBar.progress = m.currentPosition * 1000 / d
            }
            handler.postDelayed(this, 500)
        }
    }

    private val sleepRunnable = Runnable {
        stopPlayer()
        sleepMinutes = 0
        refreshSleepChips()
        Toast.makeText(this, "Sleep timer ပြီးပါပြီ - ရပ်တန့်လိုက်ပါသည်", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        novelId = intent.getLongExtra("id", 0L)
        dir = NovelStore.dir(this, novelId)
        if (!dir.exists()) {
            finish()
            return
        }
        val meta = NovelStore.meta(dir)
        count = meta.optInt("chapterCount", 0)

        val root = findViewById<LinearLayout>(R.id.rootContainer)
        root.addView(Ui.backBtn(this) { finish() })
        root.addView(Ui.title(this, "🔊 AUDIOBOOK STUDIO"))
        root.addView(Ui.label(this, meta.optString("title", "") + " • အသံ: " + meta.optString("voice", "")))

        progressView = NeonProgressView(this)
        root.addView(progressView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 22f)))

        genStatus = Ui.statusLine(this, if (count > 0) "အခန်း " + count + " ခန်း ရှိပါသည်" else "ဝတ္ထုမှာ အခန်း မရှိသေးပါ")
        root.addView(genStatus)

        root.addView(Ui.goldBtn(this, "🎧 AUDIOBOOK ထုတ်မယ်") { generateAudio() })

        val proCard = Ui.card(this, true)
        proCard.addView(Ui.section(this, "PRO FEATURES"))
        val dualRow = Ui.row(this)
        dualBtn = Ui.chip(this, dualLabel(), dualEnabled()) { toggleDual() }
        dualRow.addView(dualBtn)
        proCard.addView(dualRow)
        val proRow = Ui.row(this)
        proRow.addView(Ui.chip(this, "🌙 BACKGROUND PLAYER", false) { startBackgroundChecked() })
        proRow.addView(Ui.chip(this, "💾 FULL AUDIOBOOK တစ်ဖိုင်တည်း", false) { mergeAll() })
        proCard.addView(proRow)
        retryRow = Ui.row(this)
        proCard.addView(retryRow)
        root.addView(proCard)

        val playerCard = Ui.card(this, true)
        playerCard.addView(Ui.section(this, "PLAYER"))
        chapterLabel = Ui.body(this, "")
        chapterLabel.gravity = Gravity.CENTER
        chapterLabel.setTextColor(Ui.GOLD)
        playerCard.addView(chapterLabel)

        seekBar = SeekBar(this)
        seekBar.max = 1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val m = mp
                if (fromUser && m != null) {
                    val d = m.duration
                    if (d > 0) m.seekTo(p * d / 1000)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        playerCard.addView(seekBar)

        val ctrlRow = Ui.row(this)
        ctrlRow.gravity = Gravity.CENTER
        val prev = Ui.chip(this, "⏮", false) { playChapter(current - 1) }
        playBtn = Ui.chip(this, "▶ PLAY", false) { togglePlay() }
        val next = Ui.chip(this, "⏭", false) { playChapter(current + 1) }
        val prevLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        prevLp.rightMargin = Ui.dp(this, 16f)
        val playLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        playLp.rightMargin = Ui.dp(this, 16f)
        prev.layoutParams = prevLp
        playBtn.layoutParams = playLp
        ctrlRow.addView(prev)
        ctrlRow.addView(playBtn)
        ctrlRow.addView(next)
        playerCard.addView(ctrlRow)

        playerCard.addView(Ui.label(this, "အရှိန် (SPEED)"))
        speedRow = Ui.row(this)
        playerCard.addView(speedRow)
        playerCard.addView(Ui.label(this, "SLEEP TIMER"))
        sleepRow = Ui.row(this)
        playerCard.addView(sleepRow)

        val shareRow = Ui.row(this)
        shareRow.addView(Ui.chip(this, "📤 SHARE ဒီအခန်း", false) { shareCurrent() })
        playerCard.addView(shareRow)

        root.addView(playerCard)
        refreshSpeedChips()
        refreshSleepChips()
        refreshRetryRow()
        updateChapterLabel()
        handler.postDelayed(seekUpdater, 500)
    }

    private fun updateChapterLabel() {
        chapterLabel.text = if (count > 0) "အခန်း " + current + " / " + count else ""
    }

    private fun dualEnabled(): Boolean = NovelStore.meta(dir).optBoolean("dualVoice", false)

    private fun dualLabel(): String {
        return if (dualEnabled()) "🎚 အသံနှစ်မျိုး (ဇာတ်စကား) ON" else "🎚 အသံနှစ်မျိုး (ဇာတ်စကား) OFF"
    }

    private fun toggleDual() {
        NovelStore.saveMeta(dir, JSONObject().put("dualVoice", !dualEnabled()))
        dualBtn.text = dualLabel()
        dualBtn.setTextColor(if (dualEnabled()) Ui.GOLD else Ui.DIM)
        val msg = if (dualEnabled()) {
            "ဇာတ်စကားတွေကို အသံနှစ်မျိုးနဲ့ ဖတ်ပါမယ် (အသံသွင်းပြီးတာတွေ အသစ်ပြန်ထုတ်မှ ပြောင်းမယ်)"
        } else {
            "အသံတစ်မျိုးတည်း ပြန်ဖတ်ပါမယ်"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun refreshRetryRow() {
        retryRow.removeAllViews()
        if (failedChapters.isNotEmpty()) {
            retryRow.addView(Ui.chip(this, "🔄 RETRY - ပျက်တဲ့အခန်း " + failedChapters.size + " ခန်း", true) { retryFailed() })
        }
    }

    private fun startBackgroundChecked() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startBackground()
        }
    }

    private fun startBackground() {
        if (count < 1) {
            Toast.makeText(this, "ဝတ္ထုမှာ အခန်း မရှိသေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val f = NovelStore.audioFile(dir, current)
        if (!f.exists()) {
            Toast.makeText(this, "အရင်ဆုံး AUDIOBOOK ထုတ်မယ် နှိပ်ပြီးမှ background ဖွင့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        PlaybackService.start(this, novelId, current)
        Toast.makeText(this, "Background player ဖွင့်ပြီးပါပြီ - screen ပိတ်လည်း ဆက်ဖွင့်နေပါမယ်", Toast.LENGTH_SHORT).show()
    }

    private fun synth(text: String, voice: String): ByteArray {
        return if (dualEnabled()) {
            DualVoiceUtil.synthesize(text, voice, DualVoiceUtil.altVoiceFor(voice), 1.0)
        } else {
            EdgeTtsClient.synthesizeMp3(text, voice, 1.0)
        }
    }

    private fun refreshSpeedChips() {
        speedRow.removeAllViews()
        val speeds = listOf("0.75x", "1x", "1.25x", "1.5x")
        val vals = listOf(0.75f, 1.0f, 1.25f, 1.5f)
        for (i in speeds.indices) {
            speedRow.addView(Ui.chip(this, speeds[i], speed == vals[i]) { pickSpeed(vals[i]) })
        }
    }

    private fun refreshSleepChips() {
        sleepRow.removeAllViews()
        val labels = listOf("OFF", "15m", "30m", "60m")
        val mins = listOf(0, 15, 30, 60)
        for (i in labels.indices) {
            sleepRow.addView(Ui.chip(this, labels[i], sleepMinutes == mins[i]) { pickSleep(mins[i]) })
        }
    }

    private fun pickSpeed(v: Float) {
        speed = v
        val m = mp
        if (m != null) {
            m.playbackParams = m.playbackParams.setSpeed(speed)
        }
        refreshSpeedChips()
    }

    private fun pickSleep(mins: Int) {
        handler.removeCallbacks(sleepRunnable)
        sleepMinutes = mins
        refreshSleepChips()
        if (mins > 0) {
            handler.postDelayed(sleepRunnable, (mins * 60 * 1000).toLong())
            Toast.makeText(this, "Sleep timer " + mins + " မိနစ် ချိန်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAudio() {
        if (count < 1) {
            Toast.makeText(this, "ဝတ္ထုမှာ အခန်း မရှိသေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        genCancelled = false
        failedChapters.clear()
        refreshRetryRow()
        val voice = NovelStore.meta(dir).optString("voice", EdgeTtsClient.VOICE_MY_FEMALE)
        val dialog = AlertDialog.Builder(this)
            .setMessage("Audiobook ထုတ်နေပါသည်...\nခဏစောင့်ပါ (တစ်ခန်းလျှင် ချက်ချင်းတွေ့ရမည်)")
            .setCancelable(false)
            .setNegativeButton("ရပ်တန့်") { _, _ -> genCancelled = true }
            .show()
        thread(start = true) {
            try {
                for (n in 1..count) {
                    if (genCancelled) break
                    val f = NovelStore.audioFile(dir, n)
                    if (f.exists()) {
                        runOnUiThread {
                            progressView.setProgress(n * 100 / count)
                            genStatus.text = "အခန်း " + n + " - ရှိပြီးသား ဖြစ်သည်"
                        }
                        continue
                    }
                    runOnUiThread { genStatus.text = "အခန်း " + n + " ကို အသံသွင်းနေပါသည်..." }
                    try {
                        val text = NovelStore.readChapter(dir, n)
                        val mp3 = synth(text, voice)
                        f.parentFile?.mkdirs()
                        f.writeBytes(mp3)
                    } catch (e: Exception) {
                        DebugLog.log(this, "ch " + n + " synth error: " + (e.message ?: ""))
                        failedChapters.add(n)
                    }
                    runOnUiThread { progressView.setProgress(n * 100 / count) }
                }
                if (failedChapters.isNotEmpty() && !genCancelled) {
                    runOnUiThread { genStatus.text = "ပျက်တဲ့အခန်းတွေ ပြန်ကြိုးစားနေပါသည်..." }
                    val retry = ArrayList(failedChapters)
                    failedChapters.clear()
                    for (n in retry) {
                        if (genCancelled) break
                        try {
                            val text = NovelStore.readChapter(dir, n)
                            val mp3 = synth(text, voice)
                            val f = NovelStore.audioFile(dir, n)
                            f.parentFile?.mkdirs()
                            f.writeBytes(mp3)
                        } catch (e: Exception) {
                            failedChapters.add(n)
                        }
                    }
                }
                val result = if (failedChapters.isEmpty()) {
                    if (genCancelled) "ရပ်တန့်ခဲ့ပါသည်" else "Audiobook ပြီးပါပြီ ✓"
                } else {
                    "အခန်းတချို့ ပျက်နေသေးသည်: " + failedChapters.joinToString(", ")
                }
                runOnUiThread {
                    genStatus.text = result
                    refreshRetryRow()
                    if (failedChapters.isEmpty()) Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "audiobook error: " + (e.message ?: ""))
                runOnUiThread { genStatus.text = "အမှား: " + (e.message ?: "") }
            } finally {
                runOnUiThread { dialog.dismiss() }
            }
        }
    }

    private fun retryFailed() {
        if (failedChapters.isEmpty()) return
        val list = ArrayList(failedChapters)
        genCancelled = false
        val voice = NovelStore.meta(dir).optString("voice", EdgeTtsClient.VOICE_MY_FEMALE)
        val dialog = AlertDialog.Builder(this)
            .setMessage("ပျက်တဲ့အခန်းတွေ ပြန်ကြိုးစားနေပါသည်...")
            .setCancelable(false)
            .setNegativeButton("ရပ်တန့်") { _, _ -> genCancelled = true }
            .show()
        thread(start = true) {
            val stillFailed = ArrayList<Int>()
            try {
                for (n in list) {
                    if (genCancelled) break
                    runOnUiThread { genStatus.text = "အခန်း " + n + " ပြန်ကြိုးစားနေပါသည်..." }
                    try {
                        val text = NovelStore.readChapter(dir, n)
                        val mp3 = synth(text, voice)
                        val f = NovelStore.audioFile(dir, n)
                        f.parentFile?.mkdirs()
                        f.writeBytes(mp3)
                    } catch (e: Exception) {
                        DebugLog.log(this, "ch " + n + " retry error: " + (e.message ?: ""))
                        stillFailed.add(n)
                    }
                }
            } finally {
                runOnUiThread { dialog.dismiss() }
            }
            failedChapters.clear()
            failedChapters.addAll(stillFailed)
            runOnUiThread {
                genStatus.text = if (stillFailed.isEmpty()) {
                    "ပြန်ကြိုးစားပြီးပါပြီ ✓"
                } else {
                    "နောက်တစ်ကြိမ်လည်း ပျက်နေသေး: " + stillFailed.joinToString(", ")
                }
                refreshRetryRow()
            }
        }
    }

    private fun mergeAll() {
        if (count < 1) {
            Toast.makeText(this, "ဝတ္ထုမှာ အခန်း မရှိသေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        var missing = 0
        for (n in 1..count) {
            if (!NovelStore.audioFile(dir, n).exists()) missing++
        }
        if (missing > 0) {
            Toast.makeText(this, "အခန်း " + missing + " ခုရဲ့ audio မရှိသေးပါ - အရင်ဆုံး AUDIOBOOK ထုတ်မယ် နှိပ်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val title = NovelStore.meta(dir).optString("title", "novel")
        genStatus.text = "Full audiobook စစ်ခွဲနေပါသည်..."
        thread(start = true) {
            try {
                val merged = File(cacheDir, "full_audiobook.mp3")
                FileOutputStream(merged).use { out ->
                    for (n in 1..count) {
                        val ch = NovelStore.audioFile(dir, n)
                        FileInputStream(ch).use { it.copyTo(out, 65536) }
                    }
                }
                val name = (title + " full audiobook.mp3").replace(" ", "_")
                val path = DownloadsUtil.saveBytes(this, name, "audio/mpeg", merged.readBytes())
                runOnUiThread {
                    genStatus.text = "Full audiobook ပြီးပါပြီ ✓"
                    AlertDialog.Builder(this)
                        .setTitle("✓ FULL AUDIOBOOK")
                        .setMessage("အခန်း " + count + " ခန်းကို MP3 တစ်ဖိုင်တည်း သိမ်းပြီးပါပြီ။\n" + path + "\n\nSHARE လုပ်မလား")
                        .setPositiveButton("SHARE") { _, _ ->
                            DownloadsUtil.shareFile(this, merged, "audio/mpeg")
                        }
                        .setNegativeButton("နောက်မှ", null)
                        .show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "merge error: " + (e.message ?: ""))
                runOnUiThread {
                    genStatus.text = "Full audiobook မအောင်မြင်ပါ"
                    Toast.makeText(this, "အမှား: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun togglePlay() {
        val m = mp
        if (m != null && m.isPlaying) {
            m.pause()
        } else if (m != null) {
            m.start()
        } else {
            playChapter(current)
        }
        updatePlayBtn()
    }

    private fun playChapter(n: Int) {
        if (n < 1 || n > count) {
            Toast.makeText(this, "အခန်း ထပ်မရှိပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val f = NovelStore.audioFile(dir, n)
        if (!f.exists()) {
            Toast.makeText(this, "ဒီအခန်းရဲ့ audio မရှိသေးပါ - AUDIOBOOK ထုတ်မယ် နှိပ်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            mp?.release()
            mp = MediaPlayer()
            mp?.setDataSource(f.absolutePath)
            mp?.prepare()
            mp?.setOnCompletionListener {
                if (current + 1 <= count) playChapter(current + 1) else stopPlayer()
            }
            mp?.playbackParams = mp!!.playbackParams.setSpeed(speed)
            mp?.start()
            current = n
            updateChapterLabel()
            updatePlayBtn()
        } catch (e: Exception) {
            DebugLog.log(this, "play error: " + (e.message ?: ""))
            Toast.makeText(this, "ဖွင့်မရပါ: " + (e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayer() {
        runOnUiThread {
            try {
                mp?.stop()
            } catch (e: Exception) {
            }
            mp?.release()
            mp = null
            updatePlayBtn()
        }
    }

    private fun updatePlayBtn() {
        val m = mp
        playBtn.text = if (m != null && m.isPlaying) "⏸ PAUSE" else "▶ PLAY"
    }

    private fun shareCurrent() {
        val f = NovelStore.audioFile(dir, current)
        if (!f.exists()) {
            Toast.makeText(this, "ဒီအခန်းရဲ့ audio မရှိသေးပါ", Toast.LENGTH_SHORT).show()
            return
        }
        DownloadsUtil.shareFile(this, f, "audio/mpeg")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        mp?.release()
        mp = null
    }
}
