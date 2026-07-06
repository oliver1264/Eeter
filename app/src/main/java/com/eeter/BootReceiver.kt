package com.eeter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eeter.data.SettingsStore
import com.eeter.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Launches the app when the device finishes booting, so on a car head unit the
 * radio comes up with the ignition (and AutoPlay resumes the last station).
 * Controlled by the "Start on boot" switch in Settings. Phones typically block
 * background activity launches, so in practice this is a head-unit feature.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        // Receivers get ~10 s; the DataStore read is quick.
        val enabled = runCatching {
            runBlocking { SettingsStore(context).startOnBoot.first() }
        }.getOrDefault(true)
        if (!enabled) return
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    }
}
