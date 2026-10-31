#!/bin/sh
# Makes the Nexus 4 a dedicated player (roadmap A6), or puts it back.
# Mako becomes the home screen and the camera is turned off. What it changes is saved on
# the phone (/data/local/tmp/mako-appliance.prev) and restored by --undo.
# Usage: scripts/appliance.sh [--undo] [device-serial]
set -eu
undo=
if [ "${1:-}" = "--undo" ]; then undo=1; shift; fi
S=${1:-}
a() { if [ -n "$S" ]; then adb -s "$S" "$@"; else adb "$@"; fi </dev/null; }
# Changing Mako's components needs root; adbd forgets it after a reboot.
if [ "$(a shell id -u | tr -d '\r')" != 0 ]; then a root >/dev/null; sleep 2; a wait-for-device; fi

home=com.simonpena.mako/com.simonpena.mako.Home
saved=/data/local/tmp/mako-appliance.prev
current_home() {
    a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
        tail -1 | tr -d '\r'
}
cameras() {  # enabled apps that take photos (LineageOS 15.1: Snap)
    a shell cmd package query-activities --brief -a android.media.action.STILL_IMAGE_CAMERA |
        tr -d '\r' | grep / | cut -d/ -f1 | sort -u
}

if [ -z "$undo" ]; then
    if [ -z "$(a shell "cat $saved 2>/dev/null" | tr -d '\r')" ]; then
        a shell "echo home=$(current_home) > $saved"   # only the first time: never save Mako itself
    fi
    a shell pm enable "$home" >/dev/null
    a shell cmd package set-home-activity "$home" >/dev/null
    echo "Home screen: $(current_home) (was $(a shell cat $saved | sed -n 's/^home=//p' | tr -d '\r'))"
    # No camera: it opens from the lock screen and from a double press of the
    # power button, which also fires by accident.
    if ! a shell "grep -q '^gesture=' $saved"; then
        a shell "echo gesture=$(a shell settings get secure camera_double_tap_power_gesture_disabled | tr -d '\r') >> $saved"
    fi
    a shell settings put secure camera_double_tap_power_gesture_disabled 1
    for p in $(cameras); do
        a shell pm disable-user --user 0 "$p" >/dev/null
        a shell "grep -q '^camera=$p\$' $saved || echo camera=$p >> $saved"
    done
    # Mako hides the navigation bar; skip Android's one-time "Viewing full screen" notice.
    if ! a shell "grep -q '^immersive=' $saved"; then
        a shell "echo immersive=$(a shell settings get secure immersive_mode_confirmations | tr -d '\r') >> $saved"
    fi
    a shell settings put secure immersive_mode_confirmations confirmed
    echo "Camera: off ($(a shell cat $saved | sed -n 's/^camera=//p' | tr -d '\r' | tr '\n' ' '))"
else
    previous=$(a shell "cat $saved 2>/dev/null" | sed -n 's/^home=//p' | tr -d '\r')
    if [ -z "$previous" ]; then echo "Nothing to undo: no saved settings on the phone."; exit 1; fi
    a shell cmd package set-home-activity "$previous" >/dev/null
    a shell pm disable "$home" >/dev/null
    for p in $(a shell cat $saved | sed -n 's/^camera=//p' | tr -d '\r'); do a shell pm enable "$p" >/dev/null; done
    immersive=$(a shell cat $saved | sed -n 's/^immersive=//p' | tr -d '\r')
    case "$immersive" in
        ''|null) a shell settings delete secure immersive_mode_confirmations >/dev/null ;;
        *) a shell settings put secure immersive_mode_confirmations "$immersive" ;;
    esac
    gesture=$(a shell cat $saved | sed -n 's/^gesture=//p' | tr -d '\r')
    case "$gesture" in
        ''|null) a shell settings delete secure camera_double_tap_power_gesture_disabled >/dev/null ;;
        *) a shell settings put secure camera_double_tap_power_gesture_disabled "$gesture" ;;
    esac
    a shell rm "$saved"
    echo "Home screen: $(current_home)"
fi
