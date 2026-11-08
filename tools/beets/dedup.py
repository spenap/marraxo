#!/usr/bin/env python3
"""One file per song, for album folders that hold several copies of an album.

Old rips often left two sets of files in one folder ("01 - Song.mp3" and
"01 Song.mp3"). Keeping one file per song covers every case at once:
exact doubles lose one copy, a smaller set inside a larger one gives the
larger set, and two partial sets combine into a fuller album. When two
files of a song differ in audio (two rips), the higher bitrate wins; then
the one with cover art.

For each folder with doubles, links the chosen files into
out/dedup/<n>/<album> for beets to import, and lists them in out/dedup.tsv.
The original folders are not touched.

Usage: dedup.py FOLDER...   or   dedup.py --from-triage
"""
import hashlib
import os
import re
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # settings.py, also under python -I
import settings  # noqa: E402

AUDIO = (".mp3", ".m4a", ".ogg", ".flac", ".wav", ".opus")


@dataclass
class Song:
    name: str   # file name
    key: str    # the song, whatever the numbering and spelling
    audio: str  # hash of the audio without tags
    kbps: int
    cover: bool
    seconds: float = 0
    path: str = ""


def song_key(name):
    stem = re.sub(r"\.[A-Za-z0-9]+$", "", name)
    stem = re.sub(r"^\s*(\d+[-.])?\d+\s*[-._]*\s*", "", stem)  # 01 - , 01. , 1-02
    stem = unicodedata.normalize("NFKD", stem)
    stem = "".join(c for c in stem if not unicodedata.combining(c)).lower()
    return re.sub(r"[^a-z0-9]+", "", stem)


SAME_LENGTH = 2  # seconds: rips of one song differ by a second or two at most


def pick(songs):
    """One file per song, in the order songs first appear. Two files are the
    same song when the titles match and the lengths are within SAME_LENGTH:
    classical movements share titles ("Ouverture" six times), and an album
    can have two different tracks with one name."""
    groups = []
    for s in songs:
        for g in groups:
            if g[0].key == s.key and abs(g[0].seconds - s.seconds) <= SAME_LENGTH:
                g.append(s)
                break
        else:
            groups.append([s])
    return [max(g, key=lambda s: (s.kbps, s.cover)) if len(g) > 1 else g[0] for g in groups]


# ------------------------------------------------------------------ reading files

def describe(path):
    import mutagen
    from mutagen.id3 import ID3, ID3NoHeaderError
    data = Path(path).read_bytes()
    start, cover = 0, False
    if path.lower().endswith(".mp3"):
        try:
            tags = ID3(path)
            start, cover = tags.size, any(k.startswith("APIC") for k in tags.keys())
        except ID3NoHeaderError:
            pass
    end = len(data) - 128 if data[-128:-125] == b"TAG" else len(data)
    info = mutagen.File(path)
    kbps = int(getattr(getattr(info, "info", None), "bitrate", 0) or 0) // 1000
    if not cover and info is not None and getattr(info, "pictures", None):
        cover = True
    name = os.path.basename(path)
    seconds = getattr(getattr(info, "info", None), "length", 0) or 0
    return Song(name, song_key(name), hashlib.md5(data[start:end]).hexdigest(), kbps, cover, seconds, path)


def main(args):
    if args == ["--from-triage"]:
        rows = [l for l in open("out/triage.md") if l.startswith("| · |")]
        args = [str(settings.music() / l.split("|")[2].strip()) for l in rows]
    out = Path("out/dedup")
    lines = []
    for n, folder in enumerate(args, 1):
        files = sorted(f for f in os.listdir(folder) if f.lower().endswith(AUDIO))
        songs = [describe(os.path.join(folder, f)) for f in files]
        kept = pick(songs)
        if len(kept) == len(songs):
            continue
        dest = out / f"{n:03d}" / os.path.basename(folder.rstrip("/"))
        dest.mkdir(parents=True, exist_ok=True)
        for s in kept:
            link = dest / s.name
            if not link.exists():
                link.symlink_to(s.path)
        lines.append(f"{folder}\t{dest.resolve()}\t{len(songs)}\t{len(kept)}")
        print(f"{len(songs):3} files → {len(kept):3} songs  {folder}")
    Path("out/dedup.tsv").write_text("".join(l + "\n" for l in lines))


if __name__ == "__main__":
    main(sys.argv[1:])
