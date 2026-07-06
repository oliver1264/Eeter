package com.eeter.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * Web "now playing" poller for the stations whose audio streams carry no usable
 * song metadata. Polls the broadcaster's web API and reports "Artist - Title"
 * via [onUpdate] on the main thread.
 *
 * Kotlin reimplementation of the old `ya.NowPlay` smali patch.
 * kind: 1 = Star FM, 2 = Power Hit Radio, 3 = Sky Plus DnB, 4 = Star FM Plus.
 * (Star FM / Star FM Plus moved here in v3.5: their Icecast streams started
 * sending an empty StreamTitle, so ICY metadata no longer works for them.
 * Their get_onair_data AJAX endpoint serves Star FM EESTI's feed for the main
 * Star FM page id — a bug on their end — so kinds 1/4 scrape the on-air widget
 * out of the server-rendered station page instead, reading only the first ~50 KB.)
 */
class NowPlay(private val onUpdate: (artist: String, title: String) -> Unit) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start(kind: Int) {
        stop()
        if (kind !in 1..4) return
        job = scope.launch {
            delay(2000)
            while (isActive) {
                val text = runCatching { fetchWeb(kind) }.getOrNull()
                if (!text.isNullOrEmpty()) {
                    val dash = text.indexOf(" - ")
                    val artist = if (dash > 0) text.substring(0, dash) else text
                    val title = if (dash > 0) text.substring(dash + 3) else ""
                    withContext(Dispatchers.Main) { onUpdate(artist, title) }
                }
                // The scraped pages are cached ~1 min server-side (their own site
                // refreshes every 60 s), so poll those more gently than the APIs.
                delay(if (kind == 1 || kind == 4) 30_000L else 8_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {

    /** One-shot web "now playing" lookup, also used by [StationProbe] for the grid tiles. */
    fun fetchWeb(kind: Int): String? = when (kind) {
        1 -> scrapeOnAir("https://raadiod.tv3.ee/starfm/")
        4 -> scrapeOnAir("https://raadiod.tv3.ee/starfmplus/")
        3 -> {
            val o = JSONObject(httpGet("https://skyplus.sky.ee/api/radio-stations/2/now-playing"))
            combine(o.optString("artist"), o.optString("title"))
        }
        else -> {
            val o = JSONObject(
                httpPost(
                    "https://raadiod.tv3.ee/wp-admin/admin-ajax.php?action=get_onair_data",
                    "currentPageID=192&onAirOnly=false",
                    "https://raadiod.tv3.ee/power/",
                ),
            )
            val d = o.optJSONObject("data")
            d?.let { combine(it.optString("currentArtist"), it.optString("currentSong")) }
        }
    }

    /**
     * Pulls the current artist/song out of the server-rendered on-air widget of a
     * raadiod.tv3.ee station page. The widget sits ~43 KB into the HTML, so the
     * (gzipped) response is read only until both fields are found, then the
     * connection is dropped — a few KB on the wire per poll.
     */
    private fun scrapeOnAir(pageUrl: String): String? {
        // Cache-buster keeps CDN page caching from serving a stale song.
        val c = open("$pageUrl?t=${System.currentTimeMillis()}", "GET", null, null)
        return try {
            c.setRequestProperty("Accept-Encoding", "gzip")
            val raw = try {
                c.inputStream
            } catch (e: Exception) {
                c.errorStream ?: throw e
            }
            val input = if (c.contentEncoding == "gzip") GZIPInputStream(raw) else raw
            input.use { stream ->
                val bos = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (bos.size() < 400_000) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    bos.write(buf, 0, n)
                    val html = String(bos.toByteArray(), StandardCharsets.UTF_8)
                    val artist = onAirField(html, "artist")
                    val song = onAirField(html, "song")
                    if (artist != null && song != null) {
                        return combine(decodeEntities(artist), decodeEntities(song))
                    }
                }
                null
            }
        } finally {
            c.disconnect()
        }
    }

    /** Extracts `<span class="on-air__current-<field>">...</span>` (must be complete). */
    private fun onAirField(html: String, field: String): String? =
        Regex("on-air__current-$field\">([^<]*)<").find(html)?.groupValues?.get(1)

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&#038;", "&")
        .replace("&#039;", "'")
        .replace("&#8217;", "’")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

    private fun combine(artistRaw: String?, titleRaw: String?): String? {
        val a = clean(artistRaw)
        val t = clean(titleRaw)
        return when {
            a.isEmpty() && t.isEmpty() -> null
            a.isEmpty() -> t
            t.isEmpty() -> a
            else -> "$a - $t"
        }
    }

    private fun clean(s: String?): String {
        val v = s?.trim().orEmpty()
        return if (v == "null") "" else v
    }

    private fun httpGet(url: String): String = read(open(url, "GET", null, null))

    private fun httpPost(url: String, body: String, referer: String): String =
        read(open(url, "POST", body, referer))

    private fun open(url: String, method: String, body: String?, referer: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
        c.setRequestProperty("Accept", "application/json, text/javascript, */*")
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        if (body != null) {
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            if (referer != null) {
                c.setRequestProperty("Referer", referer)
                c.setRequestProperty("Origin", "https://raadiod.tv3.ee")
            }
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        return c
    }

    private fun read(c: HttpURLConnection): String {
        val input = try {
            c.inputStream
        } catch (e: Exception) {
            c.errorStream ?: throw e
        }
        input.use { stream ->
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                bos.write(buf, 0, n)
            }
            c.disconnect()
            return String(bos.toByteArray(), StandardCharsets.UTF_8)
        }
    }

    } // companion object
}
