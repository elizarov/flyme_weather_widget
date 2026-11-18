#!/bin/bash
set -euo pipefail
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
serial="${1:-emulator-5560}"
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Emulator only' >&2; exit 1; }
[[ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
"$adb" -s "$serial" shell am start --windowingMode 1 -f 0x10008000 -n me.elizarov.weather/.PreviewActivity --ez light_text true
