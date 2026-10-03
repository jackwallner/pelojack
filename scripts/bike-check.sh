#!/usr/bin/env bash
# Read-only report on the attached tablet: what it is and whether Pelojack can reach the bike.
source "$(dirname "$0")/lib.sh"
SERIAL=$(bike_serial)

prop() { bike shell getprop "$1" | tr -d '\r'; }

echo "Tablet:        $(prop ro.product.brand) $(prop ro.product.model)"
echo "Android:       $(prop ro.build.version.release) (API $(prop ro.build.version.sdk))"
echo "Build:         $(prop ro.build.display.id)"
echo "Screen:        $(bike shell wm size | tr -d '\r' | sed 's/Physical size: //'), $(bike shell wm density | tr -d '\r' | sed 's/Physical density: //') dpi"
echo "Home screen:   $(bike shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')"
echo
if bike shell pm path com.onepeloton.affernetservice >/dev/null 2>&1; then
  echo "Bike service:  installed (resistance control is possible)"
else
  echo "Bike service:  MISSING. This is not a Bike+, or Peloton renamed the service."
fi
if bike shell pm path "$APP_ID" >/dev/null 2>&1; then
  echo "Pelojack:      installed"
else
  echo "Pelojack:      not installed"
fi
echo
echo "Peloton packages:"
packages=$(bike shell pm list packages | tr -d '\r' | sed 's/^package://' | grep -i peloton | sort || true)
sed 's/^/  /' <<<"${packages:-none}"
