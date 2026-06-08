package com.eeter.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.eeter.data.Station

/** Builds MediaItems / browse nodes from [Station]s. */
object MediaItems {
    const val ROOT = "root"
    const val FAVORITES = "favorites"
    const val ALL = "all"
    private const val PREFIX = "st:"

    fun stationMediaId(id: Int) = "$PREFIX$id"

    fun parseStationId(mediaId: String): Int? =
        if (mediaId.startsWith(PREFIX)) mediaId.removePrefix(PREFIX).toIntOrNull() else null

    fun logoUri(s: Station, pkg: String): Uri? =
        if (s.logoRes != 0) Uri.parse("android.resource://$pkg/${s.logoRes}") else null

    fun browsableNode(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()

    fun stationItem(s: Station, pkg: String, withUri: Boolean, highQuality: Boolean = true): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(s.name)
            .setStation(s.name)
            .setArtworkUri(logoUri(s, pkg))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()
        val b = MediaItem.Builder()
            .setMediaId(stationMediaId(s.id))
            .setMediaMetadata(meta)
        if (withUri) b.setUri(if (highQuality) s.urlHigh else s.urlLow)
        return b.build()
    }
}
