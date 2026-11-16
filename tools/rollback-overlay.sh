#!/system/bin/sh
# Run as root on DHU1. No OEM package or APK is removed.
set -eu
[ "$(getprop ro.vendor.dhu)" = 1 ] || { echo 'Expected DHU1'; exit 1; }
cmd overlay disable --user current me.elizarov.weather.overlay
echo 'Original launcher resources restored. Restart launcher to clear its XML cache.'
# Keep the disabled system RRO installed; deleting it is unnecessary for rollback.
