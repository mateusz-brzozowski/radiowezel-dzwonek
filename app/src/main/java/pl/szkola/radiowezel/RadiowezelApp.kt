package pl.szkola.radiowezel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.szkola.radiowezel.data.SchoolSchedule
import pl.szkola.radiowezel.data.SettingsRepository
import pl.szkola.radiowezel.data.db.AppDatabase
import pl.szkola.radiowezel.ota.AssetSync
import pl.szkola.radiowezel.remote.CommandListener
import pl.szkola.radiowezel.scheduler.BellScheduler
import pl.szkola.radiowezel.util.PolishHolidays
import java.time.LocalDate

class RadiowezelApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var database: AppDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    private var commandListener: CommandListener? = null

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.get(this)
        settingsRepository = SettingsRepository(this)
        ensureChannels()
        applicationScope.launch {
            // Clear stuck force-OTA flag (was causing install/restart loops with Device Owner).
            settingsRepository.setPendingForceUpdate(false)
            settingsRepository.ensureDeviceId()
            seedHolidaysIfNeeded()
            applySchoolScheduleIfNeeded()
            BellScheduler(this@RadiowezelApp).rescheduleAll()
            // Alarm asset pack (separate from APK OTA)
            try {
                AssetSync(this@RadiowezelApp).sync(manual = false)
            } catch (_: Exception) {
            }
            startRemoteIfEnabled()
        }
    }

    fun startRemoteIfEnabled() {
        applicationScope.launch {
            val cfg = settingsRepository.settings.first()
            if (!cfg.firebaseEnabled) {
                commandListener?.stop()
                commandListener = null
                return@launch
            }
            if (commandListener == null) {
                commandListener = CommandListener(this@RadiowezelApp)
            }
            commandListener?.start()
        }
    }

    private suspend fun seedHolidaysIfNeeded() {
        if (database.holidayDao().count() > 0) return
        val year = LocalDate.now().year
        (PolishHolidays.fixedForYear(year) + PolishHolidays.fixedForYear(year + 1)).forEach {
            database.holidayDao().insert(it)
        }
    }

    private suspend fun applySchoolScheduleIfNeeded() {
        val settings = settingsRepository.settings.first()
        if (settings.scheduleTemplateVersion >= SchoolSchedule.TEMPLATE_VERSION) return
        val tracks = database.trackDao().getAll()
        database.bellEventDao().deleteAll()
        SchoolSchedule.buildEvents(tracks).forEach { database.bellEventDao().upsert(it) }
        settingsRepository.setScheduleTemplateVersion(SchoolSchedule.TEMPLATE_VERSION)
    }

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                "status",
                getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                "school_alarms",
                "Alarmy szkolne",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null) }
        )
    }
}
