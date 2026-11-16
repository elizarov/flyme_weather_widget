#!/bin/bash
# Update the widget app after the overlay has been registered. No restarts.
set -euo pipefail
project="$(cd "$(dirname "$0")/.." && pwd)"
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
serial="${1:?Usage: tools/update.sh DEVICE_SERIAL}"
device() { "$adb" -s "$serial" "$@"; }
test "$(device shell getprop ro.vendor.dhu | tr -d '\r')" = 1
android_user="$(device shell am get-current-user | tr -d '\r')"
[[ "$android_user" =~ ^[0-9]+$ ]] || { echo 'Cannot determine the current Android user.' >&2; exit 1; }
device shell pm path --user "$android_user" me.elizarov.weather.overlay | grep -q '^package:' || {
    echo 'First register the system overlay using stage-overlay.sh; see README.' >&2; exit 1;
}
overlays="$(device shell cmd overlay list --user "$android_user" | tr -d '\r')"
if ! printf '%s\n' "$overlays" | grep -Eq '^[[:space:]]*\[x\][[:space:]]+me\.elizarov\.weather\.overlay[[:space:]]*$'; then
    echo 'Enable the weather overlay for the current Android user first; see README.' >&2; exit 1
fi
device install --no-incremental -r --user "$android_user" "$project/widget/build/outputs/apk/release/widget-release.apk"
device shell am broadcast --user "$android_user" -a me.elizarov.weather.UPDATE_MINUTE \
    -n me.elizarov.weather/.WeatherWidgetProvider
echo 'Widget updated without restarting Android or the launcher.'
