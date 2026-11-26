package pl.szkola.radiowezel.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

object OtaDefaults {
    const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/mateusz-brzozowski/radiowezel-dzwonek/master/updates/version.json"
    const val ASSETS_JSON_URL =
        "https://raw.githubusercontent.com/mateusz-brzozowski/radiowezel-dzwonek/master/updates/assets.json"
}

data class AppSettings(
    val otaUrl: String = OtaDefaults.VERSION_JSON_URL,
    val assetsUrl: String = OtaDefaults.ASSETS_JSON_URL,
    val forceVolumePercent: Int = 100,
    val forceVolumeEnabled: Boolean = true,
    val lastOtaCheckAt: Long = 0L,
    val lastOtaMessage: String = "",
    val pendingForceUpdate: Boolean = false,
    val pendingApkPath: String = "",
    val pendingChangelog: String = "",
    val scheduleTemplateVersion: Int = 0,
    val assetsBundleVersion: Int = 0,
    val assetsMessage: String = "",
    val alarmPin: String = "",
    val deviceId: String = "",
    val remoteToken: String = "",
    val firebaseEnabled: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private val otaUrlKey = stringPreferencesKey("ota_url")
    private val assetsUrlKey = stringPreferencesKey("assets_url")
    private val forceVolumeKey = intPreferencesKey("force_volume")
    private val forceVolumeEnabledKey = booleanPreferencesKey("force_volume_enabled")
    private val lastOtaCheckKey = longPreferencesKey("last_ota_check")
    private val lastOtaMessageKey = stringPreferencesKey("last_ota_message")
    private val pendingForceKey = booleanPreferencesKey("pending_force_update")
    private val pendingApkKey = stringPreferencesKey("pending_apk_path")
    private val pendingChangelogKey = stringPreferencesKey("pending_changelog")
    private val scheduleTemplateKey = intPreferencesKey("schedule_template_version")
    private val assetsBundleKey = intPreferencesKey("assets_bundle_version")
    private val assetsMessageKey = stringPreferencesKey("assets_message")
    private val alarmPinKey = stringPreferencesKey("alarm_pin")
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val remoteTokenKey = stringPreferencesKey("remote_token")
    private val firebaseEnabledKey = booleanPreferencesKey("firebase_enabled")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            otaUrl = prefs[otaUrlKey] ?: OtaDefaults.VERSION_JSON_URL,
            assetsUrl = prefs[assetsUrlKey] ?: OtaDefaults.ASSETS_JSON_URL,
            forceVolumePercent = prefs[forceVolumeKey] ?: 100,
            forceVolumeEnabled = prefs[forceVolumeEnabledKey] ?: true,
            lastOtaCheckAt = prefs[lastOtaCheckKey] ?: 0L,
            lastOtaMessage = prefs[lastOtaMessageKey].orEmpty(),
            pendingForceUpdate = prefs[pendingForceKey] ?: false,
            pendingApkPath = prefs[pendingApkKey].orEmpty(),
            pendingChangelog = prefs[pendingChangelogKey].orEmpty(),
            scheduleTemplateVersion = prefs[scheduleTemplateKey] ?: 0,
            assetsBundleVersion = prefs[assetsBundleKey] ?: 0,
            assetsMessage = prefs[assetsMessageKey].orEmpty(),
            // Empty until set in Settings — never ship a known default PIN.
            alarmPin = prefs[alarmPinKey].orEmpty(),
            deviceId = prefs[deviceIdKey].orEmpty(),
            remoteToken = prefs[remoteTokenKey].orEmpty(),
            firebaseEnabled = prefs[firebaseEnabledKey] ?: false
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

    suspend fun setScheduleTemplateVersion(version: Int) {
        context.dataStore.edit { it[scheduleTemplateKey] = version }
    }

    suspend fun setAssetsUrl(url: String) {
        context.dataStore.edit { it[assetsUrlKey] = url.trim() }
    }

    suspend fun setAssetsBundleVersion(version: Int) {
        context.dataStore.edit { it[assetsBundleKey] = version }
    }

    suspend fun setAssetsMessage(message: String) {
        context.dataStore.edit { it[assetsMessageKey] = message }
    }

    suspend fun setAlarmPin(pin: String) {
        val cleaned = pin.filter { ch -> ch.isDigit() }.take(6)
        require(cleaned.length in 4..6) { "PIN musi mieć 4–6 cyfr" }
        context.dataStore.edit { it[alarmPinKey] = cleaned }
    }

    suspend fun ensureDeviceId(): String {
        val current = settings.first().deviceId
        if (current.isNotBlank()) return current
        val id = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[deviceIdKey] = id }
        return id
    }

    suspend fun ensureRemoteToken(): String {
        val current = settings.first().remoteToken
        if (current.length >= 24) return current
        val token = generateRemoteToken()
        context.dataStore.edit { it[remoteTokenKey] = token }
        return token
    }

    suspend fun regenerateRemoteToken(): String {
        val token = generateRemoteToken()
        context.dataStore.edit { it[remoteTokenKey] = token }
        return token
    }

    suspend fun setFirebaseEnabled(enabled: Boolean) {
        context.dataStore.edit { it[firebaseEnabledKey] = enabled }
        if (enabled) {
            ensureDeviceId()
            ensureRemoteToken()
        }
    }

    private fun generateRemoteToken(): String {
        val bytes = ByteArray(24)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
