#!/bin/sh
# Cold-start Marraxo, fling down a library tab, and print frame statistics.
# Fails (exit 1) above MAX_JANKY percent janky frames (default 5), or if
# too few frames were drawn to judge (screen off, app not in front).
# Usage: [MAX_JANKY=5] [TAB=Genres] scripts/scroll-bench.sh [device-serial]
# TAB: the library tab to fling (its English label; default Albums, the
# album grid), freshly opened: with play history the app opens on Home.
# The phone must not have a secure lock screen (PIN/pattern).
set -eu
S=${1:-}
a() { if [ -n "$S" ]; then adb -s "$S" "$@"; else adb "$@"; fi </dev/null; }

a shell input keyevent KEYCODE_WAKEUP
a shell wm dismiss-keyguard
a shell am force-stop com.simonpena.marraxo
a shell am start -W -n com.simonpena.marraxo/.MainActivity >/dev/null
sleep 3                                   # library query and first frame
TAB=${TAB:-Albums}
if [ -n "$TAB" ]; then
    a shell uiautomator dump /sdcard/bench.xml >/dev/null
    xy=$(a shell cat /sdcard/bench.xml | grep -oE "text=\"$TAB\"[^>]*bounds=\"[^\"]*\"" | head -1 |
        grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | tr '[],' '   ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}')
    [ -n "$xy" ] || { echo "No '$TAB' tab on screen"; exit 1; }
    a shell input tap $xy
    sleep 1
fi
a shell dumpsys gfxinfo com.simonpena.marraxo reset >/dev/null
for _ in 1 2 3 4 5 6 7 8; do
    a shell input swipe 384 1000 384 300 120   # fast fling up = scroll down
    sleep 1
done
stats=$(a shell dumpsys gfxinfo com.simonpena.marraxo |
    grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow UI thread|Number Slow bitmap uploads|Number Slow issue draw commands')
echo "$stats"

frames=$(echo "$stats" | sed -n 's/.*Total frames rendered: \([0-9]*\).*/\1/p')
janky=$(echo "$stats" | sed -n 's/.*Janky frames: [0-9]* (\([0-9.]*\)%).*/\1/p')
max=${MAX_JANKY:-5}
if [ "${frames:-0}" -lt 100 ]; then
    echo "FAIL: only ${frames:-0} frames drawn; was the screen on and Marraxo in front?"
    exit 1
fi
if awk "BEGIN { exit !($janky > $max) }"; then
    echo "FAIL: $janky% janky frames (limit $max%)"
    exit 1
fi
echo "PASS: $janky% janky frames (limit $max%)"
