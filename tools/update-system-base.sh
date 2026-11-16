#!/system/bin/sh
# Keep our own preinstalled base in sync with the tested /data app update.
set -eu
[ "$(getprop ro.vendor.dhu)" = 1 ] || exit 1
target=/system/app/WeatherWidgetOverlay/WeatherWidgetOverlay.apk
[ -f "$target" ] || exit 1
trap 'mount -o remount,ro /system' EXIT
mount -o remount,rw /system
cp /data/local/tmp/weather-overlay.apk "$target"
chown 0:0 "$target"
chmod 644 "$target"
restorecon "$target"
