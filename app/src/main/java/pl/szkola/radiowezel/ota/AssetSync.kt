package pl.szkola.radiowezel.ota

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pl.szkola.radiowezel.RadiowezelApp
import pl.szkola.radiowezel.data.OtaDefaults
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AssetFile(
    val id: String,
    val url: String,
    val sha256: String,
    val kind: String
)

data class AssetSyncProgress(
    val bundleVersion: Int = 0,
    val total: Int = 0,
    val done: Int = 0,
    val message: String = "",
    val complete: Boolean = false,
    val missingIds: List<String> = emptyList()
)

class AssetSync(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val settings get() = (context.applicationContext as RadiowezelApp).settingsRepository

    private val _progress = MutableStateFlow(AssetSyncProgress())
    val progress: StateFlow<AssetSyncProgress> = _progress

    fun alarmsDir(): File = File(context.filesDir, "alarms").also { it.mkdirs() }

    fun localFile(assetId: String): File = File(alarmsDir(), "$assetId.mp3")

    fun hasAsset(assetId: String): Boolean {
        val f = localFile(assetId)
        return f.exists() && f.length() > 0
    }

    suspend fun refreshStatus(): AssetSyncProgress = withContext(Dispatchers.IO) {
        val required = AlarmAssetIds.ALL
        val missing = required.filter { !hasAsset(it) }
        val p = AssetSyncProgress(
            bundleVersion = settings.settings.first().assetsBundleVersion,
            total = required.size,
            done = required.size - missing.size,
            message = if (missing.isEmpty()) "Paczka alarmów kompletna" else "Brakuje ${missing.size} plików",
            complete = missing.isEmpty(),
            missingIds = missing
        )
        _progress.value = p
        p
    }

    suspend fun sync(manual: Boolean = false): AssetSyncProgress = withContext(Dispatchers.IO) {
        try {
            val assetsUrl = resolveAssetsUrl()
            _progress.value = _progress.value.copy(message = "Pobieram manifest…")
            val manifest = fetchManifest(assetsUrl)
            val files = manifest.files
            var done = 0
            for (file in files) {
                val target = localFile(file.id)
                val ok = target.exists() && sha256(target).equals(file.sha256, ignoreCase = true)
                if (!ok) {
                    _progress.value = AssetSyncProgress(
                        bundleVersion = manifest.bundleVersion,
                        total = files.size,
                        done = done,
                        message = "Pobieram ${file.id}…",
                        complete = false
                    )
                    download(file.url, target)
                    val digest = sha256(target)
                    if (!digest.equals(file.sha256, ignoreCase = true)) {
                        target.delete()
                        error("Hash mismatch for ${file.id}")
                    }
                }
                done++
                _progress.value = AssetSyncProgress(
                    bundleVersion = manifest.bundleVersion,
                    total = files.size,
                    done = done,
                    message = "Paczka alarmów $done/${files.size}",
                    complete = false
                )
            }
            settings.setAssetsBundleVersion(manifest.bundleVersion)
            settings.setAssetsMessage("Paczka v${manifest.bundleVersion} OK")
            refreshStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Asset sync failed", e)
            val p = _progress.value.copy(message = "Błąd paczki: ${e.message}", complete = false)
            _progress.value = p
            settings.setAssetsMessage(p.message)
            p
        }
    }

    private suspend fun resolveAssetsUrl(): String {
        val cfg = settings.settings.first()
        if (cfg.assetsUrl.isNotBlank()) return cfg.assetsUrl
        // Try version.json for assetsUrl
        try {
            val req = Request.Builder().url(cfg.otaUrl.ifBlank { OtaDefaults.VERSION_JSON_URL }).get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()?.trim()?.removePrefix("\uFEFF").orEmpty()
                    val url = JSONObject(body).optString("assetsUrl", "")
                    if (url.isNotBlank()) {
                        settings.setAssetsUrl(url)
                        return url
                    }
                }
            }
        } catch (_: Exception) {
        }
        return OtaDefaults.ASSETS_JSON_URL
    }

    private fun fetchManifest(url: String): Manifest {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string()?.trim()?.removePrefix("\uFEFF") ?: error("empty")
            val json = JSONObject(body)
            val arr = json.getJSONArray("files")
            val files = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        AssetFile(
                            id = o.getString("id"),
                            url = o.getString("url"),
                            sha256 = o.getString("sha256"),
                            kind = o.optString("kind", "")
                        )
                    )
                }
            }
            return Manifest(json.getInt("bundleVersion"), files)
        }
    }

    private fun download(url: String, target: File) {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download HTTP ${resp.code} $url")
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            resp.body?.byteStream()?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("empty body")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Manifest(val bundleVersion: Int, val files: List<AssetFile>)

    companion object {
        private const val TAG = "AssetSync"
    }
}

object AlarmAssetIds {
    val ALL = listOf(
        "tone_modulated_announce",
        "tone_continuous_clear",
        "tone_evacuate",
        "tone_lockdown",
        "tone_shelter",
        "tone_bomb_chime",
        "voice_evac_fire",
        "voice_evac_drill",
        "voice_bomb",
        "voice_lockdown",
        "voice_shelter",
        "voice_air_raid",
        "voice_all_clear",
        "voice_test_short"
    )
}
