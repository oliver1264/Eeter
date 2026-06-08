package com.eeter.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.eeter.data.AUDIO_FOCUS_CONTINUE
import com.eeter.data.AUDIO_FOCUS_RESTART
import com.eeter.data.FavoritesStore
import com.eeter.data.SettingsStore
import com.eeter.data.Station
import com.eeter.data.Stations
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Media3 service that drives playback, the media notification, the lock screen, and
 * the Android Auto browse tree (Favorites / All stations). For the 3 stations whose
 * streams carry no song metadata it runs [NowPlay] and injects the web "now playing".
 */
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    private val nowPlay = NowPlay { artist, title -> applyWebMetadata(artist, title) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var audioFocusMode = 0

    /**
     * In-memory snapshot of the user's favorite station ids, kept current by collecting
     * [FavoritesStore]. Android Auto's library callbacks are synchronous (they return a
     * resolved future immediately), so we need the favorites available without suspending.
     */
    @Volatile
    private var favoriteIds: List<Int> = Stations.defaultFavorites().map { it.id }

    override fun onCreate() {
        super.onCreate()

        // Request ICY metadata so Shoutcast/Icecast stations report the current song
        // ("StreamTitle"). ExoPlayer folds it into mediaMetadata.title automatically.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Eeter/1.0")
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // Wrap around the station queue so previous/next are always available.
        player.repeatMode = Player.REPEAT_MODE_ALL

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val kind = mediaItem?.mediaId
                    ?.let { MediaItems.parseStationId(it) }
                    ?.let { Stations.byId[it]?.nowPlayingKind } ?: 0
                if (kind in 1..3) nowPlay.start(kind) else nowPlay.stop()
            }
        })

        // ICY "StreamTitle" metadata for all other Shoutcast/Icecast stations.
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onMetadata(eventTime: AnalyticsListener.EventTime, metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    (metadata.get(i) as? IcyInfo)?.title?.let { applyStreamTitle(it) }
                }
            }
        })

        // "Restart after regaining focus": re-buffer from the live edge when playback
        // resumes after a transient audio-focus loss.
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady &&
                    audioFocusMode == AUDIO_FOCUS_RESTART &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                ) {
                    player.prepare()
                }
            }
        })

        // Apply the audio-focus preference live (handle focus unless "continue anyway").
        scope.launch {
            SettingsStore(this@PlaybackService).audioFocus.collect { mode ->
                audioFocusMode = mode
                player.setAudioAttributes(AudioAttributes.DEFAULT, mode != AUDIO_FOCUS_CONTINUE)
            }
        }

        // Keep the favorites snapshot current for the Android Auto browse tree and the
        // next/previous queue it builds.
        scope.launch {
            FavoritesStore(this@PlaybackService).favoriteIds.collect { ids ->
                if (ids.isNotEmpty()) favoriteIds = ids
            }
        }

        // Tapping the media notification / output card opens the app's main screen.
        val builder = MediaLibrarySession.Builder(this, player, LibraryCallback())
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            val pi = PendingIntent.getActivity(
                this, 0, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setSessionActivity(pi)
        }
        session = builder.build()
    }

    /** Apply an ICY "StreamTitle" (usually "Artist - Title") for kind-0 stations. */
    private fun applyStreamTitle(raw: String) {
        val title = raw.trim()
        if (title.isEmpty()) return
        val kind = player.currentMediaItem?.mediaId
            ?.let { MediaItems.parseStationId(it) }
            ?.let { Stations.byId[it]?.nowPlayingKind } ?: 0
        if (kind in 1..3) return // web poller owns these
        val dash = title.indexOf(" - ")
        if (dash > 0) applyWebMetadata(title.substring(0, dash), title.substring(dash + 3))
        else applyWebMetadata("", title)
    }

    /** Replace the current item's metadata with the web-sourced artist/title. */
    private fun applyWebMetadata(artist: String, title: String) {
        val idx = player.currentMediaItemIndex
        if (idx < 0 || player.mediaItemCount == 0) return
        val cur = player.currentMediaItem ?: return
        // Surfaces like the media-output panel and Android Auto show the `title`
        // field as the prominent line, so fold the "Artist - Title" line into it
        // to keep artist-first ordering everywhere (matching the main window).
        val display = when {
            artist.isNotEmpty() && title.isNotEmpty() -> "$artist - $title"
            artist.isNotEmpty() -> artist
            else -> title
        }
        val meta = cur.mediaMetadata.buildUpon()
            .setTitle(display)
            .setArtist(null)
            .build()
        player.replaceMediaItem(idx, cur.buildUpon().setMediaMetadata(meta).build())
    }

    /** The user's favorite stations, in canonical order, resolved from the snapshot. */
    private fun favoriteStations(): List<Station> = favoriteIds.mapNotNull { Stations.byId[it] }

    /**
     * The full queue a tapped station should play within, so next/previous traverse the
     * whole list. A favorite plays inside the favorites queue; anything else plays inside
     * the complete station list.
     */
    private fun queueFor(stationId: Int): List<Station> =
        if (stationId in favoriteIds) favoriteStations() else Stations.all

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Dismissing the app (swipe from recents) should fully stop playback, not
        // keep streaming in the background. Stop the player, clear the queue so the
        // media notification is dismissed, then stop the service.
        player.stop()
        player.clearMediaItems()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        nowPlay.stop()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(MediaItems.browsableNode(MediaItems.ROOT, "Eeter"), params),
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val pkg = packageName
            val items: List<MediaItem> = when (parentId) {
                MediaItems.ROOT -> listOf(
                    MediaItems.browsableNode(MediaItems.FAVORITES, "Favorites"),
                    MediaItems.browsableNode(MediaItems.ALL, "All stations"),
                )
                MediaItems.FAVORITES -> favoriteStations().map { MediaItems.stationItem(it, pkg, false) }
                MediaItems.ALL -> Stations.all.map { MediaItems.stationItem(it, pkg, false) }
                else -> emptyList()
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params),
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val st = MediaItems.parseStationId(mediaId)?.let { Stations.byId[it] }
                ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            return Futures.immediateFuture(
                LibraryResult.ofItem(MediaItems.stationItem(st, packageName, true), null),
            )
        }

        /** Browsers/AA hand back items with only a mediaId — resolve them to playable URIs. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val st = MediaItems.parseStationId(item.mediaId)?.let { Stations.byId[it] }
                if (st != null) MediaItems.stationItem(st, packageName, true) else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /**
         * Android Auto (and the notification / lock screen) plays a station by handing us a
         * single item tapped in the browse tree. If we played just that one item the queue
         * would have length 1 and the next/previous arrows would have nowhere to go. Expand
         * the selection into its full sibling list (favorites, or all stations) positioned at
         * the tapped station, so the arrows step through every station.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val tappedId = mediaItems.singleOrNull()?.mediaId?.let { MediaItems.parseStationId(it) }
            if (tappedId != null) {
                val queue = queueFor(tappedId)
                val idx = queue.indexOfFirst { it.id == tappedId }
                if (idx >= 0) {
                    val resolved = queue.map { MediaItems.stationItem(it, packageName, true) }
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(resolved, idx, startPositionMs),
                    )
                }
            }
            // Already a multi-item queue (or unknown ids): just resolve each to a playable URI.
            val resolved = mediaItems.map { item ->
                val st = MediaItems.parseStationId(item.mediaId)?.let { Stations.byId[it] }
                if (st != null) MediaItems.stationItem(st, packageName, true) else item
            }
            val safeStart = if (startIndex == C.INDEX_UNSET) 0 else startIndex
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(resolved, safeStart, startPositionMs),
            )
        }

        /**
         * "Resume playback" from Android Auto / Bluetooth (the play button pressed with no
         * active queue) restores the full favorites list starting at the first favorite, so
         * the arrows work immediately on connect instead of being stuck on a single station.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val favs = favoriteStations().ifEmpty { Stations.all }
            val resolved = favs.map { MediaItems.stationItem(it, packageName, true) }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(resolved, 0, C.TIME_UNSET),
            )
        }
    }
}
