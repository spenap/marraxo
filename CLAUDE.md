# Nexus 4 music player

An offline-first music player, first built for an LG Nexus 4 ("mako") as a
dedicated music device, and also meant to run on a Samsung Galaxy S25. It
plays through headphones/Bluetooth, or over the LAN (UPnP) to two Sonos One
SL speakers or a Samsung TV.

- Full original brief: `docs/original-brief.md` (a guide, not gospel).
- Plan and progress: `docs/ROADMAP.md`. Update it when a milestone item is
  done or a decision is made.
- UI reference: `docs/mockups/prototype.html` (interactive, "Glow").

## Components (one repo)
- `android/`: Kotlin, Gradle multi-module, deployed with ADB.
- `tools/`: Python desktop tools (music library inventory/enrichment).
- `docs/`: plans, decisions, protocol notes, mockups.

## Target devices
One APK for both: `minSdk` = the Nexus 4 ROM's API level, `targetSdk` =
current Android.
- **Nexus 4** (development device and performance floor): Snapdragon S4
  Pro, 32-bit ARMv7, 2 GB RAM, 768×1280. ROM/Android version NOT YET
  DECIDED; it's chosen in a separate project and the result lands in
  the ROM notes. Until then, assume API 25–27.
- **Samsung Galaxy S25** (main phone): arm64, current Android/One UI.
  Handle the newer platform rules: `READ_MEDIA_AUDIO` (API 33+) instead of
  `READ_EXTERNAL_STORAGE`, the notification permission (API 33+), and the
  `mediaPlayback` foreground service type (API 34+). Samsung's battery
  management can put background apps to sleep; test long playback on it.
- Check every dependency's minSdk against the Nexus 4 API. Avoid native
  libraries unless they ship both armeabi-v7a and arm64-v8a.

## Reuse: a future audiobook app
Parts of this app should be reusable in a later audiobook player. Keep
reusable code in `core` modules that know nothing about "albums" or
"music"; the music app is one consumer of them.
- Likely reusable: playback service and queue, output routing (local,
  UPnP, Sonos), UPnP discovery/control and the HTTP file server, local
  media scanning, artwork and palette colours, play history, and the Glow
  design system.
- Music-specific: library browsing (albums/artists/genres), stats screens.
- Playback must not assume short tracks: keep resume position, playback
  speed and seeking generic, since audiobooks will need them.
- Set module boundaries now, but don't build audiobook features
  (chapters, bookmarks, sleep timer) until that app is started.

## Principles
- Offline at runtime: no Internet, cloud services, accounts, analytics,
  or Google Play Services. Internet is fine for desktop tooling and builds.
- Smallest maintainable stack; no premature abstraction. Module
  boundaries are the exception: they are cheap now and costly later.
- Playback lives in a service, independent of the UI.
- Design for the Nexus 4 first: keep image allocations, animations and
  background work modest. If it is smooth there, it is smooth on the S25.
- Never expose arbitrary filesystem paths over HTTP; serve only selected
  tracks.
- Never modify the original music collection without a backup and a
  change manifest.
- Record play history from the first version; stats depend on it and it
  cannot be rebuilt later.
- Verify protocol and compatibility assumptions against real hardware
  (both phones, the Sonos speakers, the TV) and record findings in `docs/`.
