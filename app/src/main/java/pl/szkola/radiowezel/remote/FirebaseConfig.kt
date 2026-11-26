package pl.szkola.radiowezel.remote

import android.content.Context
import org.json.JSONObject

data class FirebaseConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val databaseURL: String,
    val messagingSenderId: String,
    val storageBucket: String,
    val deviceEmail: String,
    val devicePassword: String
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() &&
            apiKey != "REPLACE_ME" &&
            projectId.isNotBlank() &&
            databaseURL.isNotBlank() &&
            deviceEmail.isNotBlank() &&
            devicePassword.isNotBlank() &&
            devicePassword != "REPLACE_DEVICE_PASSWORD"
}

object FirebaseConfigLoader {
    fun load(context: Context): FirebaseConfig {
        return try {
            val raw = context.assets.open("firebase_config.json").bufferedReader().use { it.readText() }
            val o = JSONObject(raw)
            FirebaseConfig(
                apiKey = o.optString("apiKey"),
                appId = o.optString("appId"),
                projectId = o.optString("projectId"),
                databaseURL = o.optString("databaseURL"),
                messagingSenderId = o.optString("messagingSenderId", "0"),
                storageBucket = o.optString("storageBucket", ""),
                deviceEmail = o.optString("deviceEmail"),
                devicePassword = o.optString("devicePassword")
            )
        } catch (_: Exception) {
            FirebaseConfig("", "", "", "", "0", "", "", "")
        }
    }
}
