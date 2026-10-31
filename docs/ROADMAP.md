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
- [x] Playback state survives app restart (queue, position, repeat and
      shuffle since 2026-10-31).
- [x] Library limited to the shared `Music/` folder.
- [x] "Hide from library" on albums and tracks, stored in the app
      database and reversible. (Long-press; ⋮ → Hidden to show again.) Done when a hidden album stays hidden after
      a rescan and can be restored.
- [x] Rescan without restarting the app (and pick up files pushed over
      ADB/USB). Library ⋮ → Rescan library; scans only new and deleted files.
      Open: after the first big sync (2,000 files added, 660 removed) the
      rescan finished but the screen kept the old library until the app
      restarted. Reproduce with a test before fixing.
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
- [x] Years tab: a scrubber with decade marks (drag or tap to jump) and a
      label that shows the decade while scrolling fast, the year when slow.
- [x] Letter scrubbers on Albums and Artists (2026-10-31): any list more
      than three screens long gets one. Letters come from the sort key, so
      "The Corrs" is C and accented names sort and letter with their base
      letter (the sort now folds accents too).
- [ ] Scrubber polish, after a few days of use: hidden while idle and
      fading in on scroll; the letter or year in a bubble beside the thumb
      (as Google Photos does) instead of at the top; a light haptic tick
      at each letter or decade. Today's always-on grey letters are the
      2010 `fastScroll` look.
- [ ] Original visual identity (Now Playing, Library, Queue, Details).
- [ ] Done when it stays responsive with the full library on the device.
- [ ] Radio v0: long-press a track → Start radio. Biases on tags and
      history (decade, genre, artist, favourites, not recently played),
      randomness, no artist repeat within 5 tracks, 3 tracks queued ahead.
      See `discovery.md`.
- [ ] Favourites and a Home of cards (smart playlists with a readable
      rule: genres, decades, players and studios from the liner notes,
      moods later). Design: `cards.md`; mockup: `mockups/cards.html`.
- [x] About screen, with open-source licences (AboutLibraries: 89
      libraries, Apache 2.0 and MIT, texts readable offline) (from a ⋮ menu on the Library header, alongside
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
- [x] **Mako as the home screen.** An `activity-alias` with the HOME
      category, disabled in the APK and enabled on the Nexus 4
      (`pm enable`, then `cmd package set-home-activity`). Home always
      returns to the library. A ⋮ menu offers Mako settings, Android
      Settings and "Other apps" (opens Trebuchet, which stays installed).
      Done when Home lands in Mako and Android Settings is two taps away.
- [x] **Now Playing over the lock screen** (2026-10-31): with music
      loaded, waking the phone shows Mako's Now Playing (`LockActivity`,
      `showWhenLocked`), sharp and with working controls; Close or Back
      unlocks to the library. The system lock screen couldn't do it:
      Android 8–9 shrinks session artwork to 320 dp (640 px here) and
      stretches it full screen. Only as the home screen, only below
      Android 10.
- [x] **Navigation bar hidden in Mako** (2026-10-31): immersive mode, in-app
      and without root; a swipe up from the bottom edge shows the bar for
      a few seconds. Home already lands in Mako and Back is Mako's own;
      Recents can't be intercepted by apps, so it stays one swipe away.
      Not lock task (kiosk): it would also block the notification shade
      and the way out to Settings. Frees 48 dp.
- [x] **System settings** (2026-10-31), agreed in a settings review
      (artifact, 30 settings): airplane mode on with only the cell radio
      off (no SIM, so it kept searching); location, NFC, mobile data off;
      no touch, lock or dial sounds; no auto-rotate, screen saver,
      heads-up or lock-screen notifications; LED for charging only; shade
      tiles Wi-Fi, Bluetooth, Volume, Torch, Airplane (brightness slider
      stays); status bar without signal or airplane icons, with battery
      percentage; time zone Europe/Madrid. Unchanged on purpose: 30 s
      timeout, adaptive brightness, vibrate on touch, Wi-Fi during sleep,
      Doze, battery saver off.
