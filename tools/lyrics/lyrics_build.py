#!/usr/bin/env python3
"""Build lyrics.db for the phone from the fetched cache (docs/lyrics.md).

Tables (schema SCHEMA; the app refuses versions it doesn't know):
  meta(key, value)
  lyrics(path, synced, plain, instrumental, lrclib_id)
    synced: LRC text with line timestamps, or NULL; plain: the words, or NULL.

Songs are keyed by their path inside the Music folder, as the phones see
it ("Phil Collins/Face Value/01 In the Air Tonight.mp3"). Only songs with
words are stored: an instrumental has nothing to open. Words that aren't
lyrics (lyrics_check.py) and rejected paths are left out.

Usage: lyrics_build.py [OUT]   (default out/lyrics.db)
"""
import argparse
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # lyrics_fetch.py, also under python -I
import lyrics_check  # noqa: E402
import lyrics_fetch  # noqa: E402

SCHEMA = 1
HERE = Path(__file__).resolve().parent


def build(out, cache, rejected=frozenset()):
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
        if not (synced or plain) or a["path"] in rejected or lyrics_check.check(a):
            continue
        db.execute("INSERT INTO lyrics VALUES (?, ?, ?, ?, ?)", (a["path"], synced, plain, int(bool(a.get("instrumental"))), a.get("id")))
        n += 1
    db.commit()
    db.close()
    return n


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("out", nargs="?", type=Path, default=HERE / "out" / "lyrics.db")
    out = p.parse_args().out
    n = build(out, lyrics_fetch.Cache(lyrics_fetch.CACHE), lyrics_check.read_rejects())
    print(f"{n} songs with lyrics → {out}")


if __name__ == "__main__":
    sys.exit(main())
