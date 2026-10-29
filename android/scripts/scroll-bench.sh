#!/bin/sh
# Cold-start Mako, fling down the album grid, and print frame statistics.
# Usage: scripts/scroll-bench.sh [device-serial]
# The phone must not have a secure lock screen (PIN/pattern).
set -eu
S=${1:-}
a() { if [ -n "$S" ]; then adb -s "$S" "$@"; else adb "$@"; fi </dev/null; }

a shell input keyevent KEYCODE_WAKEUP
a shell wm dismiss-keyguard
a shell am force-stop com.simonpena.mako
a shell am start -W -n com.simonpena.mako/.MainActivity >/dev/null
sleep 3                                   # library query and first frame
a shell dumpsys gfxinfo com.simonpena.mako reset >/dev/null
for _ in 1 2 3 4 5 6 7 8; do
    a shell input swipe 384 1000 384 300 120   # fast fling up = scroll down
    sleep 1
done
a shell dumpsys gfxinfo com.simonpena.mako |
    grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow UI thread|Number Slow bitmap uploads|Number Slow issue draw commands'
