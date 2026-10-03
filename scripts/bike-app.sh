#!/usr/bin/env bash
# Installs or updates an APK or app bundle that you are authorized to use, keeping its data.
# Usage: bike-app.sh <file.apk | file.apkm | file.xapk | file.zip>
# Bundles (.apkm, .xapk, .zip) are unpacked and their split APKs installed together.
source "$(dirname "$0")/lib.sh"
[[ $# -eq 1 && -f "$1" ]] || { echo "Usage: $0 <file.apk|.apkm|.xapk|.zip>" >&2; exit 1; }
ROUTE=$(bike_route)
[[ $ROUTE == adb ]] && SERIAL=$(bike_serial)
FILE=$1

# Installs the given APKs together, over adb or the dev link.
install_apks() {
  if [[ $ROUTE == adb ]]; then
    bike install-multiple -r "$@"
  else
    link_install - "$@"
  fi
}

# The ABI of the tablet, for picking a bundle's native-code split.
tablet_abi() {
  if [[ $ROUTE == adb ]]; then bike shell getprop ro.product.cpu.abi | tr -d '\r'; else echo arm64-v8a; fi
}

case "$FILE" in
  *.apk)
    install_apks "$FILE"
    ;;
  *.apkm | *.xapk | *.zip)
    WORK=$(mktemp -d -t pelojack-app)
    trap 'rm -rf "$WORK"' EXIT
    unzip -q -o "$FILE" '*.apk' -d "$WORK"
    # A bundle carries splits for every ABI and density; the device rejects the ones it cannot use.
    abi=$(tablet_abi | tr - _)
    apks=()
    while IFS= read -r apk; do
      name=$(basename "$apk")
      if [[ $name == split_config.*_v* || $name == config.arm* || $name == config.x86* ]]; then
        [[ $name == *"$abi"* ]] || continue
      fi
      apks+=("$apk")
    done < <(find "$WORK" -name '*.apk' | sort)
    install_apks "${apks[@]}"
    ;;
  *)
    echo "Not an APK or APK bundle: $FILE" >&2
    exit 1
    ;;
esac
