#!/bin/sh
# Simulate a radio station on the local collection (docs/radio.md).
# Usage: scripts/station.sh --artist "The Strokes" --title "You Only Live Once"
#        [--weights Radio|Similar|Era|People|Anything|genre=…,decade=…,people=…,artist=…,band=…]
#        [--compare <weights>] [--picks 20] [--seed 1]
set -eu
cd "$(dirname "$0")/.."
props=""
while [ $# -ge 2 ]; do
    props="$props
-Pstation.${1#--}=$2"
    shift 2
done
IFS='
'
# shellcheck disable=SC2086
./gradlew --offline testDebugUnitTest --tests com.simonpena.makofi.StationSimulator $props 2>&1 |
    sed -n '/STANDARD_OUT/,/^BUILD/p' | sed '1d;$d'
