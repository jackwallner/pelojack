# Shared by the bike scripts. Source it; do not run it.
set -euo pipefail

APP_ID=com.jackwallner.pelojack
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"

# Use the SDK's adb when none is on the PATH.
if ! command -v adb >/dev/null && [[ -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  PATH="$ANDROID_HOME/platform-tools:$PATH"
fi
command -v adb >/dev/null || { echo "adb not found. Install it with: brew install android-platform-tools" >&2; exit 1; }

build_apk() { (cd "$ROOT/android" && ./gradlew --no-daemon -q assembleDebug); }

# The tablet's adb serial: $PELOJACK_SERIAL, or the only attached device that is not an emulator.
bike_serial() {
  if [[ -n "${PELOJACK_SERIAL:-}" ]]; then
    echo "$PELOJACK_SERIAL"
    return
  fi
  local serials
  serials=$(adb devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }')
  if [[ -z "$serials" ]]; then
    echo "No tablet found. Connect over Wi-Fi with bike-wifi.sh, or plug the bike in over USB with USB debugging on." >&2
    exit 1
  fi
  if [[ $(wc -l <<<"$serials") -gt 1 ]]; then
    # USB and Wi-Fi at once is usually the same tablet listed twice; prefer the cable.
    local ids
    ids=$(while read -r s; do adb -s "$s" shell getprop ro.serialno </dev/null 2>/dev/null | tr -d '\r'; done <<<"$serials" | sort -u)
    if [[ $(wc -l <<<"$ids") -gt 1 ]]; then
      echo "More than one device attached. Set PELOJACK_SERIAL to the tablet's serial." >&2
      exit 1
    fi
    serials=$(awk '!/:/ { print; found = 1; exit } { first = first ? first : $0 } END { if (!found) print first }' <<<"$serials")
  fi
  echo "$serials"
}

bike() { adb -s "$SERIAL" "$@"; }

# The dev link: Pelojack's own server on the home network, for when adb is gone after a restart.
LINK_PORT=8765
LINK_TOKEN_FILE="$HOME/.pelojack-token"
ADDRESS_FILE="$ROOT/.bike-address"

# True when adb can reach the tablet (USB or Wi-Fi), without printing anything.
have_adb_tablet() {
  [[ -n "${PELOJACK_SERIAL:-}" ]] && return 0
  adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { found = 1 } END { exit !found }'
}

link() {
  local method=$1 path=$2
  shift 2
  curl -sS --fail-with-body -m "${LINK_TIMEOUT:-600}" -X "$method" \
    -H "X-Pelojack-Token: $(cat "$LINK_TOKEN_FILE")" "http://$(cat "$ADDRESS_FILE"):$LINK_PORT$path" "$@"
}

link_ok() {
  [[ -s "$LINK_TOKEN_FILE" ]] || return 1
  [[ -s "$ADDRESS_FILE" ]] && LINK_TIMEOUT=4 link GET /ping >/dev/null 2>&1 && return 0
  link_find
}

# The tablet may get a new address after a restart: look for the dev link on this Mac's /24.
link_find() {
  local mine prefix found
  mine=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null) || return 1
  prefix=${mine%.*}
  found=$(seq 1 254 | xargs -P 64 -I{} sh -c 'curl -s -o /dev/null -w "%{http_code} {}\n" -m 1 "http://$0.{}:$1/ping" -H "X-Pelojack-Token: $2"' \
    "$prefix" "$LINK_PORT" "$(cat "$LINK_TOKEN_FILE")" | awk '$1 == 200 { print $2; exit }')
  [[ -n "$found" ]] || return 1
  echo "$prefix.$found" > "$ADDRESS_FILE"
}

# adb when it can reach the tablet, otherwise the dev link. Prints "adb" or "link".
bike_route() {
  # PELOJACK_ROUTE=link forces the dev link, for testing it with the cable still in.
  if [[ -n "${PELOJACK_ROUTE:-}" ]]; then echo "$PELOJACK_ROUTE"; return; fi
  if have_adb_tablet; then echo adb; return; fi
  if link_ok; then echo link; return; fi
  echo "The tablet is not reachable over USB, adb Wi-Fi, or Pelojack's dev link. Is it on and on Wi-Fi?" >&2
  exit 1
}

# Installs APKs (a base plus any splits) through the dev link. Usage: link_install <package|-> <apk>...
link_install() {
  local package=$1 session apk
  shift
  session=$(link POST "/install/begin?package=${package/#-/}" | python3 -c 'import json, sys; print(json.load(sys.stdin)["session"])')
  for apk in "$@"; do
    link POST "/install/add?session=$session&name=$(basename "$apk")" -H 'Content-Type: application/octet-stream' --data-binary @"$apk" >/dev/null
  done
  link POST "/install/commit?session=$session"
  echo
}

# Copies a file into one of Pelojack's folders (workouts, videos, import) through the dev link.
link_put() {
  link PUT "/files/$1/$(basename "$2")" -H 'Content-Type: application/octet-stream' --data-binary @"$2" >/dev/null
}
