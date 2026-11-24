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
import pl.szkola.radiowezel.data.AppSettings
import pl.szkola.radiowezel.data.db.BellEventEntity
import pl.szkola.radiowezel.data.db.BellType
import pl.szkola.radiowezel.data.db.HolidayEntity
import pl.szkola.radiowezel.data.db.TrackEntity
import pl.szkola.radiowezel.ota.UpdateChecker
import pl.szkola.radiowezel.ota.UpdateResult
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
    val snackbar: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RadiowezelApp
    private val db = app.database
    private val settingsRepo = app.settingsRepository

    private val extras = MutableStateFlow(
        Extras(
            nextBellLabel = null,
            batteryOptimizedIgnored = BatteryHelper.isIgnoringBatteryOptimizations(application),
            autoTimeEnabled = BatteryHelper.isAutoTimeEnabled(application),
            isDeviceOwner = KioskHelper.isDeviceOwner(application),
            otaBusy = false,
            snackbar = null
        )
    )

    val uiState: StateFlow<UiState> = combine(
        db.bellEventDao().observeAll(),
        db.trackDao().observeAll(),
        db.holidayDao().observeAll(),
        settingsRepo.settings,
        extras
    ) { events, tracks, holidays, settings, ex ->
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
            snackbar = ex.snackbar
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        refreshStatus()
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
        durationSeconds: Int,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            db.bellEventDao().upsert(
                BellEventEntity(
                    id = id ?: 0,
                    timeMinutes = timeMinutes,
                    type = type,
                    trackId = trackId,
                    durationSeconds = durationSeconds,
                    enabled = enabled
                )
            )
            BellScheduler(getApplication()).rescheduleAll()
            refreshStatus()
            extras.update { it.copy(snackbar = "Zapisano dzwonek") }
        }
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
                extras.update { it.copy(snackbar = "Dodano utwór: $name") }
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

    fun ringNow(trackId: Long?, durationSeconds: Int = 15) {
        viewModelScope.launch {
            val track = trackId?.let { db.trackDao().getById(it) }
                ?: db.trackDao().getAll().firstOrNull()
            BellPlayerService.play(
                context = getApplication(),
                trackPath = track?.filePath,
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
                    extras.update { it.copy(snackbar = "Nowa wersja ${result.info.versionName}") }
                    UpdateChecker(getApplication()).installApk(result.apkFile)
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

    private data class Extras(
        val nextBellLabel: String?,
        val batteryOptimizedIgnored: Boolean,
        val autoTimeEnabled: Boolean,
        val isDeviceOwner: Boolean,
        val otaBusy: Boolean,
        val snackbar: String?
    )
}
