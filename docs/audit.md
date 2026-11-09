# Audit

Third-party checks of what the app does, newest first. Each names the APK
it checked by version and SHA-256.

## 2026-11-09 · No trackers
Marraxo v0.5.0 (SHA-256 `f2142307…de7148a`). The app has no analytics,
accounts or Google Play Services (AGENTS.md), and its libraries (listed on
the About screen, all Apache-2.0 or MIT) include no tracker SDK, so there
is nothing for a tracker scanner to match.

Exodus Privacy's scanner (exodus-core 1.3.14, the library behind
[reports.exodus-privacy.eu.org](https://reports.exodus-privacy.eu.org))
read the APK: 3,676 classes (the app plus AndroidX, Media3 and Compose)
and these permissions — media playback in the foreground, reading audio
(and legacy external storage for the Nexus 4), notifications, wake lock,
Internet and network state (speakers and the TV on the local network),
Wi-Fi state and multicast (finding them), and the app-private receiver
permission AndroidX adds. The online tracker-signature match did not run:
Exodus's `reports.exodus-privacy.eu.org/api/trackers` endpoint is
unreachable. A signature scan from a networked host is pending; an earlier
release matched all 432 signatures against the APK and found none.
