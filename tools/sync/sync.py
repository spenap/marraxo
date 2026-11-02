#!/usr/bin/env python3
"""Put as much of the cleaned collection on a phone as fits (a first rotation).

Picks albums from beets' library: albums you have played on that phone
(Makofi's play history) first, the rest in a stable random order, until the
phone's free space minus a margin is used. Then copies what is missing and
removes what is no longer picked. An album already there that changed in
the collection since (retagged, renamed, tracks added) is refreshed file
by file, compared by name and size.

Only removes albums an earlier sync copied (listed on the phone in
Music/.mako-sync). Anything else in the Music folder, such as music copied
by hand, stays unless --replace-unmanaged is given; a hand copy identical
to the collection's album is adopted as if sync had copied it.

Also copies the liner notes (tools/linernotes/out/linernotes.db) into
Makofi's own folder, when they changed.

Shows the plan and changes nothing unless --apply is given.

Usage: sync.py SERIAL [--margin-gb 1.5] [--rotate [--seed N]] [--replace-unmanaged] [--apply]
"""
import argparse
import hashlib
import os
import sqlite3
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent
LIBRARY = HERE.parent / "beets" / "out" / "library.db"
COLLECTION = Path("~/Music")
PHONE_MUSIC = "/sdcard/Music"
MANIFEST = f"{PHONE_MUSIC}/.mako-sync"
HISTORY = "/data/data/com.simonpena.makofi/databases/history.db"
NOTES = HERE.parent / "linernotes" / "out" / "linernotes.db"
# Makofi's external files folder: the app reads it without a permission, and
# adb can write it on Android 8 to 17 (checked on the Nexus 4 and the Pixel 5).
PHONE_NOTES = "/sdcard/Android/data/com.simonpena.makofi/files/linernotes.db"
LYRICS = HERE.parent / "lyrics" / "out" / "lyrics.db"
PHONE_LYRICS = "/sdcard/Android/data/com.simonpena.makofi/files/lyrics.db"
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


def select(albums, free, margin, already, seed=0, reclaimable=0, rotate=False, include=()):
    """Played albums first (most played first), then those asked for by
    [include], then the ones already on the phone, then the rest in a stable
    order, while they fit. [rotate]: forget what's on the phone, so a new
    [seed] brings a different unplayed part.

    Each album's place comes from a hash of its own path, not a shuffle of
    the whole list: adding albums to the collection must not reorder (and so
    swap out) the ones already copied.

    [already]: synced albums on the phone, whose space is reusable;
    [reclaimable]: bytes that removing music copied by hand would free."""
    budget = free - margin + reclaimable + sum(a.bytes for a in albums if a.path in already)
    played = sorted((a for a in albums if a.plays > 0), key=lambda a: -a.plays)
    kept = set(already) if not rotate else set()
    wanted = set(include)
    rest = sorted((a for a in albums if a.plays == 0), key=lambda a: (a.path not in wanted, a.path not in kept, fill_key(a.path, seed)))
    chosen, used, adding = [], 0, False
    for a in played + rest:
        # An album already on the phone may use the margin: removing it just to
        # restore a few MB of headroom would make every sync churn. Not once
        # something new is coming in: then the margin is kept.
        room = budget + (margin if a.path in already and not adding else 0)
        if used + a.bytes <= room:
            chosen.append(a)
            used += a.bytes
            adding = adding or a.path not in already
    return chosen


def fill_key(path, seed):
    """An album's place in the fill order: the same whatever else is in the collection."""
    return hashlib.sha1(f"{seed}:{path}".encode()).hexdigest()


def plan(selected, managed, on_phone, replace_unmanaged=False):
    """What to copy and remove. Music copied by hand is never touched unless
    replace_unmanaged; a hand copy of a picked album is then replaced by
    the cleaned one (removed, then copied)."""
    unmanaged = [p for p in on_phone if p not in managed]
    remove = [p for p in on_phone if (p in managed and p not in selected) or (p in unmanaged and replace_unmanaged)]
    kept = [p for p in selected if p in unmanaged and not replace_unmanaged]
    copy = [p for p in selected if p not in kept and (p not in on_phone or p in remove)]
    return Plan(copy, remove, kept)


SAME_TIME = 2  # seconds; adb push keeps a file's time, some filesystems round it


def refresh(local, phone, times=True):
    """Files to push and to delete so an album on the phone matches the
    collection's, both given as {file name: (size, time)}. A retag often
    keeps the size (the tag has padding), so the time counts too."""
    def same(a, b):
        return b is not None and a[0] == b[0] and (not times or abs(a[1] - b[1]) <= SAME_TIME)
    push = sorted(f for f, st in local.items() if not same(st, phone.get(f)))
    delete = sorted(f for f in phone if f not in local)
    return push, delete


def adoptable(unmanaged, local, phone):
    """Albums copied by hand that are identical to the collection's; by size only, as a hand copy has its own times."""
    return [a for a in unmanaged if a in local and refresh(local[a], phone.get(a, {}), times=False) == ([], [])]


def parse_phone_files(out):
    """{album folder: {file name: (size, time)}} from lines of "SIZE TIME /sdcard/Music/PATH"."""
    files = {}
    for line in out.splitlines():
        size, _, rest = line.partition(" ")
        time, _, path = rest.partition(" ")
        if size.isdigit() and time.isdigit() and path.startswith(PHONE_MUSIC + "/"):
            album, name = os.path.split(path[len(PHONE_MUSIC) + 1:])
            files.setdefault(album, {})[name] = (int(size), int(time))
    return files


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


