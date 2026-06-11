package com.eeter.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eeter.data.AUDIO_FOCUS_CONTINUE
import com.eeter.data.AUDIO_FOCUS_PAUSE
import com.eeter.data.AUDIO_FOCUS_RESTART
import com.eeter.data.FavoritesStore
import com.eeter.data.SettingsStore
import com.eeter.data.Station
import com.eeter.data.Stations
import com.eeter.playback.MediaItems
import com.eeter.playback.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Drawer background, matching the reference's dark-navy menu. */
private val DrawerColor = Color(0xFF0B1A2E)
private val DrawerSelected = Color(0xFF12263F)
private val DefaultBrand = Color(0xFF1A1A1E)

class MainActivity : ComponentActivity() {

    private var controller by mutableStateOf<MediaController?>(null)
    private val eq = EqualizerController()
    private lateinit var settings: SettingsStore

    // Cached settings used by the (non-composable) play path.
    private var highQuality = true
    private var wifiOnly = false

    // Current favorites, used to build the playback queue (enables notification next/prev).
    private var favorites: List<Station> = emptyList()

    // Autoplay runs at most once per launch (a fresh Activity = a cold start).
    private var autoplayChecked = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch { settings.highQuality.collect { highQuality = it } }
        lifecycleScope.launch { settings.wifiOnly.collect { wifiOnly = it } }
        lifecycleScope.launch {
            FavoritesStore(this@MainActivity).favoriteIds.collect { ids ->
                favorites = ids.mapNotNull { Stations.byId[it] }
            }
        }
        // Apply the equalizer state on launch (defaults to on + Classical).
        lifecycleScope.launch {
            val enabled = settings.eqEnabled.first()
            val presetPref = settings.eqPreset.first()
            val names = eq.presetNames()
            if (names.isNotEmpty()) {
                val classical = names.indexOfFirst { it.equals("Classical", ignoreCase = true) }.coerceAtLeast(0)
                val preset = if (presetPref in names.indices) presetPref else classical
                eq.usePreset(preset)
                eq.setEnabled(enabled)
            }
        }
        setContent {
            EeterTheme {
                AppScreen(controller = controller, eq = eq, onPlay = ::play, onClose = ::closeApp)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get()
            maybeAutoplay()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        controller?.release()
        controller = null
    }

    override fun onDestroy() {
        eq.release()
        super.onDestroy()
    }

    private fun maybeAutoplay() {
        if (autoplayChecked) return
        val c = controller ?: return
        autoplayChecked = true
        // Leave it alone only if a previous (surviving) session is actually playing;
        // a stale/stopped session should still be (re)started.
        if (c.isPlaying) return
        lifecycleScope.launch {
            if (!settings.autoplay.first()) return@launch
            val id = settings.lastStationId.first()
            val station = Stations.byId[id] ?: return@launch
            play(station)
        }
    }

    private fun play(station: Station, browseAll: Boolean = false) {
        val c = controller ?: return
        if (wifiOnly && !onWifi()) {
            Toast.makeText(this, "Wi-Fi only is on — not on Wi-Fi", Toast.LENGTH_SHORT).show()
            return
        }
        // Build the queue from whichever list the station was picked in, so the media
        // notification / lock screen previous/next traverse the same stations as the
        // on-screen arrows: the full "All" list when browsing All, else favorites
        // (a non-favorite is appended so it still has a 1-item queue to sit in).
        val queue = when {
            browseAll -> Stations.all
            favorites.any { it.id == station.id } -> favorites
            else -> favorites + station
        }
        val startIndex = queue.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
        val items = queue.map { MediaItems.stationItem(it, packageName, true, highQuality) }
        c.setMediaItems(items, startIndex, C.TIME_UNSET)
        c.prepare()
        c.play()
        lifecycleScope.launch { settings.setLastStation(station.id) }
    }

    private fun onWifi(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun closeApp() {
        controller?.run {
            pause()
            stop()
        }
        finishAndRemoveTask()
    }
}

@Composable
private fun AppScreen(
    controller: MediaController?,
    eq: EqualizerController,
    onPlay: (Station, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val favStore = remember { FavoritesStore(context) }
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    var showEq by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val favoriteIds by favStore.favoriteIds.collectAsStateWithLifecycle(initialValue = emptyList())

    // Mirror controller playback state into Compose.
    var isPlaying by remember { mutableStateOf(false) }
    var nowTitle by remember { mutableStateOf<String?>(null) }
    var nowArtist by remember { mutableStateOf<String?>(null) }
    var currentId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(controller) {
        val c = controller
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                nowTitle = player.mediaMetadata.title?.toString()
                nowArtist = player.mediaMetadata.artist?.toString()
                currentId = player.currentMediaItem?.mediaId
            }
        }
        if (c != null) {
            isPlaying = c.isPlaying
            nowTitle = c.mediaMetadata.title?.toString()
            nowArtist = c.mediaMetadata.artist?.toString()
            currentId = c.currentMediaItem?.mediaId
            c.addListener(listener)
        }
        onDispose { c?.removeListener(listener) }
    }

    val favSet = favoriteIds.toSet()
    val favorites = favoriteIds.mapNotNull { Stations.byId[it] }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // The player swipes through a list of stations. When the user picks a station from
    // the "All" section it swipes through the whole station list; otherwise it swipes
    // through favorites (a non-favorite is appended so it can still be shown/favorited).
    var selected by remember { mutableStateOf<Station?>(null) }
    var navAll by remember { mutableStateOf(false) }
    val sel = selected
    val base = if (navAll) Stations.all else favorites
    val pages = remember(base, sel) {
        if (sel != null && base.none { it.id == sel.id }) base + sel else base
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // Once playback is active, switching stations (arrows/swipe) keeps playing the
    // newly shown station without needing to press play again. drop(1) ignores the
    // initial (non-user) page value so it can't override the actual playing station.
    LaunchedEffect(pages, controller) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { p ->
            val s = pages.getOrNull(p) ?: return@collect
            val c = controller ?: return@collect
            if (c.isPlaying && c.currentMediaItem?.mediaId != MediaItems.stationMediaId(s.id)) {
                onPlay(s, navAll)
            }
        }
    }

    // Open directly on the last-played station that AutoPlay resumes, instead of
    // starting on the first favorite (NRJ FM) and visibly sliding across to it.
    // Runs once, as soon as the favorites list is ready; later navigation
    // (swipe / arrows / notification) is left untouched.
    val startStationId by produceState(initialValue = -1, settings) {
        value = settings.lastStationId.first()
    }
    var openedOnLastStation by remember { mutableStateOf(false) }
    LaunchedEffect(pages, startStationId) {
        if (openedOnLastStation || pages.isEmpty() || startStationId < 0) return@LaunchedEffect
        val idx = pages.indexOfFirst { it.id == startStationId }
        if (idx >= 0) {
            pagerState.scrollToPage(idx)
            openedOnLastStation = true
        }
    }

    // While playing, follow the player's current station (e.g. when changed from the
    // media notification / lock screen) by scrolling the pager to it.
    LaunchedEffect(currentId, isPlaying, pages) {
        if (!isPlaying) return@LaunchedEffect
        val id = currentId ?: return@LaunchedEffect
        val idx = pages.indexOfFirst { MediaItems.stationMediaId(it.id) == id }
        if (idx >= 0 && idx != pagerState.currentPage) pagerState.animateScrollToPage(idx)
    }

    if (showEq) {
        EqualizerScreen(eq = eq, settings = settings, onBack = { showEq = false })
        return
    }
    if (showSettings) {
        SettingsScreen(settings = settings, onBack = { showSettings = false })
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            StationDrawer(
                favorites = favorites,
                currentId = currentId,
                onSelect = { station, fromAll ->
                    navAll = fromAll
                    onPlay(station, fromAll)
                    selected = station
                    val list = if (fromAll) Stations.all else favorites
                    val idx = (if (list.any { it.id == station.id }) list else list + station)
                        .indexOfFirst { it.id == station.id }
                    scope.launch {
                        if (idx >= 0) pagerState.animateScrollToPage(idx)
                        drawerState.close()
                    }
                },
            )
        },
    ) {
        // Wide/landscape displays (e.g. 1920x720 car head units) get a grid of big
        // logo tiles instead of the swipe player: tap plays, the view stays put.
        val config = LocalConfiguration.current
        if (config.screenWidthDp > config.screenHeightDp) {
            StationGridScreen(
                favorites = favorites,
                currentId = currentId,
                isPlaying = isPlaying,
                statusLine = if (currentId != null) nowPlayingLine(nowTitle, nowArtist) else null,
                onMenu = { scope.launch { drawerState.open() } },
                onOpenEq = { showEq = true },
                onOpenSettings = { showSettings = true },
                onClose = onClose,
                onTile = { s ->
                    val c = controller
                    val isCurrent = currentId == MediaItems.stationMediaId(s.id)
                    if (isCurrent && c != null) {
                        if (c.isPlaying) c.pause() else c.play()
                    } else {
                        navAll = false
                        selected = s
                        onPlay(s, false)
                    }
                },
            )
            return@ModalNavigationDrawer
        }
        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().background(DefaultBrand))
            return@ModalNavigationDrawer
        }
        val page = pagerState.currentPage.coerceIn(0, pages.size - 1)
        val pageStation = pages[page]
        val isCurrent = currentId == MediaItems.stationMediaId(pageStation.id)

        PlayerScreen(
            pages = pages,
            pagerState = pagerState,
            pageStation = pageStation,
            isPlaying = isPlaying && isCurrent,
            statusLine = if (currentId != null) nowPlayingLine(nowTitle, nowArtist) else null,
            isFavorite = pageStation.id in favSet,
            onMenu = { scope.launch { drawerState.open() } },
            onOpenEq = { showEq = true },
            onOpenSettings = { showSettings = true },
            onClose = onClose,
            onPrev = { if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) } },
            onNext = { if (page < pages.size - 1) scope.launch { pagerState.animateScrollToPage(page + 1) } },
            onTogglePlay = {
                val c = controller
                if (isCurrent && c != null && c.isPlaying) c.pause() else onPlay(pageStation, navAll)
            },
            onToggleFav = { scope.launch { favStore.toggle(pageStation.id) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(
    pages: List<Station>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    pageStation: Station,
    isPlaying: Boolean,
    statusLine: String?,
    isFavorite: Boolean,
    onMenu: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFav: () -> Unit,
) {
    val brand = rememberDominantColor(pageStation.logoRes, pageStation.brand)
    val panel = lerp(brand, Color.Black, 0.22f)
    val onBrand = if (brand.luminance() > 0.6f) Color(0xCC000000) else Color.White
    val page = pagerState.currentPage.coerceIn(0, pages.size - 1)

    Box(Modifier.fillMaxSize().background(brand)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Top bar: hamburger + overflow menu.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = onBrand)
                }
                Spacer(Modifier.weight(1f))
                OverflowMenu(tint = onBrand, onEqualizer = onOpenEq, onSettings = onOpenSettings, onClose = onClose)
            }

            // Logo pager with prev/next arrows + a single centered play disc.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { p ->
                    val s = pages[p]
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (s.logoRes != 0) {
                            AsyncImage(
                                model = s.logoRes,
                                contentDescription = s.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(0.62f).padding(8.dp),
                            )
                        } else {
                            Text(
                                text = displayName(s.name),
                                color = onBrand,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }
                // Play/pause disc over the centre of the logo.
                Box(
                    Modifier.align(Alignment.Center).size(96.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.18f))
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(56.dp),
                    )
                }
                if (page > 0) {
                    IconButton(onClick = onPrev, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous station", tint = onBrand, modifier = Modifier.size(40.dp))
                    }
                }
                if (page < pages.size - 1) {
                    IconButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next station", tint = onBrand, modifier = Modifier.size(40.dp))
                    }
                }
            }

            // Now-playing status (artist - title), marquee like the reference.
            Text(
                text = statusLine ?: displayName(pageStation.name),
                color = onBrand,
                fontWeight = if (statusLine != null) FontWeight.SemiBold else FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).basicMarquee(),
            )

            ControlPanel(panel = panel, onBrand = onBrand, isFavorite = isFavorite, onToggleFav = onToggleFav)
        }
    }
}

