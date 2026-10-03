#!/usr/bin/env bash
# Opts in to Pelojack's dev link, so the Mac can update the bike after the tablet restarts (when adb
# over Wi-Fi is gone). Needs adb once (USB or Wi-Fi). Safe to run again.
#
# It makes Pelojack device owner (installs then need no tap on the tablet), gives the app a token
# kept in ~/.pelojack-token, remembers the tablet's address, and checks the link answers.
source "$(dirname "$0")/lib.sh"
SERIAL=$(bike_serial)

if [[ ! -s "$LINK_TOKEN_FILE" ]]; then
  (umask 077 && openssl rand -hex 24 > "$LINK_TOKEN_FILE")
fi
bike shell am start -n "$APP_ID/.MainActivity" --es devlink-token "$(cat "$LINK_TOKEN_FILE")" >/dev/null

if bike shell dumpsys device_policy | grep -q "admin=ComponentInfo{$APP_ID/"; then
  echo "Pelojack is device owner."
else
  # Refused if the tablet has an account or another owner; the link then asks for a tap per install.
  if bike shell dpm set-device-owner "$APP_ID/.devlink.OwnerReceiver" 2>&1 | grep -q "Success"; then
    echo "Pelojack is now device owner."
  else
    echo "Could not make Pelojack device owner; installs over the link will ask for a tap on the tablet." >&2
  fi
fi

ip=$(bike shell ip -f inet addr show wlan0 | awk '/inet / { sub(/\/.*/, "", $2); print $2 }')
[[ -n "$ip" ]] || { echo "The tablet is not on Wi-Fi, so the dev link cannot work." >&2; exit 1; }
echo "$ip" > "$ADDRESS_FILE"
for _ in 1 2 3 4 5; do
  if reply=$(LINK_TIMEOUT=4 link GET /ping 2>/dev/null); then
    echo "Dev link answers at $ip:$LINK_PORT: $reply"
    exit 0
  fi
  sleep 2
done
echo "The dev link at $ip:$LINK_PORT does not answer." >&2
exit 1