- [-] ~~No lock screen~~: dropped. Swipe stays, so a press in a bag lands
      on the lock screen; Mako's lock-screen player shows the music.
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
      a Glow wallpaper and a Glow boot animation (`/system/media`, root).
      The status bar stays (battery, Wi-Fi, time).
      A full SystemUI theme (overlay APK) only if these aren't enough.
- [ ] `android/scripts/appliance.sh [--undo]`: applies all of the above
      over ADB and saves the previous values for `--undo`. Done so far:
      the home screen (on the Nexus 4 since 2026-10-30); the camera and
      its double-press-power gesture off, and the system settings above
      (2026-10-31). Every previous value is saved; --undo was checked to
      restore all of them exactly.

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
- [ ] Let the renderer advance by itself: hand it the next track ahead
      (`SetNextAVTransportURI`; the TV supports it, check Sonos), so the
      app no longer has to judge where a track ends. Today it counts a stop
      in the last fifth of a track as its end, because Sonos's position
      drifts behind the audio of VBR MP3s (3:23 reported of 3:35).
- [ ] Poll the renderer less when nothing needs it: the app asks the
      speaker for its state every second, paused or playing. Every few
      seconds while paused, and GENA events instead of polling (B0),
      should let Wi-Fi and the CPU sleep. Test first; measure again.
- [ ] Recover when a speaker stops by itself. Overnight on 2026-10-31,
      after 50 clean track changes in 3 h, Estudio stopped 10 s into a
      track (00:49:49) with Mako's URL still loaded, status OK and Wi-Fi
      up; Mako took it as "stopped elsewhere" and paused. When a renderer
      stops early in a track and still holds our URL, resume once (Seek +
      Play) before giving up. Test first with the fake renderer; also log
      the media server's requests to see if the phone served too slowly.
