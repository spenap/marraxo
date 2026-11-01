# Lyrics: research and v1

Status: v1 built overnight 2026-11-01 (asked for while the user slept, no
questions possible, no playback). Decisions below are reversible; the ones
that most deserve a second look are marked **(review)**.

## What there is to work with

| Source | What it gives | Use for Mako |
|---|---|---|
| Tags in the files (`USLT` in MP3, `©lyr` in M4A, `LYRICS` in FLAC/Ogg) | Whatever the ripper or tagger wrote | **Barely anything here.** Of the 5,248 songs in Music-clean, 5,201 have an empty lyrics tag (beets wrote empty frames), 20 have under 80 characters, 27 have more; some of those are junk ("MP3 MIX by JENSENBRAZIL … blogspot.com"). Not worth a fallback in v1. |
| **LRCLIB** (lrclib.net) | Free, open database: plain and **line-timed (LRC)** lyrics, some word-timed; no key; asks clients to identify themselves and stay ~90 requests/min; matches a song by title, artist, album and **duration within ±2 s** | **Chosen.** Fetched once on the laptop, like the liner notes. |
| LRCLIB database dump | The whole database, SQLite, gzipped: **49 GB** (30 Oct 2026) | Not downloaded: too big to take on someone's connection unasked. Would remove the dependence on the API being up (see Outage). **(review)** |
| Musixmatch API | The largest catalogue, with timed lyrics | Licensed and paid; the free plan returns ~30% of each song's lyrics. Not for a personal offline player. |
| Genius API | Annotations, metadata | Its API leaves the lyrics out on purpose; scraping its pages reportedly breaks its terms. No. |
| NetEase, QQ Music and similar | Large timed catalogues | Unofficial APIs; no. |

## Formats

- **LRC**: `[mm:ss.xx] words`, one line per timestamp; tags such as
  `[ar:]`, `[ti:]`, `[offset:+ms]`; several stamps on one line for a
  repeated chorus. LRCLIB's `syncedLyrics`. What v1 shows.
- **Word timings** ("enhanced LRC" `<mm:ss.xx>word`, LRCLIB's Lyricsfile
  YAML, Apple's TTML): karaoke-style highlighting word by word. v1 reads
  LRC with word timings and drops them (keeps the words).
- **ID3 `SYLT`**: timed lyrics inside MP3 tags; rare, none here.

## Rights

Lyrics are copyrighted. For a personal player on the user's own
collection, fetching them for private reading is the same use LRCLIB's
own players make; Mako:
- never commits them (`tools/lyrics/out/` is git-ignored), never sends
  them anywhere but the user's own phones, and publishes nothing back to
  LRCLIB;
- credits the source on the lyrics screen ("Lyrics from LRCLIB").

## How v1 works

1. **Fetch** (`tools/lyrics/lyrics_fetch.py`): for each song in the clean
   inventory, `GET /api/get` with title, the *song's* artist (not the
   album artist, so compilations work), album and duration. On 404, a
   search by title and artist, taking a result within 2 s of the song's
   length, timed lyrics first. Every answer is cached, "missing" too, so a
   run resumes and nothing is asked twice. Polite: names itself in the
   `User-Agent` (no personal details), ~0.7 s between requests, honours
   `Retry-After`; server errors are retried with backoff.
2. **Outage**: when LRCLIB's API is down (it answered 502 to everything
   from about 01:20 on 1 Nov while its website was up), five failing songs
   in a row stop the run instead of crawling through 5,000 with retries;
   `fetch-until-done.sh` tries again every 10 minutes for up to 10 hours,
   then builds the database.
3. **Build** (`lyrics_build.py`): `lyrics.db`, schema 1:
   `lyrics(path, synced, plain, instrumental, lrclib_id)`, keyed by the
   path inside the Music folder, as the liner notes are.
4. **Sync** (`tools/sync/sync.py`): copied to the app's own folder on each
   phone when it changed, with the music and the notes.
5. **Show** (Mako): a Lyrics button in Now Playing, beside Up next, when
   the song has lyrics. Timed lyrics follow the song: the line being sung
   is white and bold, a third of the way down; tapping a line goes there.
   Words without times scroll as text; instrumental songs say so.

