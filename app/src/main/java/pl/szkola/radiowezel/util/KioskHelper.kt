package pl.szkola.radiowezel.util

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

object KioskHelper {
    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun startLockTask(activity: Activity) {
        try {
            if (isDeviceOwner(activity)) {
                val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(activity, AdminReceiver::class.java)
                dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
            }
            activity.startLockTask()
        } catch (_: Exception) {
            // Lock task may fail if not whitelisted
        }
    }

    fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (_: Exception) {
        }
    }
}
