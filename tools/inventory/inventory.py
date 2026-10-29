#!/usr/bin/env python3
"""Read-only inventory of a music collection (roadmap C0).

Walks the collection, reads tags and audio properties, and writes:
  - inventory.jsonl: one JSON object per file
  - report.html: statistics and representative problems

It never writes to the collection: files are only opened for reading.

Usage: inventory.py COLLECTION_DIR OUT_DIR
"""
import argparse
import hashlib
import html
import json
import os
import re
import struct
import sys
import time
import unicodedata
from collections import Counter, defaultdict

import mutagen
from mutagen.id3 import ID3NoHeaderError
from mutagen.mp3 import BitrateMode

AUDIO = {"mp3", "m4a", "ogg", "flac", "mpc", "wma", "wav", "opus"}
PLAYABLE_ON_ANDROID = {"mp3", "m4a", "ogg", "flac", "wav", "opus"}
IMAGES = {"jpg", "jpeg", "png", "gif", "tif", "tiff", "bmp"}
JUNK = {"ds_store", "ini", "db", "html", "htm", "url", "nfo", "sfv", "m3u", "txt", "log", "cue", "plist"}


# ------------------------------------------------------------------ helpers

def norm(s):
    """Comparison key: no accents, case, punctuation or leading 'the'."""
    if not s:
        return ""
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c)).lower()
    s = re.sub(r"^the\s+", "", s.strip())
    return re.sub(r"[^a-z0-9]+", "", s)


def image_size(data):
    """(width, height) of JPEG/PNG bytes, without decoding the image."""
    if data[:8] == b"\x89PNG\r\n\x1a\n" and len(data) >= 24:
        return struct.unpack(">II", data[16:24])
    if data[:2] == b"\xff\xd8":
        i = 2
        while i + 9 < len(data):
            if data[i] != 0xFF:
                i += 1
                continue
            marker = data[i + 1]
            if marker in (0xC0, 0xC1, 0xC2):
                h, w = struct.unpack(">HH", data[i + 5:i + 9])
                return w, h
            i += 2 + struct.unpack(">H", data[i + 2:i + 4])[0]
    return None


def has_id3v1(path):
    with open(path, "rb") as f:
        f.seek(0, os.SEEK_END)
        if f.tell() < 128:
            return False
        f.seek(-128, os.SEEK_END)
        return f.read(3) == b"TAG"


def first(tags, *keys):
    for k in keys:
        v = tags.get(k)
        if v:
            v = v[0] if isinstance(v, list) else v
            v = str(v).strip()
            if v:
                return v
    return None


# ------------------------------------------------------------------ readers

def read_mp3(path, rec):
    f = mutagen.File(path)
    info = f.info
    rec.update(duration=round(info.length, 2), bitrate=info.bitrate // 1000,
               sample_rate=info.sample_rate, channels=info.channels,
               bitrate_mode={BitrateMode.CBR: "CBR", BitrateMode.VBR: "VBR",
                             BitrateMode.ABR: "ABR"}.get(info.bitrate_mode, "no encoder header (usually CBR)"))
    rec["id3v1"] = has_id3v1(path)
    tags = f.tags
    if tags is None:
        rec["id3v2"] = None
        rec["tags"] = {}
        return
    rec["id3v2"] = "2.%d" % tags.version[1]
    t = lambda fid: str(tags[fid].text[0]).strip() if fid in tags and tags[fid].text else None
    txxx = {fr.desc: str(fr.text[0]) for fr in tags.getall("TXXX") if fr.text}
    rec["tags"] = {k: v for k, v in {
        "title": t("TIT2"), "artist": t("TPE1"), "album": t("TALB"),
        "albumartist": t("TPE2"), "track": t("TRCK"), "disc": t("TPOS"),
        "date": t("TDRC") or t("TYER"), "genre": t("TCON"), "composer": t("TCOM"),
        "mb_albumid": txxx.get("MusicBrainz Album Id"),
        "mb_trackid": next((fr.data.decode(errors="replace") for fr in tags.getall("UFID")
                            if fr.owner == "http://musicbrainz.org"), None),
        "acoustid": txxx.get("Acoustid Id"),
    }.items() if v}
    pics = tags.getall("APIC")
    rec["art"] = [{"type": p.type, "mime": p.mime, "bytes": len(p.data), "size": image_size(p.data)} for p in pics]


