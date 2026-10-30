# Roadmap

Reordered from `original-brief.md` §7: the riskiest assumptions are tested
first, and anything that doesn't need the phone can start while the ROM is
being flashed.

Each item has a "done when" criterion. Tick items off as they're completed
and record decisions in the log at the bottom.

## Track A: Phone

### A0. ROM chosen and flashed
Done in a separate repository.
- [x] Done when `RESULT.md` exists and its API level and known issues are
      copied into `CLAUDE.md`. (LineageOS 15.1, API 27.)

### A1. Android feasibility spike
- [ ] Gradle project in `android/` with dependency versions pinned
      against the Nexus 4 API. Done when `./gradlew assembleDebug` builds and the same APK
      installs and launches on both the Nexus 4 and the S25.
      (Nexus 4 done since 2026-10-29; S25 not tried yet.)
- [x] Compose vs Views decision. Done when a trivial scrolling album grid
      exists in the chosen toolkit, it scrolls smoothly on the device, and
      the decision is logged below. (Compose: 1.3% janky frames.)
- [x] Local MP3 playback with Media3. Done when a pushed MP3 plays.
- [ ] Background playback. Done when playback continues with the screen
      off for 10+ minutes and lock-screen/media-session controls work.
- [ ] Headphone unplug pauses playback; Bluetooth audio works.
- [ ] Done when all of the above work with Wi-Fi off.
- [ ] S25: runtime permissions (`READ_MEDIA_AUDIO`, notifications) and
      an hour of screen-off playback without Samsung putting the app to
      sleep.

### A2. Minimal music player (brief Milestone 1)
- [x] MediaStore library scan, basic metadata, play/pause/next/prev/seek.
- [x] Playback state survives app restart.
- [x] Library limited to the shared `Music/` folder.
- [x] "Hide from library" on albums and tracks, stored in the app
      database and reversible. (Long-press; ⋮ → Hidden to show again.) Done when a hidden album stays hidden after
      a rescan and can be restored.
- [x] Rescan without restarting the app (and pick up files pushed over
      ADB/USB). Library ⋮ → Rescan library; scans only new and deleted files.
- [ ] Play history recorded from day one: one row per play with track,
      start time, seconds played, skipped or not, and output device.
      Done when a day of listening shows up correctly in the database.
      Recording since 2026-10-30 (`PlayHistory.kt`, `history.db`). Known
      gap: a track in progress is lost if the process is killed (an
      install, a crash) rather than stopped.
- [ ] A basic but polished Now Playing screen.

### A3. Library and visual design (brief Milestone 2)
- [ ] Album/artist browsing, cached artwork, search, queue, favourites.
      Done: tabs (Albums, Artists, Genres, Years) with artist/genre filters,
      search ignoring accents and case. Still to do: queue, favourites.
- [ ] Original visual identity (Now Playing, Library, Queue, Details).
- [ ] Done when it stays responsive with the full library on the device.
- [ ] Radio v0: long-press a track → Start radio. Biases on tags and
      history (decade, genre, artist, favourites, not recently played),
      randomness, no artist repeat within 5 tracks, 3 tracks queued ahead.
      See `discovery.md`.
