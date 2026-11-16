#!/bin/bash
set -euo pipefail
project="$(cd "$(dirname "$0")/.." && pwd)"
launcher="${1:-$project/local-assets/AutoLauncher.apk}"
weather="${2:-$project/local-assets/AutoWeather.apk}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [[ -z "${JAVA_HOME:-}" && -d /Applications/Android\ Studio.app/Contents/jbr/Contents/Home ]]; then
    export JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home
fi
export ANDROID_HOME="$sdk"
python3 "$project/tools/prepare-assets.py" --launcher "$launcher" --weather "$weather"
python3 "$project/tools/signing.py" init
"$project/gradlew" -p "$project" -PtestBuildType=release :widget:assembleRelease :widget:assembleReleaseAndroidTest :widget:lintRelease
build="$project/overlay/build"
mkdir -p "$build"
"$sdk/build-tools/36.0.0/aapt2" compile --dir "$project/overlay/res" -o "$build/patch.zip"
"$sdk/build-tools/36.0.0/aapt2" compile --dir "$project/local-assets/generated/overlay-res" -o "$build/resources.zip"
"$sdk/build-tools/36.0.0/aapt2" link -o "$build/unsigned.apk" --manifest "$project/overlay/AndroidManifest.xml" \
    -I "$sdk/platforms/android-36/android.jar" -I "$launcher" \
    --package-id 0x7e --allow-reserved-package-id --auto-add-overlay --no-resource-deduping --no-resource-removal "$build/patch.zip" "$build/resources.zip"
"$sdk/build-tools/36.0.0/zipalign" -f 4 "$build/unsigned.apk" "$build/aligned.apk"
python3 "$project/tools/signing.py" sign --apksigner "$sdk/build-tools/36.0.0/apksigner" \
    --input "$build/aligned.apk" --output "$build/weather-overlay.apk"
"$sdk/build-tools/36.0.0/apksigner" verify "$project/widget/build/outputs/apk/release/widget-release.apk"
"$sdk/build-tools/36.0.0/apksigner" verify "$build/weather-overlay.apk"
python3 "$project/tools/verify-release.py" --aapt2 "$sdk/build-tools/36.0.0/aapt2" \
    --apksigner "$sdk/build-tools/36.0.0/apksigner" \
    --widget "$project/widget/build/outputs/apk/release/widget-release.apk" \
    --overlay "$build/weather-overlay.apk" \
    --test "$project/widget/build/outputs/apk/androidTest/release/widget-release-androidTest.apk"
echo "Widget: $project/widget/build/outputs/apk/release/widget-release.apk"
echo "Overlay: $build/weather-overlay.apk"
