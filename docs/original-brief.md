# Project: A Modern, Offline-First Music Player for the Nexus 4

## 1. Vision

Build a personal music player that turns an old LG Nexus 4 into a dedicated, self-contained music device — essentially a custom iPod touch, but with a modern interface and local-network streaming capabilities.

The project has three main components:

1. **An Android music player:** a polished, custom-designed native application running on the Nexus 4.
2. **A music library enrichment pipeline:** a desktop tool that analyses, identifies, and enriches an old collection of MP3 files.
3. **Sonos integration:** the ability to play local music through two Sonos One SL speakers over the local Wi-Fi network.

The system should work without a mobile connection, cloud services, or an Internet connection during normal playback. Internet access may be used during the initial music metadata enrichment process, but it must not be a runtime dependency.

This is a personal project. Prioritise reliability, simplicity, maintainability, and a distinctive user experience over commercial-scale architecture.

## 2. Hardware and Environment

### Android device
- Device: LG Nexus 4 (codename `mako`).
- CPU: Qualcomm Snapdragon S4 Pro.
- RAM: 2 GB.
- Display: 1280 × 768 pixels.
- Audio: 3.5 mm headphone jack and Bluetooth.
- Intended ROM: LineageOS 15.1 / Android 8.1 Oreo, or a suitable community ROM with a newer Android version if it offers meaningful benefits.

The exact ROM has not yet been selected. Verify device compatibility, available ROMs, build requirements, and hardware functionality before committing to a target Android version.

Do not assume that a modern Android library or application dependency will work on the selected ROM. Explicitly verify minimum SDK requirements and compatibility with the device's ARM architecture.

### Development environment
- Primary development machine: Linux.
- Preferred programming language for Android: Kotlin.
- Desktop tooling may use Python.
- Build, install, and debug the Android application using Gradle and ADB.
- The application should be developed in a Git repository with reproducible build and deployment instructions.

### Audio output
- Local playback through the Nexus 4's headphone jack or Bluetooth.
- Remote playback through two Sonos One SL speakers on the same local network.
- The speakers may already be configured as a stereo pair or grouped through the Sonos system.

## 3. Core Requirements

### A. Native Android Music Player

Build a native Android application rather than modifying the system music application.

The application must:

- Browse locally stored music.
- Display albums, artists, tracks, genres, and playlists.
- Support search across the local library.
- Display album artwork.
- Play, pause, skip, seek, shuffle, and repeat.
- Maintain a playback queue.
- Support favourites and basic personal ratings.
- Remember playback state across application restarts.
- Continue playing with the screen off.
- Provide lock-screen controls and Android media-session integration.
- Handle headphone disconnection and audio interruptions appropriately.
- Work entirely offline.
- Avoid requiring Google Play Services or a user account.

Playback should remain independent of the UI so that navigation, screen rotation, or application backgrounding cannot unnecessarily interrupt music.

#### Technology choices

Investigate the following options:

- Kotlin for the application.
- AndroidX Media3 / ExoPlayer for playback, using a version compatible with the target Android release.
- Android media sessions and an appropriate foreground service for background playback.
- MediaStore for discovering local audio files.
- SQLite or Room for persistent library metadata, if the selected Room version is compatible.
- A suitable image-loading and caching solution for album artwork.

Jetpack Compose is an option, but do not assume the latest Compose toolchain supports the chosen Android environment. Evaluate its compatibility and resource usage against a traditional Android View-based UI.

Choose the smallest maintainable technology stack that meets the requirements.

### B. Visual Design

The UI should look and feel like a contemporary music player rather than an application designed in 2012.

Use modern music applications as visual inspiration, including Apple Music, Spotify, Nothing OS, and minimalist high-end audio players.

Do not simply reproduce another application's branding or proprietary assets. Develop an original visual identity inspired by modern design patterns.

Target screens:

1. **Now Playing**
   - Large album artwork.
   - Artist and track information.
   - Playback progress and seeking.
   - Large, accessible playback controls.
   - Subtle artwork-derived background colours or gradients.
   - Persistent access to output-device selection.

2. **Library**
   - Album grid with cached artwork.
   - Artist and album browsing.
   - Search and filtering.
   - Genre and year navigation where metadata is available.

3. **Queue**
   - Upcoming tracks.
   - Reordering and removal.
   - Shuffle and repeat controls.

4. **Album and Artist Details**
   - Artwork, metadata, track listing, and related information.
   - Additional credits and discography information when available.

5. **Output Selection**
   - Nexus 4 local playback.
   - Individual Sonos speakers.
   - Sonos groups where supported.

The Nexus 4 has limited RAM and relatively old graphics hardware. Avoid excessive animations, unnecessary recompositions, oversized image allocations, and expensive background work.

The interface should remain responsive with large libraries and low-resolution album artwork.

### C. Local Music Storage and Transfer

Music will initially be transferred from a Linux computer to the Nexus 4.

Support standard Android file-transfer mechanisms:

- USB file transfer through MTP where available.
- ADB for development and bulk transfers.
- Optional Wi-Fi-based library synchronisation in a later phase.