def read_generic(path, rec):
    f = mutagen.File(path)
    if f is None:
        rec["error"] = "unrecognised"
        return
    info = f.info
    rec.update(duration=round(getattr(info, "length", 0) or 0, 2),
               bitrate=(getattr(info, "bitrate", 0) or 0) // 1000,
               sample_rate=getattr(info, "sample_rate", None),
               channels=getattr(info, "channels", None))
    tags = f.tags or {}
    rec["tags"] = {k: v for k, v in {
        "title": first(tags, "title", "\xa9nam", "Title"),
        "artist": first(tags, "artist", "\xa9ART", "Artist"),
        "album": first(tags, "album", "\xa9alb", "Album"),
        "albumartist": first(tags, "albumartist", "aART", "Album Artist"),
        "track": first(tags, "tracknumber", "trkn", "Track"),
        "disc": first(tags, "discnumber", "disk"),
        "date": first(tags, "date", "\xa9day", "Year"),
        "genre": first(tags, "genre", "\xa9gen", "Genre"),
        "mb_albumid": first(tags, "musicbrainz_albumid", "----:com.apple.iTunes:MusicBrainz Album Id"),
    }.items() if v}
    covr = tags.get("covr") if hasattr(tags, "get") else None
    pics = list(covr or [])
    rec["art"] = [{"bytes": len(bytes(p)), "size": image_size(bytes(p))} for p in pics]
    if not pics and hasattr(tags, "get") and tags.get("metadata_block_picture"):
        rec["art"] = [{"bytes": None, "size": None}]


# ------------------------------------------------------------------ scan

def scan(root):
    records = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort()
        for name in sorted(filenames):
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, root)
            ext = name.rsplit(".", 1)[-1].lower() if "." in name else ""
            st = os.stat(path)
            parts = rel.split(os.sep)
            rec = {"path": rel, "ext": ext, "bytes": st.st_size,
                   "mtime": time.strftime("%Y-%m-%d", time.localtime(st.st_mtime)),
                   "folder_artist": parts[0] if len(parts) > 2 else None,
                   "folder_album": parts[1] if len(parts) > 2 else None}
            rec["kind"] = "audio" if ext in AUDIO else "image" if ext in IMAGES else \
                "junk" if ext in JUNK or name.startswith("._") else "other"
            if rec["kind"] == "audio" and ext != "mpc":
                try:
                    (read_mp3 if ext == "mp3" else read_generic)(path, rec)
                except (mutagen.MutagenError, ID3NoHeaderError, OSError, ValueError) as e:
                    rec["error"] = f"{type(e).__name__}: {e}"
            records.append(rec)
            if len(records) % 500 == 0:
                print(f"  {len(records)} files…", file=sys.stderr)
    return records


