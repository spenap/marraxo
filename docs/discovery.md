# Discovery: exploring the collection by sound, people, era and mood

Status: design, not started. Roadmap: C4 (desktop) and A5 (app).

The collection is ~6,300 tracks in ~465 albums, much of it Celtic, Spanish
pop/rock, soundtracks and pop. Discovery means finding your way around
*this* collection: "what else here sounds like this?", "something calm",
"the 90s Spanish scene", "who connects these two Irish albums?".

Everything heavy runs once on the desktop. The phone gets precomputed
results and never runs a model.

## Principles

1. **Several views, not one score.** "Sounds like", "connected by people",
   "same era and scene" and "same mood" are different questions. Each gets
   its own distance and its own label in the UI; they are never blended
   into one opaque number.
2. **Explain every suggestion.** "Shares Liam O'Flynn", "both slow,
   acoustic, in D minor", "both Spanish pop-rock, 1993". A suggestion
   without a reason is not shown.
3. **Works without MusicBrainz.** Audio features come from the files, so
   albums that beets couldn't match (obscure Spanish and Celtic releases)
   still take part.
4. **Inspect on the desktop first.** Every signal is checked in an HTML
   explorer before anything ships to the phone.

## Signals

| View | Source | Gives |
|---|---|---|
| **Sound** | Audio embeddings computed from the files, averaged per album | "Sounds like", the map, radio |
| **Style and mood** | Classifiers on top of the audio embeddings | Styles (a few hundred Discogs-style labels), moods/themes (calm, energetic, melancholic, epic, festive…), danceability, voice vs instrumental, acoustic vs electronic, tempo, key |
| **People** | MusicBrainz relationships (members, performers, producers, engineers, labels, studios) | "Connected by", the musician graph |
| **Era and scene** | Original release year, country, label, MusicBrainz genres and tags | Timelines, "same scene" |
| **Topic** | Text embeddings of Wikipedia summaries (album and artist) | Themes such as the sea, Ireland, film scores, protest songs |
| **You** (later) | Play history: albums played in the same session | "Goes well with", from your own habits |

### Candidate models (verify versions and licences before use)

- **Essentia (MTG) models:** Discogs-EffNet embeddings with classifier
  heads for Discogs styles, MTG-Jamendo moods/themes, danceability,
  voice/instrumental, plus Essentia's own tempo and key estimators. Very
  close to what we need out of the box. The pretrained models are
  CC BY-NC-SA: fine for personal use, not for publishing the app with them.
- **CLAP** (contrastive audio–text): puts audio and text in the same
  space, so "rainy evening, acoustic guitar" can be matched against audio.
  The text side runs on the desktop only (see "Free-text moods" below).
- **A sentence-embedding model** for the Wikipedia summaries.
- The **people** view needs no model: a weighted graph (shared member >
  shared producer > shared label) is explainable by construction.

Practical risk: some of these packages lag behind new Python versions
(this machine has 3.14). Use a separate environment pinned to an older
Python for the analysis step.

## The app is model-agnostic

The app understands a fixed contract (the `discovery.db` schema), never a
particular model. Models can be swapped on the desktop without an app
update.

| Kind | Example | App behaviour |
|---|---|---|
| **View**: vectors, neighbours, reason template | Sounds like, Similar topics | A row per view, labelled from the database |
| **Label set**: names with scores | Moods, styles | Chips; ranking by score |
| **Scalar**: number with unit | Tempo, energy, danceability | Sorting, filtering, smooth radio steps |
| **Edge with reason** | "Liam O'Flynn plays on both" | "Connected by", musician graph |

Rules:
- Every view records the model and version that produced it. Vectors are
  only compared within one view, never across models.
- Display names for views and labels live in the database, so new moods
  or styles appear without an app change.
- Free-text vocabulary vectors ship inside the same view as the audio
  vectors they are compared with.
- `discovery.db` carries a schema version; the app refuses versions it
  doesn't know instead of guessing.

## Licensing

Neither the app nor the repository ships a model or `discovery.db`. The
desktop tool downloads models at run time, and its output describes the
user's own collection and stays on their devices. So non-commercial model
licences (such as Essentia's CC BY-NC-SA) don't conflict with the app
being open source. Never commit models or generated databases.

## Identity: results must survive renames

beets renames files, and rotation moves them on and off the phone. Key
every result by the **audio content** (the AcoustID fingerprint, or a
hash of the audio frames without tags), not by path. Map to paths only
when packaging for the phone.

## On the phone

Shipped as `discovery.db` (SQLite) next to `linernotes.db`:

- Per album and track: a compact vector (reduced to ~64 dimensions,
  8-bit), mood and style scores, tempo and key.
- Precomputed top neighbours per view, with their reasons.
- 2D map coordinates per album (UMAP or similar).

A brute-force cosine search over 6,300 × 64-dimension vectors is a few
million multiplications, a few milliseconds even on the Nexus 4. No
search index is needed.

### Free-text moods without a model on the phone

The phone can't run a text encoder. Instead, the desktop embeds a fixed
vocabulary of a few hundred mood/theme phrases ("melancholic", "late
night", "sea shanty", "epic orchestral") with CLAP's text side and ships
those vectors. Typing on the phone matches against the vocabulary, then
searches the audio vectors with the matched phrase's vector.

## What the app gets

1. **"Sounds like" and "Connected by"** rows on the album screen, each
   with its reason.
2. **Moods:** chips (calm, energetic, melancholic, festive, epic…) that
   list albums and tracks by score; long-press to start a mood radio.
3. **Map:** the whole collection as a pannable map where nearby albums
   sound alike, coloured by decade or style. Tap to play. 465 points is
   trivial to draw.
4. **Timeline:** styles and scenes by original release year.
5. **Radio:** endless play from a seed track (see "Radio" below).
6. **Musician graph:** from the liner notes, follow a musician to every
   album in the collection they play on.
7. **Year in review:** "your year in moods".

## Radio

In the spirit of Amarok's dynamic playlists: a queue generator driven by
**biases**, each a scoring rule, plus randomness. It needs no models to
start, and each new signal becomes one more bias.

- Seed: long-press a track → Start radio.
- v0 biases (tags and the app database only): same decade, same genre,
  same artist (low weight), favourite (small boost), not played recently.
- Later biases: sounds like (vector distance), people connections, tempo,
  key and energy close to the previous track, mood.
- Pick at random among the best-scoring candidates.
- Rules: no track twice per session, no artist again within 5 tracks.
- Keep 3 tracks queued ahead of the current one.

## Phases

- **D0. Desktop explorer.** Essentia features and the people graph for the
  whole collection; an HTML explorer with the map, neighbour lists and
  mood rankings. Done when the neighbours for 20 albums you know well look
  right to you.
- **D1. Topics and free text.** CLAP and Wikipedia text embeddings;
  clusters named from their top styles and tags (an LLM may suggest
  names, marked as suggestions, never presented as facts).
- **D2. Package and ship.** `discovery.db`; the app's "Sounds like", Moods
  and Map, prototyped in HTML first.
- **D3. Radio and habits.** Sequencing, then play-history signals once
  there is enough history.

## Open questions

- Analyse whole tracks or a window (e.g. 60 s from the middle)? Whole
  tracks are better for long folk sets; windows are faster. Estimate CPU
  time on a sample first.
- How much does the people graph add for albums that MusicBrainz knows
  well vs the long tail?
