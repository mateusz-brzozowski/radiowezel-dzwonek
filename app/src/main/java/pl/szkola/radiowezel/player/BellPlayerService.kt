package pl.szkola.radiowezel.player

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
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pl.szkola.radiowezel.MainActivity
import pl.szkola.radiowezel.R
import pl.szkola.radiowezel.RadiowezelApp
import pl.szkola.radiowezel.scheduler.BellScheduler
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.PI

/**
 * Plays a local track for a fixed duration with smooth fade-in / fade-out
 * (cosine curve — no hard cut).
 */
class BellPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var previousMusicVolume: Int? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var playing = false
    private var playerVolume = 0f
    private var fadeRunnable: Runnable? = null

    private val beginFadeOutRunnable = Runnable { fadeOutThenStop(reschedule = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                fadeOutThenStop(reschedule = false, fadeMs = FAST_FADE_MS)
                return START_NOT_STICKY
            }
            ACTION_PLAY, null -> {
                val trackPath = intent?.getStringExtra(EXTRA_TRACK_PATH)
                val durationSec = (intent?.getIntExtra(EXTRA_DURATION_SEC, 30) ?: 30).coerceIn(1, 600)
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.playing_bell)
                startForeground(NOTIFICATION_ID, buildNotification(title))
                startPlayback(trackPath, durationSec, title)
            }
        }
        return START_STICKY
    }

    private fun startPlayback(trackPath: String?, durationSec: Int, title: String) {
        cancelFades()
        releasePlayerOnly(keepPlayingFlag = false)
        playing = true
        acquireWakeLock()
        forceMusicVolume()
        requestFocus()

        val fileOk = !trackPath.isNullOrBlank() && File(trackPath).exists() && File(trackPath).length() > 0
        Log.i(TAG, "play path=$trackPath exists=$fileOk duration=${durationSec}s")

        if (!fileOk) {
            Log.w(TAG, "Brak pliku — fallback ToneGenerator")
            playToneFallback(durationSec)
            return
        }

        val totalMs = durationSec * 1000L
        val fadeIn = min(FADE_IN_MS, totalMs / 4)
        val fadeOut = min(FADE_OUT_MS, totalMs / 3)
        val holdBeforeFadeOut = (totalMs - fadeOut).coerceAtLeast(0L)

        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )
                setDataSource(trackPath)
                isLooping = true
                setVolume(0f, 0f)
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    playToneFallback(durationSec)
                    true
                }
                prepare()
                start()
                Log.i(
                    TAG,
                    "MediaPlayer started, musicVol=${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/" +
                        "${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}, fadeIn=${fadeIn}ms fadeOut=${fadeOut}ms"
                )
            }
            playerVolume = 0f
            startForeground(NOTIFICATION_ID, buildNotification(title))
            fadeTo(1f, fadeIn)
            handler.removeCallbacks(beginFadeOutRunnable)
            handler.postDelayed(beginFadeOutRunnable, holdBeforeFadeOut)
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się odtworzyć pliku", e)
            playToneFallback(durationSec)
        }
    }

    private fun playToneFallback(durationSec: Int) {
        scope.launch {
            val tone = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            } catch (e: Exception) {
                Log.e(TAG, "ToneGenerator fail", e)
                handler.post { stopPlayback(reschedule = true) }
                return@launch
            }
            val endAt = System.currentTimeMillis() + durationSec * 1000L
            try {
                while (System.currentTimeMillis() < endAt && playing) {
                    tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    Thread.sleep(600)
                }
            } finally {
                try {
                    tone.release()
                } catch (_: Exception) {
                }
                handler.post { stopPlayback(reschedule = true) }
            }
        }
    }

    /** Cosine ease — smooth in/out without linear “steps” feel. */
    private fun easeInOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return (0.5 - 0.5 * cos(PI * x)).toFloat()
    }

    private fun fadeTo(target: Float, durationMs: Long, onDone: (() -> Unit)? = null) {
        cancelFadeStepsOnly()
        val startVol = playerVolume
        val endVol = target.coerceIn(0f, 1f)
        if (durationMs <= 0L || mediaPlayer == null) {
            applyPlayerVolume(endVol)
            onDone?.invoke()
            return
        }
        val steps = (durationMs / STEP_MS).toInt().coerceAtLeast(1)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                if (!playing || mediaPlayer == null) {
                    onDone?.invoke()
                    return
                }
                step++
                val t = step.toFloat() / steps
                applyPlayerVolume(startVol + (endVol - startVol) * easeInOut(t))
                if (step < steps) {
                    handler.postDelayed(this, STEP_MS)
                } else {
                    applyPlayerVolume(endVol)
                    fadeRunnable = null
                    onDone?.invoke()
                }
            }
        }
        fadeRunnable = runnable
        handler.post(runnable)
    }

    private fun applyPlayerVolume(vol: Float) {
        playerVolume = vol.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(playerVolume, playerVolume)
        } catch (_: Exception) {
        }
    }

    private fun fadeOutThenStop(reschedule: Boolean, fadeMs: Long = FADE_OUT_MS) {
        handler.removeCallbacks(beginFadeOutRunnable)
        if (mediaPlayer == null || !playing) {
            stopPlayback(reschedule)
            return
        }
        fadeTo(0f, fadeMs) {
            stopPlayback(reschedule)
        }
    }

    private fun cancelFadeStepsOnly() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
    }

    private fun cancelFades() {
        handler.removeCallbacks(beginFadeOutRunnable)
        cancelFadeStepsOnly()
    }

    private fun forceMusicVolume() {
        try {
            val settings = runBlocking {
                (application as RadiowezelApp).settingsRepository.settings.first()
            }
            if (!settings.forceVolumeEnabled) return
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            previousMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * settings.forceVolumePercent / 100f).toInt().coerceIn(1, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            if (am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
            Log.i(TAG, "Forced STREAM_MUSIC volume to $target/$max")
        } catch (e: Exception) {
            Log.e(TAG, "forceMusicVolume failed", e)
        }
    }

    private fun restoreVolume() {
        previousMusicVolume?.let { vol ->
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
            } catch (_: Exception) {
            }
        }
        previousMusicVolume = null
    }

    private fun requestFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
        audioFocusRequest = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "radiowezel:bell").apply {
            setReferenceCounted(false)
            acquire((11 * 60 * 1000).toLong())
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun releasePlayerOnly(keepPlayingFlag: Boolean = false) {
        cancelFades()
        if (!keepPlayingFlag) playing = false
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        playerVolume = 0f
    }

    private fun stopPlayback(reschedule: Boolean) {
        releasePlayerOnly()
        abandonFocus()
        restoreVolume()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (reschedule) {
            scope.launch {
                try {
                    BellScheduler(applicationContext).rescheduleAll()
                } catch (e: Exception) {
                    Log.e(TAG, "reschedule failed", e)
                }
            }
        }
    }

    private fun buildNotification(title: String): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BellPlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.playing_bell))
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_bell),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Odtwarzanie dzwonka radiowęzła"
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        cancelFades()
        playing = false
        releasePlayerOnly()
        abandonFocus()
        restoreVolume()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BellPlayer"
        const val ACTION_PLAY = "pl.szkola.radiowezel.action.PLAY"
        const val ACTION_STOP = "pl.szkola.radiowezel.action.STOP"
        const val EXTRA_TRACK_PATH = "track_path"
        const val EXTRA_DURATION_SEC = "duration_sec"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "bell_playback"
        private const val NOTIFICATION_ID = 1001

        /** Soft rise at the start of the bell */
        private const val FADE_IN_MS = 2_500L
        /** Soft fall before end — no hard cut */
        private const val FADE_OUT_MS = 4_000L
        private const val FAST_FADE_MS = 800L
        private const val STEP_MS = 40L

        fun play(
            context: Context,
            trackPath: String?,
            durationSec: Int,
            title: String
        ) {
            val intent = Intent(context, BellPlayerService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_TRACK_PATH, trackPath)
                putExtra(EXTRA_DURATION_SEC, durationSec)
                putExtra(EXTRA_TITLE, title)
            }
            context.startForegroundService(intent)
        }
    }
}
