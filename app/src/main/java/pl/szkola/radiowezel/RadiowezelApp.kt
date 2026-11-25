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
import pl.szkola.radiowezel.ota.UpdateChecker
import pl.szkola.radiowezel.scheduler.BellScheduler
import pl.szkola.radiowezel.util.PolishHolidays
import java.time.LocalDate

class RadiowezelApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var database: AppDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.get(this)
        settingsRepository = SettingsRepository(this)
        ensureChannels()
        applicationScope.launch {
            seedHolidaysIfNeeded()
            applySchoolScheduleIfNeeded()
            BellScheduler(this@RadiowezelApp).rescheduleAll()
            UpdateChecker(this@RadiowezelApp).checkForUpdates(manual = false)
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
        // Apply when tracks exist (wiosna/chopin) or always replace empty/old schedule on this update
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
    }
}
