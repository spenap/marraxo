# Roadmap

Reordered from `original-brief.md` §7: the riskiest assumptions are tested
first, and anything that doesn't need the phone can start while the ROM is
being flashed.

Each item has a "done when" criterion. Tick items off as they're completed
and record decisions in the log at the bottom.

## Track A: Phone (blocked on ROM)

### A0. ROM chosen and flashed
Done in a separate repository.
- [ ] Done when `RESULT.md` exists and its API level and known issues are
      copied into `CLAUDE.md`.

### A1. Android feasibility spike
- [ ] Gradle project in `android/` with dependency versions pinned against
      the target API. Done when `./gradlew assembleDebug` builds and the
      APK installs and launches via `adb install`.
- [ ] Compose vs Views decision. Done when a trivial scrolling album grid
      exists in the chosen toolkit, it scrolls smoothly on the device, and
      the decision is logged below.
- [ ] Local MP3 playback with Media3. Done when a pushed MP3 plays.
- [ ] Background playback. Done when playback continues with the screen
      off for 10+ minutes and lock-screen/media-session controls work.
- [ ] Headphone unplug pauses playback; Bluetooth audio works.
- [ ] Done when all of the above work with Wi-Fi off.

### A2. Minimal music player (brief Milestone 1)
- [ ] MediaStore library scan, basic metadata, play/pause/next/prev/seek.
- [ ] Playback state survives app restart.
- [ ] A basic but polished Now Playing screen.

### A3. Library and visual design (brief Milestone 2)
- [ ] Album/artist browsing, cached artwork, search, queue, favourites.
- [ ] Original visual identity (Now Playing, Library, Queue, Details).
- [ ] Done when it stays responsive with the full library on the device.

## Track B: Sonos (no phone needed for B0)

### B0. Sonos feasibility from the laptop
- [x] Check whether the speakers use the S1 or S2 app/firmware. (S2, 18.6)
- [x] SSDP discovery finds both One SLs (and their group, if any).
- [ ] Serve an MP3 over HTTP from the laptop; `SetAVTransportURI` + `Play`
      makes a speaker fetch and play it.
- [ ] Seek works and duration is reported correctly. Test with byte-range
      requests and check which HTTP headers Sonos actually sends.
- [ ] Pause/stop/volume (RenderingControl) and a GENA subscription work.
- [ ] Group behaviour: commands sent to the group coordinator play in sync.
- [ ] Works with the router's Internet uplink disconnected.
- Done when the findings are written to `docs/sonos-protocol.md`.

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
- Which ROM / Android version? (A0)
- Does local UPnP playback still work on S2 18.6? Services are
  advertised; playback not yet tested. (B0)
- Compose or Views? (A1)

## Decision log
- 2026-10-29: One repo with `android/`, `tools/`, `docs/`. The three
  tracks run in parallel, with risky items first.
- 2026-10-29: Speakers are S2 (firmware 18.6), two separate rooms
  (Estudio, Cocina), not paired. See `sonos-protocol.md`.
- 2026-10-29: Music collection is a few GB on a backup drive; fits on
  the phone.
- 2026-10-29: UI direction is Glow (artwork-tinted, dark), with Sleeve's
  "Playing on <room> · <model>" output row. See `mockups/directions.html`.
