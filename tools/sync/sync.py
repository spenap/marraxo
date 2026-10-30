#!/usr/bin/env python3
"""Put as much of the cleaned collection on a phone as fits (a first rotation).

Picks albums from beets' library: albums you have played on that phone
(Mako's play history) first, the rest in a stable random order, until the
phone's free space minus a margin is used. Then copies what is missing and
removes what is no longer picked.

Only removes albums an earlier sync copied (listed on the phone in
Music/.mako-sync). Anything else in the Music folder, such as music copied
by hand, stays unless --replace-unmanaged is given.

Shows the plan and changes nothing unless --apply is given.

Usage: sync.py SERIAL [--margin-gb 1.5] [--seed N] [--replace-unmanaged] [--apply]
"""
import argparse
import os
import random
import sqlite3
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent
LIBRARY = HERE.parent / "beets" / "out" / "library.db"
COLLECTION = Path("~/Music-clean")
PHONE_MUSIC = "/sdcard/Music"
MANIFEST = f"{PHONE_MUSIC}/.mako-sync"
HISTORY = "/data/data/com.simonpena.mako/databases/history.db"
AUDIO = (".mp3", ".m4a", ".ogg", ".opus", ".flac", ".wav", ".mpc")
GB = 1024 ** 3


@dataclass
class Album:
    path: str  # relative to the collection, e.g. "Altan/The Blue Idol"
    bytes: int
    plays: int = 0


@dataclass
class Plan:
    copy: list
    remove: list
    kept: list  # picked, but on the phone as copied by hand: left alone


def select(albums, free, margin, already, seed=0, reclaimable=0):
    """Played albums first (most played first), then a stable shuffle, while they fit.

    [already]: synced albums on the phone, whose space is reusable;
    [reclaimable]: bytes that removing music copied by hand would free."""
    budget = free - margin + reclaimable + sum(a.bytes for a in albums if a.path in already)
    played = sorted((a for a in albums if a.plays > 0), key=lambda a: -a.plays)
    rest = [a for a in albums if a.plays == 0]
    random.Random(seed).shuffle(rest)
    chosen, used = [], 0
    for a in played + rest:
        if used + a.bytes <= budget:
            chosen.append(a)
            used += a.bytes
    return chosen


def plan(selected, managed, on_phone, replace_unmanaged=False):
    """What to copy and remove. Music copied by hand is never touched unless
    replace_unmanaged; a hand copy of a picked album is then replaced by
    the cleaned one (removed, then copied)."""
    unmanaged = [p for p in on_phone if p not in managed]
    remove = [p for p in on_phone if (p in managed and p not in selected) or (p in unmanaged and replace_unmanaged)]
    kept = [p for p in selected if p in unmanaged and not replace_unmanaged]
    copy = [p for p in selected if p not in kept and (p not in on_phone or p in remove)]
    return Plan(copy, remove, kept)


# ------------------------------------------------------------------ the phone

def adb(serial, *args, check=True):
    r = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, stdin=subprocess.DEVNULL)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.strip()}")
    return r.stdout


def quote(path):
    return "'" + path.replace("'", "'\\''") + "'"


def phone_free(serial):
    line = adb(serial, "shell", "df", "-k", PHONE_MUSIC).strip().splitlines()[-1].split()
    return int(line[3]) * 1024


def phone_albums(serial):
    """Album folders (relative to Music) that hold audio files."""
    out = adb(serial, "shell", f"find {PHONE_MUSIC} -type f", check=False)
    dirs = {os.path.dirname(f[len(PHONE_MUSIC) + 1:]) for f in out.splitlines() if f.lower().endswith(AUDIO)}
    return sorted(d for d in dirs if d)


def phone_bytes(serial, folders):
    """Size of these Music folders on the phone."""
    total = 0
    for f in folders:
        out = adb(serial, "shell", f"du -sk {quote(f'{PHONE_MUSIC}/{f}')}", check=False).split()
        total += int(out[0]) * 1024 if out and out[0].isdigit() else 0
    return total


