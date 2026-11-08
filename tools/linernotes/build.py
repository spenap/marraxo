#!/usr/bin/env python3
"""Build linernotes.db for the phone from the fetched cache (roadmap C3).

Tables (schema SCHEMA; the app refuses versions it doesn't know):
  meta(key, value)
  album(id, mb_release, artist, title, year, label, catno, country,
        summary, summary_lang, wikipedia_url)
  track(path, album_id, title, mb_recording)
  credit(album_id, path, role, name, detail)
    path NULL: an album credit (producer, studio…); otherwise the track's.

Tracks are keyed by their path inside the Music folder, which is what the
phones see ("Altan/The Blue Idol/01 Daily Growing.mp3").

Usage: build.py [OUT]   (default out/linernotes.db)
"""
import os
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # fetch.py, also under python -I
import fetch  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # settings.py, also under python -I
import settings  # noqa: E402

SCHEMA = 1
HERE = Path(__file__).resolve().parent

TABLES = """
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE album (id INTEGER PRIMARY KEY, mb_release TEXT, artist TEXT, title TEXT, year INTEGER,
                    label TEXT, catno TEXT, country TEXT, summary TEXT, summary_lang TEXT, wikipedia_url TEXT);
CREATE TABLE track (path TEXT PRIMARY KEY, album_id INTEGER, title TEXT, mb_recording TEXT);
CREATE TABLE credit (album_id INTEGER, path TEXT, role TEXT, name TEXT, detail TEXT);
CREATE INDEX credit_path ON credit(path);
CREATE INDEX credit_album ON credit(album_id);
"""


def credits(relations):
    """(role, name, detail) for MusicBrainz relations to people, places and works."""
    for rel in relations or []:
        who = rel.get("artist") or rel.get("place") or rel.get("label")
        if who:
            yield rel["type"], who.get("name", ""), ", ".join(rel.get("attributes") or [])
        if rel.get("type") == "performance" and rel.get("work"):
            yield from credits(rel["work"].get("relations"))  # composer, lyricist…


def build(out, albums, items, cache):
    out = Path(out)
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript(TABLES)
    db.execute("INSERT INTO meta VALUES ('schema', ?)", (str(SCHEMA),))
    tracks_of = {}
    for it in items:
        tracks_of.setdefault(it["mb_albumid"], []).append(it)
    for a in albums:
        release = cache.get("release", a["mb_albumid"]) if a["mb_albumid"] else None
        release = None if release == fetch.Cache.MISSING else release
        label = catno = country = summary = lang = url = None
        if release:
            info = (release.get("label-info") or [{}])[0]
            label = (info.get("label") or {}).get("name")
            catno, country = info.get("catalog-number"), release.get("country")
            wiki = cache.get("wikipedia", (release.get("release-group") or {}).get("id", "")) or {}
            if wiki != fetch.Cache.MISSING:
                summary, lang = wiki.get("extract"), wiki.get("lang")
                url = ((wiki.get("content_urls") or {}).get("desktop") or {}).get("page")
        cur = db.execute(
            "INSERT INTO album (mb_release, artist, title, year, label, catno, country, summary, summary_lang, wikipedia_url)"
            " VALUES (?,?,?,?,?,?,?,?,?,?)",
            (a["mb_albumid"] or None, a["albumartist"], a["album"], a["year"], label, catno, country, summary, lang, url))
        album_id = cur.lastrowid
        if not release:
            continue
        for role, name, detail in credits(release.get("relations")):
            db.execute("INSERT INTO credit VALUES (?, NULL, ?, ?, ?)", (album_id, role, name, detail))
        recordings = {t["recording"]["id"]: t["recording"] for m in release.get("media", []) for t in m.get("tracks", [])}
        for it in tracks_of.get(a["mb_albumid"], []):
            db.execute("INSERT OR REPLACE INTO track VALUES (?,?,?,?)", (it["path"], album_id, it["title"], it["mb_trackid"]))
            for role, name, detail in credits((recordings.get(it["mb_trackid"]) or {}).get("relations")):
                db.execute("INSERT INTO credit VALUES (?,?,?,?,?)", (album_id, it["path"], role, name, detail))
    db.commit()
    return db


def library():
    db = sqlite3.connect(f"file:{fetch.LIBRARY}?mode=ro", uri=True)
    albums = [dict(zip(("mb_albumid", "albumartist", "album", "year"), r))
              for r in db.execute("SELECT mb_albumid, albumartist, album, year FROM albums ORDER BY albumartist, album")]
    items = [{"mb_albumid": a, "mb_trackid": t, "title": ti, "path": os.fsdecode(p).removeprefix(f"{settings.music()}/")}
             for a, t, ti, p in db.execute("SELECT albums.mb_albumid, items.mb_trackid, items.title, items.path "
                                           "FROM items JOIN albums ON items.album_id = albums.id")]
    db.close()
    return albums, items


def main(out=HERE / "out" / "linernotes.db"):
    albums, items = library()
    db = build(out, albums, items, fetch.Cache(fetch.CACHE))
    n = lambda sql: db.execute(sql).fetchone()[0]
    print(f"{out}: {n('SELECT COUNT(*) FROM album')} albums, {n('SELECT COUNT(*) FROM album WHERE summary IS NOT NULL')}"
          f" with a story, {n('SELECT COUNT(*) FROM track')} tracks, {n('SELECT COUNT(*) FROM credit')} credits,"
          f" {os.path.getsize(out) // 1024} KB")


if __name__ == "__main__":
    main(*sys.argv[1:])
