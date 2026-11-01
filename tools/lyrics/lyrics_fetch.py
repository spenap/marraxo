#!/usr/bin/env python3
"""Fetch lyrics for the cleaned collection from LRCLIB (docs/lyrics.md).

For every song in the clean inventory, asks LRCLIB (https://lrclib.net), a
free, open lyrics database, once: by title, artist, album and duration,
which must match within 2 s; failing that, by search, taking a result
within 2 s, timed lyrics first. Every answer is cached as JSON under
out/cache/, "missing" included, so a run can stop and start again and
nothing is asked twice.

Lyrics are copyrighted: they stay in out/ (git-ignored) and on the
phones, for personal use; nothing is published back.

Polite by LRCLIB's rules: identifies itself, sends one request at a time
~0.7 s apart (~85 a minute, under its ~90 for identified clients), and
waits as long as a 429 or 503 says.

Usage: lyrics_fetch.py [--limit N]
"""
import argparse
import hashlib
import json
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
INVENTORY = HERE.parent / "inventory" / "out-clean" / "inventory.jsonl"
CACHE = HERE / "out" / "cache"
API = "https://lrclib.net/api"
# LRCLIB asks for the app's name and version; no personal contact details are sent.
USER_AGENT = "MakoLyrics/0.1 (personal offline music player; lyrics for a private collection)"
INTERVAL = 0.7
TOLERANCE = 2  # seconds: LRCLIB's own for /api/get


def read_albums(file):
    """Album folders, one per line, relative to the Music folder; a line starting with "#" is a comment ("#" can be in a name)."""
    lines = (line.strip().rstrip("/") for line in Path(file).read_text().splitlines())
    return {line for line in lines if line and not line.startswith("#")}


def songs(inventory, albums=None):
    """The songs the phones can play (tagged audio), as LRCLIB is asked about them; only from [albums] if given."""
    for line in Path(inventory).read_text().splitlines():
        r = json.loads(line)
        tags = r.get("tags")
        if r.get("kind") != "audio" or not tags or not r.get("duration"):
            continue
        if albums is not None and r["path"].rsplit("/", 1)[0] not in albums:
            continue
        yield {"path": r["path"], "title": tags.get("title", ""), "artist": tags.get("artist") or tags.get("albumartist", ""),
               "album": tags.get("album", ""), "duration": round(r["duration"])}


def lookup(web, song):
    """LRCLIB's record for [song] with "status": "found", or {"status": "missing"}."""
    query = {"track_name": song["title"], "artist_name": song["artist"], "album_name": song["album"], "duration": song["duration"]}
    status, _, body = web(f"{API}/get?{urllib.parse.urlencode(query)}")
    if status == 200:
        return {"status": "found", **json.loads(body)}
    if status != 404:
        raise RuntimeError(f"LRCLIB answered {status} for {song['path']}")
    status, _, body = web(f"{API}/search?{urllib.parse.urlencode({'track_name': song['title'], 'artist_name': song['artist']})}")
    if status != 200:
        raise RuntimeError(f"LRCLIB search answered {status} for {song['path']}")
    close = [r for r in json.loads(body) if r.get("duration") and abs(r["duration"] - song["duration"]) <= TOLERANCE]
    if not close:
        return {"status": "missing"}
    best = min(close, key=lambda r: (not r.get("syncedLyrics"), abs(r["duration"] - song["duration"])))
    return {"status": "found", **best}


class Patient:
    """Wraps a web getter: on 429, 503 or a server error, waits (as long as
    Retry-After says, else longer each time, plus jitter) and asks again."""

    def __init__(self, web, sleep=time.sleep, jitter=lambda: random.uniform(0, 0.3), tries=4):
        self.web, self.sleep, self.jitter, self.tries = web, sleep, jitter, tries

    def __call__(self, url):
        for attempt in range(self.tries):
            status, headers, body = self.web(url)
            if status not in RETRY:
                return status, headers, body
            wait = float(headers.get("Retry-After", 5 * 2 ** attempt))
            self.sleep(wait * (1 + self.jitter()))
        return status, headers, body


RETRY = (429, 500, 502, 503, 504)
GIVE_UP_AFTER = 5  # songs failing in a row: the API is down


class Cache:
    """One JSON file per song, named by a hash of its path."""

    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def _file(self, path):
        return self.root / (hashlib.sha1(path.encode()).hexdigest() + ".json")

    def has(self, path):
        return self._file(path).exists()

    def put(self, path, answer):
        self._file(path).write_text(json.dumps({"path": path, **answer}, ensure_ascii=False))

    def all(self):
        for f in sorted(self.root.glob("*.json")):
            yield json.loads(f.read_text())


def fetch_all(web, cache, todo, report=lambda n, total, song, answer: None):
    """Asks about every song not cached yet; returns the paths the server kept failing on (left uncached, for the next run)."""
    todo = [s for s in todo if not cache.has(s["path"])]
    failed, in_a_row = [], 0
    for n, song in enumerate(todo, 1):
        try:
            answer = lookup(web, song)
        except (RuntimeError, OSError, ValueError) as e:
            failed.append(song["path"])
            report(n, len(todo), song, {"status": "failed", "error": str(e)})
            in_a_row += 1
            if in_a_row >= GIVE_UP_AFTER:
                break  # the API is down: stop, and let the next run try
            continue
        in_a_row = 0
        cache.put(song["path"], answer)
        report(n, len(todo), song, answer)
    return failed


def web_getter():
    last = [0.0]

    def get(url):
        gap = INTERVAL - (time.monotonic() - last[0])
        if gap > 0:
            time.sleep(gap)
        last[0] = time.monotonic()
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status, dict(r.headers), r.read().decode()
        except urllib.error.HTTPError as e:
            return e.code, dict(e.headers), e.read().decode(errors="replace")
        except (urllib.error.URLError, TimeoutError) as e:  # the network itself: treat as a server hiccup
            return 503, {}, str(e)
    return Patient(get)


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--limit", type=int)
    p.add_argument("--albums", type=Path, help="only these album folders, one per line (e.g. the ones cleared by the cleanup)")
    a = p.parse_args()
    todo = list(songs(INVENTORY, read_albums(a.albums) if a.albums else None))[: a.limit]
    found = [0]

    def report(n, total, song, answer):
        found[0] += answer["status"] == "found"
        if n % 50 == 0 or n == total:
            print(f"  {n}/{total}, {found[0]} found", flush=True)

    failed = fetch_all(web_getter(), Cache(CACHE), todo, report)
    print(f"Done: {found[0]} found; {len(failed)} left for the next run (server errors).")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
