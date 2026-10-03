#!/usr/bin/env bash
# Everything after plugging the bike in, in one command.
#
#   scripts/setup.sh             Plug the bike's tablet into this Mac over USB, then run this.
#
# It builds Pelojack while it waits for the tablet, checks the tablet is a Bike+, installs the app,
# makes it the home screen, grants its permissions, turns on Bluetooth and Location, and confirms
# the app reached the bike service.
# Safe to run again: it reinstalls over the top and keeps your rides.
source "$(dirname "$0")/lib.sh"
SCRIPTS="$ROOT/scripts"

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
ok() { printf '    ok: %s\n' "$*"; }
warn() { printf '    \033[33mwarning:\033[0m %s\n' "$*"; WARNINGS+=("$*"); }
die() { printf '\n\033[31mSetup stopped:\033[0m %s\n' "$*" >&2; exit 1; }
WARNINGS=()

# Prints the tablet's serial once it is attached and authorized, saying what to do meanwhile.
# A USB serial wins over a Wi-Fi one, since both can be the same tablet.
wait_for_bike() {
  local said="" deadline=$((SECONDS + 600)) offline=0 line serial state
  while ((SECONDS < deadline)); do
    if [[ -n "${PELOJACK_SERIAL:-}" ]]; then
      line=$(adb devices | awk -v s="$PELOJACK_SERIAL" 'NR > 1 && $1 == s { print $1, $2 }')
    else
      line=$(adb devices | awk 'NR > 1 && NF >= 2 && $1 !~ /^emulator-/ { print ($1 ~ /:/ ? 1 : 0), $1, $2 }' | sort -n | head -1 | cut -d' ' -f2-)
    fi
    serial=${line%% *}
    state=${line#* }
    case "$state" in
      device) echo "$serial"; return ;;
      unauthorized) [[ $said == auth ]] || echo "    On the bike's screen: tick \"Always allow from this computer\" and tap Allow." >&2; said=auth ;;
      # Briefly offline is normal while the tablet connects; only a stuck one needs a replug.
      offline)
        offline=$((offline + 1))
        if ((offline == 8)); then echo "    The tablet is stuck offline. Unplug the cable and plug it back in." >&2; fi
        ;;
      *) [[ $said == plug ]] || echo "    Waiting for the tablet. Plug its USB-C port into this Mac (USB debugging on)." >&2; said=plug ;;
    esac
    sleep 2
  done
  die "no tablet showed up in 10 minutes."
}

step "Building Pelojack in the background (a few minutes)"
BUILD_LOG=$(mktemp -t pelojack-build)
build_apk >"$BUILD_LOG" 2>&1 &
BUILD_PID=$!
trap 'kill "$BUILD_PID" 2>/dev/null || true' EXIT

step "Waiting for the tablet"
adb start-server >/dev/null 2>&1
SERIAL=$(wait_for_bike)
export PELOJACK_SERIAL=$SERIAL
ok "connected to $SERIAL"
# A tablet that has just been switched on shows up before Android has finished starting.
for _ in $(seq 1 90); do
  [[ $(bike shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') == 1 ]] && break
  sleep 2
done
EMULATOR=false
[[ $SERIAL == emulator-* ]] && EMULATOR=true

step "Checking the tablet"
"$SCRIPTS/bike-check.sh" | sed 's/^/    /'
if bike shell pm path com.onepeloton.affernetservice >/dev/null 2>&1; then
  ok "bike service is there"
elif $EMULATOR; then
  warn "emulator: no bike service, Pelojack will use the simulated bike"
else
  die "this tablet has no Peloton bike service. Is it a Bike+ tablet?"
fi
MODEL=$(bike shell getprop ro.product.model | tr -d '\r')
[[ $MODEL == PLTN-TTR01 ]] || $EMULATOR || warn "model is $MODEL, not PLTN-TTR01 (Bike+). Resistance control is only known to work on the Bike+."

step "Finishing the build"
if ! wait "$BUILD_PID"; then
  tail -40 "$BUILD_LOG" >&2
  die "the build failed (full log: $BUILD_LOG)."
fi
ok "built $(basename "$APK")"

step "Installing and making Pelojack the home screen"
PELOJACK_SKIP_BUILD=1 "$SCRIPTS/bike-install.sh" | sed 's/^/    /'

step "Bluetooth"
if [[ $(bike shell settings get global bluetooth_on | tr -d '\r') != 1 ]]; then
  bike shell cmd bluetooth_manager enable >/dev/null 2>&1 || bike shell svc bluetooth enable >/dev/null 2>&1 || true
  sleep 3
fi
if [[ $(bike shell settings get global bluetooth_on | tr -d '\r') == 1 ]]; then
  ok "Bluetooth is on"
else
  warn "Bluetooth is off. Turn it on in Android settings for heart rate and the Apple Watch."
fi

step "Checking Pelojack on the tablet"
bike logcat -c 2>/dev/null || true
bike shell am force-stop "$APP_ID"
bike shell am start -W -n "$APP_ID/.MainActivity" >/dev/null
bound=false
for _ in $(seq 1 15); do
  if bike shell dumpsys activity services com.onepeloton.affernetservice 2>/dev/null | grep -q "$APP_ID"; then
    bound=true
    break
  fi
  sleep 1
done
if bike shell pidof "$APP_ID" >/dev/null 2>&1; then ok "Pelojack is running"; else warn "Pelojack is not running"; fi
if bike logcat -d -b crash 2>/dev/null | grep -q "$APP_ID"; then
  bike logcat -d -b crash | grep -A12 "$APP_ID" | head -20 | sed 's/^/    /'
  warn "Pelojack crashed on start (log above)."
fi
if $bound; then
  ok "connected to the bike service"
elif ! $EMULATOR; then
  warn "Pelojack has not connected to the bike service. Open Settings > Bike and tablet > Details on the tablet for the reason."
fi

if ! $EMULATOR; then
  step "Optional cable-free updates"
  echo "Run scripts/bike-wifi.sh enable for ADB over Wi-Fi until the tablet restarts."
  echo "For restart-persistent updates, review the local-network dev link, then run scripts/bike-link.sh."
fi

echo
if ((${#WARNINGS[@]})); then
  printf '\033[33mSet up, with %d warning(s):\033[0m\n' "${#WARNINGS[@]}"
  printf '  - %s\n' "${WARNINGS[@]}"
else
  printf '\033[32mDone.\033[0m The bike now boots into Pelojack. You can unplug it.\n'
fi
