#!/usr/bin/env bash
# Restarts the tablet and waits for Pelojack's authenticated Wi-Fi link to return.
source "$(dirname "$0")/lib.sh"
before=$(link GET /ping | python3 -c 'import json, sys; print(json.load(sys.stdin)["uptimeMillis"])')
link POST /reboot
echo
for _ in $(seq 1 60); do
  sleep 2
  now=$(LINK_TIMEOUT=3 link GET /ping 2>/dev/null | python3 -c 'import json, sys; print(json.load(sys.stdin)["uptimeMillis"])' 2>/dev/null) || continue
  if [[ "$now" -lt "$before" ]]; then echo "Restarted. Pelojack is reachable over Wi-Fi."; exit 0; fi
done
echo "Pelojack has not returned over Wi-Fi yet. Check the tablet." >&2
exit 1
