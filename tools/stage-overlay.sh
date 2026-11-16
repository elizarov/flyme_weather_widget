#!/system/bin/sh
# Run as root on DHU1 after pushing the built RRO to /data/local/tmp.
set -eu
[ "$(getprop ro.vendor.dhu)" = 1 ] || { echo 'Expected DHU1'; exit 1; }
target=/system/app/WeatherWidgetOverlay
[ ! -e "$target" ] || { echo 'Overlay directory already exists; inspect before updating'; exit 1; }
trap 'mount -o remount,ro /system' EXIT
mount -o remount,rw /system
mkdir "$target"
cp /data/local/tmp/weather-overlay.apk "$target/WeatherWidgetOverlay.apk"
chown -R 0:0 "$target"
chmod 755 "$target"
chmod 644 "$target/WeatherWidgetOverlay.apk"
restorecon -RF "$target"
# Do not enable here: Package Manager must first scan this system package.
echo 'Staged, disabled by default. Android framework restart required for scan.'
