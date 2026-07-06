package com.eeter.playback

import com.eeter.data.Station
import java.net.HttpURLConnection
import java.net.URL

/**
 * One-shot "now playing" lookup for stations that are NOT currently being played,
 * used by the landscape grid to caption every tile with its current song.
 *
 * Stations with a web API (nowPlayingKind 1..3) are asked there. Everything else
 * gets an ICY metadata probe: connect with `Icy-MetaData: 1`, skip one audio block
 * (`icy-metaint` bytes, typically 16 KB), read the first metadata block and pull
 * `StreamTitle` out of it. Costs one short stream connection per call.
 */
object StationProbe {

    fun nowPlaying(station: Station): String? =
        if (station.nowPlayingKind in 1..4) NowPlay.fetchWeb(station.nowPlayingKind)
        else icyStreamTitle(station.urlLow)

    private fun icyStreamTitle(url: String): String? {
        if (url.endsWith(".m3u8")) return null // HLS carries no ICY metadata
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Icy-MetaData", "1")
            c.setRequestProperty("User-Agent", "Eeter/1.0")
            val metaInt = c.getHeaderFieldInt("icy-metaint", -1)
            if (metaInt <= 0 || metaInt > 256 * 1024) return null
            c.inputStream.use { input ->
                // Skip the first audio block (metaInt bytes).
                var toSkip = metaInt
                val buf = ByteArray(16 * 1024)
                while (toSkip > 0) {
                    val n = input.read(buf, 0, minOf(buf.size, toSkip))
                    if (n < 0) return null
                    toSkip -= n
                }
                // Metadata block: 1 length byte (x16) then "StreamTitle='...';" + padding.
                val len = input.read()
                if (len <= 0) return null
                val meta = ByteArray(len * 16)
                var off = 0
                while (off < meta.size) {
                    val n = input.read(meta, off, meta.size - off)
                    if (n < 0) break
                    off += n
                }
                Regex("StreamTitle='(.*?)';").find(String(meta, 0, off, Charsets.UTF_8))
                    ?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }
}
