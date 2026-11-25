package pl.szkola.radiowezel.ota

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pl.szkola.radiowezel.BuildConfig
import pl.szkola.radiowezel.RadiowezelApp
import pl.szkola.radiowezel.util.KioskHelper
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val force: Boolean,
    val changelog: String
)

sealed class UpdateResult {
    data class UpToDate(val message: String) : UpdateResult()
    data class Available(val info: UpdateInfo, val apkFile: File) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
    data class Skipped(val message: String) : UpdateResult()
}

class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val settings get() = (context.applicationContext as RadiowezelApp).settingsRepository

    suspend fun checkForUpdates(manual: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        val cfg = settings.settings.first()
        if (cfg.otaUrl.isBlank()) {
            val msg = "Brak URL do version.json w ustawieniach"
            settings.setOtaResult(msg)
            return@withContext UpdateResult.Skipped(msg)
        }

        // Auto-check at most once per day unless manual
        if (!manual && cfg.lastOtaCheckAt > 0 &&
            System.currentTimeMillis() - cfg.lastOtaCheckAt < DAY_MS
        ) {
            return@withContext UpdateResult.Skipped("Pominięto (sprawdzono dziś)")
        }

        try {
            val info = fetchVersion(cfg.otaUrl)
            if (info.versionCode <= BuildConfig.VERSION_CODE) {
                val msg = "Aktualna wersja (${BuildConfig.VERSION_NAME})"
                settings.setOtaResult(msg)
                settings.setPendingForceUpdate(false)
                return@withContext UpdateResult.UpToDate(msg)
            }

            val apk = downloadApk(info.apkUrl)
            settings.setOtaResult("Dostępna ${info.versionName}")
            if (info.force) {
                settings.setPendingForceUpdate(true, apk.absolutePath, info.changelog)
            } else {
                settings.setPendingForceUpdate(false, apk.absolutePath, info.changelog)
            }
            UpdateResult.Available(info, apk)
        } catch (e: Exception) {
            val msg = "Błąd OTA: ${e.message}"
            settings.setOtaResult(msg)
            UpdateResult.Error(msg)
        }
    }

    private fun fetchVersion(url: String): UpdateInfo {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("Pusta odpowiedź")
            // Strip BOM if present
            val clean = body.trim().removePrefix("\uFEFF")
            val json = JSONObject(clean)
            return UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName", "?"),
                apkUrl = json.getString("apkUrl"),
                force = json.optBoolean("force", false),
                changelog = json.optString("changelog", "")
            )
        }
    }

    private fun downloadApk(apkUrl: String): File {
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        val out = File(dir, "update.apk")
        if (out.exists()) out.delete()
        val request = Request.Builder().url(apkUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Pobieranie APK HTTP ${response.code}")
            response.body?.byteStream()?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Pusty APK")
        }
        return out
    }

    /**
     * Install at most once at a time. Never auto-called in a tight loop from receivers.
     */
    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (!installInFlight.compareAndSet(false, true)) {
            Log.w(TAG, "Install already in progress — skip")
            return false
        }
        return try {
            if (KioskHelper.isDeviceOwner(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                silentInstall(apkFile)
            } else {
                promptInstall(apkFile)
                true
            }
        } finally {
            // Allow another attempt after a delay (install itself restarts the process)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                installInFlight.set(false)
            }, 60_000L)
        }
    }

    private fun promptInstall(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun silentInstall(apkFile: File): Boolean {
        return try {
            // Abandon any leftover sessions that can spam the UI
            val installer = context.packageManager.packageInstaller
            installer.mySessions.toList().forEach { info ->
                try {
                    installer.abandonSession(info.sessionId)
                } catch (_: Exception) {
                }
            }
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            apkFile.inputStream().use { input ->
                session.openWrite("app.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(callback.intentSender)
            session.close()
            Log.i(TAG, "Silent install committed session=$sessionId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Silent install failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val installInFlight = AtomicBoolean(false)
    }
}
