package com.gikoko.novelcreator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.gikoko.novelcreator.novel.NovelStore
import java.io.File

class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "novel_playback"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.gikoko.novelcreator.action.START"
        const val ACTION_TOGGLE = "com.gikoko.novelcreator.action.TOGGLE"
        const val ACTION_NEXT = "com.gikoko.novelcreator.action.NEXT"
        const val ACTION_PREV = "com.gikoko.novelcreator.action.PREV"
        const val ACTION_STOP = "com.gikoko.novelcreator.action.STOP"

        fun start(ctx: Context, novelId: Long, chapter: Int) {
            val i = Intent(ctx, PlaybackService::class.java)
            i.action = ACTION_START
            i.putExtra("id", novelId)
            i.putExtra("chapter", chapter)
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }
    }

    private var mp: MediaPlayer? = null
    private var novelId = 0L
    private var dir: File? = null
    private var novelTitle = ""
    private var count = 0
    private var current = 1
    private var session: MediaSession? = null
    private var audioManager: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Audiobook Player", NotificationManager.IMPORTANCE_LOW)
            ch.description = "ဝတ္ထု audiobook background playback"
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        session = MediaSession(this, "NovelPlayback")
        session?.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { resume() }
            override fun onPause() { pause() }
            override fun onSkipToNext() { play(current + 1) }
            override fun onSkipToPrevious() { play(current - 1) }
            override fun onStop() { stopPlayback() }
        })
        session?.isActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                novelId = intent.getLongExtra("id", 0L)
                dir = NovelStore.dir(this, novelId)
                val meta = NovelStore.meta(dir!!)
                novelTitle = meta.optString("title", "")
                count = meta.optInt("chapterCount", 0)
                current = intent.getIntExtra("chapter", 1)
                startForeground(NOTIF_ID, buildNotification())
                play(current)
            }
            ACTION_TOGGLE -> {
                val m = mp
                if (m != null && m.isPlaying) pause() else resume()
            }
            ACTION_NEXT -> play(current + 1)
            ACTION_PREV -> play(current - 1)
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun play(n: Int) {
        val d = dir
        if (d == null || n < 1 || n > count) {
            stopPlayback()
            return
        }
        val f = NovelStore.audioFile(d, n)
        if (!f.exists()) {
            stopPlayback()
            return
        }
        try {
            mp?.release()
            mp = MediaPlayer()
            mp?.setDataSource(f.absolutePath)
            mp?.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            mp?.prepare()
            mp?.setOnCompletionListener { play(current + 1) }
            mp?.start()
            current = n
            requestFocus()
            updateSession()
            notifyUi()
        } catch (e: Exception) {
            stopPlayback()
        }
    }

    private fun resume() {
        val m = mp
        if (m != null && !m.isPlaying) {
            m.start()
            updateSession()
            notifyUi()
        }
    }

    private fun pause() {
        val m = mp
        if (m != null && m.isPlaying) {
            m.pause()
            updateSession()
            notifyUi()
        }
    }

    private fun requestFocus() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            if (Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
                    }
                    .build()
                audioManager?.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
        }
    }

    private fun updateSession() {
        val s = session ?: return
        val playing = mp?.isPlaying == true
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
            )
            .setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (mp?.currentPosition ?: 0).toLong(),
                1.0f
            )
            .build()
        s.setPlaybackState(state)
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "အခန်း " + current)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, novelTitle)
                .build()
        )
    }

    private fun nm(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notifyUi() {
        try {
            nm().notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val contentI = PendingIntent.getActivity(
            this, 0,
            Intent(this, AudioActivity::class.java).putExtra("id", novelId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleI = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextI = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevI = PendingIntent.getService(
            this, 3,
            Intent(this, PlaybackService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val playing = mp?.isPlaying == true
        builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(novelTitle)
            .setContentText(if (count > 0) "အခန်း " + current + " / " + count else novelTitle)
            .setContentIntent(contentI)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "⏮", prevI).build())
            .addAction(Notification.Action.Builder(null, if (playing) "⏸" else "▶", toggleI).build())
            .addAction(Notification.Action.Builder(null, "⏭", nextI).build())
        return builder.build()
    }

    private fun stopPlayback() {
        try {
            mp?.stop()
        } catch (e: Exception) {
        }
        mp?.release()
        mp = null
        session?.isActive = false
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mp?.release()
        } catch (e: Exception) {
        }
        mp = null
        session?.release()
        session = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
