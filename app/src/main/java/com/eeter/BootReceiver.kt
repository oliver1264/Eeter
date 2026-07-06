package com.eeter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.eeter.data.SettingsStore
import com.eeter.data.Stations
import com.eeter.playback.PlaybackService
import com.eeter.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Brings the radio up when the device finishes booting (car head units: the radio
 * comes up with the ignition). Two-pronged, because Android 14 blocks activity
 * launches from the background unless "Display over other apps" is granted:
 *  1. starts [PlaybackService] with BOOT_AUTOPLAY so the last station starts
 *     PLAYING right away (media-playback services may start from boot on A14);
 *  2. tries to open [MainActivity] a few times (head units are often not ready
 *     at the first attempt). Works once the overlay permission is granted or on
 *     permissive ROMs; MainActivity prompts for the grant.
 * Controlled by the "Start on boot" switch in Settings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        // Receivers get ~10 s; the DataStore reads are quick.
        val settings = SettingsStore(context)
        val enabled = runCatching {
            runBlocking { settings.startOnBoot.first() }
        }.getOrDefault(true)
        if (!enabled) return

        // Kick off playback of the last station immediately (independent of the UI).
        // Only when the service is guaranteed to reach startForeground(), i.e. it
        // will actually start playing — otherwise the pending FGS start would crash.
        val autoplay = runCatching {
            runBlocking {
                settings.autoplay.first() && Stations.byId[settings.lastStationId.first()] != null
            }
        }.getOrDefault(false)
        if (autoplay) {
            runCatching {
                context.startForegroundService(
                    Intent(context, PlaybackService::class.java)
                        .setAction(PlaybackService.ACTION_BOOT_AUTOPLAY),
                )
            }
        }

        // Open the UI: first try ~2 s after boot, once more at ~7 s (goAsync keeps
        // the receiver alive; blocked launches fail silently, so just fire both).
        val result = goAsync()
        val handler = Handler(Looper.getMainLooper())
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        handler.postDelayed({ runCatching { context.startActivity(launch) } }, 2_000)
        handler.postDelayed(
            {
                runCatching { context.startActivity(launch) }
                result.finish()
            },
            7_000,
        )
    }
}
