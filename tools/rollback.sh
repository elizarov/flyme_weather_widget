#!/bin/bash
set -euo pipefail
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
serial="${1:?Usage: tools/rollback.sh DEVICE_SERIAL}"
device() { "$adb" -s "$serial" "$@"; }
test "$(device shell getprop ro.vendor.dhu | tr -d '\r')" = 1
device shell cmd overlay disable --user current me.elizarov.weather.overlay
echo 'Overlay disabled. Original widget will return when the launcher next reloads its configuration.'
