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
import pl.szkola.radiowezel.alarm.AlarmState
import pl.szkola.radiowezel.alarm.AlarmType
import pl.szkola.radiowezel.ota.AssetSync
import pl.szkola.radiowezel.scheduler.BellScheduler
import java.io.File
import kotlin.math.PI
import kotlin.math.cos

/**
 * High-priority alarm playback: tone intro → voice ×2 → looping tone until STOP / max.
 * Preempts lesson bells via [AlarmState].
 */
class AlarmPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var previousMusicVolume: Int? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var playing = false
    private var playerVolume = 0f
    private var fadeRunnable: Runnable? = null
    private var phaseRunnable: Runnable? = null
    private var hardStopRunnable: Runnable? = null
    private var currentType: AlarmType? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm(rescheduleBells = true)
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val typeId = intent?.getStringExtra(EXTRA_TYPE) ?: return START_NOT_STICKY
                val drill = intent.getBooleanExtra(EXTRA_DRILL, false)
                val type = AlarmType.fromId(typeId) ?: return START_NOT_STICKY
                // Stop lesson bell if playing
                try {
                    stopService(Intent(this, BellPlayerService::class.java).setAction(BellPlayerService.ACTION_STOP))
                } catch (_: Exception) {
                }
                startAlarm(type, drill)
            }
        }
        return START_STICKY
    }

    private fun startAlarm(type: AlarmType, drill: Boolean) {
        cancelAllCallbacks()
        releasePlayer()
        playing = true
        currentType = type
        AlarmState.setActive(type)
        acquireWakeLock()
        forceMusicVolume()
        requestFocus()

        val title = if (drill) "ĆWICZENIE: ${type.label}" else "ALARM: ${type.label}"
        startForeground(NOTIFICATION_ID, buildNotification(title))

        val sync = AssetSync(this)
        val tone = sync.localFile(type.toneId)
        val voice = sync.localFile(type.voiceFor(drill))
        if (!tone.exists() || !voice.exists()) {
            Log.e(TAG, "Missing assets tone=${tone.exists()} voice=${voice.exists()}")
            stopAlarm(rescheduleBells = true)
            return
        }

        type.maxSeconds?.let { max ->
            hardStopRunnable = Runnable { stopAlarm(rescheduleBells = true) }
            handler.postDelayed(hardStopRunnable!!, max * 1000L)
        }

        // Phase 1: tone intro ~12s (or file length capped)
        playFile(tone, loop = true, fadeIn = true) {
            // Phase 2: voice twice
            playVoiceTwice(voice) {
                // Phase 3: looping tone until stop
                if (playing) {
                    playFile(tone, loop = true, fadeIn = true)
                }
            }
        }
    }

    private fun playVoiceTwice(voice: File, onDone: () -> Unit) {
        playFile(voice, loop = false, fadeIn = true) {
            if (!playing) return@playFile
            playFile(voice, loop = false, fadeIn = false) {
                onDone()
            }
        }
    }

    private fun playFile(file: File, loop: Boolean, fadeIn: Boolean, onCompletion: (() -> Unit)? = null) {
        releasePlayer(keepPlaying = true)
        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                isLooping = loop
                setVolume(if (fadeIn) 0f else 1f, if (fadeIn) 0f else 1f)
                setOnCompletionListener {
                    if (!loop) onCompletion?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "error what=$what extra=$extra")
                    onCompletion?.invoke()
                    true
                }
                prepare()
                start()
            }
            playerVolume = if (fadeIn) 0f else 1f
            if (fadeIn) fadeTo(1f, FADE_MS)
            if (loop && onCompletion != null) {
                // For intro looping tone, cut after INTRO_MS
                phaseRunnable = Runnable {
                    fadeTo(0f, FADE_MS) { onCompletion() }
                }
                handler.postDelayed(phaseRunnable!!, INTRO_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "playFile failed", e)
            onCompletion?.invoke()
        }
    }

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return (0.5 - 0.5 * cos(PI * x)).toFloat()
    }

    private fun fadeTo(target: Float, durationMs: Long, onDone: (() -> Unit)? = null) {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        val start = playerVolume
        val end = target.coerceIn(0f, 1f)
        if (durationMs <= 0 || mediaPlayer == null) {
            applyVol(end)
            onDone?.invoke()
            return
        }
        val steps = (durationMs / 40L).toInt().coerceAtLeast(1)
        var step = 0
        val r = object : Runnable {
            override fun run() {
                if (mediaPlayer == null) {
                    onDone?.invoke()
                    return
                }
                step++
                applyVol(start + (end - start) * ease(step.toFloat() / steps))
                if (step < steps) handler.postDelayed(this, 40)
                else {
                    applyVol(end)
                    fadeRunnable = null
                    onDone?.invoke()
                }
            }
        }
        fadeRunnable = r
        handler.post(r)
    }

    private fun applyVol(v: Float) {
        playerVolume = v
        try {
            mediaPlayer?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    private fun stopAlarm(rescheduleBells: Boolean) {
        cancelAllCallbacks()
        playing = false
        currentType = null
        AlarmState.setActive(null)
        releasePlayer()
        abandonFocus()
        restoreVolume()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (rescheduleBells) {
            scope.launch(Dispatchers.IO) {
                try {
                    BellScheduler(applicationContext).rescheduleAll()
                } catch (e: Exception) {
                    Log.e(TAG, "reschedule", e)
                }
            }
        }
    }

    private fun cancelAllCallbacks() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        phaseRunnable?.let { handler.removeCallbacks(it) }
        hardStopRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
        phaseRunnable = null
        hardStopRunnable = null
    }

    private fun releasePlayer(keepPlaying: Boolean = false) {
        if (!keepPlaying) {
            // nothing
        }
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
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
        } catch (_: Exception) {
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
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
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
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "radiowezel:alarm").apply {
            setReferenceCounted(false)
            acquire(15 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun buildNotification(title: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Alarmy", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, AlarmPlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Alarm aktywny — naciśnij STOP")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(open)
            .addAction(0, "STOP", stop)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    override fun onDestroy() {
        cancelAllCallbacks()
        playing = false
        AlarmState.setActive(null)
        releasePlayer()
        abandonFocus()
        restoreVolume()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmPlayer"
        const val ACTION_START = "pl.szkola.radiowezel.action.ALARM_START"
        const val ACTION_STOP = "pl.szkola.radiowezel.action.ALARM_STOP"
        const val EXTRA_TYPE = "alarm_type"
        const val EXTRA_DRILL = "drill"
        private const val CHANNEL_ID = "school_alarms"
        private const val NOTIFICATION_ID = 2001
        private const val FADE_MS = 800L
        private const val INTRO_MS = 12_000L

        fun start(context: Context, type: AlarmType, drill: Boolean) {
            val i = Intent(context, AlarmPlayerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TYPE, type.id)
                putExtra(EXTRA_DRILL, drill)
            }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AlarmPlayerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
