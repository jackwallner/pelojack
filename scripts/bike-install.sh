#!/usr/bin/env bash
# Builds Pelojack, installs it on the attached tablet, and makes it the home screen.
source "$(dirname "$0")/lib.sh"
STATE="$ROOT/.bike-original-home"

# After a tablet restart adb is gone; Pelojack's dev link installs the update instead. Home screen
# and permissions are already set from the first install and survive restarts.
if [[ $(bike_route) == link ]]; then
  [[ -n "${PELOJACK_SKIP_BUILD:-}" ]] || build_apk
  before=$(link GET /ping | python3 -c 'import json, sys; print(json.load(sys.stdin)["lastUpdate"])')
  link_install "$APP_ID" "$APK"
  for _ in $(seq 1 45); do
    sleep 2
    now=$(LINK_TIMEOUT=3 link GET /ping 2>/dev/null | python3 -c 'import json, sys; print(json.load(sys.stdin)["lastUpdate"])' 2>/dev/null) || continue
    if [[ "$now" != "$before" ]]; then echo "Installed over the dev link."; exit 0; fi
  done
  echo "Pelojack did not come back with the new build. Check the tablet." >&2
  exit 1
fi
SERIAL=$(bike_serial)

home_activity() {
  bike shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r'
}

# setup.sh builds while it waits for the tablet, then skips the build here.
[[ -n "${PELOJACK_SKIP_BUILD:-}" ]] || build_apk
bike install -r "$APK"

current=$(home_activity)
if [[ "$current" != "$APP_ID/"* ]]; then
  # Remembered so bike-restore.sh can put the original home screen back.
  echo "$current" > "$STATE"
  bike shell cmd package set-home-activity "$APP_ID/.MainActivity"
fi
# Music controls read other apps' media sessions, which needs notification access.
bike shell cmd notification allow_listener "$APP_ID/$APP_ID.media.MediaListener" || true
# Third-party app overlays can keep a ride active while another app is in front.
bike shell appops set "$APP_ID" SYSTEM_ALERT_WINDOW allow || true
# Bluetooth scanning for heart rate monitors; which permission applies depends on the Android version.
for permission in ACCESS_FINE_LOCATION BLUETOOTH_SCAN BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE; do
  bike shell pm grant "$APP_ID" "android.permission.$permission" 2>/dev/null || true
done
# Android 11 and older only return Bluetooth scan results (heart rate straps) with Location on.
if [[ $(bike shell getprop ro.build.version.sdk | tr -d '\r') -lt 31 ]]; then
  bike shell settings put secure location_mode 3 2>/dev/null || true
fi
bike shell am start -n "$APP_ID/.MainActivity" >/dev/null

echo "Installed. Home screen is now: $(home_activity)"
