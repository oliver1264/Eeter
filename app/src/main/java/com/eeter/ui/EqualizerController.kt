package com.eeter.ui

import android.media.audiofx.Equalizer

/**
 * Simple wrapper around a global [Equalizer] (audio session 0 = output mix).
 * Global session keeps it independent of the playback service's ExoPlayer instance,
 * which is enough for an MVP. Requires the MODIFY_AUDIO_SETTINGS permission.
 */
class EqualizerController {

    private var eq: Equalizer? = null
    var enabled: Boolean = false
        private set

    private fun ensure() {
        if (eq == null) {
            runCatching { eq = Equalizer(1000, 0) }
        }
    }

    fun presetNames(): List<String> {
        ensure()
        val e = eq ?: return emptyList()
        return (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
    }

    fun setEnabled(on: Boolean) {
        ensure()
        runCatching {
            eq?.enabled = on
            enabled = on
        }
    }

    fun usePreset(index: Int) {
        ensure()
        runCatching { eq?.usePreset(index.toShort()) }
    }

    fun currentPreset(): Int = runCatching { eq?.currentPreset?.toInt() ?: -1 }.getOrDefault(-1)

    // --- Bands ---

    fun bandCount(): Int {
        ensure()
        return runCatching { eq?.numberOfBands?.toInt() ?: 0 }.getOrDefault(0)
    }

    /** [min, max] band gain in millibels. */
    fun bandLevelRange(): IntRange {
        ensure()
        val r = runCatching { eq?.bandLevelRange }.getOrNull() ?: return 0..0
        return r[0].toInt()..r[1].toInt()
    }

    fun bandLevel(index: Int): Int =
        runCatching { eq?.getBandLevel(index.toShort())?.toInt() ?: 0 }.getOrDefault(0)

    fun setBandLevel(index: Int, level: Int) {
        ensure()
        runCatching { eq?.setBandLevel(index.toShort(), level.toShort()) }
    }

    /** Human label for a band's centre frequency, e.g. "60 Hz" or "14 kHz". */
    fun bandLabel(index: Int): String {
        ensure()
        val milliHz = runCatching { eq?.getCenterFreq(index.toShort()) ?: 0 }.getOrDefault(0)
        val hz = milliHz / 1000
        return if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
        enabled = false
    }
}
