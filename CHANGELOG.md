# Changelog

All notable changes to Eeter are documented here.

## v4.3 — 2026-07-06

- Launcher-style tile rearranging on the grid: long-press a station tile and
  all tiles start wobbling; drag the held tile anywhere and the others shift
  out of the way. Tap anywhere (a tile or the background) to save the layout
  and exit. The saved order persists across restarts and also drives the
  drawer and the playback queue. While rearranging, page swiping is paused
  and the top bar shows "Move the tiles — tap anywhere to save".

## v4.2 — 2026-07-06

- Landscape grid is now 5 columns x 3 rows (15 stations per swipe page,
  was 4 x 3 / 12).

## v4.1 — 2026-07-06

- Sky Plus now-playing restored: its stream started sending an empty ICY
  StreamTitle (the same failure Star FM had), so Sky Plus now uses the
  sky.ee web API (radio-station id 1) like Sky Plus DnB already did (id 2).
  New nowPlayingKind 5.

## v4.0 — 2026-07-06

- Station name removed from under the tiles — the logo speaks for itself.
  Each tile is now just the artwork card plus the white artist – track line,
  and the cards grew taller with the freed space.

## v3.9 — 2026-07-06

- Artist – track line under each tile is now full white (was 50% gray).
- Bigger station buttons: grid spacing tightened (10 → 6 dp), caption gap
  reduced, and the logo's padding inside the card halved so the artwork
  fills more of the tile.

## v3.8 — 2026-07-06

- Station grid page background is now pure black (was dark navy); the
  station cards themselves keep their navy tile color, matching the
  reference design's black backdrop.

## v3.7 — 2026-07-06

- Station tiles restyled to the Spotify-like reference design (app_icons.jpg
  on Drive): each station is now a square rounded artwork card with the
  station name below it in gray, and the current song as a smaller second
  line under the name. Previously the song line sat inside the tile and the
  card stretched to fill the whole grid cell.

## v3.6 — 2026-07-06

- Star FM now shows the CORRECT station's artist/track. tv3's now-playing
  API turned out to serve Star FM Eesti's feed for the main Star FM page id
  (a bug on their end), so Star FM and Star FM Plus now scrape the on-air
  widget from the server-rendered station page instead (only the first
  ~50 KB of the page is read, polled every 30 s).
- Boot start hardened further: the playback service (which is allowed to
  start at boot and stays alive) now retries opening the app window at 3 s,
  10 s, 25 s, 45 s and 70 s after boot — head units are often not ready
  until well after BOOT_COMPLETED. Retries stop once the window is visible.
- The app also relaunches itself right after being updated
  (MY_PACKAGE_REPLACED). This doubles as a diagnostic: if the app window
  opens after installing an update but not after ignition, the head unit
  isn't delivering the boot broadcast (check its own "app auto start"
  setting or whether ignition-off is really a shutdown vs. sleep).

## v3.5 — 2026-07-06

- Start on boot now actually works on Android 14 head units:
  - The boot receiver starts playback of the last station directly through
    the playback service, so the radio starts PLAYING with the ignition even
    if Android blocks opening the window from the background.
  - The app-window launch is retried (2 s and 7 s after boot) for head units
    that aren't ready at the first attempt.
  - The app shows a one-time prompt to grant "Display over other apps" —
    the permission Android 14 requires for the boot launch to open the UI.
    Re-armed if the "Start on boot" switch is toggled on again.
- Star FM artist/track restored: the broadcaster's streams now send an empty
  ICY StreamTitle (verified: `StreamTitle='';`), so Star FM and Star FM Plus
  were moved to the raadiod.tv3.ee web now-playing API (page IDs 2 / 1485),
  the same one already used for Power Hit Radio.

## v3.4 — 2026-07-06

- Start on boot: the app now opens automatically when the device finishes
  booting (car head units — the radio comes up with the ignition, and
  AutoPlay resumes the last station). Controlled by a new "Start on boot"
  switch in Settings (default on). If the head unit blocks the launch,
  grant Eeter "Display over other apps".
- Landscape grid tiles now show the current song ("Artist - Track") under
  each station logo. The playing station uses the live stream metadata;
  the other visible tiles are polled every 30 s (web now-playing APIs for
  Star FM Eesti / Power Hit / Sky Plus DnB, a short ICY metadata probe for
  the rest). Only the visible grid page is polled to limit data use.

## v3.3 — 2026-06-11

- Volume bar now appears on head units whose physical volume knob changes
  the volume through the MCU without persisting it to Android settings
  (e.g. Android 14 units on A_OS_P firmware): volume changes are now also
  detected via the system volume-changed broadcast plus a 0.5 s fallback
  poll, instead of relying only on the settings observer.

## v3.2 — 2026-06-11

- The landscape volume bar now also appears automatically when the volume
  is changed with hardware controls (knob / steering wheel / buttons), not
  only via the on-screen volume button. Same 5-second auto-hide.

## v3.1 — 2026-06-11

- Volume control on the landscape grid screen: a volume button in the top
  bar pops up a floating volume bar (slider + percentage indicator) that
  hides automatically 5 seconds after the last touch. Stays in sync with
  the hardware volume buttons.

## v3.0 — 2026-06-11

- Landscape favorites grid now paginates instead of scrolling: 12 stations
  (4×3) per page, swipe left/right between pages, indicator dots at the
  bottom. Favorites past the first twelve (e.g. Elmar) get a full-size tile
  on the next page instead of overflowing below the screen.

## v2.9 — 2026-06-11

- Fixed the MyHits Dance logo rendering smaller/wider than other station
  logos: the source image was a 1920×1080 widescreen canvas with the square
  artwork centered between large white margins. Trimmed and re-padded to a
  958×958 square (and converted from mislabeled PNG to real WebP).

## v2.8 — 2026-06-11

- New landscape mode for wide displays (e.g. 1920×720 car head units):
  instead of the swipe player, favorites are shown as a grid of big logo
  tiles. Tapping a tile plays the station without leaving the grid; tapping
  the playing tile pauses/resumes; the playing station is marked with a
  white border. Top bar keeps the menu drawer, a now-playing marquee and
  the overflow menu. Portrait mode is unchanged.

## v2.7 and earlier

Earlier development happened before this changelog was kept. Highlights:

- **v2.x** — complete station-logo coverage (all stations have curated
  square logos), per-station brand-color overrides, pruned dead streams,
  removed duplicate stations.
- **v1.x** — core app: swipeable player with per-station brand colors,
  favorites, search drawer, equalizer, settings (quality / autoplay /
  Wi-Fi only / audio focus), media notification, Android Auto support,
  autoplay of the last station on launch.
