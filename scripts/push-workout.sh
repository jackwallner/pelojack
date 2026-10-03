#!/usr/bin/env bash
# Copies workout JSON files to the tablet. They appear the next time Pelojack comes to the front.
# Usage: push-workout.sh workout.json [more.json ...]
source "$(dirname "$0")/lib.sh"
[[ $# -gt 0 ]] || { echo "Usage: $0 workout.json [more.json ...]" >&2; exit 1; }
ROUTE=$(bike_route)
[[ $ROUTE == adb ]] && SERIAL=$(bike_serial)
DEST=/sdcard/Android/data/$APP_ID/files/workouts

for file in "$@"; do
  python3 -c 'import json, sys; w = json.load(open(sys.argv[1])); assert w["id"] and w["name"] and w["segments"]' "$file"
  if [[ $ROUTE == adb ]]; then bike push "$file" "$DEST/$(basename "$file")" >/dev/null; else link_put workouts "$file"; fi
  echo "Pushed $(basename "$file")"
done
