#!/usr/bin/env python3
"""Decide which skipped albums are safe to import without a person.

Reads beets' candidate output for each album (out/candidates/*.txt, from
out/gather.sh) and accepts an album only when the match is unambiguous:
one candidate at 80% or more, every local track matched, every changed
track within 5 s and with the same title apart from case and punctuation.
Missing tracks are fine (partial rips). Everything else is left for a
person, with the reason.

Writes out/triage.md (every decision and its evidence, for review) and
out/triage-accept.tsv (folder, MusicBrainz release) for import.
"""
import difflib
import re
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path

MIN_SIMILARITY = 80.0
MAX_LENGTH_DIFF = 5  # seconds


@dataclass
class Decision:
    folder: str
    accept: bool
    reason: str
    release: str = ""
    similarity: float = 0.0


def norm(s):
    s = unicodedata.normalize("NFKD", s.replace("’", "'").replace("‘", "'"))
    s = "".join(c for c in s if not unicodedata.combining(c)).lower()
    return re.sub(r"[^a-z0-9]+", "", s)


def same_title(a, b):
    a, b = norm(a), norm(b)
    return a == b or a.startswith(b) or b.startswith(a) or difflib.SequenceMatcher(None, a, b).ratio() >= 0.85


def seconds(t):
    m, s = t.split(":")
    return int(m) * 60 + int(s)


CHANGED = re.compile(r"≠ \(#\d+\) (.+?)\s+\((\d+:\d\d)\) -> \(#\d+\) (.+?)\s+\((\d+:\d\d)\)")


def decide(text):
    folder = re.search(r"^(/.*) \(\d+ items\)$", text, re.M)
    folder = folder.group(1) if folder else "?"
    if "Candidates:" in text:
        best = re.search(r"1\. \(([\d.]+)%\)", text)
        return Decision(folder, False, f"no clear match (best candidate {best.group(1) if best else '?'}%)")
    match = re.search(r"Match \(([\d.]+)%\)", text)
    if not match:
        return Decision(folder, False, "no candidate found")
    similarity = float(match.group(1))
    release = re.search(r"musicbrainz\.org/release/([0-9a-f-]{36})", text)
    release = release.group(1) if release else ""
    d = Decision(folder, False, "", release, similarity)
    if similarity < MIN_SIMILARITY:
        d.reason = f"weak match ({similarity}%)"
        return d
    unmatched = re.search(r"Unmatched tracks \((\d+)", text)
    if unmatched:
        d.reason = f"{unmatched.group(1)} local tracks match nothing on the release"
        return d
    for left, lt, right, rt in CHANGED.findall(text):
        if abs(seconds(lt) - seconds(rt)) > MAX_LENGTH_DIFF:
            d.reason = f"“{left.strip()}” is {lt} here but {rt} on the release"
            return d
        if not same_title(left, right):
            d.reason = f"“{left.strip()}” matched to “{right.strip()}”"
            return d
    title = re.search(r"Match \([\d.]+%\):\n\s+.+? - (.+)", text)
    local = folder.rstrip("/").rsplit("/", 1)[-1]
    if title and not same_title(local, title.group(1)):
        d.reason = f"album title differs: “{local}” vs “{title.group(1).strip()}”"
        return d
    missing = re.search(r"Missing tracks \((\d+)/(\d+)", text)
    d.accept = True
    d.reason = f"{similarity}%" + (f", {missing.group(1)} of {missing.group(2)} missing" if missing else ", complete")
    return d


def main(directory="out/candidates"):
    decisions = [decide(p.read_text()) for p in sorted(Path(directory).glob("*.txt"))]
    accepted = [d for d in decisions if d.accept]
    with open("out/triage-accept.tsv", "w") as f:
        for d in accepted:
            f.write(f"{d.folder}\t{d.release}\n")
    with open("out/triage.md", "w") as f:
        f.write(f"# Triage of {len(decisions)} skipped albums\n\n{len(accepted)} accepted, "
                f"{len(decisions) - len(accepted)} left for a person.\n\n| | Album folder | Why |\n|---|---|---|\n")
        for d in sorted(decisions, key=lambda d: (not d.accept, d.folder)):
            name = d.folder.removeprefix("~/Music/")
            link = f" ([release](https://musicbrainz.org/release/{d.release}))" if d.release else ""
            f.write(f"| {'✓' if d.accept else '·'} | {name} | {d.reason}{link} |\n")
    print(f"{len(accepted)} of {len(decisions)} accepted; see out/triage.md")


if __name__ == "__main__":
    main(*sys.argv[1:])
