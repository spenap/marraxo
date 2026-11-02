# Makofi

[![CI](https://github.com/spenap/makofi/actions/workflows/ci.yml/badge.svg)](https://github.com/spenap/makofi/actions/workflows/ci.yml)

An offline music player for Android. It plays the music stored on the phone,
through headphones or Bluetooth, or over the Wi-Fi to Sonos speakers and
TVs (UPnP). No account, no Internet, no tracking.

Makofi is Swahili for applause. It also keeps "mako", the codename of the
LG Nexus 4 (2012) it was first built for, which still runs it as a dedicated
music player.

## Status

A personal project in beta on three devices: a Nexus 4 (Android 8.1, the
performance floor), a Pixel 5 and a Samsung tablet. Not on any app store.

Working today:

- Library: albums, artists, genres and years, with letter scrubbers,
  search, and compilations shown as "Various artists".
- Playback in a service, with a queue, shuffle, repeat, a radio from any
  song, and a lock-screen player.
- Lyrics that follow the song, and liner notes (credits, studios, the story
  of the album), both prepared on a computer and read offline.
- Playing to Sonos speakers and a TV on the same network.
- Play history kept on the phone, never sent anywhere.

Plans, known issues and ideas: [`docs/ROADMAP.md`](docs/ROADMAP.md).
Decisions and why: [`docs/decisions.md`](docs/decisions.md).

## How it's built

- `android/`: the app, in Kotlin with Jetpack Compose and Media3. One APK
  for Android 8.1 (API 27) and up.
- `tools/`: Python tools for the computer side: tidying the collection's
  tags and covers (with [beets](https://beets.io)), fetching liner notes and
  lyrics, and copying music and data to the phones.
- `docs/`: plans, decisions, protocol notes and mockups.

Install on a connected phone:

    android/scripts/install.sh [device-serial]

Releases: `android/scripts/release.sh vX.Y.Z` tags a green `main` and
publishes an APK signed with the release key as a GitHub Release.

Tests: `./gradlew testDebugUnitTest` in `android/`, and `pytest` in
`tools/`; both run in CI on every push. UI tests
(`./gradlew connectedDebugAndroidTest`) and the scroll benchmark
(`android/scripts/scroll-bench.sh`) need a phone, so they run by hand.

## Credits

- **Libraries.** Makofi is built on 40 open-source libraries (35 under the
  Apache License 2.0, 5 under MIT). Their notices are collected at build
  time and shown in the app: ⋮ → About → Open-source licences.
- **Data.** Liner notes from [MusicBrainz](https://musicbrainz.org) and
  [Wikipedia](https://www.wikipedia.org)
  ([CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/)), covers
  from the [Cover Art Archive](https://coverartarchive.org), lyrics from
  [LRCLIB](https://lrclib.net). Not distributed.

## Licence

[MIT](LICENSE).
