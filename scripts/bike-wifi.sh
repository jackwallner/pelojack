#!/usr/bin/env bash
# Reaches the tablet over Wi-Fi so the other scripts work without a cable.
#
#   bike-wifi.sh pair <ip:pairing-port> <code>   First time. Values come from the tablet:
#                                                Developer options > Wireless debugging >
#                                                Pair device with pairing code.
#   bike-wifi.sh connect [ip:port]               Every time after. With no address, the tablet
#                                                is found on the local network, or the last
#                                                address used is tried.
#   bike-wifi.sh enable                          For tablets with no Wireless debugging option:
#                                                run once with a USB cable attached.
source "$(dirname "$0")/lib.sh"

# Address of a tablet advertising wireless debugging on this network, if any.
discover() {
  adb mdns services 2>/dev/null | awk '/_adb-tls-connect/ { print $NF; exit }'
}

connect() {
  local address=${1:-$(discover)}
  # A tablet switched with `adb tcpip` does not advertise itself; try where it was last time.
  [[ -n "$address" || ! -s "$ADDRESS_FILE" ]] || address="$(cat "$ADDRESS_FILE"):5555"
  if [[ -z "$address" ]]; then
    echo "No tablet found on the network. Pass the IP address and port shown under Wireless debugging." >&2
    exit 1
  fi
  # macOS can block a long-running adb server from the local network ("No route to host") while
  # other tools reach the tablet fine; a fresh server started from this shell gets through. The
  # tablet's adb also needs a moment after switching to Wi-Fi.
  local attempt
  for attempt in 1 2 3 4 5; do
    adb connect "$address" >/dev/null 2>&1 || true
    if adb devices | awk -v a="$address" '$1 == a && $2 == "device" { found = 1 } END { exit !found }'; then
      # Remembered for the next connect and for Pelojack's dev link.
      echo "${address%:*}" > "$ADDRESS_FILE"
      echo "Connected to $address"
      return
    fi
    if [[ $attempt == 2 ]]; then
      adb kill-server >/dev/null 2>&1 || true
      adb start-server >/dev/null 2>&1
    fi
    sleep 3
  done
  echo "Could not connect to $address over Wi-Fi." >&2
  exit 1
}

case "${1:-}" in
  pair)
    [[ $# -eq 3 ]] || { echo "Usage: $0 pair <ip:pairing-port> <code>" >&2; exit 1; }
    adb pair "$2" "$3"
    connect
    ;;
  connect)
    connect "${2:-}"
    ;;
  enable)
    SERIAL=$(bike_serial)
    ip=$(bike shell ip -f inet addr show wlan0 | awk '/inet / { sub(/\/.*/, "", $2); print $2 }')
    [[ -n "$ip" ]] || { echo "The tablet is not on Wi-Fi." >&2; exit 1; }
    echo "$ip" > "$ADDRESS_FILE"
    if [[ $(bike shell getprop ro.build.version.sdk | tr -d '\r') -ge 30 ]]; then
      # Survives reboots, but the port changes; `connect` finds it again.
      bike shell settings put global adb_wifi_enabled 1
      sleep 3
    fi
    if [[ -z "$(discover)" ]]; then
      # Older Android, or the setting was ignored. This lasts until the tablet restarts.
      bike tcpip 5555
      sleep 3
      connect "$ip:5555"
    else
      connect
    fi
    echo "Unplug the cable. If two devices are listed below, that is the same tablet twice."
    adb devices
    ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
