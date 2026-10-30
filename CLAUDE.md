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
- `android/`: Kotlin + Jetpack Compose + Media3, single `app` module.
  Install on a phone: `android/scripts/install.sh` (release build +
  ahead-of-time compile). Judge performance only on that: the debug build
  is ~100 ms/frame on the Nexus 4. Measure with
  `android/scripts/scroll-bench.sh` (`dumpsys gfxinfo`). SDK in
  `~/Android/Sdk`, JDK 17.
- `tools/`: Python desktop tools (music library inventory/enrichment).
- `docs/`: plans, decisions, protocol notes, mockups.

## Target devices
One APK for both: `minSdk` = the Nexus 4 ROM's API level, `targetSdk` =
current Android.
- **Nexus 4** (development device and performance floor): Snapdragon S4
  Pro, 32-bit ARMv7 (armeabi-v7a only), 2 GB RAM, 768×1280. LineageOS
  15.1, Android 8.1, **API 27**, no Google apps, ADB root available.
  Details and network findings: the ROM notes.
- **Samsung Galaxy S25** (main phone): arm64, current Android/One UI.
  Handle the newer platform rules: `READ_MEDIA_AUDIO` (API 33+) instead of
  `READ_EXTERNAL_STORAGE`, the notification permission (API 33+), and the
  `mediaPlayback` foreground service type (API 34+). Samsung's battery
  management can put background apps to sleep; test long playback on it.
- Check every dependency's minSdk against the Nexus 4 API. Avoid native
  libraries unless they ship both armeabi-v7a and arm64-v8a.

## Principles
- Offline at runtime: no Internet, cloud services, accounts, analytics,
  or Google Play Services. Internet is fine for desktop tooling and builds.
- Smallest maintainable stack; no premature abstraction.
- Playback lives in a service, independent of the UI.
- Design for the Nexus 4 first: keep image allocations, animations and
  background work modest. If it is smooth there, it is smooth on the S25.
- Never expose arbitrary filesystem paths over HTTP; serve only selected
  tracks.
- Never modify the original music collection without a backup and a
  change manifest.
- Record play history from the first version; stats depend on it and it
  cannot be rebuilt later.
- New features are test-driven: write the failing test first, at the
  cheapest level that can catch the behaviour (JVM unit test, Robolectric,
  the fake renderer), then the code. `./gradlew testDebugUnitTest` must
  pass before committing. A device check confirms; it doesn't replace a
  test. Levels and plans: ROADMAP.md, Track T.
- Verify protocol and compatibility assumptions against real hardware
  (both phones, the Sonos speakers, the TV) and record findings in `docs/`.
