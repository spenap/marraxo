# Radios and smart playlists: research

## Station simulator

    android/scripts/station.sh --artist "The Strokes" --title "You Only Live Once" \
        [--weights Radio] [--compare Era] [--picks 20] [--seed 1]

Runs `Radio.kt` on the local collection and prints each pick's score by
reason, then per station: picks linked only by genre and era, albums,
artists, most songs from one album, times widened, and the score against
picks judged by ear in `radio-reference.tsv`. Weights: a preset (`Radio`,
`Similar`, `Era`, `People`, `Anything`) or `genre=…,decade=…,people=…,artist=…,band=…`.

Status: researched and decided, 2026-10-31. Builds on `discovery.md` (Radio) and
`cards.md` (cards are rules). The radio engine and station simulator are
implemented; smart playlists are not.

## What other players do

| Player | Kind | How it works | Lesson for Mako |
|---|---|---|---|
| iTunes / Apple Music, foobar2000, Navidrome (`.nsp` files) | Smart playlist | A rule over tags and play counts, plus a sort and a limit, re-evaluated when opened. Navidrome: JSON with `all`/`any` conditions, `sort` (can be `random`), `limit` or `limitPercent`. | A card is exactly this: filter, order, limit. Store rules as data. |
| Amarok dynamic playlists | Radio | A tree of **biases**: search, tag match, And / Or / If-Else, Partition (proportions), "next track on the same album", Last.fm similar. New tracks are picked **at random among the matches**; keeps 10 upcoming and 5 played, refills as it plays. Conflicting biases can leave it empty. | Biases plus randomness, a small window ahead, and a fallback (If-Else) so a strict rule never stops the music. |
| Jellyfin / Emby Instant Mix | Radio | Reportedly genre only: random tracks from the seed's genres. Users find it weak; plugins exist to improve it. | Genre alone is not enough. |
| Plexamp | Radio | Similarity from audio analysis ("sonic analysis"). Guest DJ modes mix into what's playing: *Stretch* (sonic adventure), *Contempo* (same era), *Groupie* (same artist). A "reach" setting widens the search. Forum reports: needs the whole library analysed; poor under ~10,000 tracks; repeats remixes. | Named modes and one "reach" knob are understandable. Audio similarity alone struggles in small libraries, and Mako's phones hold ~2,000 tracks. |
| MusicBee Auto-DJ | Radio | Random picks from a chosen auto-playlist (rules), plus Last.fm similar artists. | Radio over a smart playlist is a useful combination. Online similarity is out for Mako. |
| Spotify shuffle (2014) | Shuffle | After complaints about the same artist twice in a row, it spreads each artist's tracks across the list (after Martin Fiedler's 2007 "balanced shuffle"), shuffling within each artist. | Random isn't what people mean by shuffle; spread artists. |

## What research says

- **Simple baselines are hard to beat.** Bonnin & Jannach (ISMIR 2013)
  found a baseline from artist information and popularity competitive
  with sophisticated methods. Start simple; measure before adding models.
- **Coherence and diversity pull in opposite directions,** and both
  matter: hand-made playlists usually span several genres, yet transitions
  that jar are noticed (McFee & Lanckriet). Some listeners don't notice
  order at all (Tintarev et al.), so ordering is the last thing to tune.
- **Offline metrics and perception differ** (Kamehkhosh, Jannach &
  Bonnin, UMAP 2017): judge with a listener, not only a number.

## What Mako can score with today

| Signal | State | Note |
|---|---|---|
| Tags: artist, album, year, genre families | Ready | Families (Rock, Pop, Indie, Español…) are better radio units than raw tags. |
| **Liner notes: people, studios, labels** | Ready (`LinerNotes.kt`) | Unusual: "same musician", "same studio", "same producer" are explainable and need no model. Plexamp and Jellyfin have nothing like it. |
| Hidden | Ready | Never played by radio. |
| Play history | Recording, **not usable yet** | 200 plays on the Nexus 4, 169 of them the overnight battery test on Estudio. Needs real listening, and the test plays excluded. |
| Favourites | Not built | Planned in `cards.md`. |
| Audio similarity, mood, tempo | Not built | Discovery D0–D2, desktop models. |

The pools are small: 2,130 tracks on the Nexus 4. Rock is 43 albums; a
strict radio ("Phil Cunningham, 1980s") runs dry within the hour. Every
radio needs to widen itself rather than stop or repeat.

## Proposal

**One engine, two uses.** A *rule* selects and scores tracks. A smart
playlist (a card) applies it once: filter, order, limit. A radio applies
it again at every step, from what's playing.

**Radio v0**
- Seeds: a track ("Radio from this song"), an album, an artist, a genre
  tile (long-press), and from the liner notes a person or a studio
  ("Phil Cunningham radio").
- Score each candidate as a weighted sum of biases: same genre family,
  same decade, shares a person or studio (liner notes), same artist (low),
  not heard recently (once history is real).
- Pick at random weighted by score, not always the top one, so radio
  doesn't settle into a loop.
