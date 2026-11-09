# Roadmap

Reordered from `original-brief.md` §7: the riskiest assumptions are tested
first, and anything that doesn't need the phone can start while the ROM is
being flashed.

Each item has a "done when" criterion. Tick items off as they're completed
and record decisions in `decisions.md`.

## Track A: Phone

### A0. ROM chosen and flashed
- [x] Done when the ROM's API level and known issues are in `AGENTS.md`.
      (LineageOS 15.1, API 27.)

### A1. Android feasibility spike
- [ ] Gradle project in `android/` with dependency versions pinned
      against the Nexus 4 API. Done when `./gradlew assembleDebug` builds and the same APK
      installs and launches on both the Nexus 4 and the S25.
      (Nexus 4 done since 2026-10-29; S25 not tried yet.)
- [x] Compose vs Views decision. Done when a trivial scrolling album grid
      exists in the chosen toolkit, it scrolls smoothly on the device, and
      the decision is logged in `decisions.md`. (Compose: 1.3% janky frames.)
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
      Fixed 2026-10-31: after the first big sync (2,000 files added, 660
      removed) the screen kept the old library until a restart, because
      the wait for the scanner had a fixed 120 s limit and the library
      reloaded half-indexed. It now waits while files keep coming (gives
      up after 30 s of silence) and shows "Rescanning… n of m files".
- [ ] Play history recorded from day one: one row per play with track,
      start time, seconds played, skipped or not, and output device.
      Done when a day of listening shows up correctly in the database.
      Recording since 2026-10-30 (`PlayHistory.kt`, `history.db`). Known
      gap: a track in progress is lost if the process is killed (an
      install, a crash) rather than stopped.
- [x] A basic but polished Now Playing screen: see A3, "Now Playing
      scrolls" (2026-11-02), and the file-only controls (2026-11-06).
- [ ] "Open with" for audio files from other apps (`OpenFile.kt`): the file
      replaces the queue, isn't added to the library, and plays with the
      usual controls and outputs. Done when checked by hand on both phones
      and a speaker. Pixel 5 (2026-11-06): a library track, an indexed
      file in Download and an unindexed one from Files all play, seek
      after the Files screen closes, and show their cover when MediaStore
      has one. Now Playing shows in 76 ms plus reading the file (~2 s for
      6.5 MB unindexed); one Back returns to Files. Estudio played and
      seeked an unindexed one. Still to check: the S25.

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
- [x] Genre families (2026-10-31): tags that differ only in case, accents
      or punctuation are one genre ("Pop-Rock", "Pop Rock"; the mis-encoded
      "Pop espańol"), "Rock/Classic Rock" counts for both, and genres that
      share a word form a family (Indie: Indie, Indie Pop, Indie Rock; a
      genre can be in two). A family of up to 12 albums opens straight to
      them, a bigger one to its genres; each row says which. Genres with a
      single album go in "Other", at the end (inside a family they stay).
      The Nexus 4's 38 tags make 13 rows: Rock, Pop, Indie and Español as
      families, 8 genres, and Other with 13 one-album genres.
- [x] Genre tiles (2026-10-31): the Genres tab is a grid of tiles, each
      with one of its covers tilted in the corner on a dark tone of that
      cover's colour. Another cover each time you open the tab from another
      tab; nothing moves while you look. Chosen over playable cards on Home
      and a list with stacked covers (`mockups/genre-cards.html`,
      `mockups/genre-tiles.html`). Scroll bench, cold: 2.4–2.7% janky
      frames on the Nexus 4 (album grid: 4.1%).
- [x] Tiles or lists, a switch in the ⋮ menu (2026-10-31), for the beta to
      decide: Genres and Artists as tiles (the default) or as lists.
      Albums is already a cover grid; Years stays a list, its scrubber
      reads down a timeline (decade tiles if the beta wants tiles there).
      Tiles draw no shadows and load covers only once a fling stops, or
      under a finger held 200 ms (2026-11-02; before, covers waited for
      the finger to lift): with shadows and loading mid-fling, Artists
      was 69–81% janky.
