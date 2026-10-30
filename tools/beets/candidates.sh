#!/bin/sh
# Shows beets' candidates for album folders without importing anything.
# beets applies strong matches without asking, even when every answer is
# "skip" (that imported 7 albums on 2026-10-30), so this runs it against a
# throwaway library and folder, with copying and tag writing off.
# Usage: ./candidates.sh LIST OUTDIR   (LIST: one album folder per line)
set -eu
here=$(cd "$(dirname "$0")" && pwd)
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
mkdir -p "$2"
n=0
while IFS= read -r dir; do
    n=$((n+1))
    yes s | head -40 | "$here/beet" -l "$scratch/library.db" -d "$scratch/music" \
        import -C -W --noincremental -l /dev/null "$dir" 2>&1 |
        sed 's/\x1b\[[0-9;]*m//g' > "$2/$(printf %03d $n).txt"
done < "$1"