/** Tile background/highlight for the landscape favorites grid. */
private val TileColor = Color(0xFF12263F)

/**
 * Landscape favorites grid for wide displays (car head units): 4 columns x 3 rows
 * of big logo buttons. Tapping a tile plays that station without leaving the grid;
 * tapping the playing tile toggles pause. With >12 favorites the grid paginates:
 * swipe left/right between pages (indicator dots at the bottom).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationGridScreen(
    favorites: List<Station>,
    currentId: String?,
    isPlaying: Boolean,
    statusLine: String?,
    onMenu: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onTile: (Station) -> Unit,
) {
    // Volume bar overlay: shown by the top-bar volume button, hides 5 s after
    // the last interaction (each slider touch bumps volumeTouch to restart it).
    var showVolume by remember { mutableStateOf(false) }
    var volumeTouch by remember { mutableIntStateOf(0) }
    LaunchedEffect(showVolume, volumeTouch) {
        if (showVolume) {
            delay(5_000)
            showVolume = false
        }
    }

    Box(Modifier.fillMaxSize().background(DrawerColor)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Top bar: hamburger + now-playing marquee + volume + overflow menu.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
                }
                Text(
                    text = statusLine ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).basicMarquee(),
                )
                IconButton(onClick = { showVolume = !showVolume }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume", tint = Color.White)
                }
                OverflowMenu(tint = Color.White, onEqualizer = onOpenEq, onSettings = onOpenSettings, onClose = onClose)
            }

            // 12 tiles (4x3) per page; extra favorites go onto further swipe pages.
            val gridPages = remember(favorites) { favorites.chunked(12) }
            val gridPager = rememberPagerState(pageCount = { gridPages.size })
            val spacing = 10.dp
            HorizontalPager(state = gridPager, modifier = Modifier.weight(1f).fillMaxWidth()) { p ->
                val stations = gridPages[p]
                Column(
                    Modifier.fillMaxSize().padding(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    for (row in 0 until 3) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            for (col in 0 until 4) {
                                val s = stations.getOrNull(row * 4 + col)
                                if (s != null) {
                                    val isCurrent = currentId == MediaItems.stationMediaId(s.id)
                                    StationTile(
                                        station = s,
                                        highlighted = isCurrent && isPlaying,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onClick = { onTile(s) },
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            // Page indicator dots (only when there is more than one page).
            if (gridPages.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(gridPages.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape)
                                .background(
                                    if (i == gridPager.currentPage) Color.White
                                    else Color.White.copy(alpha = 0.35f)
                                ),
                        )
                    }
                }
            }
        }

        if (showVolume) {
            VolumeOverlay(
                onInteract = { volumeTouch++ },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            )
        }
    }
}

/** Floating volume bar: icon + slider + percentage, on a translucent pill. */
@Composable
private fun VolumeOverlay(onInteract: () -> Unit, modifier: Modifier = Modifier) {
    val vol = rememberSystemVolume()
    Row(
        modifier
            .fillMaxWidth(0.55f)
            .clip(RoundedCornerShape(28.dp))
            .background(TileColor.copy(alpha = 0.95f))
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp),
        )
        Slider(
            value = vol.volume.toFloat(),
            onValueChange = {
                vol.set(it.toInt())
                onInteract()
            },
            valueRange = 0f..vol.max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(
            text = "${vol.volume * 100 / vol.max}%",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StationTile(
    station: Station,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(TileColor)
            .then(
                if (highlighted) Modifier.border(3.dp, Color.White.copy(alpha = 0.9f), shape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (station.logoRes != 0) {
            AsyncImage(
                model = station.logoRes,
                contentDescription = displayName(station.name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(14.dp),
            )
        } else {
            Text(
                text = displayName(station.name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

@Composable
private fun OverflowMenu(tint: Color, onEqualizer: () -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Equalizer") }, onClick = { expanded = false; onEqualizer() })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { expanded = false; onSettings() })
            DropdownMenuItem(text = { Text("Close application") }, onClick = { expanded = false; onClose() })
        }
    }
}

/** System media volume mirrored into Compose state, with a setter. */
private class VolumeState(val max: Int, initial: Int, private val audio: AudioManager) {
    var volume by mutableIntStateOf(initial)
    fun set(v: Int) {
        volume = v.coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
}

/** Tracks the system media volume (kept in sync with hardware buttons, etc.). */
@Composable
private fun rememberSystemVolume(): VolumeState {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val state = remember {
        VolumeState(
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            initial = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            audio = audio,
        )
    }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return state
}

@Composable
private fun ControlPanel(panel: Color, onBrand: Color, isFavorite: Boolean, onToggleFav: () -> Unit) {
    val vol = rememberSystemVolume()

    Column(
        Modifier.fillMaxWidth().background(panel).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onToggleFav, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorite",
                tint = onBrand,
                modifier = Modifier.size(30.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = onBrand.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            Slider(
                value = vol.volume.toFloat(),
                onValueChange = { vol.set(it.toInt()) },
                valueRange = 0f..vol.max.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = onBrand,
                    activeTrackColor = onBrand,
                    inactiveTrackColor = onBrand.copy(alpha = 0.3f),
                ),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun StationDrawer(
    favorites: List<Station>,
    currentId: String?,
    onSelect: (Station, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    fun matches(s: Station) = q.isEmpty() || displayName(s.name).lowercase().contains(q)
    val recommended = favorites.filter(::matches)
    val all = Stations.all.filter(::matches)

    ModalDrawerSheet(
        drawerContainerColor = DrawerColor,
        drawerContentColor = Color.White,
        modifier = Modifier.width(300.dp),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SearchField(query = query, onChange = { query = it })
            LazyColumn(Modifier.weight(1f)) {
                item { DrawerSection(icon = Icons.Filled.Star, label = "Favorites") }
                items(recommended, key = { "r${it.id}" }) { s -> DrawerRow(s, currentId) { onSelect(s, false) } }
                item { DrawerSection(icon = Icons.AutoMirrored.Filled.List, label = "All") }
                items(all, key = { "a${it.id}" }) { s -> DrawerRow(s, currentId) { onSelect(s, true) } }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF12263F))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF8AA0B8), modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (query.isEmpty()) Text("Search", color = Color(0xFF8AA0B8), fontSize = 15.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DrawerSection(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        Modifier.fillMaxWidth().background(DrawerSelected).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun DrawerRow(station: Station, currentId: String?, onClick: () -> Unit) {
    val isCurrent = currentId == MediaItems.stationMediaId(station.id)
    Text(
        text = displayName(station.name),
        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val highQuality by settings.highQuality.collectAsStateWithLifecycle(initialValue = true)
    val autoplay by settings.autoplay.collectAsStateWithLifecycle(initialValue = true)
    val wifiOnly by settings.wifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val audioFocus by settings.audioFocus.collectAsStateWithLifecycle(initialValue = AUDIO_FOCUS_PAUSE)
    var showFocusDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Settings", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        SettingSwitch(
            title = "Quality",
            caption = "Use the high-quality stream.",
            checked = highQuality,
            onChange = { scope.launch { settings.setHighQuality(it) } },
        )
        SettingSwitch(
            title = "AutoPlay",
            caption = "Play radio automatically on app startup.",
            checked = autoplay,
            onChange = { scope.launch { settings.setAutoplay(it) } },
        )
        SettingSwitch(
            title = "WiFi Only",
            caption = "Play radio only on Wi-Fi.",
            checked = wifiOnly,
            onChange = { scope.launch { settings.setWifiOnly(it) } },
        )
        SettingBasic(
            title = "Audio focus",
            caption = "How to behave during lost audio focus.",
            onClick = { showFocusDialog = true },
        )
    }

    if (showFocusDialog) {
        AudioFocusDialog(
            current = audioFocus,
            onSelect = { scope.launch { settings.setAudioFocus(it) }; showFocusDialog = false },
            onDismiss = { showFocusDialog = false },
        )
    }
}

private val audioFocusOptions = listOf(
    AUDIO_FOCUS_CONTINUE to "Continue playing anyway",
    AUDIO_FOCUS_PAUSE to "Pause stream",
    AUDIO_FOCUS_RESTART to "Restart stream after regaining focus",
)

@Composable
private fun AudioFocusDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Audio focus") },
        text = {
            Column {
                audioFocusOptions.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == current, onClick = { onSelect(value) })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingBasic(title: String, caption: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(caption, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

@Composable
private fun SettingSwitch(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(caption, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EqualizerScreen(eq: EqualizerController, settings: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(eq.enabled) }
    var current by remember { mutableStateOf(eq.currentPreset()) }
    val presets = remember { eq.presetNames() }
    val bandCount = remember { eq.bandCount() }
    val range = remember { eq.bandLevelRange() }
    // Band levels, refreshed whenever a preset is applied.
    var levels by remember { mutableStateOf((0 until bandCount).map { eq.bandLevel(it) }) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { levels = (0 until bandCount).map { eq.bandLevel(it) } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        // Toolbar: back, title, enable switch.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Equalizer", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; eq.setEnabled(it); scope.launch { settings.setEqEnabled(it) } }, modifier = Modifier.padding(end = 12.dp))
        }

        if (presets.isEmpty() && bandCount == 0) {
            Text(
                "No equalizer available on this device.",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }

        // Preset chips.
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEachIndexed { i, name ->
                FilterChip(
                    selected = i == current,
                    onClick = {
                        eq.setEnabled(true); enabled = true
                        eq.usePreset(i); current = i
                        refresh++
                        scope.launch { settings.setEqEnabled(true); settings.setEqPreset(i) }
                    },
                    label = { Text(name) },
                )
            }
        }

        // Band sliders.
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            for (i in 0 until bandCount) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        eq.bandLabel(i),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(64.dp),
                    )
                    Slider(
                        value = levels.getOrElse(i) { 0 }.toFloat(),
                        onValueChange = { v ->
                            eq.setEnabled(true); enabled = true
                            eq.setBandLevel(i, v.toInt())
                            levels = levels.toMutableList().also { it[i] = v.toInt() }
                            current = -1 // custom
                        },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Loads the logo and extracts a vibrant/dominant brand color for the background. */
@Composable
private fun rememberDominantColor(logoRes: Int, override: Long = 0): Color {
    val context = LocalContext.current
    var color by remember(logoRes, override) {
        mutableStateOf(if (override != 0L) Color(override) else DefaultBrand)
    }
    LaunchedEffect(logoRes, override) {
        if (override != 0L || logoRes == 0) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            val req = ImageRequest.Builder(context).data(logoRes).allowHardware(false).build()
            val result = ImageLoader(context).execute(req)
            (result.drawable as? BitmapDrawable)?.bitmap
        } ?: return@LaunchedEffect
        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
        if (swatch != null) color = Color(swatch.rgb)
    }
    return color
}

private fun nowPlayingLine(title: String?, artist: String?): String? = when {
    title.isNullOrEmpty() -> null
    artist.isNullOrEmpty() -> title
    else -> "$artist - $title"
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Decode any double-escaped \\uXXXX sequences left in the station names. */
private fun displayName(name: String): String {
    if (!name.contains("\\u")) return name
    val sb = StringBuilder()
    var i = 0
    while (i < name.length) {
        if (i + 1 < name.length && name[i] == '\\' && name[i + 1] == 'u' && i + 5 < name.length) {
            val code = name.substring(i + 2, i + 6).toIntOrNull(16)
            if (code != null) {
                sb.append(code.toChar()); i += 6; continue
            }
        }
        sb.append(name[i]); i++
    }
    return sb.toString()
}
