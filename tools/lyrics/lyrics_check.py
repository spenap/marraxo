#!/usr/bin/env python3
"""Find lyrics that aren't lyrics: LRCLIB takes anyone's submissions (docs/lyrics.md).

Seen for songs in the collection: a note saying "instrumental", a Russian
paragraph on a Dutch trance track, "Artist: … Album: … Title: …" instead
of words, a remark that a song can't be translated. lyrics_build.py leaves
out what this flags, and the paths listed in out/lyrics-rejects.txt (one
per line, "#" starts a comment) for what no rule can tell: real lyrics of
another song, pasted under this one.

review() lists lyrics far too short or long for the song, for a person to
read: cut short after a line, or the lyrics of a longer version. Real
lyrics live at both ends too (a hook sung once, fast rap), so it never
rejects on its own.

Usage: lyrics_check.py   (lists what it flags, then what to review)
"""
import re
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # lyrics_fetch.py, also under python -I
import lyrics_fetch  # noqa: E402

REJECTS = Path(__file__).resolve().parent / "out" / "lyrics-rejects.txt"
TIMES = re.compile(r"\[\d+:\d+(?:[.:]\d+)?\]|<\d+:\d+(?:[.:]\d+)?>")
DETAILS = re.compile(r"^\s*(?:artist|album|title)\s*:", re.I | re.M)
NOTES = re.compile(r"cannot be translated|no lyrics|lyrics (?:are )?not available|there are no (?:lyrics|words)|instrumental (?:track|version)", re.I)


def script(ch):
    """The writing system of a letter: "LATIN", "CYRILLIC", "GREEK", "CJK"…"""
    return unicodedata.name(ch, "?").split(" ")[0]


def scripts(text):
    letters = [script(c) for c in text if c.isalpha()]
    return {s: letters.count(s) / len(letters) for s in set(letters)} if letters else {}


def suspect(title, artist, text):
    """Why [text] isn't the lyrics of [title] by [artist], or None if it may well be."""
    words = re.findall(r"[^\W\d_]+", TIMES.sub(" ", text or ""))
    if not words:
        return None
    if len(words) <= 12 and any(w.lower() == "instrumental" for w in words):
        return "says instrumental"
    if len(DETAILS.findall(text)) >= 2:
        return "song details"
    if NOTES.search(text):
        return "a note about the song"
    main, share = max(scripts(text).items(), key=lambda kv: kv[1])
    if share > .5 and main not in scripts(title + " " + artist):
        return "another alphabet"
    return None


FEW, MANY = 4, 250  # words a minute; the collection's median is 57, 90% within 23–119


def review(answer):
    """Why a cached answer's lyrics deserve a look for their length, or None."""
    text = answer.get("plainLyrics") or answer.get("syncedLyrics") or ""
    words = len(re.findall(r"[^\W\d_]+", TIMES.sub(" ", text)))
    if not words or not answer.get("duration"):
        return None
    rate = words / (answer["duration"] / 60)
    if rate < FEW:
        return f"too few words ({rate:.0f} a minute)"
    if rate > MANY:
        return f"too many words ({rate:.0f} a minute)"
    return None


def read_rejects(path=REJECTS):
    if not Path(path).exists():
        return set()
    return {line.strip() for line in Path(path).read_text().splitlines() if line.strip() and not line.startswith("#")}


def check(answer):
    """suspect() for a cached LRCLIB answer: its plain words, or its timed lines."""
    text = answer.get("plainLyrics") or answer.get("syncedLyrics") or ""
    return suspect(answer.get("trackName") or "", answer.get("artistName") or "", text)


def main():
    flagged = [(a["path"], why, " ".join((a.get("plainLyrics") or a.get("syncedLyrics") or "").split())[:80])
               for a in lyrics_fetch.Cache(lyrics_fetch.CACHE).all() if a.get("status") == "found" and (why := check(a))]
    for path, why, text in sorted(flagged):
        print(f"{why:22} {path}\n{'':22} {text}")
    print(f"{len(flagged)} flagged\n")
    rejected = read_rejects()
    looks = [(a["path"], why) for a in lyrics_fetch.Cache(lyrics_fetch.CACHE).all()
             if a.get("status") == "found" and not check(a) and a["path"] not in rejected and (why := review(a))]
    for path, why in sorted(looks):
        print(f"review: {why:28} {path}")
    print(f"{len(looks)} to review")


if __name__ == "__main__":
    sys.exit(main())