### Keeping in time

- The screen asks the player for the position and sleeps until the next
  line is due (at least once a second, so a seek is followed), instead of
  redrawing every frame: cheap on the Nexus 4.
- On a speaker or the TV, the position is the renderer's, polled (every
  5 s, every second near a song's end) and extrapolated in between
  (`RendererPlayer`), so lyrics follow what the room hears, not the phone.
  Expect lines to land within a few hundred ms; Sonos buffers before
  playing, and if lyrics run early there, a per-renderer offset is the fix.
- **(review)** LRC files are sometimes off for a particular release
  (another master, a longer intro). The ±2 s duration rule avoids most;
  a "lyrics are early/late" nudge per song would fix the rest.

## Chromecast

Mako doesn't cast to Chromecast today (its renderers are UPnP: Sonos and
the Samsung TV). If it did:

- **Lyrics on the phone, in time with a Chromecast: yes.** A Chromecast
  reports the playing position in its media status, as Sonos does; Mako
  would poll and extrapolate it exactly as `RendererPlayer` does.
- **Talking to a Chromecast without Google Play Services is possible but
  real work.** Google's Cast SDK for Android needs Play Services, which
  Mako avoids. The protocol itself (CASTV2: protobuf messages over TLS on
  port 8009, found by mDNS `_googlecast._tcp`) is implemented
  independently by others (pychromecast, VLC). The Default Media Receiver
  would play a URL from Mako's own server, as Sonos does.
- **Lyrics on the TV screen: hard.** The Default Media Receiver shows
  title, artist and artwork, and its look can't be changed. Showing lyrics
  needs a Custom Web Receiver: an HTML app registered in Google's Cast
  developer console and hosted on an HTTPS server the Chromecast loads it
  from, which conflicts with Mako being offline. Text tracks (WebVTT) are a
  feature for video captions; whether the default receiver would show
  them over an audio stream is unverified.
- The Samsung TV here is DLNA, not Cast; DLNA has no standard way to show
  lyrics either.

## Results

The overnight run (1 Nov) mostly met an outage. LRCLIB's API answered 502
to everything from 01:20 to about 03:20, then failed intermittently. Try 11
began at 06:24 and had covered only 200 songs by 08:55, slowed by retries,
when it was stopped.

- Covered: 235 of 5,248 songs (alphabetically, artists A to Am).
- Found: 139 (59%). Of those, 112 have timed lyrics, 9 plain lyrics only,
  and 18 are marked instrumental. Not found: 96, mostly folk and
  instrumental records.
- Built `lyrics.db` from those and copied only that file to the Nexus 4,
  Pixel 5 and tablet. Good albums to try: ABBA (33 timed), Amy
  Winehouse, Arctic Monkeys, AC/DC, Amaral.
- Not yet checked on a real song: tapping a line to seek, and whether
  lines land on time over Sonos.

To fetch the rest, run `fetch-until-done.sh` again when the API is
healthy. The cache keeps what was already answered.

## Next

- Word-by-word highlighting where LRCLIB has word timings.
- A per-song "lyrics early/late" nudge, remembered.
- Search lyrics ("the song that says…") from the database, offline.
- Lyrics from the song sheet for any song, not only the one playing.
- The LRCLIB dump, if outages or misses make it worth 49 GB. **(review)**

Sources: [LRCLIB API docs](https://lrclib.net/docs.md),
[LRCLIB llms.txt](https://lrclib.net/llms.txt) (dumps), lyriq 1.6.0
source (dump index URL); Musixmatch plans as reported by
[third parties](https://freeapihub.com/apis/musixmatch);
Genius: [LyricsGenius "How it works"](https://lyricsgenius.readthedocs.io/en/latest/how_it_works.html),
[bigishdata (2016)](https://bigishdata.com/2016/09/27/getting-song-lyrics-from-geniuss-api-scraping/);
Cast: [Web Receiver overview](https://developers.google.com/cast/docs/web_receiver).
