#!/usr/bin/env python3
"""Fetch liner notes for the cleaned collection (roadmap C3).

For every album beets matched to MusicBrainz, fetches once and caches as
JSON under out/cache/:
  - the release, with tracks, performers and instruments, songwriters,
    producers, engineers, studios and labels (one request);
  - its release group and artists, for their Wikidata links;
  - a Wikipedia summary for the album, in Spanish or else English.

Runs for hours: MusicBrainz allows one request per second. It can be
stopped and started again; whatever is cached is never fetched twice,
and pages that don't exist are remembered as missing.

Usage: fetch.py [--limit N]
"""
import argparse
import json
import sqlite3
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
LIBRARY = HERE.parent / "beets" / "out" / "library.db"
CACHE = HERE / "out" / "cache"

# Identifies the app, as MusicBrainz asks; no personal contact details.
USER_AGENT = "MakoLinerNotes/0.1 (personal offline music player; liner notes for a private collection)"
MB = "https://musicbrainz.org/ws/2"
RELEASE_INC = ("recordings+artist-credits+labels+release-groups+recording-level-rels"
               "+work-rels+work-level-rels+artist-rels+url-rels+place-rels")
LANGUAGES = ("es", "en")


class NotFound(Exception):
    pass


class RateLimiter:
    """At most one request every `interval` seconds."""

    def __init__(self, interval, clock=time.monotonic, sleep=time.sleep):
        self.interval, self.clock, self.sleep = interval, clock, sleep
        self.last = None

    def wait(self):
        if self.last is not None:
            gap = self.interval - (self.clock() - self.last)
            if gap > 0:
                self.sleep(gap)
        self.last = self.clock()


class Cache:
    """One JSON file per thing fetched; a "missing" marker for pages that don't exist."""

    MISSING = {"_missing": True}

    def __init__(self, root):
        self.root = Path(root)

    def path(self, kind, key):
        return self.root / kind / f"{key}.json"

    def get(self, kind, key):
        p = self.path(kind, key)
        return json.loads(p.read_text()) if p.exists() else None

    def put(self, kind, key, value):
        p = self.path(kind, key)
        p.parent.mkdir(parents=True, exist_ok=True)
        tmp = p.with_suffix(".tmp")
        tmp.write_text(json.dumps(value, ensure_ascii=False))
        tmp.replace(p)  # never a half-written file if interrupted


def web_getter(mb_limit, wiki_limit):
    """Real HTTP: JSON in, NotFound for 404s; MusicBrainz and Wikimedia rate-limited separately."""
    def get(url):
        (mb_limit if "musicbrainz.org" in url else wiki_limit).wait()
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(req, timeout=30) as r:
                    return json.load(r)
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    raise NotFound(url)
                if e.code in (429, 503) and attempt < 3:  # throttled: back off
                    time.sleep(5 * (attempt + 1))
                    continue
                raise
    return get


def wikidata_id(relations):
    for rel in relations or []:
        if rel.get("type") == "wikidata":
            return rel["url"]["resource"].rstrip("/").rsplit("/", 1)[-1]
    return None


def wikipedia_title(entity, qid):
    links = entity.get("entities", {}).get(qid, {}).get("sitelinks", {})
    for lang in LANGUAGES:
        if f"{lang}wiki" in links:
            return lang, links[f"{lang}wiki"]["title"]
    return None


def cached(web, cache, kind, key, url):
    """The cached page, fetching it the first time; None if it doesn't exist."""
    value = cache.get(kind, key)
    if value is None:
        try:
            value = web(url)
        except NotFound:
            value = Cache.MISSING
        cache.put(kind, key, value)
    return None if value == Cache.MISSING else value


def album(web, cache, release_id):
    release = cached(web, cache, "release", release_id, f"{MB}/release/{release_id}?inc={RELEASE_INC}&fmt=json")
    if release is None:
        return
    for credit in release.get("artist-credit", []):
        aid = credit["artist"]["id"]
        cached(web, cache, "artist", aid, f"{MB}/artist/{aid}?inc=url-rels+aliases&fmt=json")
    rg_id = release.get("release-group", {}).get("id")
    if not rg_id:
        return
    group = cached(web, cache, "release-group", rg_id, f"{MB}/release-group/{rg_id}?inc=url-rels+tags+genres&fmt=json")
    if cache.get("wikipedia", rg_id) is not None:
        return
    qid = wikidata_id((group or {}).get("relations"))
    entity = qid and cached(web, cache, "wikidata", qid, f"https://www.wikidata.org/wiki/Special:EntityData/{qid}.json")
    found = entity and wikipedia_title(entity, qid)
    if not found:
        cache.put("wikipedia", rg_id, Cache.MISSING)
        return
    lang, title = found
    summary = cached(web, cache, "wikipedia", rg_id,
                     f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{urllib.parse.quote(title)}")
    if summary is not None and "lang" not in summary:
        summary["lang"] = lang
        cache.put("wikipedia", rg_id, summary)


def release_ids():
    """Albums beets matched to MusicBrainz, read-only."""
    db = sqlite3.connect(f"file:{LIBRARY}?mode=ro", uri=True)
    try:
        return [r[0] for r in db.execute("SELECT DISTINCT mb_albumid FROM albums WHERE mb_albumid != '' ORDER BY albumartist, album")]
    finally:
        db.close()


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--limit", type=int, help="stop after this many albums")
    a = p.parse_args()
    web = web_getter(RateLimiter(1.1), RateLimiter(0.2))
    cache = Cache(CACHE)
    ids = release_ids()[: a.limit]
    started = time.monotonic()
    for n, rid in enumerate(ids, 1):
        try:
            album(web, cache, rid)
        except Exception as e:  # keep going; the album is retried on the next run
            print(f"  {rid}: {type(e).__name__}: {e}", file=sys.stderr)
        if n % 10 == 0 or n == len(ids):
            print(f"{n}/{len(ids)} albums, {(time.monotonic() - started) / 60:.0f} min", flush=True)


if __name__ == "__main__":
    main()
