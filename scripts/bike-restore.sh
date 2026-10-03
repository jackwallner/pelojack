#!/usr/bin/env bash
# Puts the tablet's original home screen back. Pelojack stays installed as a normal app.
#   bike-restore.sh            restore the home screen
#   bike-restore.sh --remove   also give back device owner and uninstall Pelojack
source "$(dirname "$0")/lib.sh"
SERIAL=$(bike_serial)
STATE="$ROOT/.bike-original-home"

if [[ ! -s "$STATE" ]]; then
  echo "No saved home screen. Pick one on the tablet under Settings > Apps > Default apps." >&2
  exit 1
fi
bike shell cmd package set-home-activity "$(cat "$STATE")"
bike shell input keyevent KEYCODE_HOME
echo "Home screen restored to $(cat "$STATE")"

if [[ "${1:-}" == "--remove" ]]; then
  # A device owner cannot be uninstalled until it gives the role back itself.
  [[ -s "$LINK_TOKEN_FILE" ]] && bike shell am start -n "$APP_ID/.MainActivity" --es release-owner "$(cat "$LINK_TOKEN_FILE")" >/dev/null
  sleep 2
  bike uninstall "$APP_ID"
fi
