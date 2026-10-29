# Roadmap

Reordered from `original-brief.md` §7: the riskiest assumptions are tested
first, and anything that doesn't need the phone can start while the ROM is
being flashed.

Each item has a "done when" criterion. Tick items off as they're completed
and record decisions in the log at the bottom.

## Track A: Phone (blocked on ROM)

### A0. ROM chosen and flashed
Done in a separate repository.
- [x] Done when `RESULT.md` exists and its API level and known issues are
      copied into `CLAUDE.md`. (LineageOS 15.1, API 27.)

### A1. Android feasibility spike
- [ ] Gradle project in `android/` with dependency versions pinned
      against the Nexus 4 API. Done when `./gradlew assembleDebug` builds and the same APK
      installs and launches on both the Nexus 4 and the S25.
- [ ] Compose vs Views decision. Done when a trivial scrolling album grid
      exists in the chosen toolkit, it scrolls smoothly on the device, and
      the decision is logged below.
- [ ] Local MP3 playback with Media3. Done when a pushed MP3 plays.
- [ ] Background playback. Done when playback continues with the screen
      off for 10+ minutes and lock-screen/media-session controls work.
- [ ] Headphone unplug pauses playback; Bluetooth audio works.
- [ ] Done when all of the above work with Wi-Fi off.
- [ ] S25: runtime permissions (`READ_MEDIA_AUDIO`, notifications) and
      an hour of screen-off playback without Samsung putting the app to
      sleep.

### A2. Minimal music player (brief Milestone 1)
- [ ] MediaStore library scan, basic metadata, play/pause/next/prev/seek.
- [ ] Playback state survives app restart.
- [x] Library limited to the shared `Music/` folder.
- [ ] "Hide from library" on albums and tracks, stored in the app
      database and reversible. Done when a hidden album stays hidden after
      a rescan and can be restored.
- [ ] Rescan without restarting the app (and pick up files pushed over
      ADB/USB).
- [ ] Play history recorded from day one: one row per play with track,
      start time, seconds played, skipped or not, and output device.
      Done when a day of listening shows up correctly in the database.
- [ ] A basic but polished Now Playing screen.

### A3. Library and visual design (brief Milestone 2)
- [ ] Album/artist browsing, cached artwork, search, queue, favourites.
- [ ] Original visual identity (Now Playing, Library, Queue, Details).
- [ ] Done when it stays responsive with the full library on the device.

### A4. Stats and Year in review
- [ ] Stats screen: top artists/songs, listening heatmap, outputs, library
      details for geeks (see `mockups/prototype.html`).
- [ ] Year in review story cards, with a shareable summary image.

## Track B: Sonos and TV (no phone needed for B0)

### B0. Renderer feasibility from the laptop
- [x] Check whether the speakers use the S1 or S2 app/firmware. (S2, 18.6)
- [x] SSDP discovery finds both One SLs (and their group, if any).
- [ ] Serve an MP3 over HTTP from the laptop; `SetAVTransportURI` + `Play`
      makes a speaker fetch and play it.
- [ ] Seek works and duration is reported correctly. Test with byte-range
      requests and check which HTTP headers Sonos actually sends.
- [ ] Pause/stop/volume (RenderingControl) and a GENA subscription work.
- [ ] Group behaviour: commands sent to the group coordinator play in sync.
- [x] Samsung The Frame TV found as a DLNA renderer with AVTransport;
      accepts MP3/FLAC/AAC.
- [ ] TV plays a served MP3 via `SetAVTransportURI` + `Play`.
- [ ] Works with the router's Internet uplink disconnected.
- Done when the findings are written to `docs/upnp-renderers.md`.

### B1. Phone as HTTP server + controller (brief Milestone 3)
Needs A1 and B0.
- [ ] Embedded HTTP server serving only selected tracks, with range support.
- [ ] Discovery, speaker/group selection, playback commands, state sync,
      volume.
- [ ] Done when a track from the phone plays on Sonos, seeks, and stays up
      for a full album with the screen off.

## Track C: Music library tool (no phone needed)

### C0. Read-only inventory (brief §5 Phase 1)
- [ ] Python tool in `tools/` scans the collection without writing to it.
- [ ] Done when an HTML report shows stats, missing/contradictory tags,
      artwork coverage and duplicates.

### C1. Identification, normalisation, artwork (Phases 2–4)
- [ ] MusicBrainz/AcoustID matching with confidence scores and a review
      queue.

### C2. Safe writing (Phase 5)
- [ ] Backup, change manifest, reversible writes, validation.

## Later (brief Milestone 5)
- Wi-Fi library sync, error recovery, battery tuning, offline test suite.

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