- [ ] Measure the phone's baseline draw and Mako's share properly.
      Overnight 2026-10-31: 122 mA average streaming to Estudio (battery
      chip's charge counter, 1,104 mAh in 9 h); `current_now` polled over
      adb reads ~2.5× too high because adb wakes the phone. Log on the
      phone itself with USB unplugged, read the BMS counter, and compare
      idle, playing to a speaker, and playing to headphones, before and
      after the polling fix.
- [ ] Sonos groups (Estudio + Cocina), and the TV from the app.
- [x] Shuffle and repeat on a renderer; they move with the queue between
      the phone and a speaker.
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
- [x] Fetch once per MusicBrainz ID (release, recording, work, artist,
      with relationships) and cache the JSON; respect MusicBrainz's 1
      request/s and send a User-Agent. Optionally a Wikipedia summary via
      Wikidata (CC BY-SA: show attribution). (`tools/linernotes/fetch.py`;
      Spanish summary first, else English.)
- [ ] Build `linernotes.db` (SQLite) keyed by the track's path inside
      `Music-clean`, and push it with the music. Built
      (`tools/linernotes/build.py`, schema 1); pushing it is still to do.
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

### C5. Lyrics
- [ ] Fetch synced lyrics (LRC) from LRCLIB once per track on the desktop
      (open, no key; full dumps available), cached like the liner notes;
      ship them in `linernotes.db`. Never committed (copyright).
- [ ] App: a lyrics view in Now Playing, the current line highlighted and
      scrolling with the music, on the phone and on speakers. Hidden for
      instrumentals.

### C6. Recognise a song from your library (Shazam-style, offline)
- [ ] Desktop prototype: index the library with landmark fingerprints
      (spectrogram peak pairs; e.g. audfprint, or Olaf/Panako, check
      licences), then match 5–10 s clips recorded on the phone near a
      speaker. Done when 9 of 10 clips find the right song.
- [ ] Ship the index; the app records with the microphone (asked only
      when used), matches on the phone, and opens the album.
- AcoustID fingerprints from beets don't fit this: they identify clean
  whole files, not noisy clips.
- Implement from the paper, not from AGPL code (Olaf, Panako): Wang,
  "An Industrial-Strength Audio Search Algorithm", ISMIR 2003. Its main
  patent, US 6,990,453 (filed 2001), shows "Expired – Lifetime" on Google
  Patents (2026-10-30). Before publishing, have the continuations
  (US 8,190,435, 8,386,258, 8,700,407, 9,899,030) confirmed expired, and
  stick to the 2003 method, not later refinements.

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
      has no remote. Also runs pytest for `tools/`.
- [x] **T4. UI tests** (Compose; `./gradlew connectedDebugAndroidTest` on
      the Nexus 4, or an API 27 emulator in CI): tabs and filters, hide and
      show, search plays a result. The debug build is a separate app
      (`com.simonpena.mako.debug`), so test runs never touch real data.
      Not covered here: queue restore (unit-tested) and rescan (needs real
      files in MediaStore).
- [x] **T5. Performance gate**: `scroll-bench.sh` fails above 5% janky
      frames (`MAX_JANKY`) or under 100 frames drawn. Real phone only.
- [x] **T6. Hardware smoke tests**: `tools/upnp/probe.py` checks each step
      and ends with PASS/FAIL, against the real speakers and TV, run by
      hand. Passed 2026-10-30 on Estudio and on the TV (with `--next`).

## Track I: Languages
The UI is English with hard-coded strings; Spanish should be a first-class
language.
- [ ] Move UI text to string resources, with `plurals` for counts (the
      "1 albums" bug came from an English-only plural helper).
- [ ] Spanish translation; follow the phone's language.
- [ ] Locale-aware sorting: artists sort ignoring "The" today, which is
      English-only ("Los Planetas", "Les Négresses Vertes"); use a
      `Collator` for accents.
- [ ] UI tests run in both languages.

## Track U: Usage, measured
Local only: a log on the phone, read on the laptop, never sent anywhere
(like play history). Its job is to decide what to polish and what to drop.
- [ ] Events table in the app: screen opened, card played, search used,
      output changed, renderer stop or error, time from tap to music.
      Nothing beyond what is already in the library and play history.
- [ ] Pull it with the play history; a weekly report on the laptop that
      answers:
      - Which screens and features do I use (tabs, search, Years, cards,
        liner notes, lyrics)?
      - How often do I play on speakers or the TV, and which?
      - How often does a renderer stop on its own or fail?
      - How long from tapping the app to hearing music, per phone?
- Rule: a feature unused for 30 days gets questioned (improve, hide or
  remove), and the decision goes in the decision log.

## Ideas and not doing
New ideas wait here until there is evidence (Track U) or a clear need;
then they move into a track with a "done when".

Parked:
- Full SystemUI theme for the Nexus 4 (overlay APK), only if the A6
  basics aren't enough.
- Glow boot animation; dock-mode clock.
- Year in review story cards (A4) before a year of history exists.
- Merging favourites and play history across both phones.
- Musepack (.mpc) conversion for the 10 tracks Android can't play.
- Chromecast as an output, from the app (not the system Cast tile). The
  Cast SDK needs Google Play Services (ruled out), but Cast v2 is open
  enough: mDNS discovery (`_googlecast._tcp`, NsdManager), TLS on port
  8009 with protobuf messages, launch the Default Media Receiver and give
  it a URL from Mako's media server. A `CastPlayer` beside `RendererPlayer`
  behind the same session swap. The Chromecast itself needs Internet to
  load its receiver; the phone doesn't. Need: the user owns one.
- Mako quick-settings tiles (`TileService`): "Playing on…" to switch
  output, and a sleep timer.
- Gesture navigation on the Nexus 4. Android 8.1 has no system gestures
  (they arrived in 9 and 10), so it would be in-app: swipe from the edge
  to go back and swipe down to close Now Playing, once the navigation
  bar is hidden (A6). Evidence to look for: how often people reveal the
  bar just to press Back (Track U).

Not doing (and why):
- Audiobooks, or splitting the app into reusable modules for them:
  out of scope (decided 2026-10-29).
- Cloud sync, streaming services, accounts, Google Play Services: the
  app is offline by design.
- Remote analytics or remote feature flags: same reason; usage stays on
  the phone (Track U).
- Contributing to, or imitating, another player: its maintainer objects to
  LLM-written code.

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
