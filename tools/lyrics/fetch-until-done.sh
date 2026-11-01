#!/bin/sh
# Fetch lyrics until every song has an answer, waiting out LRCLIB outages
# (10 minutes between tries, for up to 10 hours), then build lyrics.db.
# Usage: lyrics/fetch-until-done.sh   (log: lyrics/out/fetch.log)
cd "$(dirname "$0")" || exit 1
mkdir -p out
for try in $(seq 60); do
    echo "== try $try, $(date '+%F %T')"
    if ../.venv/bin/python -I lyrics_fetch.py; then
        ../.venv/bin/python -I lyrics_build.py && exit 0
    fi
    sleep 600
done
exit 1
