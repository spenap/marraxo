# Decisions

Lasting decisions, newest first: what was decided, why, and where the
detail lives. Topic docs (`radio.md`, `lyrics.md`, `upnp-renderers.md`…)
keep the reasoning in full; this log is the index of what's in force.

- Add an entry when a decision constrains later work: a rule, a choice
  between real alternatives, something ruled out. Not for every UI tweak;
  commits cover those.
- Entries aren't rewritten. A reversal is a new entry that says what it
  supersedes and what changed our minds; the old entry only gains a
  "Superseded by" line.
- Ideas not yet decided go to ROADMAP.md, "Ideas and not doing".

Entries before 2026-11-01 were moved here from ROADMAP.md's decision log
or recovered from commits and topic docs.

## 2026-11-07 · A lost speaker or TV hands back to the phone, paused
When the phone leaves the Wi-Fi, or a renderer misses 3 polls in a row,
playback moves back to this phone with the queue and position, paused,
and a message names the output that was lost. It doesn't resume: the
phone's speaker shouldn't start on its own. Detail: `PlaybackService`.

## 2026-11-06 · Device tests on any phone; performance on the Nexus 4
`connectedDebugAndroidTest` may run on whichever phone is connected;
`scroll-bench.sh` stays on the Nexus 4.
Why: the tests check behaviour, which doesn't depend on the phone; CI
still runs them on a Nexus 4 emulator. Detail: AGENTS.md.

## 2026-11-06 · Files opened from other apps replace the queue
Opening an audio file with Makofi plays it in place of the queue,
without adding it to the library. `MainActivity`, started by the VIEW
intent, shows only Now Playing (no library to load) and one Back returns
to the app that opened it. A file MediaStore knows
(matched by name and size) plays as that MediaStore track, so the
library, history and saved queue know it. Any other file is copied to
the cache (only the latest is kept), recorded by its tags, and not saved
in the queue: the sender's grant ends when the screen it opened closes
(seen on the Pixel 5).
Why: one Now Playing, notification and output picker for every song.
Detail: `OpenFile.kt`.

## 2026-11-02 · AGENTS.md holds the project's instructions
Why: so any agent (Claude Code, Codex…) reads the same rules; CLAUDE.md
imports it. Working rules kept in one agent's private notes moved there.
Detail: AGENTS.md.

## 2026-11-02 · MIT licence; data credited, not distributed
Why: the user's choice. Compatible with every library (35 Apache-2.0,
5 MIT), whose notices ship in the app (About → Open-source licences).
Data from MusicBrainz, Wikipedia (CC BY-SA), the Cover Art Archive and
LRCLIB is credited in the app and not distributed.
Detail: README.md, LICENSE.

