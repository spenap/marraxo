#!/bin/sh
# Makes the Nexus 4 a dedicated player (roadmap A6), or puts it back.
# Makofi becomes the home screen, the camera is turned off, and the system
# settings agreed in the settings review (2026-10-31) are applied. Every
# previous value is saved on the phone (/data/local/tmp/mako-appliance.prev)
# the first time it changes, and --undo restores them all.
# Usage: scripts/appliance.sh [--undo] [device-serial]
set -eu
undo=
if [ "${1:-}" = "--undo" ]; then undo=1; shift; fi
S=${1:-}
a() { if [ -n "$S" ]; then adb -s "$S" "$@"; else adb "$@"; fi </dev/null; }
# Changing Makofi's components needs root; adbd forgets it after a reboot.
if [ "$(a shell id -u | tr -d '\r')" != 0 ]; then a root >/dev/null; sleep 2; a wait-for-device; fi

home=com.simonpena.makofi/com.simonpena.makofi.Home
saved=/data/local/tmp/mako-appliance.prev
current_home() {
    a shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
        tail -1 | tr -d '\r'
}
cameras() {  # enabled apps that take photos (LineageOS 15.1: Camera2)
    a shell cmd package query-activities --brief -a android.media.action.STILL_IMAGE_CAMERA |
        tr -d '\r' | grep / | cut -d/ -f1 | sort -u
}

# Settings live in several places; "where" names one of them.
#   system | secure | global   Android's settings
#   lineage                    LineageOS's own system settings
#   location                   the GPS provider (secure location_providers_allowed)
#   nfc | bluetooth            those radios
#   tz                         the time zone
get() {
    case $1 in
        system|secure|global) a shell settings get "$1" "$2" ;;
        lineage) a shell content query --uri content://lineagesettings/system --projection value --where "name=\'$2\'" |
                     sed -n 's/.*value=//p' ;;
        location) a shell settings get secure location_providers_allowed | grep -q gps && echo on || echo off ;;
        nfc) a shell dumpsys nfc | grep -q 'mState=on' && echo on || echo off ;;
        bluetooth) a shell dumpsys bluetooth_manager | grep -q 'enabled: true' && echo on || echo off ;;
        tz) a shell getprop persist.sys.timezone ;;
    esac | tr -d '\r' | sed 's/^$/null/'
}
put() {
    case $1 in
        system|secure|global)
            if [ "$3" = null ]; then a shell settings delete "$1" "$2" >/dev/null; else a shell settings put "$1" "$2" "$3"; fi ;;
        lineage) v=$3; [ "$v" = null ] && v=0   # never set: LineageOS's default, and it refuses empty values
                 a shell content insert --uri content://lineagesettings/system --bind name:s:"$2" --bind value:s:"$v" ;;
        location) if [ "$3" = on ]; then v=+gps; else v=-gps; fi; a shell settings put secure location_providers_allowed $v ;;
        nfc|bluetooth) if [ "$3" = on ]; then a shell svc "$1" enable; else a shell svc "$1" disable; fi ;;
        tz) a shell service call alarm 3 s16 "$3" >/dev/null ;;
    esac
}
# Sets a value, saving the previous one the first time only (never Makofi's own).
set_() {
    if ! a shell "grep -qF 'set=$1|$2|' $saved"; then
        a shell "echo 'set=$1|$2|$(get "$1" "$2")' >> $saved"
    fi
    put "$1" "$2" "$3"
}
airplane() {  # tells the system the airplane setting changed (it only reads the broadcast)
    a shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state "$1" >/dev/null
}

if [ -z "$undo" ]; then
    if [ -z "$(a shell "cat $saved 2>/dev/null" | tr -d '\r')" ]; then
        a shell "echo home=$(current_home) > $saved"   # only the first time: never save Makofi itself
    fi
    a shell pm enable "$home" >/dev/null
    a shell cmd package set-home-activity "$home" >/dev/null
    echo "Home screen: $(current_home) (was $(a shell cat $saved | sed -n 's/^home=//p' | tr -d '\r'))"

    # No camera: it opens from the lock screen and from a double press of the
    # power button, which also fires by accident.
    set_ secure camera_double_tap_power_gesture_disabled 1
    for p in $(cameras); do
        a shell pm disable-user --user 0 "$p" >/dev/null
        a shell "grep -q '^camera=$p\$' $saved || echo camera=$p >> $saved"
    done
    # Makofi hides the navigation bar; skip Android's one-time "Viewing full screen" notice.
    set_ secure immersive_mode_confirmations confirmed

    # Display and sound
    set_ system accelerometer_rotation 0       # Makofi is portrait-only
    set_ secure screensaver_enabled 0          # dock mode will replace the clock screen saver
    set_ system sound_effects_enabled 0        # no clicks in the headphones
    set_ system lockscreen_sounds_enabled 0
    set_ system dtmf_tone 0
    set_ system notification_light_pulse 0     # the LED stays for charging

    # Radios. No SIM, but the cell radio keeps searching: airplane mode turns
    # off only the cell radio. Wi-Fi follows airplane_mode_radios; Bluetooth
    # on this build doesn't, so it's switched back to what it was (Android
    # then keeps it on in airplane mode, across reboots).
    bt=$(get bluetooth radio)
    set_ bluetooth radio "$bt"
    set_ global airplane_mode_radios cell,wimax
    set_ global airplane_mode_on 1
    airplane true
    sleep 3   # let Bluetooth finish turning off before turning it back on
    put bluetooth radio "$bt"
    set_ global mobile_data 0
    set_ location gps off
    set_ nfc radio off
    set_ global wifi_networks_available_notification_on 0
    set_ global ble_scan_always_enabled 0

    # Lock screen, notifications, shade and status bar
    set_ secure lock_screen_show_notifications 0   # Makofi's lock-screen player shows the music
    set_ global heads_up_notifications_enabled 0
    set_ secure sysui_qs_tiles wifi,bt,volume_panel,flashlight,airplane
    set_ secure icon_blacklist mobile,airplane
    set_ lineage status_bar_show_battery_percent 2

    # Time zone: without a SIM it can't be detected.
    set_ global auto_time_zone 0
    set_ tz zone Europe/Madrid

    echo "Camera: off ($(a shell cat $saved | sed -n 's/^camera=//p' | tr -d '\r' | tr '\n' ' '))"
    echo "Settings: $(a shell "grep -c '^set=' $saved" | tr -d '\r') saved for --undo"
else
    previous=$(a shell "cat $saved 2>/dev/null" | sed -n 's/^home=//p' | tr -d '\r')
    if [ -z "$previous" ]; then echo "Nothing to undo: no saved settings on the phone."; exit 1; fi
    a shell cmd package set-home-activity "$previous" >/dev/null
    a shell pm disable "$home" >/dev/null
    for p in $(a shell cat $saved | sed -n 's/^camera=//p' | tr -d '\r'); do a shell pm enable "$p" >/dev/null; done
    # Restore in reverse order of setting, then let the radios catch up.
    a shell cat $saved | tr -d '\r' | sed -n 's/^set=//p' | sed -n '1!G;h;$p' | while IFS='|' read -r where key value; do
        put "$where" "$key" "$value"
    done
    if [ "$(get global airplane_mode_on)" = 1 ]; then airplane true; else airplane false; fi
    a shell rm "$saved"
    echo "Home screen: $(current_home)"
fi
