#!/bin/sh
# Build the optimised APK, install it, and compile it ahead of time.
# Without the compile step ART only JIT-compiles, and scrolling on the
# Nexus 4 is noticeably janky until background dexopt runs.
# Usage: scripts/install.sh [device-serial]
set -eu
cd "$(dirname "$0")/.."
S=${1:-}
a() { if [ -n "$S" ]; then adb -s "$S" "$@"; else adb "$@"; fi </dev/null; }
./gradlew assembleRelease -q --console=plain
a install -r app/build/outputs/apk/release/app-release.apk
a shell cmd package compile -m speed-profile -f com.simonpena.mako
