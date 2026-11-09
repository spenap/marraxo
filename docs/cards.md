# Favourites and cards

Status: design, not started. Mockup: `docs/mockups/cards.html`.

A Home screen of cards in shelves, as Spotify does, where every card is a
**smart playlist**: a rule over the library, play history and liner notes
that yields tracks, with a sentence saying why. This is the Amarok-style
"biases" idea from `discovery.md`, given a face. Everything runs on the
phone, offline, from data it already has.

## Favourites

- A heart on Now Playing, on album screens, and in the long-press menu.
  Albums and songs can both be favourites.
- Stored like hidden items: keyed by artist, album and title, so they
  survive rescans and rotation.
- Used everywhere else: a Favourites card, a bias for radio, and first
  place when the sync picks what fits on a phone.
- Play history also suggests favourites without a heart ("Heavy
  rotation": played through often, rarely skipped). The heart is the
  explicit signal; history is the implicit one. Both are shown, never mixed.
- Later: merge hearts from both phones on the laptop during a sync.

## A card is a rule

```
Card(
  title     "Phil Cunningham",
  why       "Plays on 8 of your albums: accordion, bodhrán, whistles",
  select    credit.name = "Phil Cunningham" and role in (instrument, vocal),
  order     shuffle | by album | most played | least recently played,
  limit     100 tracks,
  mode      playlist | radio (endless, with biases, as in discovery.md),
)
```

- **Select** filters on what the phone knows: tags (genre, year, artist),
  play history (plays, skips, last played, added), favourites, hidden,
  and liner notes (musicians, producers, studios, labels).
- **Why** is required. A card that can't explain itself isn't shown.
- **Tracks** are computed when the card is opened, so cards follow the
  library as it changes.
- Cards with fewer than ~8 tracks are not shown.

## Shelves

Rotated daily (seeded by the date) so Home changes without being random
on every visit.

| Shelf | Source | Available |
|---|---|---|
| Jump back in | Albums played recently | Now (play history) |
| Favourites, Heavy rotation | Hearts; plays without skips | With favourites |
| Your genres | Album genres (84 Celtic, 66 Pop, 40 Rock…) | Now |
| Decades | Original year (138 from the 90s, 170 from the 2000s) | Now |
| Players in your collection | Musicians on 3+ albums (Phil Cunningham on 8, Nathan East on 6) | Now (liner notes) |
| Recorded at | Studios on 2+ albums (AIR Studios 9, Windmill Lane 5) | Now (liner notes) |
| Moods | Calm, energetic, melancholic… | With discovery (D2) |
| Sounds like… | Neighbours of a recent album | With discovery (D2) |

Decided for Home (2026-11-09, `mockups/home.html`): Home is the first
tab; "Jump back in" is Continue, mixing albums, artists, radios and
genres, led by a card that resumes the last album at its track; genre and
decade shelves come first, then people, studios and Rediscover in
rotation. "Not played here yet" is dropped: much of the collection is
unplayed by choice.

The players and studios shelves are something streaming apps can't do:
they come from the credits fetched for this collection.

## Building it

1. Favourites (store, heart, long-press), test first.
2. The card model: rules as data, evaluated against the library, history
   and `linernotes.db`. Pure functions, unit-tested with fixtures.
3. Home screen with shelves (Glow), prototyped in `mockups/` first.
4. Playing a card: playlist now; radio once Radio v0 exists (A3).
5. Desktop-defined cards later: `discovery.db` label sets (moods, styles)
   become shelves without an app change, as `discovery.md` describes.
