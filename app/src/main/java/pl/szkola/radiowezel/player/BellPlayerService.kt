package pl.szkola.radiowezel.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.szkola.radiowezel.MainActivity
import pl.szkola.radiowezel.R
import pl.szkola.radiowezel.RadiowezelApp
import pl.szkola.radiowezel.scheduler.BellScheduler
import java.io.File

class BellPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopJob: Job? = null
    private var previousVolume: Int? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                return START_NOT_STICKY
            }
            ACTION_PLAY, null -> {
                val trackPath = intent?.getStringExtra(EXTRA_TRACK_PATH)
                val durationSec = intent?.getIntExtra(EXTRA_DURATION_SEC, 30) ?: 30
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.playing_bell)
                startForeground(NOTIFICATION_ID, buildNotification(title))
                play(trackPath, durationSec, title)
            }
        }
        return START_STICKY
    }

    private fun play(trackPath: String?, durationSec: Int, title: String) {
        stopPlayback(restartService = false)
        acquireWakeLock()
        maybeForceVolume()

        if (trackPath.isNullOrBlank() || !File(trackPath).exists()) {
            // Fallback: system alarm tone via MediaPlayer with no file — just wait duration with notification
            stopJob = scope.launch {
                delay(durationSec.coerceAtLeast(1) * 1000L)
                stopPlayback()
            }
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(trackPath)
                isLooping = true
                setOnPreparedListener { mp ->
                    mp.start()
                    startForeground(NOTIFICATION_ID, buildNotification(title))
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            stopPlayback()
            return
        }

        stopJob = scope.launch {
            delay(durationSec.coerceAtLeast(1) * 1000L)
            stopPlayback()
        }
    }

    private fun maybeForceVolume() {
        scope.launch(Dispatchers.IO) {
            val settings = (application as RadiowezelApp).settingsRepository.settings.first()
            if (!settings.forceVolumeEnabled) return@launch
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            previousVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = (max * settings.forceVolumePercent / 100f).toInt().coerceIn(1, max)
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }
    }

    private fun restoreVolume() {
        previousVolume?.let { vol ->
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
        }
        previousVolume = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "radiowezel:bell").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun stopPlayback(restartService: Boolean = true) {
        stopJob?.cancel()
        stopJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        restoreVolume()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (restartService) {
            // Reschedule next bells after playback
            scope.launch(Dispatchers.IO) {
                BellScheduler(applicationContext).rescheduleAll()
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
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_bell),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Odtwarzanie dzwonka radiowęzła"
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopPlayback(restartService = false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "pl.szkola.radiowezel.action.PLAY"
        const val ACTION_STOP = "pl.szkola.radiowezel.action.STOP"
        const val EXTRA_TRACK_PATH = "track_path"
        const val EXTRA_DURATION_SEC = "duration_sec"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "bell_playback"
        private const val NOTIFICATION_ID = 1001

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
