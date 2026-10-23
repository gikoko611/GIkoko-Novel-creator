package com.gikoko.novelcreator

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.net.EdgeTtsClient
import com.gikoko.novelcreator.novel.NovelStore
import com.gikoko.novelcreator.util.DownloadsUtil
import kotlin.concurrent.thread

class AudioActivity : AppCompatActivity() {

    private var novelId = 0L
    private lateinit var dir: java.io.File
    private var count = 0
    private var current = 1
    private var speed = 1.0f
    private var sleepMinutes = 0

    @Volatile
    private var genCancelled = false

    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var progressView: NeonProgressView
    private lateinit var genStatus: TextView
    private lateinit var chapterLabel: TextView
    private lateinit var playBtn: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var speedRow: LinearLayout
    private lateinit var sleepRow: LinearLayout

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
        updateChapterLabel()
        handler.postDelayed(seekUpdater, 500)
    }

    private fun updateChapterLabel() {
        chapterLabel.text = if (count > 0) "အခန်း " + current + " / " + count else ""
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
            m.playbackParams = m.playbackParams.setPlaybackSpeed(speed)
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
        val voice = NovelStore.meta(dir).optString("voice", EdgeTtsClient.VOICE_MY_FEMALE)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Audiobook ထုတ်နေပါသည်...\nခဏစောင့်ပါ (တစ်ခန်းလျှင် ချက်ချင်းတွေ့ရမည်)")
            .setCancelable(false)
            .setNegativeButton("ရပ်တန့်") { _, _ -> genCancelled = true }
            .show()
        thread(start = true) {
            try {
                for (n in 1..count) {
                    if (genCancelled) throw IllegalStateException("ရပ်တန့်ခဲ့ပါသည်")
                    val f = NovelStore.audioFile(dir, n)
                    if (f.exists()) {
                        runOnUiThread {
                            progressView.setProgress(n * 100 / count)
                            genStatus.text = "အခန်း " + n + " - ရှိပြီးသား ဖြစ်သည်"
                        }
                        continue
                    }
                    runOnUiThread { genStatus.text = "အခန်း " + n + " ကို အသံသွင်းနေပါသည်..." }
                    val text = NovelStore.readChapter(dir, n)
                    val mp3 = EdgeTtsClient.synthesizeMp3(text, voice, 1.0)
                    f.parentFile?.mkdirs()
                    f.writeBytes(mp3)
                    runOnUiThread { progressView.setProgress(n * 100 / count) }
                }
                runOnUiThread {
                    genStatus.text = "Audiobook ပြီးပါပြီ ✓"
                    Toast.makeText(this, "Audiobook ထုတ်ပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "audiobook error: " + (e.message ?: ""))
                runOnUiThread { genStatus.text = "ရပ်တန့်/အမှား: " + (e.message ?: "") }
            } finally {
                runOnUiThread { dialog.dismiss() }
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
            mp?.playbackParams = mp!!.playbackParams.setPlaybackSpeed(speed)
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
