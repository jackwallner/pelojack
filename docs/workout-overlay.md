# Workout overlay over other apps

An existing ride can display its workout profile over another app already installed on the tablet. This is not tied to a particular video, browser or game app; an app that blocks Android overlays may still prevent it from appearing. Third-party apps and content are not included.

## Use and permissions

- Open a ride in Pelojack. **Watch in another app** opens Android's app chooser; the tablet's app switcher can also be used. Returning to Pelojack hides the overlay and restores the full ride UI.
- Android's **Display over other apps** permission must be granted by the user. **Watch in another app** can open the permission settings page; it does not grant permission. After granting it, return and repeat the action.
- **Minimize to overlay** requests the compact overlay, then opens the actual resolved default HOME app. It does not pick an arbitrary launcher or open a launcher chooser. It refuses to leave the controls if no ride is open, overlay permission is missing, a distinct default HOME cannot be resolved, ride protection cannot start, or the overlay cannot be created. A HOME launch failure removes the overlay and reports the failure.
- If Pelojack itself is the default HOME (including after the existing setup script), minimizing refuses with a message. Choose another default Home app yourself in Android Settings if desired; this feature never changes the default launcher, enables permissions, installs an app, or changes device-owner settings. The existing setup/restore scripts are separate and have their own side effects described in the README.

Switching apps and minimizing are presentation actions: they do not open a new ride, start or resume a waiting/paused ride, finish a ride, or issue resistance commands. An already running ride continues its existing recording and control behavior; the overlay is not a pause or emergency stop. The separate pre-existing **RuneScape** action retains its explicit ride-start behavior and is not the minimize action.

## Profile and controls

The expanded view shows the workout graph, current interval and time left, next interval and duration, available cadence/resistance/power targets, and live ride metrics. **Compact** keeps the graph, current targets and next targets side by side in a horizontal strip, with live cadence, resistance, watts, heart rate and ride time below. **Expand** restores the larger view. Free rides have no prescribed workout targets; missing heart rate is shown as a dash.

Drag the title/header to move the window within the display. **Controls** returns to Pelojack. Pause, finish and resistance controls remain in the full ride UI. The overlay is hidden while Pelojack is foregrounded and removed when the ride is finished/discarded. Foreground-service ride protection is application-owned, rather than owned by the launched third-party app. Android may still restrict or kill background work; this is not a guarantee of uninterrupted control. Keep the bike's physical stop controls accessible.

## Verification scope

From `android/`, with Java 17 and the Android SDK configured:

```sh
./gradlew testDebugUnitTest assembleDebug
```

JVM tests cover the visibility/protection policy, minimize action ordering and refusal paths, profile boundaries/targets, and compact sizing. Source-wiring guards check the UI and Android HOME resolver integration. They do not exercise the Android launcher, permission UI, window lifecycle or rendered Compose layout.

Manual checks on an authorized test tablet should cover:

1. Missing overlay permission: minimize stays in Pelojack with a useful message and does not change permission or ride state.
2. Pelojack as default HOME, no default HOME, and a distinct default HOME: refusal for the first two; compact overlay before leaving for the third. Do not silently change the launcher's default to make the test pass.
3. Switch to an installed app with a not-started, paused and running ride. Verify that no transition starts/resumes a ride; existing running behavior remains unchanged.
4. Compact/expand, header drag, long targets, current/next interval boundaries and return via **Controls**. Finish/discard should remove the overlay and protection.
5. Permission revocation or unavailable HOME: controls remain recoverable without new ride commands.

Record actual device/Android versions and observations separately from host test results. Do not publish account information, third-party media screenshots or host-specific paths as test evidence.
