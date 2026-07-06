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
 *     the service then also retries opening the UI across the first minute;
 *  2. tries to open [MainActivity] itself as an early fallback.
 * Also handles MY_PACKAGE_REPLACED, so the app reopens right after an update —
 * which doubles as a test that launching works at all on a given ROM.
 * Controlled by the "Start on boot" switch in Settings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        val isUpdate = action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isBoot && !isUpdate) return
        // Receivers get ~10 s; the DataStore reads are quick.
        val settings = SettingsStore(context)
        val enabled = runCatching {
            runBlocking { settings.startOnBoot.first() }
        }.getOrDefault(true)
        if (!enabled) return

        // On boot, kick off playback of the last station immediately (independent
        // of the UI). Only when the service is guaranteed to reach
        // startForeground(), i.e. it will actually start playing — otherwise the
        // pending FGS start would crash. Not done after app updates (no surprise
        // audio); there the UI launch below still autoplays if it gets through.
        if (isBoot) {
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
        }

        // Open the UI: first try ~2 s in, once more at ~7 s (goAsync keeps the
        // receiver alive that long; the service retries further out). Blocked
        // launches fail silently, so just fire and let the guards dedupe.
        val result = goAsync()
        val handler = Handler(Looper.getMainLooper())
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        handler.postDelayed(
            { if (!MainActivity.isVisible) runCatching { context.startActivity(launch) } },
            2_000,
        )
        handler.postDelayed(
            {
                if (!MainActivity.isVisible) runCatching { context.startActivity(launch) }
                result.finish()
            },
            7_000,
        )
    }
}
