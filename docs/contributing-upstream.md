# Contributing back to MusicBrainz, AcoustID and beets

Status: ideas, not started. Everything goes out under Simon's own
accounts; nothing is submitted automatically.

Some contributions need the source discs and aren't possible here; the
most useful ones don't need them.

## AcoustID fingerprints (best value, low effort)

Each confidently matched track gives us an audio fingerprint plus its
MusicBrainz recording ID. Submitting that pair helps other people's files
match automatically.

- Command: `tools/beets/beet submit <query>` (chroma plugin). Needs a free
  AcoustID user API key in the beets config (`acoustid.apikey`).
- Submit only the first-pass matches (~92%+, the 290 albums imported
  before 2026-10-30 08:16). Leave the second-pass matches (~88%) until
  they are spot-checked: a fingerprint linked to the wrong recording does
  harm.

## MusicBrainz

- **Adding missing releases:** allowed only with a source to cite
  (Discogs, the label's site, Bandcamp, booklet photos), never from file
  tags alone. Candidates: the albums still unmatched after both beets
  passes (`tools/beets/out/import-pass2.log`, minus those resolved in the
  interactive review). Many Celtic and Spanish releases are likely on
  Discogs. New editors' edits wait for votes.
- **Corrections:** wrong titles or years noticed during review, with an
  edit note.
- **Not possible without the source discs:** disc IDs (need the disc's
  table of contents) and Cover Art Archive scans (must be your own scans).

## beets

Possible bug reports (search the tracker first, they may be known):

- beets 2.5.1 applies the "data source" distance penalty to MusicBrainz
  candidates too, lowering good matches. Our workaround:
  `match.distance_weights.data_source: 0.0`.
- The chroma plugin fails on Python 3.13+ ("audio could not be decoded")
  because `audioread` needs `audioop`, which Python removed. Workaround:
  uninstall `audioread` so pyacoustid falls back to `fpcalc`.

Code contributions: check beets' contributing guide for a policy on
AI-written code first.

## Possible helpers

- A script that prepares the `beet submit` run for the first-pass albums.
- A list of unmatched albums that have a Discogs page.
