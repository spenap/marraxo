#!/bin/sh
# Makes the Nexus 4 a dedicated player (roadmap A6), or puts it back.
# For now: Mako becomes the home screen. The previous launcher is saved on
# the phone (/data/local/tmp/mako-appliance.prev) and restored by --undo.
# Usage: scripts/appliance.sh [--undo] [device-serial]
set -eu
undo=
if [ "${1:-}" = "--undo" ]; then undo=1; shift; fi
S=${1:-}
a() { if [ -n "$S" ]; then adb -s "$S" "$@"; else adb "$@"; fi </dev/null; }

home=com.simonpena.mako/com.simonpena.mako.Home
saved=/data/local/tmp/mako-appliance.prev
current_home() {
    a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
        tail -1 | tr -d '\r'
}

if [ -z "$undo" ]; then
    if [ -z "$(a shell "cat $saved 2>/dev/null" | tr -d '\r')" ]; then
        a shell "echo home=$(current_home) > $saved"   # only the first time: never save Mako itself
    fi
    a shell pm enable "$home" >/dev/null
    a shell cmd package set-home-activity "$home" >/dev/null
    echo "Home screen: $(current_home) (was $(a shell cat $saved | sed -n 's/^home=//p' | tr -d '\r'))"
else
    previous=$(a shell "cat $saved 2>/dev/null" | sed -n 's/^home=//p' | tr -d '\r')
    if [ -z "$previous" ]; then echo "Nothing to undo: no saved settings on the phone."; exit 1; fi
    a shell cmd package set-home-activity "$previous" >/dev/null
    a shell pm disable "$home" >/dev/null
    a shell rm "$saved"
    echo "Home screen: $(current_home)"
fi