def phone_manifest(serial):
    return [l for l in adb(serial, "shell", f"cat {MANIFEST} 2>/dev/null", check=False).splitlines() if l.strip()]


def write_manifest(serial, paths):
    with tempfile.NamedTemporaryFile("w", delete=False) as f:
        f.write("\n".join(sorted(paths)) + "\n")
    adb(serial, "push", f.name, MANIFEST)
    os.unlink(f.name)


def plays_by_album(serial):
    """(artist, album) → plays, from Mako's history on the phone (needs adb root); empty if unavailable."""
    adb(serial, "root", check=False)
    with tempfile.TemporaryDirectory() as d:
        local = os.path.join(d, "history.db")
        if subprocess.run(["adb", "-s", serial, "pull", HISTORY, local], capture_output=True).returncode != 0:
            return {}
        db = sqlite3.connect(local)
        rows = db.execute("SELECT artist, album, COUNT(*) FROM plays WHERE listened_ms >= 30000 GROUP BY artist, album").fetchall()
        db.close()
    return {(a or "", b or ""): n for a, b, n in rows}


# ------------------------------------------------------------------ the collection

def collection_albums(plays):
    db = sqlite3.connect(f"file:{LIBRARY}?mode=ro", uri=True)
    rows = db.execute("SELECT albums.id, albums.albumartist, albums.album, MIN(items.path) FROM albums "
                      "JOIN items ON items.album_id = albums.id GROUP BY albums.id").fetchall()
    db.close()
    albums = []
    for _, artist, title, path in rows:
        folder = Path(os.fsdecode(path)).parent
        if not folder.is_dir():
            continue
        size = sum(f.stat().st_size for f in folder.iterdir() if f.is_file())
        albums.append(Album(str(folder.relative_to(COLLECTION)), size, plays.get((artist, title), 0)))
    return albums


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("serial")
    p.add_argument("--margin-gb", type=float, default=1.5)
    p.add_argument("--seed", type=int, default=0, help="change it to rotate the unplayed part")
    p.add_argument("--replace-unmanaged", action="store_true", help="also remove music not copied by this script")
    p.add_argument("--apply", action="store_true")
    a = p.parse_args()

    on_phone, managed = phone_albums(a.serial), phone_manifest(a.serial)
    albums = collection_albums(plays_by_album(a.serial))
    unmanaged = [x for x in on_phone if x not in managed]
    reclaimable = phone_bytes(a.serial, unmanaged) if a.replace_unmanaged else 0
    chosen = select(albums, phone_free(a.serial), int(a.margin_gb * GB), [x for x in on_phone if x in managed],
                    a.seed, reclaimable)
    todo = plan([c.path for c in chosen], managed, on_phone, a.replace_unmanaged)
    size = {x.path: x.bytes for x in albums}
    print(f"{len(chosen)} of {len(albums)} albums fit ({sum(c.bytes for c in chosen) / GB:.1f} GB).")
    print(f"Copy {len(todo.copy)} ({sum(size[x] for x in todo.copy) / GB:.1f} GB), remove {len(todo.remove)}.")
    for x in todo.remove:
        print(f"  remove {x}")
    for x in todo.kept:
        print(f"  keep as copied by hand: {x}")
    if not a.apply:
        print("Plan only; nothing changed. Add --apply to do it.")
        return
    managed = set(managed)
    for x in todo.remove:
        adb(a.serial, "shell", f"rm -r {quote(f'{PHONE_MUSIC}/{x}')}")
        managed.discard(x)
        write_manifest(a.serial, managed)
    for n, x in enumerate(todo.copy, 1):
        parent = os.path.dirname(f"{PHONE_MUSIC}/{x}")
        adb(a.serial, "shell", f"mkdir -p {quote(parent)}")
        adb(a.serial, "push", str(COLLECTION / x), parent + "/")
        managed.add(x)
        write_manifest(a.serial, managed)  # after each album, so an interrupted sync resumes
        print(f"  {n}/{len(todo.copy)} {x}", flush=True)
    print("Done. In Mako: ⋮ → Rescan library.")


if __name__ == "__main__":
    sys.exit(main())
