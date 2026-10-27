# Apple Watch Heart Rate on a Peloton Bike+: Your Options

> Three ways to get Apple Watch heart rate onto a Peloton Bike+: Peloton's One-Tap tracking, a Bluetooth or ANT+ strap, or the open-source Pelojack app.

Web page: https://jackwallner.com/ios/pelojack/apple-watch-heart-rate-peloton-bike-plus.html

## How to use Apple Watch heart rate with a Peloton Bike+

By [Jack Wallner](https://jackwallner.com/) · Last updated October 27, 2026

One-Tap Apple Watch Tracking is Peloton's own feature that connects your Apple Watch to a class on the Bike+: set up the Apple Watch option in the Peloton iPhone app, start a class on the Bike+, and tap Connect on the notification that appears on your watch. It requires an All-Access Membership. The Bike+ also accepts Bluetooth and ANT+ heart rate monitors. A third option is Pelojack, an independent open-source app that sends Apple Watch heart rate to the bike's tablet without a Peloton account.

## What are the options for heart rate on a Peloton Bike+?

There are three: Peloton's One-Tap Apple Watch Tracking, a Bluetooth or ANT+ heart rate monitor, and the open-source Pelojack app.

| Option | Peloton membership | Where heart rate shows | Setup |
| --- | --- | --- | --- |
| One-Tap Apple Watch Tracking | All-Access Membership required | Heart rate and zone on your Apple Watch | Peloton iPhone app, then tap Connect on the watch |
| Bluetooth or ANT+ heart rate monitor | Not stated on Peloton's pairing page | Top left of the Bike+ touchscreen | Turn the monitor on before the class |
| Pelojack (independent, open source) | No Peloton account needed for core rides | Pelojack's ride screen on the bike tablet | USB install from a Mac, Watch app built in Xcode |

## How do you set up Peloton's One-Tap Apple Watch Tracking?

Set up the Apple Watch option in the Peloton iPhone app, start a class on the Bike+, then tap Connect on your watch. Peloton's support pages say One-Tap Apple Watch Tracking is available to members with an All-Access Membership and a compatible Apple Watch, and it covers the Bike, Bike+, Tread, Tread+, Row and Guide. To set it up, Peloton lists these steps:

1. Download the Peloton app on your iPhone.
2. Open the You tab, open the menu in the top right and choose Apple Watch, then tap Set Up.
3. Accept the permission prompts, including connecting to the Apple Health app with all permissions turned on.
4. Install the Peloton Watch app on your Apple Watch. It installs automatically if automatic app install is on.

To use it, turn off low power mode on the iPhone and the watch first, because Peloton says interruptions to the workout summary metrics can occur otherwise. Pick a class on the Bike+. You do not need to open the Peloton app on the watch beforehand. When the class begins you get a push notification on the watch, and you tap Connect. If no notification arrives, open the Peloton Watch app and Peloton says it connects to your current class within 30 seconds. During the class your heart rate and heart rate zone appear on the watch.

Two details from Peloton that people miss. Workouts can only be started from the bike or the iPhone app, not from the watch app. And tapping End on the watch does not end the Peloton workout, so end it on the bike.

## Does the Bike+ work with a Bluetooth or ANT+ heart rate monitor?

Yes. Peloton says its Bikes and Treads are compatible with both Bluetooth and ANT+ devices. If your monitor is ANT+ compatible, Peloton says your heart rate automatically appears in the top left of the touchscreen, and the monitor must be on before you start the class. Peloton also sells its own heart rate band. This path does not use the Apple Watch.

Third-party apps that broadcast Apple Watch heart rate as a standard Bluetooth sensor also exist. They are not part of Peloton's documentation, so this guide does not cover them.

## What is Pelojack?

Pelojack is an unofficial, open-source ride app that runs on the Bike+ tablet as the tablet's home screen, and a restore script returns the previous one. It has its own workouts, resistance control and live ride metrics. Its Apple Watch companion reads your heart rate and sends it directly to the bike's tablet over Bluetooth. The iPhone is used to install the Watch app and is not part of the live connection.

The trade-offs are real, so read them before choosing it:

- **It is not Peloton's class library.** Pelojack runs its own workouts, so you ride Pelojack workouts instead of Peloton classes.
- **Installation is technical.** You enable USB debugging on the tablet, connect it to a Mac and run a setup script. The Apple Watch app is built in Xcode with your own Apple development team, because the project is distributed as source.
- **It can break.** Pelojack uses the tablet's local bike service, and the project says Peloton firmware updates can change that service and may break compatibility.
- **Use it only on a bike you own or are authorized to configure.** The app can change resistance, so keep the bike's stop controls within reach.

On the plus side, core rides need no Peloton account or cloud API, ride history stays on the tablet, and the code is MIT licensed on GitHub so you can read what it does.

## Which option should you pick?

If you take Peloton classes and have an All-Access Membership, One-Tap is the simplest and the one Peloton documents. If you have a heart rate strap, the Bike+ will take it directly. If you ride your own structured workouts, want power-target resistance control without a Peloton login, and are comfortable installing from a Mac, Pelojack gives you Apple Watch heart rate on that screen.

## What if heart rate does not show up?

Peloton's pages give three quick fixes:

- No notification on the watch: open the Peloton Watch app, and it should join the class within 30 seconds.
- Odd or missing summary metrics: turn off low power mode on both the iPhone and the watch.
- Strap shows nothing: turn it on before the class, and keep the sensors lightly wet if it is a chest strap.

## Pelojack: heart rate from the Watch

Pelojack is a free, independent project with no affiliation to Peloton. It is not made, approved or supported by Peloton, and Peloton and Bike+ are trademarks of Peloton Interactive, Inc. The Watch companion sends your heart rate straight to the tablet, and other compatible heart rate straps can be paired too. Ride history stays on the tablet, with no Pelojack cloud account. [See the Pelojack page](https://jackwallner.com/ios/pelojack/) for features and setup, or [read the source on GitHub](https://github.com/jackwallner/pelojack).

## Frequently asked questions

### Can an Apple Watch show heart rate on a Peloton Bike+?

Yes, through Peloton's One-Tap Apple Watch Tracking, which Peloton says is available to All-Access Members with a compatible Apple Watch. Start a class on the bike and tap Connect on the notification that appears on the watch. Pelojack, an independent app, offers another route.

### Do I need an All-Access Membership to use Apple Watch with the Bike+?

Yes, for Peloton's One-Tap Apple Watch Tracking. Peloton's support page says it is available to Members with an All-Access Membership. Pelojack's core rides do not need a Peloton account, but they run Pelojack workouts rather than Peloton classes.

### What if the Connect notification does not appear on my watch?

Open the Peloton Watch app on the Apple Watch. Peloton says it connects to your current class automatically within 30 seconds. Also turn off low power mode on the iPhone and the watch before starting.

### Does the Peloton Bike+ work with Bluetooth heart rate straps?

Yes. Peloton says its Bikes and Treads are compatible with both Bluetooth and ANT+ devices. The heart rate appears in the top left of the touchscreen once the monitor is on and detected, and the monitor must be on before the class starts.

### Is Pelojack made by Peloton?

No. Pelojack is independent and is not made, approved or supported by Peloton. Peloton and Bike+ are trademarks of Peloton Interactive, Inc.

## Sources

1. [Peloton Software: Apple Watch Compatibility & Set Up](https://support.onepeloton.com/s/article/Peloton-Software-Apple-Watch-Compatibility-Set-Up?language=en_US), Peloton Support
2. [Peloton App: Using Your Apple Watch For Peloton Classes](https://support.onepeloton.com/s/article/15240361174292-Using-Your-Apple-Watch-For-Peloton-Classes?language=en_US), Peloton Support
3. [Pairing A Heart Rate Monitor With Peloton Bikes and Treads](https://support.onepeloton.com/s/article/203418895-Pairing-A-Heart-Rate-Monitor-With-Peloton-Bikes-and-Treads?language=en_US), Peloton Support
4. [Pelojack README (compatibility, install and Apple Watch notes)](https://github.com/jackwallner/pelojack), GitHub

Peloton details come from Peloton Support pages read on October 12, 2026 and may change with software updates. Check Peloton Support for current steps. Pelojack is independent and not affiliated with Peloton.