## 2026-11-02 · The app is Makofi, `com.simonpena.makofi`
Why: "Mako" is the Nexus 4's codename (LG, 2012), so it named the device
more than the player. Makofi is Swahili for applause, keeps "mako" for
where the project was born, and reads as Mako-fi (as in hi-fi); a clap
can be its icon. Quenlla (Galician for shark) was the alternative. The
application id changed too, while only our three phones had the app:
play history, favourites and the queue started over (a copy of each
phone's history was kept on the laptop). Later the id can't change
without losing them. Notes and commits before this date say "Mako".
Phone files the old name created stay as they are: `Music/.mako-sync`
(sync's list) and the Nexus 4's `/data/local/tmp/mako-appliance.prev`.
Repository: github.com/spenap/makofi.
Detail: ROADMAP.md ("Ideas and not doing" held the shortlist).

## 2026-11-01 · Now Playing: lyrics and notes scroll below, the heart stays by the title
Why: buttons for lyrics and notes competed for room with the cover (the
Nexus 4 has little) and with a favourites heart. Of six layouts mocked,
the user chose Spotify's: cards below the controls, shown only when there
is something to show. A heart on the cover or a flipping cover were
set aside: the heart should stay visible and mean the song.
Detail: `mockups/now-playing.html`, ROADMAP.md (A3).

## 2026-11-01 · Lyrics from LRCLIB's dump, streamed, saved by artist
Updates: 2026-11-01 "Lyrics from LRCLIB, fetched on the laptop", whose
"the dump is held back" no longer holds.
Why: the API was down or half-down all day (502, then 503 on any real
lookup). The dump is 49 GB gzipped and 269 GB unpacked, so it's read as
a stream and never unpacked; only your artists' tracks are kept (8.9 GB
locally, git-ignored). Lookups follow the API's rules and take seconds,
offline. The API remains the fallback for songs the dump lacks.
Detail: `lyrics.md` ("From the dump"), `tools/lyrics/lrclib_dump.py`.

## 2026-11-01 · The clean copy is now the collection, at ~/Music
Why: the cleanup is finished, so the copy beets wrote (`Music-clean`)
became `~/Music`; the untouched original became
`~/Music-todelete`, which no tool reads. Paths inside the collection
(album folders, liner notes and lyrics keys) didn't change; the
tools' collection setting did.
Updates: 2026-10-30 "Cleanup with beets, into a copy". From here on, "the
original collection" that is never modified without a backup and a
change manifest is `~/Music`.

## 2026-11-01 · One radio; the modes become hidden, weighted feeds
Supersedes: 2026-10-31 "Radios in named modes".
Why: after using it, three or four radio types in the song sheet were too
many choices. The former modes (Similar, Same era, Same people, Anything
goes) stay in the code as feeds (`RadioWeights`), and the radio is their
weighted average. A 1 on one feed and 0 on the rest gives back that feed,
so going back is a one-line change. The simulation gates the mix too.
Not a conflict with 2026-10-30 "Discovery as separate views": the views
still exist; the radio blends them, and debug builds show which one led
each pick.
Detail: `radio.md` (Decisions 2, simulation baseline).

## 2026-11-01 · Lyrics from LRCLIB, fetched on the laptop
Why: LRCLIB is free, open, keyless and has line-timed lyrics. Musixmatch
is paid, and its free plan returns ~30% of each song; Genius's API leaves
lyrics out, and scraping its pages reportedly breaks its terms. Your own
files' lyrics tags are almost all empty. As with the liner notes, the
desktop fetches and the phone only reads `lyrics.db`. Lyrics are never
committed nor sent anywhere but your own phones. The 49 GB database dump
is held back until outages or misses justify it.
Detail: `lyrics.md`.

## 2026-11-01 · The clean copy holds MP3, AAC, Ogg Vorbis or FLAC
Why: what every target plays (both phones, the Sonos One SL, the TV).
Anything else is converted in the copy, with a change manifest. Prompted by
a Musepack album that Android can't play.
Detail: `upnp-renderers.md`, ROADMAP.md (C2).

## 2026-10-31 · Debug and decision helpers stay out of regular builds
Why: anything that shows how the app decided, rather than the music,
helps while developing and misleads while listening. So does anything
shown to help decide something. Show it only in debug builds
(`BuildConfig.DEBUG`), or drop it. Applied so far:
- the radio's reasons: debug builds only, and the log keeps them (Amarok
  didn't show its reasons either);
- the liner notes' highlighted "also in your library" hint: removed;
  names in your library are links instead.
Detail: `radio.md` (Decisions 3); commit 93f7349 and the design audit.

## 2026-10-31 · Test plays stay in play history
Why: history isn't a radio bias yet, so they don't matter. Revisit when
"not heard lately" is added.
Detail: `radio.md` (Decisions 4).

## 2026-10-31 · A radio replaces the queue
Why: as Spotify's does. The seed plays at once, then picks follow three
ahead in the normal queue, so the notification, the speakers and the TV
need nothing new.
Detail: `radio.md` (Decisions 1).

## 2026-10-31 · Radios in named modes
Superseded by: 2026-11-01 "One radio".
Why: as Plexamp's Guest DJ: names are easier to understand than one
"reach" knob.

## 2026-10-31 · An artist or genre opens a level below its tab, with Back
Why: a filter chip on Albums felt like a filter, not a place, and clearing
it left you on Albums, not where you came from.
Detail: commit 9c34db0.

## 2026-10-31 · Chromecast output is parked
Why: the Cast SDK needs Google Play Services (ruled out). The CASTV2
protocol could be spoken directly, but there's no Chromecast here to need it.
Lyrics on a TV through Cast would need a receiver hosted online.
Detail: ROADMAP.md (Ideas), `lyrics.md` (Chromecast).

## 2026-10-31 · No regressions get in
Why: WebKit's rule. Before every commit: unit tests; UI tests on the Nexus
4 for UI changes; the scroll benchmark for anything on screen, judged
against the version before (measured too, never assumed); real hardware for
speaker and TV playback. A failure that predates a change is logged with
its numbers, never a reason to let a new one in.
Detail: AGENTS.md (Principles).

## 2026-10-31 · English, Spanish and Galician, following the phone
Why: no in-app picker. The Nexus 4 can switch its system language, Galician
ships with AOSP 8.1, and Android 13+ has a per-app language.

## 2026-10-30 · Genres: keep the databases' names; the app groups them
Why: retagging to get a grouping loses information (Britpop stays
Britpop). For a borderline genre (fewer than 3 albums, or a synonym), take
MusicBrainz's most voted genre when it leads with real votes, otherwise
Discogs.
Detail: ROADMAP.md (C2).

## 2026-10-30 · New features are test-driven
Why: write the failing test first, at the cheapest level that catches the
behaviour; a device check confirms but doesn't replace a test.
Detail: AGENTS.md (Principles), ROADMAP.md (Track T).

## 2026-10-30 · Usage is measured on the phone; ideas wait for evidence
Why: a usage log read on the laptop answers what to polish and what to
drop, with no remote analytics. New ideas go to "Ideas and not doing"
until there's evidence or a clear need.
Detail: ROADMAP.md (Track U).

## 2026-10-30 · The phone holds a rotating selection of the collection
Supersedes: 2026-10-29 "The collection fits on the phone" and
2026-10-30 "A 3.8 GB test sample on the Nexus 4".
Why: the collection is 37 GB and the Nexus 4 has ~12.5 GB free.
`tools/sync` fills the phone: played albums first, then a stable shuffle.
It only removes albums it copied itself.
Detail: `tools/sync/sync.py`, ROADMAP.md (Later).

## 2026-10-30 · The app is model-agnostic
Why: it reads a versioned `discovery.db` contract (views, label sets,
scalars, edges) and never ships a model. The radio starts as tag-based
biases (Amarok-style) and gains sound-based ones later without changing
the engine.
Detail: `discovery.md`.

## 2026-10-30 · Discovery as separate, explained views
Why: sound, people, era, mood and topic as views you can name, instead of
one opaque blended score. Heavy models run on the desktop only; results
are keyed by audio content so they survive renames.
Detail: `discovery.md`.

## 2026-10-30 · Liner notes are fetched on the desktop
Why: an offline "X-ray". The desktop builds `linernotes.db`; the app never
goes online.
Detail: ROADMAP.md (C3).

## 2026-10-30 · Compose stays
Supersedes: 2026-10-29 "Compose, provisionally".
Why: on the Nexus 4's album grid, a release build compiled ahead of time
(`speed-profile`) has 1.3% janky frames (5 ms median). Debug builds are
97% janky, so performance is judged only on release builds installed with
`android/scripts/install.sh`.

## 2026-10-30 · Glow is our own; another player isn't a reference
Why: the art-tinted look is a common pattern (Apple Music, Spotify,
Android's media notification). another player's maintainer doesn't want
LLM-written code, so we neither imitate nor contribute to it.

## 2026-10-30 · Cleanup with beets, into a copy
Why: MusicBrainz and AcoustID matching, fetchart and embedart, instead of
a custom tagger. It writes to `Music-clean`, so the original collection
is never modified; changes beyond beets go through a change manifest.
Detail: ROADMAP.md (C2), `tools/beets/`.

## 2026-10-30 · The library is the shared Music folder; the app only hides
Why: other folders (test tones, recordings) aren't music. Deleting files
belongs to the desktop collection and its sync, not the phone.

## 2026-10-30 · A 3.8 GB test sample on the Nexus 4
Superseded by: 2026-10-30 "The phone holds a rotating selection".
Why: 50 albums and 664 tracks across the alphabet, with M4A and OGG among
them, until sync existed.

## 2026-10-29 · minSdk 27, from the Nexus 4's LineageOS 15.1
Why: one APK for the Nexus 4 and the Galaxy S25: minSdk is the Nexus 4
ROM's API level, targetSdk is current. The Nexus 4 is the development
device and the performance floor. For Sonos: receive SSDP replies on an
unconnected socket, hold a MulticastLock with the screen off, and keep a
WifiLock and a wake lock while serving HTTP.
Detail: `upnp-renderers.md`, the ROM notes.

## 2026-10-29 · Compose, provisionally
Superseded by: 2026-10-30 "Compose stays".

## 2026-10-29 · All outputs are generic UPnP renderers
Why: the two Sonos One SL (S2, firmware 18.6; Estudio and Cocina, not
paired) and the Samsung The Frame TV all speak UPnP AV. Sonos-specific
grouping sits on top.
Detail: `upnp-renderers.md`.

## 2026-10-29 · Play history from the first version
Why: stats depend on it and it can't be rebuilt later, so recording
shipped in A2, long before any stats screen.

## 2026-10-29 · UI direction: Glow
Why: an artwork-tinted dark look, with Sleeve's "Playing on <room> ·
<model>" output row. `mockups/prototype.html` is the reference.
Detail: `mockups/directions.html`.

## 2026-10-29 · The collection fits on the phone
Superseded by: 2026-10-30 "The phone holds a rotating selection".
Why: it was thought to be a few GB on a backup drive.

## 2026-10-29 · Offline at runtime
Why: a dedicated music device. No Internet, cloud services, accounts,
remote analytics or Google Play Services (the Nexus 4 has no Google apps).
Desktop tools and builds may go online; nothing measured on the phone is
sent elsewhere.
Detail: AGENTS.md (Principles).

## 2026-10-29 · No audiobooks, no reusable modules for them
Why: out of scope; one app module.

## 2026-10-29 · One repo, three tracks in parallel, riskiest first
Why: `android/`, `tools/` and `docs/`. Phone, speakers and library work
can proceed while one of them waits on hardware.
