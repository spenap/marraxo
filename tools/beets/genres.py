#!/usr/bin/env python3
"""Approved genre changes for the clean copy (roadmap C1).

genres.tsv lists, per album, the genre it had when the change was approved
and the genre it gets. An album is changed only while it still has that
genre, so running again changes nothing, and an album retagged since is
reported instead of overwritten.

Without --apply, only shows what would change. With it, first writes every
song's path, old and new genre to genre-changes-<time>.tsv beside the
library (out/), then sets
the genre in beets and writes the tags to the files in Music-clean.

Usage: tools/.venv/bin/python tools/beets/genres.py [--apply]
"""
import os
import sys
import time
import unicodedata
from dataclasses import dataclass

HERE = os.path.dirname(os.path.abspath(__file__))
RULES = os.path.join(HERE, "genres.tsv")


@dataclass(frozen=True)
class Rule:
    albumartist: str
    album: str
    old: str
    new: str


@dataclass(frozen=True)
class Album:
    id: int
    albumartist: str
    album: str
    genre: str


@dataclass(frozen=True)
class Change:
    album: Album
    new: str


def read_rules(text):
    rows = [line.split("\t") for line in text.splitlines() if line.strip() and not line.startswith("#")]
    return [Rule(*row[:4]) for row in rows[1:]]  # rows[0] is the header


def _key(artist, album):
    return unicodedata.normalize("NFC", artist), unicodedata.normalize("NFC", album)


def plan(albums, rules):
    """The changes to make, and what keeps a rule from applying."""
    by_name = {}
    for a in albums:
        by_name.setdefault(_key(a.albumartist, a.album), []).append(a)
    changes, problems = [], []
    for r in rules:
        found = by_name.get(_key(r.albumartist, r.album), [])
        if not found:
            problems.append(f"not in the library: {r.albumartist} - {r.album}")
        for a in found:
            if a.genre == r.old:
                changes.append(Change(a, r.new))
            elif a.genre != r.new:
                problems.append(f"{a.albumartist} - {a.album} is now {a.genre!r}, approved from {r.old!r}: left alone")
    return changes, problems


def main(argv):
    apply = "--apply" in argv
    from beets import config
    from beets.library import Library

    # config.yaml names the library by absolute path, so this works from a worktree too.
    config.set_file(os.path.join(HERE, "config.yaml"))
    path = config["library"].as_filename()
    if not os.path.exists(path):
        sys.exit(f"no library at {path}")
    lib = Library(path, config["directory"].as_filename())
    albums = [Album(a.id, a.albumartist, a.album, a.genre) for a in lib.albums()]
    with open(RULES, encoding="utf-8") as f:
        changes, problems = plan(albums, read_rules(f.read()))
    for p in problems:
        print("!", p)
    for c in changes:
        print(f"{c.album.albumartist} - {c.album.album}: {c.album.genre} → {c.new}")
    if not changes or not apply:
        print(f"{len(changes)} albums to change" + ("" if apply else " (dry run; --apply to write)"))
        return 1 if problems else 0

    manifest = os.path.join(os.path.dirname(path), time.strftime("genre-changes-%Y%m%d-%H%M%S.tsv"))
    with open(manifest, "w", encoding="utf-8") as m:
        m.write("path\told\tnew\n")
        for c in changes:
            for item in lib.get_album(c.album.id).items():
                m.write(f"{item.filepath}\t{item.genre}\t{c.new}\n")
    for c in changes:
        album = lib.get_album(c.album.id)
        album.genre = c.new
        album.store(inherit=True)  # the album's songs get the genre too
        for item in album.items():
            item.try_sync(write=True, move=False)
    print(f"{len(changes)} albums changed; manifest: {manifest}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
