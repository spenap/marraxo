# Nexus 4 music player

An offline-first music player that turns an LG Nexus 4 ("mako") into a
dedicated music device. It plays through headphones/Bluetooth, or over the
LAN (UPnP) to two Sonos One SL speakers or a Samsung TV.

- Full original brief: `docs/original-brief.md` (a guide, not gospel).
- Plan and progress: `docs/ROADMAP.md`. Update it when a milestone item is
  done or a decision is made.

## Components (one repo)
- `android/`: Kotlin app, built with Gradle, deployed with ADB.
- `tools/`: Python desktop tools (music library inventory/enrichment).
- `docs/`: plans, decisions, protocol notes.

## Target device
- Nexus 4: Snapdragon S4 Pro, 32-bit ARMv7, 2 GB RAM, 1280×768.
- ROM/Android version: NOT YET DECIDED. Being chosen and flashed in a
  separate project; the result lands in
  the ROM notes. Until then, assume API 25–27.
- Check every Android dependency's minSdk against the target API before
  adding it. Avoid native libraries unless they ship armeabi-v7a.

## Principles
- Offline at runtime: no Internet, cloud services, accounts, analytics,
  or Google Play Services. Internet is fine for desktop tooling and builds.
- Smallest maintainable stack; no premature abstraction.
- Playback lives in a service, independent of the UI.
- The phone is low-end: keep image allocations, animations and
  background work modest.
- Never expose arbitrary filesystem paths over HTTP; serve only selected
  tracks.
- Never modify the original music collection without a backup and a
  change manifest.
- Verify protocol and compatibility assumptions against real hardware
  (the phone, the Sonos speakers) and record findings in `docs/`.
