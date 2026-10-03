# Pelojack

Pelojack is an independent ride app for owners of the Peloton Bike+. It runs on the bike's Android tablet and pairs with an Apple Watch companion.

It supports custom workouts, power targets, resistance control, live ride metrics, local ride history, heart rate from Bluetooth straps or Apple Watch, music controls, video rides, and optional in-ride app or camera overlays. The built-in workouts are original Pelojack files. Core bike control uses the tablet's local bike service and does not require a Peloton account or cloud API.

Pelojack is not made, approved, or supported by Peloton. Peloton and Bike+ are trademarks of Peloton Interactive, Inc.

## Compatibility

- Designed for the Bike+ tablet, model `PLTN-TTR01`.
- Tested on Android 10. Peloton firmware updates can change the local bike service and may break compatibility.
- The app can change resistance. Use it only on a bike you own or are authorized to configure, keep the bike's stop controls accessible, and supervise rides.
- YouTube, Spotify, Nanit, and other integrated services require your own access and are subject to their terms.

## Install on Android

The setup script is for macOS and requires JDK 17, the Android SDK, and `adb` from Android Platform Tools. Set `ANDROID_HOME` if the SDK is not at the default macOS location.

1. On the bike, open **Settings > About tablet** and tap the build number seven times.
2. In **Developer options**, enable USB debugging.
3. Connect the bike tablet to your Mac over USB. Approve the computer on the tablet.
4. From the repository root, run:

   ```sh
   scripts/setup.sh
   ```

The script builds Pelojack, installs it, makes it the home screen, grants the permissions it uses, and checks that it can reach the bike service. This changes the tablet's default home app. To restore the previous home app, run `scripts/bike-restore.sh`. Add `--remove` to release Pelojack's device-owner role, if enabled, and uninstall it.

The basic setup does not enable wireless debugging or start the maintenance server. For ADB over Wi-Fi until the tablet restarts, run `scripts/bike-wifi.sh enable` while connected over USB.

For updates that work after a restart, `scripts/bike-link.sh` is available as an opt-in. It makes Pelojack the Android device owner and starts a token-authenticated HTTP maintenance server on the home network, port 8765. The server can install APKs, transfer workout and video files, and reboot the tablet. Use it only on a trusted local network, do not expose the port to the internet, and review the implementation before enabling it. `scripts/bike-restore.sh --remove` releases device-owner privileges and uninstalls Pelojack.

To install a workout file after setup, run:

```sh
scripts/push-workout.sh path/to/workout.json
```

You can also create and edit workouts in the app. The workout library is stored on the tablet.
Optional ride overlays can open other apps already installed on the tablet. To install an app you trust, use `scripts/bike-app.sh path/to/app.apk`. No third-party APKs are included.

## Apple Watch

The Apple Watch app reads heart rate and sends it directly to the bike over Bluetooth. The iPhone carries the Watch app during installation but is not in the live bike connection.

To build it, install Xcode and XcodeGen, then run:

```sh
cd ios
xcodegen generate
```

Set your own Apple development team and bundle identifiers in `project.yml`, then build and install the iPhone and Watch apps from Xcode.

## Development

Android build and unit-test command:

```sh
cd android
./gradlew testDebugUnitTest assembleDebug
```

To regenerate the iOS project after changing `project.yml` or adding/removing Swift files:

```sh
cd ios
xcodegen generate
```

## License and third-party material

Pelojack's original code is licensed under the MIT License. Third-party files keep their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `android/app/FONT-LICENSE.txt`.