def sha1(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def exact_duplicates(root, audio):
    by_size = defaultdict(list)
    for r in audio:
        by_size[r["bytes"]].append(r)
    groups = defaultdict(list)
    for same in by_size.values():
        if len(same) > 1:
            for r in same:
                groups[sha1(os.path.join(root, r["path"]))].append(r["path"])
    return [g for g in groups.values() if len(g) > 1]


# ------------------------------------------------------------------ analysis

def analyse(root, records):
    audio = [r for r in records if r["kind"] == "audio"]
    readable = [r for r in audio if "tags" in r]
    A = {"files": len(records), "audio": len(audio), "bytes": sum(r["bytes"] for r in audio)}
    A["kinds"] = Counter(r["kind"] for r in records)
    A["formats"] = Counter(r["ext"] for r in audio)
    A["seconds"] = sum(r.get("duration") or 0 for r in audio)
    A["unplayable"] = [r["path"] for r in audio if r["ext"] not in PLAYABLE_ON_ANDROID]
    A["errors"] = [(r["path"], r["error"]) for r in audio if "error" in r]
    A["junk"] = Counter(r["ext"] or r["path"].rsplit("/", 1)[-1] for r in records if r["kind"] == "junk")

    mp3 = [r for r in readable if r["ext"] == "mp3"]
    A["id3"] = Counter(
        ("ID3v" + r["id3v2"] if r.get("id3v2") else "no ID3v2") + (" + ID3v1" if r.get("id3v1") else "")
        for r in mp3)
    A["bitrate_mode"] = Counter(r.get("bitrate_mode") for r in mp3)

    def band(kbps):
        for lo in (320, 256, 192, 160, 128):
            if kbps >= lo - 2:
                return f"≥{lo}" if lo != 320 else "320"
        return "<128"
    A["bitrates"] = Counter(band(r.get("bitrate") or 0) for r in readable)
    A["sample_rates"] = Counter(r.get("sample_rate") for r in readable)

    fields = ["title", "artist", "album", "albumartist", "track", "date", "genre", "mb_albumid"]
    A["coverage"] = {f: sum(1 for r in readable if f in r["tags"]) for f in fields}
    A["readable"] = len(readable)

    # Albums = folders.
    albums = defaultdict(list)
    for r in audio:
        albums[(r["folder_artist"], r["folder_album"])].append(r)
    images_in = defaultdict(list)
    for r in records:
        if r["kind"] == "image":
            images_in[os.path.dirname(r["path"])].append(r["path"].rsplit("/", 1)[-1])
    A["albums"] = len(albums)
    problems = defaultdict(list)
    art_stats = Counter()
    for (fa, fal), tracks in sorted(albums.items(), key=lambda kv: (str(kv[0][0]), str(kv[0][1]))):
        label = f"{fa}/{fal}"
        tagged = [t for t in tracks if "tags" in t]
        embedded = sum(1 for t in tagged if t.get("art"))
        folder_imgs = images_in.get(os.path.dirname(tracks[0]["path"]), [])
        if embedded == len(tracks):
            art_stats["embedded in every track"] += 1
        elif embedded:
            art_stats["embedded in some tracks"] += 1
        elif folder_imgs:
            art_stats["folder image only"] += 1
        else:
            art_stats["no artwork"] += 1
            problems["Albums with no artwork"].append(label)
        album_tags = Counter(t["tags"].get("album") for t in tagged)
        if len(album_tags) > 1:
            problems["Albums whose tracks disagree on the album name"].append(
                f"{label}: " + " · ".join(f"{k or '∅'} ({v})" for k, v in album_tags.most_common(4)))
        aa = Counter(t["tags"].get("albumartist") or t["tags"].get("artist") for t in tagged)
        if fa and aa and norm(fa) not in {norm(a) for a in aa if a} and fa not in ("Compilations", "Varios Artistas", "Various Artists"):
            problems["Folder artist differs from the tags"].append(f"{label}: tags say {aa.most_common(1)[0][0] or '∅'}")
        nums = []
        for t in tagged:
            m = re.match(r"\s*(\d+)", t["tags"].get("track", ""))
            if m:
                nums.append(int(m.group(1)))
        if tagged and len(nums) < len(tagged):
            problems["Albums with missing track numbers"].append(f"{label}: {len(tagged) - len(nums)} of {len(tagged)}")
        elif nums and len(set(nums)) != len(nums):
            problems["Albums with repeated track numbers"].append(f"{label} (several discs mixed?)")
        if any((t.get("bitrate") or 999) < 126 for t in tagged):
            problems["Albums with tracks under 128 kbps"].append(label)
    A["art"] = art_stats

    # Same artist, different spellings.
    spellings = defaultdict(Counter)
    for r in readable:
        for name in (r["folder_artist"], r["tags"].get("artist"), r["tags"].get("albumartist")):
            if name:
                spellings[norm(name)][name] += 1
    A["spellings"] = sorted(((k, c) for k, c in spellings.items() if len(c) > 1 and k),
                            key=lambda kc: -sum(kc[1].values()))

    # Missing core tags, as examples.
    for r in readable:
        missing = [f for f in ("title", "artist", "album") if f not in r["tags"]]
        if missing:
            problems["Tracks missing title, artist or album"].append(f"{r['path']}: no {', '.join(missing)}")

    # Candidate duplicate recordings: same artist + title, similar length.
    by_song = defaultdict(list)
    for r in readable:
        t = r["tags"]
        if t.get("title") and t.get("artist"):
            by_song[(norm(t["artist"]), norm(t["title"]))].append(r)
    cands = []
    for group in by_song.values():
        if len(group) > 1:
            group.sort(key=lambda r: r.get("duration") or 0)
            if (group[-1].get("duration") or 0) - (group[0].get("duration") or 0) <= 3:
                cands.append([r["path"] for r in group])
    A["dup_candidates"] = cands
    print("  hashing same-size files for exact duplicates…", file=sys.stderr)
    A["dup_exact"] = exact_duplicates(root, audio)

    years = []
    for r in readable:
        m = re.match(r"(\d{4})", r["tags"].get("date", ""))
        if m and 1900 < int(m.group(1)) < 2030:
            years.append(int(m.group(1)))
    A["years"] = Counter(y // 10 * 10 for y in years)
    A["genres"] = Counter(r["tags"].get("genre") for r in readable if r["tags"].get("genre"))
    A["mtimes"] = sorted(r["mtime"] for r in audio)
    A["problems"] = problems
    return A


# ------------------------------------------------------------------ report

CSS = """
:root{--bg:#f6f7f7;--card:#fff;--ink:#14191a;--soft:#5b6466;--line:#dde2e3;--hi:#1f6f66;--bar:#9fd6cf;--warn:#a5551e}
@media (prefers-color-scheme:dark){:root{--bg:#0c1112;--card:#141b1c;--ink:#e8eceb;--soft:#98a3a4;--line:#263032;--hi:#9fd6cf;--bar:#2f6b66;--warn:#f0a36b}}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.55 system-ui,-apple-system,"Segoe UI",sans-serif;padding:32px max(16px,4vw) 64px}
main{max-width:1080px;margin:0 auto;display:grid;gap:28px}
h1{font-size:34px;line-height:1.1;margin:0;letter-spacing:-.02em}
h2{font-size:13px;letter-spacing:.08em;text-transform:uppercase;color:var(--soft);margin:0 0 12px;font-weight:600}
.sub{color:var(--soft);margin:6px 0 0}
.nums{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}
.nums div{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px 16px}
.nums b{display:block;font-size:26px;letter-spacing:-.02em;font-variant-numeric:tabular-nums}
.nums span{color:var(--soft);font-size:13px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:20px}
section.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:16px 18px}
table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}
td{padding:4px 0;vertical-align:middle}
td.n{text-align:right;width:70px;color:var(--soft)}
td.bar{width:45%;padding-left:12px}
td.bar i{display:block;height:8px;border-radius:4px;background:var(--bar)}
details{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 18px}
summary{cursor:pointer;font-weight:600}
summary em{font-style:normal;color:var(--warn);margin-left:6px;font-variant-numeric:tabular-nums}
details ul{margin:10px 0 4px;padding-left:18px;font-size:13px;max-height:420px;overflow:auto}
code{font:12.5px ui-monospace,Menlo,monospace}
.note{color:var(--soft);font-size:13px}
"""


def bars(counter, total=None, label=str, limit=12):
    total = total or sum(counter.values()) or 1
    rows = counter.most_common(limit)
    peak = max((v for _, v in rows), default=1)
    return "<table>" + "".join(
        f"<tr><td>{html.escape(label(k))}</td><td class='bar'><i style='width:{v / peak * 100:.1f}%'></i></td>"
        f"<td class='n'>{v:,}</td></tr>" for k, v in rows) + "</table>"


def problem_block(title, items, limit=300):
    shown = items[:limit]
    more = f"<li class='note'>…and {len(items) - limit:,} more (see inventory.jsonl)</li>" if len(items) > limit else ""
    return (f"<details><summary>{html.escape(title)}<em>{len(items):,}</em></summary><ul>"
            + "".join(f"<li><code>{html.escape(str(x))}</code></li>" for x in shown) + more + "</ul></details>")


def report(A, root):
    hours = A["seconds"] / 3600
    cov = A["coverage"]
    cov_rows = Counter({k: v for k, v in cov.items()})
    out = [f"<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
           f"<title>Collection inventory</title><style>{CSS}</style><main>",
           f"<header><h1>Collection inventory</h1><p class='sub'>{html.escape(root)} · read-only scan, "
           f"{time.strftime('%Y-%m-%d %H:%M')}</p></header>",
           "<div class='nums'>"
           f"<div><b>{A['audio']:,}</b><span>audio files</span></div>"
           f"<div><b>{A['albums']:,}</b><span>album folders</span></div>"
           f"<div><b>{A['bytes'] / 1e9:.1f} GB</b><span>of audio</span></div>"
           f"<div><b>{hours:,.0f} h</b><span>total length</span></div>"
           f"<div><b>{A['mtimes'][0] if A['mtimes'] else '–'}</b><span>oldest file date</span></div>"
           "</div>",
           "<div class='grid'>",
           f"<section class='card'><h2>Formats</h2>{bars(A['formats'])}</section>",
           f"<section class='card'><h2>Bitrate (kbps)</h2>{bars(A['bitrates'])}</section>",
           f"<section class='card'><h2>MP3 tag versions</h2>{bars(A['id3'])}</section>",
           f"<section class='card'><h2>MP3 encoding</h2>{bars(A['bitrate_mode'])}</section>",
           f"<section class='card'><h2>Tag coverage (of {A['readable']:,} readable files)</h2>"
           f"{bars(cov_rows, limit=20)}</section>",
           f"<section class='card'><h2>Album artwork</h2>{bars(A['art'])}</section>",
           f"<section class='card'><h2>Release decade</h2>{bars(A['years'], label=lambda y: f'{y}s')}</section>",
           f"<section class='card'><h2>Top genres (as tagged)</h2>{bars(A['genres'], limit=15)}</section>",
           f"<section class='card'><h2>Other files</h2>{bars(A['kinds'])}<p class='note'>Junk by type: "
           + html.escape(", ".join(f"{k} ({v})" for k, v in A["junk"].most_common())) + "</p></section>",
           "</div>", "<h2>Problems</h2>"]
    out.append(problem_block("Formats Android can't play", A["unplayable"]))
    out.append(problem_block("Files that couldn't be read", [f"{p}: {e}" for p, e in A["errors"]]))
    out.append(problem_block("Same artist, different spellings",
                             [" · ".join(f"{n} ({c})" for n, c in cnt.most_common()) for _, cnt in A["spellings"]]))
    for title in sorted(A["problems"]):
        out.append(problem_block(title, A["problems"][title]))
    out.append(problem_block("Exact duplicate files (identical bytes)", [" = ".join(g) for g in A["dup_exact"]]))
    out.append(problem_block("Possible duplicate recordings (same artist and title, length within 3 s)",
                             [" · ".join(g) for g in A["dup_candidates"]]))
    out.append("<p class='note'>Generated by tools/inventory/inventory.py. Nothing in the collection was modified.</p></main>")
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("collection")
    ap.add_argument("out")
    a = ap.parse_args()
    root = os.path.abspath(a.collection)
    os.makedirs(a.out, exist_ok=True)
    print(f"Scanning {root} (read-only)…", file=sys.stderr)
    records = scan(root)
    with open(os.path.join(a.out, "inventory.jsonl"), "w") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    A = analyse(root, records)
    with open(os.path.join(a.out, "report.html"), "w") as f:
        f.write(report(A, root))
    print(f"Done: {A['audio']:,} audio files in {A['albums']:,} album folders → {a.out}/report.html", file=sys.stderr)


if __name__ == "__main__":
    main()
