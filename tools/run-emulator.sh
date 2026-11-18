#!/bin/bash
set -euo pipefail
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
avd="${1:?Usage: tools/run-emulator.sh AVD_NAME [emulator options]}"
shift
exec "$sdk/emulator/emulator" -avd "$avd" -gpu host -feature -Vulkan \
    -crash-report-mode disabled -no-metrics -no-audio -no-snapshot-load -no-snapshot-save "$@"
