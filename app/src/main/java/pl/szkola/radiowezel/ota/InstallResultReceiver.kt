package pl.szkola.radiowezel.ota

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives PackageInstaller session commit results (Device Owner silent install). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // No-op: success restarts app via package replace; failures are rare in DO mode.
    }
}