A basic development workflow should resemble:

```bash
./gradlew assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb push ./Music /sdcard/Music
```

Provide clear setup instructions for enabling USB debugging and authorising the development computer.

Do not require Internet access for installing or using the finished application, although downloading development dependencies and building the APK will normally require an initial development setup.

The library should use stable media identifiers and metadata rather than relying exclusively on absolute file paths.

Handle deleted or moved files, rescanning, missing artwork, and duplicate tracks gracefully.

## 4. Sonos One SL Integration

The system must be able to play the Nexus 4's local music through two Sonos One SL speakers over the local network.

The intended architecture is:

1. The Android application hosts a lightweight HTTP server.
2. It exposes the selected audio file through a local HTTP URL.
3. The application discovers compatible Sonos devices on the LAN.
4. It sends playback commands to the selected speaker.
5. The speaker fetches the audio directly from the Nexus 4.

The phone should act as a controller and file server rather than continuously transcoding or relaying the audio stream.

### Investigate UPnP AV

Evaluate whether UPnP AV is a suitable implementation strategy for the target Sonos firmware.

Investigate:

- SSDP discovery.
- UPnP device descriptions.
- AVTransport.
- `SetAVTransportURI`.
- `Play`, `Pause`, `Stop`, and seeking where supported.
- RenderingControl for volume.
- GENA event subscriptions for playback-state updates.
- Supported audio formats and metadata requirements.

Do not assume that all Sonos firmware versions expose identical behaviour or that legacy UPnP interfaces will remain supported indefinitely.

Verify the protocol against the actual Sonos One SL devices and their current firmware before building the full integration.

If UPnP is unsuitable, investigate alternative supported local-control interfaces and explain their trade-offs.

### HTTP Audio Server

The embedded server must:

- Serve audio files without exposing arbitrary filesystem paths.
- Bind to a network interface reachable by the Sonos speakers.
- Support HTTP GET and byte-range requests where needed.
- Return appropriate content types.
- Handle multiple requests safely.
- Keep the selected file accessible during playback.
- Avoid unnecessary audio decoding or transcoding.
- Stop or restrict access when remote playback is no longer required.

Test seeking and playback duration reporting, not just basic playback.

A successful initial experiment should prove that a Sonos speaker can fetch and play a local MP3 from the Nexus 4.

### Speaker Grouping

Support individual speakers and, if practical, existing Sonos groups.

Do not assume that sending identical playback commands to both speakers will produce synchronised audio.

Investigate whether grouping and synchronisation should be delegated to the Sonos system itself.

The application should display the selected output target and its current playback state.

### Local Network and Offline Operation

The system must not depend on external Internet access during playback.

A local router or access point may provide Wi-Fi connectivity without Internet access.

Investigate whether a phone-hosted Wi-Fi hotspot can provide suitable connectivity, but do not assume client-to-client communication will be permitted.

The HTTP server and the Sonos speakers must be able to communicate directly over the local network.

The local music player must continue working independently if the Sonos speakers are unavailable.

## 5. Music Library Enrichment Pipeline

The source collection consists of an old MP3 library accumulated before approximately 2010, when streaming services such as Spotify became the primary listening method.

Expect inconsistent metadata, incomplete ID3 tags, poorly named files, missing album artwork, compilation albums, duplicate tracks, and ambiguous artist names.

Build a separate desktop tool to inspect and enrich the collection before modifying any files.

Python is a suitable initial choice.

### Phase 1: Read-Only Inventory

Scan the collection without modifying anything.

Extract:

- File paths and filenames.
- File formats.
- Duration, bitrate, sample rate, and file size.
- Existing ID3 metadata and ID3 versions.
- Title, artist, album, album artist, track number, disc number, year, genre, and comments.
- Embedded artwork and its dimensions.
- Missing or contradictory metadata.
- Exact file duplicates.
- Candidate duplicate recordings.

Generate a useful report, preferably HTML, showing library statistics and representative problems.

Keep all original files untouched.

### Phase 2: Identification and Metadata Recovery

Investigate these services and tools:

- MusicBrainz for release, artist, recording, and track metadata.
- AcoustID for acoustic-fingerprint-based identification where appropriate.
- Existing ID3 tags and filenames as supporting evidence.
- Reliable artwork sources for album covers.

Use deterministic matching where possible.

A language model may help with ambiguous metadata, normalisation, candidate ranking, and anomaly detection, but it must not silently invent facts.

When identifying a track, distinguish between:

- The underlying recording.
- The release or album.
- The specific edition or pressing.
- The file itself.

A matching title and artist do not necessarily identify the correct release.

Provide confidence scores, supporting evidence, and an explicit review queue for ambiguous cases.

### Phase 3: Metadata Normalisation

Normalise fields such as:

- Track title.
- Track artist.
- Album artist.
- Album title.
- Track and disc numbers.
- Release date.
- Genre.
- Composer and other available credits.
- MusicBrainz identifiers.

Preserve the distinction between album artist and track artist, particularly for compilations and collaborations.

