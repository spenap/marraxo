# Audit

Third-party checks of what the app does, newest first. Each names the APK
it checked by version and SHA-256.

## 2026-11-08 · Exodus Privacy: no trackers
Exodus Privacy's scanner (exodus-core 1.3.14, the library behind
[reports.exodus-privacy.eu.org](https://reports.exodus-privacy.eu.org)) on
v0.2.2 (SHA-256 `4ad0d7d3…d194d0`) matched its 432 tracker signatures
against the APK's 3,655 classes and found none. Permissions: media
playback in the foreground, reading audio, notifications, wake lock,
Internet and network state (speakers and the TV on the local network),
Wi-Fi state and multicast (finding them), and the app-private one AndroidX
adds.
