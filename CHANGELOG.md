# Changelog

All notable changes to Eeter are documented here.

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