Do not collapse meaningful differences between original releases, remasters, live recordings, and alternate versions.

### Phase 4: Artwork and Additional Metadata

Where reliable sources are available, retrieve:

- Album artwork.
- Release date.
- Record label.
- Track credits.
- Artist identifiers.
- Album and recording identifiers.
- Other useful structured metadata.

Keep optional or uncertain enrichment separate from authoritative tags.

Do not make the Android application dependent on online metadata APIs. Perform enrichment on the desktop and package the resulting metadata locally.

### Phase 5: Safe Writing and Validation

After the user approves the proposed changes:

- Back up the original files or provide an equally reliable rollback mechanism.
- Write normalised ID3 metadata.
- Choose an appropriate ID3 version based on compatibility requirements.
- Embed artwork where appropriate.
- Produce a machine-readable change manifest.
- Record original and updated values.
- Validate that the resulting files remain readable.
- Allow changes to be reviewed and reversed.

Never perform destructive batch updates without an auditable record.

### Metadata Architecture

Use two layers:

**Portable file metadata**
- Title, artist, album, album artist, track/disc number, date, genre, identifiers, and artwork.

**Application database**
- Playback history.
- Favourites and personal ratings.
- Custom tags.
- Extended credits and relationships.
- Local recommendations.
- Additional metadata that does not belong naturally in ID3.

This ensures the music remains usable in other applications while enabling richer functionality in the custom player.

## 6. Playback Modes

### Mode A: Standalone Portable Player

- No Wi-Fi required.
- All music stored locally.
- Headphones connected through the 3.5 mm jack or Bluetooth.
- Screen can remain off during playback.
- Minimal battery consumption.
- No online accounts or services.

### Mode B: Local Sonos Playback

- Nexus 4 connected to the same LAN as the speakers.
- Nexus serves local music over HTTP.
- Sonos fetches and plays the selected files.
- Playback control remains available through the Android application.
- Internet access is not required.
- The Nexus remains powered and connected while serving audio.

The application should clearly indicate the active playback mode.

## 7. Suggested Development Plan

### Milestone 0: Technical Feasibility

Before implementing the full application:

1. Confirm the target LineageOS version and its compatibility with the Nexus 4.
2. Build and install a minimal Kotlin application.
3. Verify local MP3 playback.
4. Verify background playback and lock-screen controls.
5. Test Sonos discovery and UPnP compatibility.
6. Serve one local MP3 over HTTP and attempt playback on a Sonos One SL.
7. Verify operation with Internet access disabled.

Document any hardware, ROM, protocol, or library limitations.

### Milestone 1: Minimal Music Player

Implement:

- Local library scanning.
- Basic metadata extraction.
- Playback and seeking.
- Play/pause/next/previous.
- Persistent playback state.
- Background playback.
- A basic but polished Now Playing screen.

### Milestone 2: Library and Visual Design

Implement:

- Album and artist browsing.
- Cached artwork.
- Search.
- Queue management.
- Favourites.
- Custom visual design.

### Milestone 3: Sonos Integration

Implement:

- Device discovery.
- Speaker selection.
- Local HTTP audio serving.
- Playback commands.
- Playback-state synchronisation.
- Volume controls.
- Group playback where supported.

### Milestone 4: Desktop Metadata Tool

Build the read-only inventory tool, then add identification, enrichment, review, and safe ID3 writing.

Keep the metadata tool independent of the Android application.

### Milestone 5: Synchronisation and Polish

Implement optional desktop-to-phone synchronisation, error recovery, battery optimisation, and a final offline test suite.

## 8. Engineering Principles

- Offline-first by design.
- No mandatory cloud dependencies.
- No user accounts or analytics.
- No unnecessary Google dependencies.
- Explicit compatibility checks for the selected Android release.
- Small, modular architecture.
- Clear separation between UI, playback, library indexing, networking, and Sonos control.
- Reproducible builds and straightforward ADB deployment.
- Graceful failure when metadata, artwork, speakers, or network access are unavailable.
- Avoid premature abstraction and unnecessary frameworks.
- Document protocol assumptions and test them against real hardware.

Security matters even on a trusted home network. Do not expose an unrestricted filesystem server or accept arbitrary remote commands.

## 9. What I Want From the Coding Agent First

Do not immediately generate the entire application.

Start by producing:

1. A technical feasibility assessment for the Nexus 4 and the proposed Android versions.
2. A comparison of Kotlin + Compose versus traditional Android Views.
3. A recommended playback architecture with compatible dependency versions.
4. A Sonos One SL compatibility assessment, including the exact UPnP operations and HTTP behaviour to test.
5. A proposed project structure.
6. A minimal proof-of-concept plan.
7. A list of unresolved assumptions and risks.
8. A staged implementation plan with concrete acceptance criteria.

Prioritise verifying the riskiest assumptions — especially legacy Android compatibility and Sonos local playback — before investing in the full UI.

The end goal is a beautiful, self-contained music player built around an old Nexus 4, powered by a carefully curated local music collection and capable of playing through headphones or Sonos without relying on the Internet.