- [ ] Artists stutters a little in both looks: 5.4–6.2% janky as tiles,
      6.0–6.5% as a list (scroll-bench, `TAB=Artists`, cold), over the 5%
      limit. Never benchmarked before; find out what the album grid
      (4.1%) does differently. Noisy on 2026-11-02 (after the Nexus 4's
      thumbnail cache was rebuilt): tiles 3.3–10.7% over five runs
      (median 8.3%), 5.6–6.9% (median 5.7%) with covers under a held
      finger.
- [ ] Covers changed in the collection stay stale on the Nexus 4 after
      a sync: Android 8.1 keeps its old album thumbnails
      (`albumthumbs`, the `album_art` table). Fixed by hand on
      2026-11-02 (cleared both, then `content read` each
      `audio/albumart/<id>` from adb, which may write the thumbnail);
      `sync.py` should do the same for the albums it refreshes.
- [ ] As the home screen, Now Playing's "Playing on … Change" row sits
      where the hidden navigation bar was, and the accessibility tree
      reports it at [0,0][0,0] (not visible): it can be tapped, but
      TalkBack and on-device UI tests can't find it. Check the window's
      visible frame in immersive mode, test first.
- [ ] Scrubber polish, after a few days of use: hidden while idle and
      fading in on scroll; the letter or year in a bubble beside the thumb
      (as Google Photos does) instead of at the top; a light haptic tick
      at each letter or decade. Today's always-on grey letters are the
      2010 `fastScroll` look.
- [ ] Original visual identity (Now Playing, Library, Queue, Details).
- [ ] Done when it stays responsive with the full library on the device.
- [x] Radio v0 (2026-10-31): long-press a song → Start radio (one radio
      since 2026-11-01: the four modes became hidden, weighted feeds).
      Replaces the queue; the seed plays at once, then the
      service keeps 3 songs ahead (`RadioQueue`), on the phone or a
      speaker; Now Playing shows "Radio from <song>" and, in debug builds, why each song
      came ("The Town House on both"). Engine: `Radio.kt`, research and
      simulation baseline: `radio.md`. Nexus 4: ~60 ms a pick (off the
      main thread), ~4 s to read the library and notes before the first
      top-up. Checked on Estudio silently.
- [x] Up next (the queue, with jumps) and Now Playing's album and artist
      as links; songs have a visible ⋮ opening a sheet (Play next, Start
      radio, Go to artist, Song notes, Hide with Undo) (2026-10-31).
- [x] Design audit (2026-10-31, artifact "Mako design audit": 20 screens
      on the Nexus 4). Done: links that look like links (liner notes
      names as buttons, Now Playing ⋮, Liner notes as a button, albums
      not on the phone under their own heading); albums get the sheet and
      Undo; shuffle, repeat and the playing song not told by colour
      alone; Play and Shuffle (artist-spreading) for artists, genres and
      albums. Left: the blank status line under "Library" (later).
      Lesson: Modifier.border on one screen took the Albums grid from ~3%
      to ~9% janky (R8); gate every UI commit on the bench's exit status.