- [ ] About screen (done except the licences list) (from a ⋮ menu on the Library header, alongside
      "Rescan library"): `versionName` from `git describe --tags --always
      --dirty`, `versionCode` from `git rev-list --count HEAD`, build date,
      device and Android version, and open-source licences via
      AboutLibraries (works offline, unlike Google's oss-licenses plugin).
      Run git via Gradle's `providers.exec`.

### A4. Stats and Year in review
- [ ] Stats screen: top artists/songs, listening heatmap, outputs, library
      details for geeks (see `mockups/prototype.html`).
- [ ] Year in review story cards, with a shareable summary image.

### A5. Discovery in the app
- [ ] Prototype "Sounds like", Moods and Map in `mockups/prototype.html`.
- [ ] Implement from `discovery.db` (C4/D2).

### A6. Dedicated player (Nexus 4 only)
The Nexus 4 becomes a music player, not a phone running a music app. The
S25 keeps its own launcher; everything here is off by default and turned
on per device. Settings are applied by a script that records the previous
values, so the phone can be put back as it was.
- [ ] **Mako as the home screen.** An `activity-alias` with the HOME
      category, disabled in the APK and enabled on the Nexus 4
      (`pm enable`, then `cmd package set-home-activity`). Home always
      returns to the library. A ⋮ menu offers Mako settings, Android
      Settings and "Other apps" (opens Trebuchet, which stays installed).
      Done when Home lands in Mako and Android Settings is two taps away.
- [ ] **No lock screen:** screen lock set to None, so waking the phone
      lands in the player.
- [ ] **Waking without the power button** (worn: it works only when
      pressed firmly, so offer easier ways; it stays the fallback):
      - plugging or unplugging the cable already wakes it
        (`wake_when_plugged_or_unplugged=1`);
      - volume keys: try LineageOS's volume-wake setting; if this build
        lacks it, find another way;
      - check whether double-tap-to-wake works on this kernel (enabled in
        settings, probably unsupported by the hardware).
- [ ] **Sleeping without it:** a "Screen off" action in Mako
      (`DevicePolicyManager.lockNow`, device admin granted over ADB),
      plus a short screen timeout.
- [ ] **Dock mode:** on the cable, stay on (`stay_on_while_plugged_in`)
      with a dimmed Now Playing (or a clock when idle).
- [ ] **Own volume overlay:** in Mako, volume keys change the volume
      without the system panel and show the Glow overlay from the
      prototype; on a speaker they set the speaker's volume, paused or not.
- [ ] **System look:** dark style plus the closest accent overlay (cyan),
      a Glow wallpaper, a Glow boot animation (`/system/media`, root), and
      the navigation bar hidden in Mako (immersive; swipe to reveal). The
      status bar stays (battery, Wi-Fi, time).
      A full SystemUI theme (overlay APK) only if these aren't enough.
- [ ] `android/scripts/appliance.sh [--undo]`: applies all of the above
      over ADB and saves the previous values for `--undo`.

## Track B: Sonos and TV (no phone needed for B0)

### B0. Renderer feasibility from the laptop
- [x] Check whether the speakers use the S1 or S2 app/firmware. (S2, 18.6)
- [x] SSDP discovery finds both One SLs (and their group, if any).
- [x] Serve an MP3 over HTTP from the laptop; `SetAVTransportURI` + `Play`
      makes a speaker fetch and play it. (Estudio, `tools/upnp/probe.py`)
- [x] Seek works and duration is reported correctly. Test with byte-range
      requests and check which HTTP headers Sonos actually sends.
- [x] Pause/stop/volume (RenderingControl) work.
- [ ] A GENA subscription reports state changes and track end.
- [ ] Group behaviour: commands sent to the group coordinator play in sync.
- [x] Samsung The Frame TV found as a DLNA renderer with AVTransport;
      accepts MP3/FLAC/AAC.
- [x] TV plays a served MP3 via `SetAVTransportURI` + `Play`, seeks and
      queues the next track (needs DLNA headers).
- [ ] Works with the router's Internet uplink disconnected.
- Done when the findings are written to `docs/upnp-renderers.md`.

### B1. Phone as HTTP server + controller (brief Milestone 3)
Needs A1 and B0.
- [x] Embedded HTTP server serving only selected tracks, with range support.
      (`upnp/MediaServer.kt`: random tokens, DLNA headers, covers.)
- [x] Discovery, speaker selection, playback commands, state sync, volume.
      Verified on Estudio 2026-10-30: hand-over at the current position,
      next, pause, back to the phone, play history per output. Volume keys
      reach the speaker only while it plays; when paused, Android sends
      them to the phone's own volume.
- [ ] Sonos groups (Estudio + Cocina), and the TV from the app.
- [ ] Shuffle and repeat on a renderer (not offered yet).
- [ ] Done when a track from the phone plays on Sonos, seeks, and stays up
      for a full album with the screen off. (Nexus 4 Wi-Fi is weak on 5 GHz,
      -90 dBm where it sits; the 2.4 GHz network needs its own password.)

## Track C: Music library tool (no phone needed)

### C0. Read-only inventory (brief §5 Phase 1)
Collection: laptop copy at `~/Music` (originals stay on
the backup drive). 37 GB, 225 artist folders, `Artist/Album/` layout,
~6,300 tracks: 6,206 MP3, 40 M4A, 37 OGG, 10 Musepack (`.mpc`, which
Android can't decode). 476 cover images next to the tracks, plus leftovers
(`.DS_Store`, `.ini`, `.html`). Known messy cases: one band under two
folder names (`R.E.M_`, `rem`), an album folder called "Álbum
desconocido", `Compilations` and `Varios Artistas`.
- [ ] Python tool in `tools/` scans the collection without writing to it.
- [ ] Done when an HTML report shows stats, missing/contradictory tags,
      artwork coverage and duplicates.

### C1. Identification, normalisation, artwork (Phases 2–4)
Done with beets (`tools/beets/`), copying into
`~/Music-clean`. Automatic pass first; skipped albums are
reviewed interactively.
- [x] Read-only inventory report (`tools/inventory/`).
- [x] Automatic pass over the whole collection (2026-10-30): 290 albums
  (4,000 tracks) imported, 168 skipped. 135 of the skipped have clean tags
  but no strong MusicBrainz match; the rest are compilations, singles or
  badly tagged. Classes in `tools/beets/out/skip-classes.json`.
- [x] Second automatic pass over the skipped albums (`tools/beets/pass2.yaml`:
  ~88% similarity, missing tracks allowed): 62 more imported, 103 left.
- [x] Spot-check pass-2 matches near 88%, and the 2 duplicate releases
  (no wrong matches; duplicate copies removed after fingerprint checks).
- [ ] Interactive review: 24 of 103 done, plus Totem, Dylan disc 1 and three
  as-is imports; 79 left in `out/review-remaining.txt` (`./review`).
- [x] Rerun the inventory on `Music-clean` and compare: of the imported
  albums, 1 lacks artwork (was 144 of 472 overall); no tracks lack year or
  track number; no artist spelling variants.
- [ ] MusicBrainz/AcoustID matching with confidence scores and a review
      queue.

### C3. Liner notes (offline "X-ray")
Learn more about a track, album or artist, offline, from data fetched on
the desktop.
- [ ] Fetch once per MusicBrainz ID (release, recording, work, artist,
      with relationships) and cache the JSON; respect MusicBrainz's 1
      request/s and send a User-Agent. Optionally a Wikipedia summary via
      Wikidata (CC BY-SA: show attribution).
- [ ] Build `linernotes.db` (SQLite) keyed by the track's path inside
      `Music-clean`, and push it with the music.
- [ ] App: a Liner notes screen for track, album and artist. Credits,
      studios and dates, "first released on", other versions in your
      library, and connections between your albums through shared
      musicians. Hidden when there's no data.
- [ ] Prototype it in `mockups/prototype.html` first.

### C4. Discovery (see `discovery.md`)
Offline similarity by sound, people, era, mood and topic, computed on the
desktop and shipped as `discovery.db`.
- [ ] D0: Essentia features + people graph, HTML explorer. Done when the
      neighbours of 20 well-known albums look right.
- [ ] D1: CLAP and Wikipedia text embeddings; named clusters.
- [ ] D2: package `discovery.db`; app "Sounds like", Moods, Map (A5).
- [ ] D3: radio sequencing; play-history signals.

### C2. Safe writing (Phase 5)
- [ ] Backup, change manifest, reversible writes, validation.

## Track T: Tests and CI
Every check done by hand on a device should end up as a test at the
cheapest level that can catch it. CI runs levels 1–3 on every push; the
others run on demand.
- [x] **T1. Unit tests** (JVM + Robolectric, no device): hiding, search
      folding, UPnP time/DIDL helpers, HTTP byte ranges, queue save and
      restore, play-history rows (completed/skipped/stopped, durations).
- [x] **T2. Fake renderer**: a local SOAP + HTTP server that behaves like
      Sonos (downloads the whole file) or the TV (needs DLNA headers,
      streams with ranges). Tests hand-over, next, track end, pause and
      "taken over by another controller" through `RendererPlayer`.
- [ ] **T3. CI**: a hosted pipeline that runs T1–T2 and builds the release
      APK (`./gradlew testDebugUnitTest assembleRelease`). Written for
      GitHub Actions (`.github/workflows/ci.yml`); not run yet, as the repo
      has no remote. Still to add: pytest for `tools/`.
- [ ] **T4. UI tests** (Compose, emulator in CI or the Nexus 4 locally):
      tabs and filters, hide and show, search plays a result, the queue
      restored after the app is killed, rescan messages.
- [x] **T5. Performance gate**: `scroll-bench.sh` fails above 5% janky
      frames (`MAX_JANKY`) or under 100 frames drawn. Real phone only.
- [ ] **T6. Hardware smoke tests**: `tools/upnp/probe.py` with assertions,
      against the real speakers and TV, run by hand.

## Later (brief Milestone 5)
- Wi-Fi library sync, error recovery, battery tuning, offline test suite.
- Rotation: the collection (37 GB) is bigger than the Nexus 4's free
  space (~12.5 GB), so the phone holds a rotating selection. The sync
  tool picks it (favourites, recent plays from the play history, albums
  not heard in a while) and swaps albums in and out. The S25 can probably
  hold everything.

## Open questions
- Does local UPnP playback still work on S2 18.6, and on the TV?
  Services are advertised; playback not yet tested. (B0)
- Compose or Views? (A1)

## Decision log
- 2026-10-29: One repo with `android/`, `tools/`, `docs/`. The three
  tracks run in parallel, with risky items first.
- 2026-10-29: Speakers are S2 (firmware 18.6), two separate rooms
  (Estudio, Cocina), not paired. See `upnp-renderers.md`.
- 2026-10-29: Music collection is a few GB on a backup drive; fits on
  the phone.
- 2026-10-29: UI direction is Glow (artwork-tinted, dark), with Sleeve's
  "Playing on <room> · <model>" output row. See `mockups/directions.html`.
- 2026-10-29: The Samsung The Frame TV (UE55LS003) is an output target
  too. All targets are generic UPnP renderers, with Sonos-specific
  grouping on top.
- 2026-10-29: Interactive prototype in `mockups/prototype.html` is the UI
  reference. Stats need play history, which can't be rebuilt later, so
  history recording ships in A2, long before the stats screens.
- 2026-10-29: The app also targets a Samsung Galaxy S25 (main phone). One
  APK: minSdk from the Nexus 4 ROM, targetSdk current. The Nexus 4 stays the
  development device and performance floor.
- 2026-10-29: ROM is LineageOS 15.1 (Android 8.1, API 27), so minSdk 27.
  For Sonos (B1), from the firmware tests: receive SSDP replies on an
  unconnected socket (Sonos replies from a random port), hold a
  MulticastLock to receive multicast with the screen off, and keep a
  WifiLock plus wake lock while serving HTTP.
- 2026-10-29: First APK uses Jetpack Compose, provisionally. A1 decides
  whether it stays, based on how it performs on the Nexus 4.
- 2026-10-30: The library is the shared `Music/` folder only; other folders
  (test tones, recordings) are ignored. Choosing folders can become a
  setting later. Removing music: the app only *hides* tracks; deleting
  files belongs to the desktop collection and its sync to the phone.
- 2026-10-30: The Nexus 4 holds a 3.8 GB test sample (50 albums, 664
  tracks, spread across the alphabet, including M4A and OGG) until sync
  and rotation exist.
- 2026-10-30: Cleanup uses beets (MusicBrainz + AcoustID, fetchart,
  embedart) instead of a custom tagger. It copies into `Music-clean`, so
  the source collection stays untouched; we write config, not a tagger.
- 2026-10-30: Glow's art-tinted look comes from a common pattern (Apple
  Music, Spotify, Android's media notification). We don't use another player (a
  GNOME player with a similar look) as a reference, and don't contribute
  to it: its maintainer doesn't want LLM-written code.
- 2026-10-30: Compose stays. Album grid fling benchmark on the Nexus 4:
  debug 97% janky (median 101 ms); release 27% (7 ms); release + AOT
  `speed-profile` 1.3% (5 ms, p90 9 ms). Install with
  `android/scripts/install.sh`. Generating a proper Baseline Profile can
  replace the manual compile step later.
- 2026-10-30: "Liner notes" (offline X-ray) is planned as C3. Data is
  fetched and packaged on the desktop; the app never goes online.
- 2026-10-30: Discovery (`discovery.md`): separate, explained views
  (sound, people, era, mood, topic) instead of one blended score; heavy
  models run on the desktop only; results keyed by audio content so they
  survive renames.
- 2026-10-30: The app is model-agnostic: it reads a versioned
  `discovery.db` contract (views, label sets, scalars, edges) and never
  ships a model. Radio starts as tag-based biases (Amarok-style) and gains
  sound-based biases later without changing the engine.
