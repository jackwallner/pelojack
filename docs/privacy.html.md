# Pelojack Privacy

> Privacy details for Pelojack, an independent open-source Bike+ ride app.

Web page: https://jackwallner.com/ios/pelojack/privacy.html

## Privacy

Last updated October 3, 2026

### Summary

Pelojack has no user accounts, advertising, analytics, or
developer-operated cloud backend. The app stores ride records and
workout files on the Bike+ tablet. Core bike control uses the
tablet's local bike service and does not send ride data to a
Pelojack server.

### Bike and ride data

The Android app reads bike telemetry and controls resistance
through the Bike+ tablet's local service. Ride summaries and
samples are stored in the app's private storage on that tablet.
Bluetooth heart-rate straps can send heart-rate readings to the
tablet during a ride.

### Apple Watch and HealthKit

The Watch companion sends heart rate directly to the Bike+ tablet
over Bluetooth. When you grant HealthKit permission, the Watch app
records the indoor cycling workout in Apple Health. Apple Health
data is handled by Apple under your device settings and Apple's
privacy terms.

### Third-party services

If you use video, music, or the optional Nanit camera viewer, the
selected service receives requests from your device. Those
services have their own privacy policies and account settings.
Pelojack does not send credentials or ride data to a Pelojack
server.

### Optional local maintenance server

The basic USB setup does not start the maintenance server. If you
run `scripts/bike-link.sh`, Pelojack becomes the
Android device owner and starts an HTTP server on your home
network at port 8765. A generated token is required. The server
can install APKs, transfer workout or video files, return app logs
and screenshots, and reboot the tablet. HTTP traffic is not
encrypted, so use it only on a trusted local network and never
expose the port to the internet. You can release device-owner
privileges and uninstall Pelojack with
`scripts/bike-restore.sh --remove`.

### Website and contact

This page is hosted by GitHub Pages, which may process ordinary
web request information under GitHub's privacy policy. For
questions or corrections, open an issue in the
[Pelojack repository](https://github.com/jackwallner/pelojack/issues).