- [ ] Radio, next: seeds from an album, artist, genre tile, and a person
      or studio on the liner-notes screen; ready sooner than ~4 s (a skip
      in the first seconds finds an empty queue); come back after a
      restart (today the queue does, the radio doesn't); "not heard
      lately" once history is real listening; a listener's verdict from
      the beta.
- [ ] `RadioSimulationTest` fails on the refreshed inventory (2026-11-09:
      454 albums, 6,052 songs): Radio shares a genre, decade or artist on
      18% of picks, limit 20%. Fails without the day's code changes too;
      CI skips it (no inventory).
- [ ] Radio picks poorly (beta, 2026-11-02): from "You Only Live Once"
      (The Strokes) came The Smashing Pumpkins and La Fuga, weak matches,
      then songs from the same few albums. Causes (Claude and Codex, read
      separately): genre family and ±1 decade let most rock through; every
      credit counts alike (the Pumpkins share only Howie Weinberg, mastering
      on one album, engineering on the other); album odds grow with album
      length; songs from an album already played match the last song on
      everything, and the album gap is only 3. Fix: credits weighed by role,
      the album's own genre before its family, album first then song,
      weight decaying per song already played from an album or artist.
      The simulator adds one: compilations carry their release year, so
      `Era` matches a 2003 Beach Boys best-of to a 2005 Strokes song.
- [x] Station simulator (2026-11-02, `scripts/station.sh`): the real `Radio.kt` from the command line on the
      local collection. Seed, weights, picks and random seed in; each pick's
      score by reason, and per station the picks linked only by genre
      and era, variety (albums, most songs from one album) and widening
      out. Compares weight sets on the same seeds. Picks marked good or
      weak by ear become a reference set that scores any weights, and a
      regression test.
- [ ] Liner notes: what the Now Playing card previews is sometimes not
      on the screen it opens (beta, 2026-11-02). Guess: the card and the
      screen read different levels (song vs album) or different fields.
      Find a song where it happens and compare the two queries.
- [ ] Album screen: the liner notes as cards below the track list,
      reached by scrolling on, as in Now Playing (beta, 2026-11-02).
      One way to show "more about this" in every view, instead of a
      button on some screens and cards on others.
- [x] Album screen: a row before each disc on multi-disc albums ("Disc 2 ·
      10 songs · 29 min"; 29 of 454 albums), and "2 discs" in the details
      (2026-11-09, `mockups/discs.html`, option A).
- [ ] Disc titles on the disc rows ("Rarities and B-Sides"; 7 albums):
      Android doesn't expose them, so from the liner notes.
- [ ] Home tab, first (`mockups/home.html`, decided 2026-11-09).
  - [x] The first tab, opening the library when there are plays (Albums
        on a first run): a large card picks up the latest album left
        halfway at its song; Continue, the albums played lately; for the
        3 latest, more of their genre and decade (the genre alone below 4
        albums), in another order each day. From `history.db`.
  - [ ] Continue with artists, radios and genres: record what started
        playback (play history keeps only the songs).
  - [ ] Nexus 4 as the home screen opens on Now Playing, Home behind it.
  - [ ] Benchmark Home's shelves flung sideways; scroll-bench flings only
        down (`TAB=Home`: 1.6–2.4% janky on the Nexus 4; Albums 3.3–3.7%).
  - [ ] Later, rotated: the same people and studios, Rediscover.
- [ ] Favourites and a Home of cards (smart playlists with a readable
      rule: genres, decades, players and studios from the liner notes,
      moods later). Design: `cards.md`; mockup: `mockups/cards.html`.
- [x] Now Playing scrolls, as Spotify's (layout F in
      `mockups/now-playing.html`): cards below the controls for lyrics
      (following the song; "Ver la letra" opens the full screen) and liner
      notes (story, a few credits, "Ver las notas"), each only when the
      song has it; when there's nothing, there's nothing. The top of the
      first card peeks (~30 dp) so it's found. The bottom row loses
      "Change" (the row is the button; TalkBack still says it) and shares
      one rule with Up next, centred on "Playing on …". The heart for
      favourites goes beside the title, which truncates. Done 2026-11-02
      (c56cea1); favourites are stored, not listed yet (Home of cards).
      Album grid bench 3.2% after; Now Playing's own scrolling not yet
      benchmarked (scroll-bench only flings tabs).
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

### A7. Releases for the S25
Dev phones keep the debug key and `install.sh`; the S25 gets tagged
releases only.
- [x] One release key, kept off the repo with a backup; `release.sh` tags
      a green `main`, builds, signs and publishes a GitHub Release
      (2026-11-02).
- [ ] The S25 installs and updates through Obtainium (read-only token).
- [x] Import lyrics and liner notes in the app (⋮ → Import lyrics and
      notes, 2026-11-02): the S25 takes them over USB with the music, as
      it can't use adb.

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
- [x] Poll the renderer less (2026-10-31): every 5 s mid-track and while
      paused, every second only after a command and in a track's last
      fifth, where a stop means "next track". A fifth as many requests
      mid-track. On Estudio the next track still starts ~1 s after the
      end, and a pause from another controller shows within 5 s.
- [ ] GENA events instead of polling (B0), and the Wi-Fi lock: Mako holds
      a high-performance Wi-Fi lock while playing to a speaker, which
      stops Wi-Fi power saving; Sonos downloads the whole track up front,
      so it may only be needed while serving. Measure first (next item).
- [x] Recover when a speaker stops by itself (2026-10-31: resumes once
      where it was; a second stop on the same track is respected; checked
      on Estudio by sending Stop twice). Overnight on 2026-10-31,
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
Collection: a laptop copy (originals stay on
the backup drive). 37 GB, 225 artist folders, `Artist/Album/` layout,
~6,300 tracks: 6,206 MP3, 40 M4A, 37 OGG, 10 Musepack (`.mpc`, which
Android can't decode). 476 cover images next to the tracks, plus leftovers
(`.DS_Store`, `.ini`, `.html`). Known messy cases: one band under two
folder names (`R.E.M_`, `rem`), an album folder called "Álbum
desconocido", `Compilations` and `Varios Artistas`.
- [x] Python tool in `tools/` scans the collection without writing to it.
- [x] Done when an HTML report shows stats, missing/contradictory tags,
      artwork coverage and duplicates (`tools/inventory/`).

### C1. Identification, normalisation, artwork (Phases 2–4)
Done with beets (`tools/beets/`), copying into
a separate clean folder. Automatic pass first; skipped albums are
reviewed interactively.
- [x] Genre synonyms the app can't see (2026-10-31): 25 albums (406
      songs) retagged, each approved one by one, by `tools/beets/genres.py`
      from `genres.tsv` (manifest in `out/genre-changes-*.tsv`). Decided:
      keep genres the online databases use and don't retag to get a
      grouping (Britpop stays Britpop); grouping is the app's job. For a
      borderline genre (fewer than 3 albums, or a synonym), take
      MusicBrainz's most voted genre when it leads with real votes,
      otherwise Discogs (curated; its style before its genre). MusicBrainz
      votes are thin (Californication: 20; Maná: 4), and its list lacks
      genres the library uses (Soundtrack, Indie, Pop español). The
      fetched release groups already link to Discogs and Wikidata.
- [x] Read-only inventory report (`tools/inventory/`).
- [x] Automatic pass over the whole collection (2026-10-30): 290 albums
  (4,000 tracks) imported, 168 skipped. 135 of the skipped have clean tags
  but no strong MusicBrainz match; the rest are compilations, singles or
  badly tagged. Classes in `tools/beets/out/skip-classes.json`.
- [x] Second automatic pass over the skipped albums (`tools/beets/pass2.yaml`:
  ~88% similarity, missing tracks allowed): 62 more imported, 103 left.
- [x] Spot-check pass-2 matches near 88%, and the 2 duplicate releases
  (no wrong matches; duplicate copies removed after fingerprint checks).
- [x] Interactive review (2026-11-01): the last 60 albums decided on a
  phone-friendly page (an artifact keeping each choice in its database),
  five at a time, then imported by script. Duplicates inside a folder
  (same recording twice, found by Chromaprint fingerprint, not by name)
  are set aside before matching: they sank good matches (Journey: 77%
  with every track right) and were in 9 of the 60 folders. 50 imported
  (matched, as-is or added to an existing album), Robin Hood rebuilt
  from three artist folders, R.E.M.'s In Time rebuilt as two discs,
  7 left out. Files a release doesn't list are kept as extra tracks.
  Manifests: `out/review-imports-*.tsv`, `out/drop-list.tsv`,
  `out/removed-albums.tsv`, `out/cover-changes-*.tsv`.
- [x] Reconciliation before the wipe (2026-11-01): every one of the
  6,818 files in `~/Music` is in Music-clean (audio compared without its
  last 3 packets, where rewritten tags bleed in), in `~/Audiobooks` or
  `~/Music-extras` (byte-identical copies), converted, on the drop list,
  or junk (`out/reconcile-*.tsv`). Spoken word went to `~/Audiobooks`,
  videos, booklets, lyrics and scans to `~/Music-extras`.
- [x] Music-clean renamed to `~/Music` (2026-11-01); the original is
  `~/Music-todelete`. Paths updated in the beets config and `library.db`
  (backup `out/library.db.pre-rename-20261101`), `sync.py`, `build.py`.
- [ ] Wipe `~/Music-todelete`, on the user's word.
- [x] Musepack (2026-11-01): No. 2 Patrick Street, the only `.mpc` album,
  converted from the originals to AAC 256 kbps `.m4a` and re-imported
  (manifest `out/format-changes-*.tsv`). This also restored track 4,
  "Tom Joad": its malformed APEv2 tag (an item with an empty key) made
  beets skip it as unreadable; the audio was fine. It was the only
  unreadable file in any import log. Format rule for the clean copy: MP3,
  AAC, Ogg Vorbis or FLAC, the formats both phones, the Sonos speakers
  and the TV all play (`docs/upnp-renderers.md`); not Opus (neither
  renderer lists it) nor Musepack. Lossless sources stay FLAC.
- [x] Rerun the inventory on `Music-clean` and compare: of the imported
  albums, 1 lacks artwork (was 144 of 472 overall); no tracks lack year or
  track number; no artist spelling variants.
- [x] MusicBrainz/AcoustID matching with confidence scores and a review
      queue: beets (`musicbrainz`, `chroma`) in two passes, then the
      interactive review (2026-11-01).

### C3. Liner notes (offline "X-ray")
Learn more about a track, album or artist, offline, from data fetched on
the desktop.
- [x] Fetch once per MusicBrainz ID (release, recording, work, artist,
      with relationships) and cache the JSON; respect MusicBrainz's 1
      request/s and send a User-Agent. Optionally a Wikipedia summary via
      Wikidata (CC BY-SA: show attribution). (`tools/linernotes/fetch.py`;
      Spanish summary first, else English.)
- [x] Build `linernotes.db` (SQLite) keyed by the track's path inside
      `Music-clean`, and push it with the music (`tools/linernotes/build.py`,
      schema 1; `sync.py` copies it to Mako's own folder,
      `Android/data/com.simonpena.marraxo/files/`, when it changed: readable
      without a permission, writable by adb on Android 8 to 17). 384
      albums, 381 matched, 234 with a story, 30,162 credits.
- [x] App: reading the notes (2026-10-31): `LinerNotes.kt` opens the
      database read-only (refusing schemas it doesn't know), groups
      credits into written / played / produced / recorded, and finds the
      other albums a person or place is on. Tracks now carry their path
      inside the Music folder: 2,106 of the Nexus 4's 2,130 files match a
      track in the notes (the rest: the 3 albums MusicBrainz didn't know).
- [x] App: song, album and person/place screens (2026-10-31, `dee0c15`):
      credits by section, the album's story with its licence, "Also in
      your library" leading to everyone's albums by year. From the song
      sheet, Now Playing and the album screen; hidden without data.
- [x] Lyrics v1 (2026-11-01, overnight; `lyrics.md`): fetched from LRCLIB
      on the laptop (`tools/lyrics`), `lyrics.db` synced like the notes,
      shown from Now Playing, timed lines following the song (also on
      the speakers, by their reported position). Lyrics never in git.
- [ ] Lyrics, next: word-by-word where LRCLIB has it; a per-song early or
      late nudge; search by words; lyrics for any song from its sheet;
      the 49 GB LRCLIB dump if the API's outages or misses justify it.
- [x] "Played by": the artist, then the band; a string section folds into
      one row (linked players stay in view) (2026-11-01).
- [ ] Liner notes polish: role words ("guitar", "producer") are English in every language;
      "first released on" and other versions in your library; a radio
      from a person or place.
- [x] Prototype it first: `mockups/liner-notes.html` (2026-10-31), a
      track (In the Air Tonight), an album (So Many Partings) and a studio
      (The Town House, 8 of your albums), from real rows.
- [ ] Stories in the app's language: fetch Galician first when the phone
      is in Galician (today: Spanish, else English).
- [ ] Some lyrics are out of sync (beta, 2026-11-02); no way yet to
      tell which. Idea: in debug builds, a pill on the lyrics card to
      mark "out of sync", saved on the phone like play history and read
      on the laptop, with LRCLIB's duration against the track's. Guess:
      timings from another version of the song (a radio edit, a live
      take) whose length is close enough to match.

### C4. Discovery (see `discovery.md`)
Offline similarity by sound, people, era, mood and topic, computed on the
desktop and shipped as `discovery.db`.
- [ ] D0: Essentia features + people graph, HTML explorer. Done when the
      neighbours of 20 well-known albums look right.
- [ ] D1: CLAP and Wikipedia text embeddings; named clusters.
- [ ] D2: package `discovery.db`; app "Sounds like", Moods, Map (A5).
- [ ] D3: radio sequencing; play-history signals.

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
- [x] Backup, change manifest, reversible writes, validation: how every
      change to the collection is made (AGENTS.md), with manifests in
      `tools/beets/out/`.

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
- [x] **T3. CI**: GitHub Actions (`.github/workflows/ci.yml`) on every
      pull request: `android` (T1–T2 and the release APK), `tools`
      (pytest) and `ui` (T4 on an Android 8.1 emulator).
- [x] **T4. UI tests** (Compose; `./gradlew connectedDebugAndroidTest` on
      the Nexus 4, or an API 27 emulator in CI): tabs and filters, hide and
      show, search plays a result. The debug build is a separate app
      (`com.simonpena.marraxo.debug`), so test runs never touch real data.
      Not covered here: queue restore (unit-tested) and rescan (needs real
      files in MediaStore).
- [x] **T5. Performance gate**: `scroll-bench.sh` fails above 5% janky
      frames (`MAX_JANKY`) or under 100 frames drawn. Real phone only.
      `TAB=Artists|Genres|Years` flings another tab. Part of the
      no-regressions checklist in AGENTS.md (2026-10-31).
- [x] **T6. Hardware smoke tests**: `tools/upnp/probe.py` checks each step
      and ends with PASS/FAIL, against the real speakers and TV, run by
      hand. Passed 2026-10-30 on Estudio and on the TV (with `--next`).

## Track I: Languages
English, Spanish and Galician, following the phone's language (Android
13+: also per app, in Settings → Apps → Mako → Language).
- [x] UI text in string resources (`res/values{,-es,-gl}/strings.xml`),
      with `plurals` for counts. `TranslationsTest` fails if a string is
      missing from a language or its placeholders differ.
- [x] Spanish and Galician translations (written by Claude; to be read by
      a native speaker on the phone).
- [ ] Locale-aware sorting: artists sort ignoring "The" today, which is
      English-only ("Los Planetas", "Les Négresses Vertes"); use a
      `Collator` for accents.
- [x] UI tests in another language: `LanguageUiTest` renders the library
      in Galician on the phone. The rest run in the phone's language
      (English on the Nexus 4).

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
  remove), and the decision goes in `decisions.md`.

## Ideas and not doing
New ideas wait here until there is evidence (Track U) or a clear need;
then they move into a track with a "done when".

Parked:
- Data from the laptop over home Wi-Fi (2026-11-02): the app pulls new
  lyrics and liner notes from the laptop, no cable.
- Scrobbling to a self-hosted server (2026-11-02): Koito or Maloja
  (ListenBrainz API), plays queued and sent on home Wi-Fi. Libre.fm
  reopened in 2026. Changes "nothing is ever sent elsewhere".
- A FAQ, if the project goes public (2026-11-02): e.g. how a collection
  gets its liner notes and lyrics.
- Hero covers (2026-11-02): the cover edge to edge at the top, as Google
  Play Music, Rdio and early Spotify did, on Now Playing, the album
  screen and an artist screen (an artist's hero is a collage of their own
  covers: no artist photos offline). Costs height on the Nexus 4 (a
  360 dp cover leaves ~250 dp below) and needs a scrim under text over
  art. Mockup: `mockups/hero.html`.
- Party light (2026-11-01, "another Homer's car idea"): pulse the torch on
  the beat. Feasible: `CameraManager.setTorchMode()` works from API 23
  (Nexus 4 included), needs no camera permission, switches in ~10-30 ms.
  Beat from the tempo per song (Discovery D0), not `Visualizer`, which
  needs the microphone permission. Constraints if ever built: at most 3
  flashes a second (WCAG 2.3.1, photosensitive epilepsy), off by default,
  a toggle in ⋮; phone playback only (Sonos and the TV play with their
  own delay, so it would flash off the beat); mind LED heat and battery.
  Measured on the Nexus 4 (2026-11-01, 400 switches): on takes 520-580 ms,
  off 90-100 ms, and the call blocks until done, so at best 1.6 flashes a
  second, unevenly: below the safety limit and too slow for a beat. Likely
  its legacy camera stack powering the camera up for each "on". Not
  measured on the Pixel 5 or S25, where it's expected to be much faster.
  Need: D0's tempo data, and wanting it for a dark room.
- Genre families from data instead of shared words. MusicBrainz has
  "subgenre of" links (britpop → alternative rock, not pop), shown on
  its genre pages but not returned by its web service (`inc=genre-rels`
  is ignored); the database dump has them. Discogs has two levels, 15
  genres over ~500 styles, but no style → genre list; the pairing is
  only per release. beets' lastgenre plugin ships a tree from
  Wikipedia (`genres-tree.yaml`). Need: the word families getting a
  grouping wrong.
- Full SystemUI theme for the Nexus 4 (overlay APK), only if the A6
  basics aren't enough.
- Glow boot animation; dock-mode clock.
- Year in review story cards (A4) before a year of history exists.
- Merging favourites and play history across both phones.
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
- Inlay and booklet art (2026-11-01): show the back cover, inlays and
  booklet pages, e.g. by panning across the art on Now Playing. The
  original collection has them for some albums (155 images that aren't
  the front cover, plus scans and two PDF booklets), kept in
  `~/Music-extras/` by `Artist/Album/`. Need: a way to tell front from
  back that doesn't trust file names, and wanting it.
- Music videos (2026-11-01). The collection has 7 (Michael Jackson's
  *Thriller* and *Bad*, Brandi Carlile's *The Story*), all H.264
  Baseline at 320×240, kept in `~/Music-extras/`. The Nexus 4 decodes
  H.264, MPEG-4 and H.263 in hardware up to 1920×1088, and VP8, VP9 and
  HEVC only in software (`/vendor/etc/media_codecs.xml`), so the target
  would be H.264 up to 720p. Media3 already plays video; the work is a
  video screen. Only the TV could show it over UPnP. Need: more videos
  than these 7.

Not doing (and why):
- Audiobooks, or splitting the app into reusable modules for them:
  out of scope (decided 2026-10-29). A chapter now and then plays
  through "Open with" (2026-11-06).
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
None open. Both earlier ones are answered: UPnP playback works on S2 18.6
and on the TV (B0), and Compose stays (A1); see `decisions.md`.

## Decision log
Moved to `decisions.md` (2026-11-01), newest first.
