package pl.szkola.radiowezel.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object TrackStorage {
    fun tracksDir(context: Context): File {
        val dir = File(context.filesDir, "tracks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun importFromUri(context: Context, uri: Uri, displayName: String): File {
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\-ąęółśżźćńĄĘÓŁŚŻŹĆŃ ]"), "_")
        val target = File(tracksDir(context), "${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Nie można odczytać pliku")
        return target
    }
}
