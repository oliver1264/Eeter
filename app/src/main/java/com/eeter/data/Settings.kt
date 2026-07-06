package com.eeter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

const val AUDIO_FOCUS_PAUSE = 0
const val AUDIO_FOCUS_CONTINUE = 1
const val AUDIO_FOCUS_RESTART = 2

/** App preferences shown in the Settings screen (overflow menu). */
class SettingsStore(private val context: Context) {

    private val highQualityKey = booleanPreferencesKey("high_quality")
    private val autoplayKey = booleanPreferencesKey("autoplay")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
    private val startOnBootKey = booleanPreferencesKey("start_on_boot")
    private val lastStationKey = intPreferencesKey("last_station")
    private val audioFocusKey = intPreferencesKey("audio_focus")
    private val eqEnabledKey = booleanPreferencesKey("eq_enabled")
    private val eqPresetKey = intPreferencesKey("eq_preset")

    val highQuality: Flow<Boolean> = context.settingsStore.data.map { it[highQualityKey] ?: true }
    val autoplay: Flow<Boolean> = context.settingsStore.data.map { it[autoplayKey] ?: true }
    val wifiOnly: Flow<Boolean> = context.settingsStore.data.map { it[wifiOnlyKey] ?: false }

    /** Launch the app automatically when the device finishes booting (car head units). */
    val startOnBoot: Flow<Boolean> = context.settingsStore.data.map { it[startOnBootKey] ?: true }
    val lastStationId: Flow<Int> = context.settingsStore.data.map { it[lastStationKey] ?: -1 }

    /** 0 = pause stream (default), 1 = continue playing anyway, 2 = restart after regaining focus. */
    val audioFocus: Flow<Int> = context.settingsStore.data.map { it[audioFocusKey] ?: AUDIO_FOCUS_PAUSE }

    /** Equalizer defaults to enabled with the Classical preset (-1 = "use Classical"). */
    val eqEnabled: Flow<Boolean> = context.settingsStore.data.map { it[eqEnabledKey] ?: true }
    val eqPreset: Flow<Int> = context.settingsStore.data.map { it[eqPresetKey] ?: -1 }

    suspend fun setHighQuality(on: Boolean) = set(highQualityKey, on)
    suspend fun setAutoplay(on: Boolean) = set(autoplayKey, on)
    suspend fun setWifiOnly(on: Boolean) = set(wifiOnlyKey, on)
    suspend fun setStartOnBoot(on: Boolean) = set(startOnBootKey, on)

    suspend fun setAudioFocus(value: Int) {
        context.settingsStore.edit { it[audioFocusKey] = value }
    }

    suspend fun setEqEnabled(on: Boolean) = set(eqEnabledKey, on)

    suspend fun setEqPreset(index: Int) {
        context.settingsStore.edit { it[eqPresetKey] = index }
    }

    suspend fun setLastStation(id: Int) {
        context.settingsStore.edit { it[lastStationKey] = id }
    }

    private suspend fun set(key: Preferences.Key<Boolean>, on: Boolean) {
        context.settingsStore.edit { it[key] = on }
    }
}
