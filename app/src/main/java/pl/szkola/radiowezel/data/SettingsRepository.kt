package pl.szkola.radiowezel.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

object OtaDefaults {
    const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/mateusz-brzozowski/radiowezel-dzwonek/master/updates/version.json"
}

data class AppSettings(
    val otaUrl: String = OtaDefaults.VERSION_JSON_URL,
    val forceVolumePercent: Int = 100,
    val forceVolumeEnabled: Boolean = true,
    val lastOtaCheckAt: Long = 0L,
    val lastOtaMessage: String = "",
    val pendingForceUpdate: Boolean = false,
    val pendingApkPath: String = "",
    val pendingChangelog: String = ""
)

class SettingsRepository(private val context: Context) {
    private val otaUrlKey = stringPreferencesKey("ota_url")
    private val forceVolumeKey = intPreferencesKey("force_volume")
    private val forceVolumeEnabledKey = booleanPreferencesKey("force_volume_enabled")
    private val lastOtaCheckKey = longPreferencesKey("last_ota_check")
    private val lastOtaMessageKey = stringPreferencesKey("last_ota_message")
    private val pendingForceKey = booleanPreferencesKey("pending_force_update")
    private val pendingApkKey = stringPreferencesKey("pending_apk_path")
    private val pendingChangelogKey = stringPreferencesKey("pending_changelog")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            otaUrl = prefs[otaUrlKey] ?: OtaDefaults.VERSION_JSON_URL,
            forceVolumePercent = prefs[forceVolumeKey] ?: 100,
            forceVolumeEnabled = prefs[forceVolumeEnabledKey] ?: true,
            lastOtaCheckAt = prefs[lastOtaCheckKey] ?: 0L,
            lastOtaMessage = prefs[lastOtaMessageKey].orEmpty(),
            pendingForceUpdate = prefs[pendingForceKey] ?: false,
            pendingApkPath = prefs[pendingApkKey].orEmpty(),
            pendingChangelog = prefs[pendingChangelogKey].orEmpty()
        )
    }

    suspend fun setOtaUrl(url: String) {
        context.dataStore.edit { it[otaUrlKey] = url.trim() }
    }

    suspend fun setForceVolume(percent: Int, enabled: Boolean) {
        context.dataStore.edit {
            it[forceVolumeKey] = percent.coerceIn(0, 100)
            it[forceVolumeEnabledKey] = enabled
        }
    }

    suspend fun setOtaResult(message: String) {
        context.dataStore.edit {
            it[lastOtaCheckKey] = System.currentTimeMillis()
            it[lastOtaMessageKey] = message
        }
    }

    suspend fun setPendingForceUpdate(pending: Boolean, apkPath: String = "", changelog: String = "") {
        context.dataStore.edit {
            it[pendingForceKey] = pending
            it[pendingApkKey] = apkPath
            it[pendingChangelogKey] = changelog
        }
    }
}