- Rules: never hidden tracks; no track twice in a session; no artist
  again within 5 tracks, no album within 3. If too few candidates pass,
  relax the weakest bias (Amarok's If-Else, applied automatically).
- Keep 3 tracks queued ahead in the normal queue, so the notification,
  the TV and the speakers just work.
- Every pick carries its reason ("Phil Cunningham plays on both",
  "Celtic, 1980s"), shown in Now Playing.
- One radio, mixing feeds by hidden weights (see Decisions).

**Smart playlists v0 (the card shelves in `cards.md`)**
- Your genres (families), Decades, Players in your collection (liner
  notes, on 3+ albums), Recorded at (studios on 2+ albums), Not played
  here yet.
- "Play" = artist-spread shuffle of the rule's tracks; "Radio" = the rule
  as a radio seed.

**Testing**
- Pure functions with a seeded random: rules, scoring, relaxation,
  spreading. JVM tests with fixture libraries.
- A simulation on the real library (exported from the Nexus 4 and the
  notes): 20 seeds × 100 steps, reporting artist repeats, how often a
  rule had to relax, and how coherent picks stay (share family, decade or
  person). A regression gate like the scroll benchmark.
- Then the beta: a listener's judgement beats the metrics (UMAP 2017).

**History hygiene, before the beta relies on it**
- Exclude automated test plays: the overnight runs on Estudio
  (2026-10-30/31), and mark future test sessions so they're skipped.

## Decisions (2026-10-31)

1. **A radio replaces the queue,** as Spotify's does: the seed plays,
   then picks follow, three ahead.
2. ~~Named modes~~ **One radio** (2026-11-01, after using it: three or
   four radio types in the UI was too many). The former modes are feeds
   (`RadioWeights.Similar`, `Era`, `People`, `Anything`), and the radio
   the app offers, `RadioWeights.Radio`, is their weighted average:
   Similar, Era and People at 1, Anything at 0. The weights aren't shown
   to the user. Nothing is lost: a 1 on one feed and 0 on the rest gives
   back that feed exactly, and the simulation still measures each. Being
   linear, averaging the weights is averaging the scores; only the band
   (how far below the best a pick may be) is averaged as a number. Without
   liner notes on the phone, the people weight simply finds nothing.
3. **Reasons are for development** (2026-10-31, after using it): shown in
   debug builds only, in Now Playing and Up next; the log keeps them.
   Amarok didn't show them either.
4. **Test plays stay in history as they are.** History isn't a bias in
   v0, so they don't matter yet; revisit when "not heard lately" is added.

## Simulation baseline

`RadioSimulationTest` (JVM, skipped without `tools/inventory/out-clean`
and `tools/linernotes/out`): the clean collection, 20 seeds × 100 songs
per mode.

2026-11-01, after 9 albums were added to the clean copy: 397 albums,
5,349 songs, 311 albums with notes. The 20 seeds are drawn from the album
list, so they changed too.

| Mode | Wider | Artist within 5 | Album within 3 | ms/pick (laptop) | Albums per 100 | Shares | Genre | Decade |
|---|---|---|---|---|---|---|---|---|
| Similar | 0% | 0 | 0 | 1.7 | 45 | 7% | 87% | 6% |
| Era | 0% | 0 | 0 | 1.5 | 61 | 0% | 0% | 100% |
| People | 1.2% | 0 | 0 | 1.7 | 38 | 81% | 16% | 1% |
| Anything | 16.4% | 0 | 0 | 1.6 | 90 | 0% | 14% | 70% |
| **Radio** (the mix) | 0% | 0 | 0 | 1.6 | 41 | 33% | 18% | 49% |

The columns on the right count each pick's main reason only: in the mix,
a shared decade is common and usually counts most, so most picks also
share a genre or people without that being their main reason.

Earlier on 1 Nov (388 albums, 5,248 songs, 298 with notes): Similar 82%
genre, People 84% shares, the mix 29% shares and 59% decade.
Earlier the night before (386 albums, 9 Musepack files skipped): People
69% shares, Similar 88% genre.
First run (290 albums, 4,000 songs, before the second import pass):
Similar 41 albums per 100 with 75% genre; People 80% shares.

Gate: no repeats; Similar, Era and the mix widen under 5%; People
explains over 60% of picks by the liner notes, the mix over 20%.

Sources: [Amarok manual: dynamic playlists](https://docs.kde.org/trunk_kf6/en/amarok/amarok/dynamic-playlists.html);
[Navidrome smart playlists](https://navidrome.org/docs/usage/features/smart-playlists/);
[Plex: sonic analysis](https://support.plex.tv/articles/sonic-analysis-music/),
[Guest DJ](https://blog.lon.tv/2023/02/02/new-plexamp-features-guest-dj-and-nfc-tags/),
[small libraries](https://forums.plex.tv/t/sonic-analysis-do-folks-bother-with-it/937638.md);
[Jellyfin BetterMix](https://forum.jellyfin.org/t-bettermix-a-plugin-for-better-instant-mix);
[Spotify, How to shuffle songs (2014)](https://engineering.atspotify.com/2014/02/how-to-shuffle-songs);
[Bonnin & Jannach, ISMIR 2013](https://archives.ismir.net/ismir2013/paper/000114.pdf);
[Kamehkhosh, Jannach & Bonnin, UMAP 2017](https://web-ainf.aau.at/pub/jannach/files/Conference_UMAP_2017.pdf);
[Jannach, Kamehkhosh & Bonnin, UMAP 2016](https://web-ainf.aau.at/pub/jannach/files/Conference_UMAP2016.pdf).
