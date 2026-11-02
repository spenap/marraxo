# Makofi

Makofi (Swahili for applause; also "Mako-fi", as in hi-fi) is an
offline-first music player, first built for an LG Nexus 4 ("mako") as a
dedicated music device, and also meant to run on a Samsung Galaxy S25. It
plays through headphones/Bluetooth, or over the LAN (UPnP) to two Sonos One
SL speakers or a Samsung TV.

- Full original brief: `docs/original-brief.md` (a guide, not gospel).
- Plan and progress: `docs/ROADMAP.md`. Update it when a milestone item is
  done.
- Decisions: `docs/decisions.md`, newest first. Add an entry for each
  lasting decision (why, and where the detail lives); a reversal is a new
  entry that supersedes the old one, never a rewrite.
- UI reference: `docs/mockups/prototype.html` (interactive, "Glow").

## Components (one repo)
- `android/`: Kotlin + Jetpack Compose + Media3, single `app` module.
  Install on a phone: `android/scripts/install.sh` (release build +
  ahead-of-time compile). Judge performance only on that: the debug build
  is ~100 ms/frame on the Nexus 4. Measure with
  `android/scripts/scroll-bench.sh` (`dumpsys gfxinfo`; fails above 5%
  janky frames). SDK in
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
- Offline at runtime: no Internet, cloud services, accounts, remote
  analytics, or Google Play Services. Internet is fine for desktop tooling
  and builds. Usage measured on the phone and read on the laptop (like play
  history) is fine; nothing is ever sent elsewhere.
- Write docs, comments and commit messages concisely: state what holds,
  not the reasoning that got there or every case it excludes. Fewer
  words leave less to go stale or wrong.
- New ideas go to ROADMAP.md, "Ideas and not doing", until there is
  evidence or a clear need.
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
- No regressions get in (WebKit's rule). Before every commit:
  1. `./gradlew testDebugUnitTest` passes.
  2. A change to the UI: `./gradlew connectedDebugAndroidTest` passes on
     the Nexus 4 (CI also runs it on a Nexus 4 emulator for every PR).
  3. A change to what's on screen or how it scrolls: `scroll-bench.sh`
     (with `TAB=` for the tab it touches) on the Nexus 4, release build,
     stays under the limit or is no worse than the version before. Measure
     that version too when it fails; never assume it was fine.
  4. A change to playback on speakers or the TV: checked on real hardware
     (`tools/upnp/probe.py`, or by hand, silently, volume restored).
  A known failure that predates the change goes in ROADMAP.md with its
  numbers; it isn't a reason to let a new one in.
- Verify protocol and compatibility assumptions against real hardware
  (both phones, the Sonos speakers, the TV) and record findings in `docs/`.

## Working on this repo
For any agent (Claude Code, Codex…) and any person.
- One branch and pull request per change. Merge only when CI's `android`,
  `tools` and `ui` jobs pass; nothing enforces it, so check. Push and
  merge when the user asks.
- Commits: atomic, Conventional Commits, ending with a `Co-Authored-By:`
  line that names the agent and model.
- Many tests failing the same way means the environment, not the code:
  read the first failure, fix the environment in the test setup, iterate
  on one class (`-Pandroid.testInstrumentationRunnerArguments.class=…`),
  and run the full suite only to confirm.
- Gate on `scroll-bench.sh`'s exit status, not its text. A run varies by
  about 2 points: repeat, and compare with the previous version's runs.
- Run long jobs (syncs, fetches, imports) in the background and stay
  reachable.
- The phones and speakers are shared: one agent at a time, nothing plays
  unless asked, volume left as found.
- Never commit lyrics, dumps, generated databases or tokens.
