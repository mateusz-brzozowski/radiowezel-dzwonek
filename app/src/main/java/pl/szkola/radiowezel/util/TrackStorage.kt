package pl.szkola.radiowezel.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object TrackStorage {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun tracksDir(context: Context): File {
        val dir = File(context.filesDir, "tracks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun importFromUri(context: Context, uri: Uri, displayName: String): File {
        val target = newTarget(context, displayName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Nie można odczytać pliku")
        return target
    }

    fun importFromUrl(context: Context, url: String, displayName: String): File {
        val target = newTarget(context, displayName)
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: error("Pusta odpowiedź")
        }
        if (target.length() <= 0L) {
            target.delete()
            error("Pobrany plik jest pusty")
        }
        return target
    }

    fun importFromBase64(context: Context, base64: String, displayName: String): File {
        val target = newTarget(context, displayName)
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        if (bytes.isEmpty()) error("Pusty plik")
        FileOutputStream(target).use { it.write(bytes) }
        return target
    }

    private fun newTarget(context: Context, displayName: String): File {
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\-ąęółśżźćńĄĘÓŁŚŻŹĆŃ ]"), "_")
        val withExt = if (safeName.contains('.')) safeName else "$safeName.mp3"
        return File(tracksDir(context), "${System.currentTimeMillis()}_$withExt")
    }
}
