#!/bin/bash
set -euo pipefail
project="$(cd "$(dirname "$0")/.." && pwd)"
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
serial="${1:-emulator-5560}"
variant="${2:-release}"
[[ "$variant" == release || "$variant" == debug ]] || { echo 'Build type must be release or debug' >&2; exit 1; }
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Emulator only' >&2; exit 1; }
test "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
"$adb" -s "$serial" shell settings put system time_12_24 24
"$adb" -s "$serial" install --no-incremental -r "$project/widget/build/outputs/apk/$variant/widget-$variant.apk"
"$adb" -s "$serial" install --no-incremental -r "$project/widget/build/outputs/apk/androidTest/$variant/widget-$variant-androidTest.apk"
output="$("$adb" -s "$serial" shell am instrument -w me.elizarov.weather.test/me.elizarov.weather.WidgetSmoke)"
printf '%s\n' "$output"
printf '%s' "$output" | grep -q 'PASS: RemoteViews'