def phone_files(serial):
    return parse_phone_files(adb(serial, "shell", f"find {PHONE_MUSIC} -type f -exec stat -c '%s %Y %n' {{}} +", check=False))


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


def sync_file(serial, local, on_phone_path):
    """Copies a file built on the laptop (liner notes, lyrics) to the phone unless it already has this version. True if copied."""
    if not Path(local).exists():
        return False
    md5 = hashlib.md5(Path(local).read_bytes()).hexdigest()
    on_phone = adb(serial, "shell", f"md5sum {quote(on_phone_path)} 2>/dev/null", check=False).split()
    if on_phone and on_phone[0] == md5:
        return False
    adb(serial, "shell", f"mkdir -p {quote(os.path.dirname(on_phone_path))}")
    adb(serial, "push", str(local), on_phone_path)
    return True


def sync_notes(serial, local=NOTES):
    return sync_file(serial, local, PHONE_NOTES)


def index_music(serial):
    """Asks Android 10+ to index the Music folder; adb push doesn't, and since
    Android 11 Makofi can't see files that aren't indexed. Older Android is left
    to Makofi's rescan, which finds them there. True if asked."""
    sdk = adb(serial, "shell", "getprop ro.build.version.sdk", check=False).strip()
    if not sdk.isdigit() or int(sdk) < 29:
        return False
    adb(serial, "shell", "content call --uri content://media --method scan_volume --arg external_primary", check=False)
    return True


def plays_by_album(serial):
    """(artist, album) → plays, from Makofi's history on the phone (needs adb root); empty if unavailable."""
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

def local_files(album):
    return {f.name: (st.st_size, int(st.st_mtime)) for f in (COLLECTION / album).iterdir() if f.is_file() for st in [f.stat()]}


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
    p.add_argument("--seed", type=int, default=0, help="with --rotate, which unplayed part comes")
    p.add_argument("--rotate", action="store_true", help="replace unplayed albums on the phone with others")
    p.add_argument("--include", action="append", default=[], metavar="ARTIST/ALBUM",
                   help="put this album on the phone, making room by removing unplayed ones (repeatable)")
    p.add_argument("--replace-unmanaged", action="store_true", help="also remove music not copied by this script")
    p.add_argument("--apply", action="store_true")
    a = p.parse_args()
    unknown = [x for x in a.include if not (COLLECTION / x).is_dir()]
    if unknown:
        sys.exit(f"Not in {COLLECTION}: {', '.join(unknown)}")

    on_phone, managed = phone_albums(a.serial), phone_manifest(a.serial)
    albums = collection_albums(plays_by_album(a.serial))
    files = phone_files(a.serial)
    local = {x.path: local_files(x.path) for x in albums if x.path in on_phone}
    adopted = adoptable([x for x in on_phone if x not in managed], local, files)
    managed = managed + adopted
    unmanaged = [x for x in on_phone if x not in managed]
    reclaimable = phone_bytes(a.serial, unmanaged) if a.replace_unmanaged else 0
    chosen = select(albums, phone_free(a.serial), int(a.margin_gb * GB), [x for x in on_phone if x in managed],
                    a.seed, reclaimable, a.rotate, a.include)
    todo = plan([c.path for c in chosen], managed, on_phone, a.replace_unmanaged)
    size = {x.path: x.bytes for x in albums}
    refreshes = {x: refresh(local[x], files.get(x, {})) for x in sorted({c.path for c in chosen} & set(managed))
                 if x in local and x not in todo.remove}
    refreshes = {x: r for x, r in refreshes.items() if r != ([], [])}
    print(f"{len(chosen)} of {len(albums)} albums fit ({sum(c.bytes for c in chosen) / GB:.1f} GB).")
    print(f"Copy {len(todo.copy)} ({sum(size[x] for x in todo.copy) / GB:.1f} GB), remove {len(todo.remove)}.")
    for x in todo.remove:
        print(f"  remove {x}")
    for x in todo.kept:
        print(f"  keep as copied by hand: {x}")
    if adopted:
        print(f"Adopt {len(adopted)} albums copied by hand, identical to the collection's.")
    for x, (push, delete) in refreshes.items():
        print(f"  refresh {x}: copy {len(push)}, delete {len(delete)}")
    if not a.apply:
        print("Plan only; nothing changed. Add --apply to do it.")
        return
    managed = set(managed)
    if adopted:
        write_manifest(a.serial, managed)
    for x, (push, delete) in refreshes.items():
        for f in delete:
            adb(a.serial, "shell", f"rm {quote(f'{PHONE_MUSIC}/{x}/{f}')}")
        for f in push:
            adb(a.serial, "push", str(COLLECTION / x / f), f"{PHONE_MUSIC}/{x}/{f}")
        print(f"  refreshed {x}", flush=True)
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
    if sync_notes(a.serial):
        print("Liner notes copied.")
    if sync_file(a.serial, LYRICS, PHONE_LYRICS):
        print("Lyrics copied.")
    if (todo.copy or todo.remove or refreshes) and index_music(a.serial):
        print("Indexed by Android.")
    print("Done. In Makofi: ⋮ → Rescan library.")


if __name__ == "__main__":
    sys.exit(main())
