package pl.szkola.radiowezel.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.szkola.radiowezel.RadiowezelApp
import pl.szkola.radiowezel.alarm.AlarmState
import pl.szkola.radiowezel.alarm.AlarmType
import pl.szkola.radiowezel.data.AppSettings
import pl.szkola.radiowezel.data.SchoolSchedule
import pl.szkola.radiowezel.data.db.BellEventEntity
import pl.szkola.radiowezel.data.db.BellType
import pl.szkola.radiowezel.data.db.HolidayEntity
import pl.szkola.radiowezel.data.db.TrackEntity
import pl.szkola.radiowezel.ota.AssetSync
import pl.szkola.radiowezel.ota.UpdateChecker
import pl.szkola.radiowezel.ota.UpdateResult
import pl.szkola.radiowezel.player.AlarmPlayerService
import pl.szkola.radiowezel.player.BellPlayerService
import pl.szkola.radiowezel.scheduler.BellScheduler
import pl.szkola.radiowezel.util.BatteryHelper
import pl.szkola.radiowezel.util.KioskHelper
import pl.szkola.radiowezel.util.TrackStorage
import java.io.File

data class UiState(
    val events: List<BellEventEntity> = emptyList(),
    val tracks: List<TrackEntity> = emptyList(),
    val holidays: List<HolidayEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val nextBellLabel: String? = null,
    val batteryOptimizedIgnored: Boolean = false,
    val autoTimeEnabled: Boolean = true,
    val isDeviceOwner: Boolean = false,
    val otaBusy: Boolean = false,
    val snackbar: String? = null,
    val assetsComplete: Boolean = false,
    val assetsProgressLabel: String = "",
    val activeAlarmLabel: String? = null,
    val assetSyncBusy: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RadiowezelApp
    private val db = app.database
    private val settingsRepo = app.settingsRepository
    private val assetSync = AssetSync(application)

    private val extras = MutableStateFlow(
        Extras(
            nextBellLabel = null,
            batteryOptimizedIgnored = BatteryHelper.isIgnoringBatteryOptimizations(application),
            autoTimeEnabled = BatteryHelper.isAutoTimeEnabled(application),
            isDeviceOwner = KioskHelper.isDeviceOwner(application),
            otaBusy = false,
            snackbar = null,
            assetSyncBusy = false
        )
    )

    val uiState: StateFlow<UiState> = combine(
        db.bellEventDao().observeAll(),
        db.trackDao().observeAll(),
        db.holidayDao().observeAll(),
        settingsRepo.settings,
        extras,
        assetSync.progress,
        AlarmState.active
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val events = values[0] as List<BellEventEntity>
        val tracks = values[1] as List<TrackEntity>
        val holidays = values[2] as List<HolidayEntity>
        val settings = values[3] as AppSettings
        val ex = values[4] as Extras
        val assets = values[5] as pl.szkola.radiowezel.ota.AssetSyncProgress
        val active = values[6] as AlarmType?
        UiState(
            events = events,
            tracks = tracks,
            holidays = holidays,
            settings = settings,
            nextBellLabel = ex.nextBellLabel,
            batteryOptimizedIgnored = ex.batteryOptimizedIgnored,
            autoTimeEnabled = ex.autoTimeEnabled,
            isDeviceOwner = ex.isDeviceOwner,
            otaBusy = ex.otaBusy,
            snackbar = ex.snackbar,
            assetsComplete = assets.complete,
            assetsProgressLabel = assets.message.ifBlank { settings.assetsMessage },
            activeAlarmLabel = active?.label,
            assetSyncBusy = ex.assetSyncBusy
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        refreshStatus()
        viewModelScope.launch { assetSync.refreshStatus() }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val next = BellScheduler(getApplication()).findNextBellLabel()
            extras.update {
                it.copy(
                    nextBellLabel = next,
                    batteryOptimizedIgnored = BatteryHelper.isIgnoringBatteryOptimizations(getApplication()),
                    autoTimeEnabled = BatteryHelper.isAutoTimeEnabled(getApplication()),
                    isDeviceOwner = KioskHelper.isDeviceOwner(getApplication())
                )
            }
            assetSync.refreshStatus()
        }
    }

    fun clearSnackbar() {
        extras.update { it.copy(snackbar = null) }
    }

    fun saveEvent(
        id: Long?,
        timeMinutes: Int,
        type: BellType,
        trackId: Long?,
        durationSeconds: Int
    ) {
        viewModelScope.launch {
            db.bellEventDao().upsert(
                BellEventEntity(
                    id = id ?: 0,
                    timeMinutes = timeMinutes,
                    type = type,
                    trackId = trackId,
                    durationSeconds = durationSeconds,
                    enabled = true
                )
            )
            BellScheduler(getApplication()).rescheduleAll()
            refreshStatus()
            extras.update { it.copy(snackbar = "Zapisano dzwonek") }
        }
    }

    fun applySchoolSchedule() {
        viewModelScope.launch {
            applySchoolScheduleInternal()
            extras.update { it.copy(snackbar = "Wczytano harmonogram lekcji (8× start + 8× koniec przerwy)") }
            refreshStatus()
        }
    }

    suspend fun applySchoolScheduleInternal() {
        val tracks = db.trackDao().getAll()
        db.bellEventDao().deleteAll()
        SchoolSchedule.buildEvents(tracks).forEach { db.bellEventDao().upsert(it) }
        settingsRepo.setScheduleTemplateVersion(SchoolSchedule.TEMPLATE_VERSION)
        BellScheduler(getApplication()).rescheduleAll()
    }

    fun deleteEvent(event: BellEventEntity) {
        viewModelScope.launch {
            db.bellEventDao().delete(event)
            BellScheduler(getApplication()).rescheduleAll()
            refreshStatus()
        }
    }

    fun importTrack(uri: Uri, name: String) {
        viewModelScope.launch {
            try {
                val file = TrackStorage.importFromUri(getApplication(), uri, name)
                db.trackDao().insert(TrackEntity(name = name, filePath = file.absolutePath))
                val lower = name.lowercase()
                if (lower.contains("wiosna") || lower.contains("chopin")) {
                    applySchoolScheduleInternal()
                    extras.update { it.copy(snackbar = "Dodano $name i odświeżono plan lekcji") }
                } else {
                    extras.update { it.copy(snackbar = "Dodano utwór: $name") }
                }
                refreshStatus()
            } catch (e: Exception) {
                extras.update { it.copy(snackbar = "Błąd importu: ${e.message}") }
            }
        }
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            db.trackDao().clearTrackRefs(track.id)
            db.trackDao().delete(track)
            File(track.filePath).delete()
            extras.update { it.copy(snackbar = "Usunięto utwór") }
        }
    }

    fun addHoliday(date: String, name: String) {
        viewModelScope.launch {
            db.holidayDao().insert(HolidayEntity(date = date, name = name, builtIn = false))
            BellScheduler(getApplication()).rescheduleAll()
            refreshStatus()
        }
    }

    fun deleteHoliday(holiday: HolidayEntity) {
        viewModelScope.launch {
            db.holidayDao().delete(holiday)
            BellScheduler(getApplication()).rescheduleAll()
            refreshStatus()
        }
    }

    fun setOtaUrl(url: String) {
        viewModelScope.launch { settingsRepo.setOtaUrl(url) }
    }

    fun setForceVolume(percent: Int, enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setForceVolume(percent, enabled) }
    }

    fun setAlarmPin(pin: String) {
        viewModelScope.launch {
            try {
                settingsRepo.setAlarmPin(pin)
                extras.update { it.copy(snackbar = "Zapisano PIN alarmowy") }
            } catch (e: Exception) {
                extras.update { it.copy(snackbar = e.message ?: "Nie udało się zapisać PIN") }
            }
        }
    }

    fun setFirebaseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setFirebaseEnabled(enabled)
            if (enabled) {
                settingsRepo.ensureDeviceId()
                settingsRepo.ensureRemoteToken()
            }
            app.startRemoteIfEnabled()
            extras.update {
                it.copy(
                    snackbar = if (enabled) {
                        "Zdalne sterowanie włączone — skopiuj Device ID i kod parowania"
                    } else {
                        "Zdalne sterowanie wyłączone"
                    }
                )
            }
        }
    }

    fun regenerateRemoteToken() {
        viewModelScope.launch {
            settingsRepo.regenerateRemoteToken()
            extras.update { it.copy(snackbar = "Wygenerowano nowy kod parowania — zaktualizuj panel WWW") }
        }
    }

    fun syncAlarmAssets() {
        viewModelScope.launch {
            extras.update { it.copy(assetSyncBusy = true, snackbar = "Pobieranie paczki alarmów…") }
            val result = assetSync.sync(manual = true)
            extras.update {
                it.copy(
                    assetSyncBusy = false,
                    snackbar = result.message
                )
            }
        }
    }

    fun confirmAndStartAlarm(type: AlarmType, drill: Boolean, pin: String): Boolean {
        val expected = extras.value.let {
            // sync read — use runBlocking-free cached from last settings via blocking first in launch
            true
        }
        var ok = false
        kotlinx.coroutines.runBlocking {
            val settings = settingsRepo.settings.first()
            ok = settings.alarmPin.length in 4..6 && pin == settings.alarmPin
            if (ok) {
                AlarmPlayerService.start(getApplication(), type, drill)
                extras.update { it.copy(snackbar = "Uruchomiono: ${type.label}") }
            } else if (settings.alarmPin.isBlank()) {
                extras.update { it.copy(snackbar = "Najpierw ustaw PIN alarmowy w Ustawieniach") }
            }
        }
        return ok && expected
    }

    fun stopAlarm() {
        AlarmPlayerService.stop(getApplication())
        extras.update { it.copy(snackbar = "Alarm zatrzymany") }
    }

    fun ringNow(trackId: Long?, durationSeconds: Int = 15) {
        viewModelScope.launch {
            if (AlarmState.isActive) {
                extras.update { it.copy(snackbar = "Alarm aktywny — dzwonek zablokowany") }
                return@launch
            }
            val track = trackId?.let { db.trackDao().getById(it) }
                ?: db.trackDao().getAll().firstOrNull()
            val path = track?.filePath
            val fileOk = path != null && File(path).exists() && File(path).length() > 0
            if (!fileOk) {
                extras.update {
                    it.copy(
                        snackbar = if (track == null) {
                            "Brak utworów — dodaj MP3 w zakładce Utwory (zagra sygnał testowy)."
                        } else {
                            "Plik utworu nie istnieje na dysku — zaimportuj ponownie."
                        }
                    )
                }
            } else {
                extras.update { it.copy(snackbar = "Odtwarzam: ${track?.name}") }
            }
            BellPlayerService.play(
                context = getApplication(),
                trackPath = if (fileOk) path else null,
                durationSec = durationSeconds,
                title = "Test dzwonka"
            )
        }
    }

    fun checkUpdates(manual: Boolean = true) {
        viewModelScope.launch {
            extras.update { it.copy(otaBusy = true) }
            when (val result = UpdateChecker(getApplication()).checkForUpdates(manual)) {
                is UpdateResult.Available -> {
                    extras.update {
                        it.copy(
                            snackbar = "Dostępna ${result.info.versionName}. Zainstaluj w Ustawieniach."
                        )
                    }
                }
                is UpdateResult.UpToDate -> extras.update { it.copy(snackbar = result.message) }
                is UpdateResult.Error -> extras.update { it.copy(snackbar = result.message) }
                is UpdateResult.Skipped -> extras.update { it.copy(snackbar = result.message) }
            }
            extras.update { it.copy(otaBusy = false) }
            refreshStatus()
        }
    }

    fun installPendingUpdate() {
        viewModelScope.launch {
            val path = settingsRepo.settings.first().pendingApkPath
            if (path.isNotBlank()) {
                UpdateChecker(getApplication()).installApk(File(path))
            }
        }
    }

    fun dismissForceUpdate() {
        viewModelScope.launch {
            settingsRepo.setPendingForceUpdate(false)
        }
    }

    private data class Extras(
        val nextBellLabel: String?,
        val batteryOptimizedIgnored: Boolean,
        val autoTimeEnabled: Boolean,
        val isDeviceOwner: Boolean,
        val otaBusy: Boolean,
        val snackbar: String?,
        val assetSyncBusy: Boolean
    )
}

/** combine of 7 flows — Array transform */
private inline fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
    f1: kotlinx.coroutines.flow.Flow<T1>,
    f2: kotlinx.coroutines.flow.Flow<T2>,
    f3: kotlinx.coroutines.flow.Flow<T3>,
    f4: kotlinx.coroutines.flow.Flow<T4>,
    f5: kotlinx.coroutines.flow.Flow<T5>,
    f6: kotlinx.coroutines.flow.Flow<T6>,
    f7: kotlinx.coroutines.flow.Flow<T7>,
    crossinline transform: suspend (Array<*>) -> R
): kotlinx.coroutines.flow.Flow<R> {
    return kotlinx.coroutines.flow.combine(f1, f2, f3, f4, f5) { a, b, c, d, e ->
        arrayOf<Any?>(a, b, c, d, e)
    }.let { firstFive ->
        kotlinx.coroutines.flow.combine(firstFive, f6, f7) { arr, s6, s7 ->
            transform(arrayOf(arr[0], arr[1], arr[2], arr[3], arr[4], s6, s7))
        }
    }
}
