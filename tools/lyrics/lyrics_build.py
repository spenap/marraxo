#!/usr/bin/env python3
"""Build lyrics.db for the phone from the fetched cache (docs/lyrics.md).

Tables (schema SCHEMA; the app refuses versions it doesn't know):
  meta(key, value)
  lyrics(path, synced, plain, instrumental, lrclib_id)
    synced: LRC text with line timestamps, or NULL; plain: the words, or NULL.

Songs are keyed by their path inside the Music folder, as the phones see
it ("Phil Collins/Face Value/01 In the Air Tonight.mp3"). Only songs with
lyrics (or known to be instrumental) are stored.

Usage: lyrics_build.py [OUT]   (default out/lyrics.db)
"""
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # lyrics_fetch.py, also under python -I
import lyrics_fetch  # noqa: E402

SCHEMA = 1
HERE = Path(__file__).resolve().parent


def build(out, cache):
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript("""
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
        CREATE TABLE lyrics (path TEXT PRIMARY KEY, synced TEXT, plain TEXT, instrumental INTEGER, lrclib_id INTEGER);
    """)
    db.execute("INSERT INTO meta VALUES ('schema', ?)", (str(SCHEMA),))
    n = 0
    for a in cache.all():
        if a.get("status") != "found":
            continue
        synced, plain = a.get("syncedLyrics") or None, a.get("plainLyrics") or None
        if not (synced or plain or a.get("instrumental")):
            continue
        db.execute("INSERT INTO lyrics VALUES (?, ?, ?, ?, ?)", (a["path"], synced, plain, int(bool(a.get("instrumental"))), a.get("id")))
        n += 1
    db.commit()
    db.close()
    return n


def main(out=HERE / "out" / "lyrics.db"):
    n = build(out, lyrics_fetch.Cache(lyrics_fetch.CACHE))
    print(f"{n} songs with lyrics → {out}")


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
