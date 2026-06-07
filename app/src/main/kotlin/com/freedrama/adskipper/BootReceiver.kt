package com.freedrama.adskipper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/**
 * Receives BOOT_COMPLETED and optionally auto-starts the service.
 * Note: MediaProjection cannot be re-obtained without user interaction,
 * so on boot we only launch MainActivity for the user to restart manually.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs: SharedPreferences = context.getSharedPreferences(
                SkipConfig.PREFS_NAME, Context.MODE_PRIVATE
            )
            val autoStart = prefs.getBoolean(SkipConfig.PREF_AUTO_START, false)
            if (autoStart) {
                // Launch MainActivity so user can re-grant screen capture
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
