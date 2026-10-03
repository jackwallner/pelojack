package com.jackwallner.pelojack.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.BuildConfig
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.bike.BikeLink
import com.jackwallner.pelojack.hr.HeartRateLink
import com.jackwallner.pelojack.hr.WatchState
import com.jackwallner.pelojack.hr.watchPermissions
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.Divider
import com.jackwallner.pelojack.ui.Dot
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.Route
import com.jackwallner.pelojack.ui.StepperRow
import com.jackwallner.pelojack.ui.ToggleRow
import com.jackwallner.pelojack.ui.Type

private const val SPOTIFY = "com.spotify.music"

@Composable
fun SettingsScreen(app: PelojackApp, nav: Navigator) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val bikeLink by app.bike.link.collectAsStateWithLifecycle()
    val heartLink by app.heartRate.link.collectAsStateWithLifecycle()
    val bpm by app.heartRate.bpm.collectAsStateWithLifecycle()
    val musicAccess by app.nowPlaying.hasAccess.collectAsStateWithLifecycle()
    val update = app.settings::update

    Page("Settings") {
        Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Group("Rider") {
                    androidx.compose.foundation.layout.Box(Modifier.padding(vertical = 10.dp)) {
                        Field("Name", settings.name, { name -> update { it.copy(name = name) } }, "Your name")
                    }
                    StepperRow("FTP", "Functional threshold power. Sets your power zones.", settings.ftp, { v -> update { it.copy(ftp = v) } }, 50..500, step = 5, format = { "$it W" })
                    StepperRow("Max heart rate", null, settings.maxHeartRate, { v -> update { it.copy(maxHeartRate = v) } }, 120..220, format = { "$it bpm" })
                    StepperRow(
                        "Weight",
                        null,
                        settings.weightLb,
                        { v -> update { it.copy(weightLb = v) } },
                        80..400,
                        format = { if (settings.metric) "${(it * 0.4536).toInt()} kg" else "$it lb" },
                    )
                    StepperRow("Weekly goal", null, settings.weeklyGoal, { v -> update { it.copy(weeklyGoal = v) } }, 1..7, format = { "$it rides" })
                    ToggleRow("Metric units", "Kilometres and kilograms", settings.metric) { v -> update { it.copy(metric = v) } }
                }
                Group("Riding") {
                    ToggleRow("Auto-follow", "Move the resistance to each segment's target", settings.autoFollow) { v -> update { it.copy(autoFollow = v) } }
                    ToggleRow("ERG mode", "Hold power targets by adjusting resistance for you", settings.erg) { v -> update { it.copy(erg = v) } }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Group("Heart rate") {
                    WatchRow(app)
                    Divider()
                    StatusRow(
                        title = settings.heartRateName ?: if (app.heartRate.simulated) "Simulated heart rate" else "Heart rate strap",
                        detail = when {
                            app.heartRate.simulated -> "Off the bike, heart rate follows the simulated effort"
                            heartLink == HeartRateLink.Connected -> "Connected${bpm?.let { "  ·  $it bpm" } ?: ""}"
                            heartLink == HeartRateLink.Searching -> "Looking for it"
                            else -> "Any Bluetooth chest strap or arm band. The watch is used first when both send."
                        },
                        color = if (heartLink == HeartRateLink.Connected) Palette.Good else Palette.Faint,
                    ) {
                        if (app.bluetoothHeartRate != null) {
                            QuietButton(if (settings.heartRateAddress == null) "Pair" else "Change", { nav.push(Route.HeartRatePairing) })
                        }
                    }
                }
                Group("Music") {
                    StatusRow(
                        title = if (musicAccess) "Music controls on" else "Music controls off",
                        detail = "Shows what Spotify or any music app is playing during rides, with play and skip",
                        color = if (musicAccess) Palette.Good else Palette.Faint,
                    ) {
                        if (!musicAccess) {
                            QuietButton("Allow", { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
                        }
                    }
                    context.packageManager.getLaunchIntentForPackage(SPOTIFY)?.let { spotify ->
                        Divider()
                        StatusRow("Spotify", "Installed", Palette.Good) { QuietButton("Open", { context.startActivity(spotify) }) }
                    }
                }
                Group("Bike and tablet") {
                    ToggleRow("Hide Android bar", "Swipe from the bottom edge to show it temporarily", settings.hideSystemBars) { v ->
                        update { it.copy(hideSystemBars = v) }
                    }
                    Divider()
                    StatusRow(
                        title = app.bike.label,
                        detail = when (bikeLink) {
                            BikeLink.Live -> "Connected"
                            BikeLink.Connecting -> "Connecting"
                            BikeLink.Unavailable -> "Not found"
                        },
                        color = if (bikeLink == BikeLink.Live) Palette.Good else Palette.Bad,
                    ) { QuietButton("Details", { nav.push(Route.Bike) }) }
                    Divider()
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        QuietButton("Android settings", { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) })
                        QuietButton("Apps", { nav.select(Route.Apps) })
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Pelojack ${BuildConfig.VERSION_NAME}. Workouts folder: ${app.library.workoutsDir?.path ?: "unavailable"}", style = Type.Small)
                }
            }
        }
    }
}

@Composable
private fun WatchRow(app: PelojackApp) {
    val state by app.watch.state.collectAsStateWithLifecycle()
    val bpm by app.watch.bpm.collectAsStateWithLifecycle()
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { app.watch.start() }
    StatusRow(
        title = "Apple Watch",
        detail = when (state) {
            WatchState.Connected -> "Connected" + (bpm?.let { "  ·  $it bpm" } ?: "  ·  starts sending when your ride starts")
            WatchState.Waiting -> "Open Pelojack on your watch near the bike. It connects on its own."
            WatchState.NeedsPermission -> "Allow Bluetooth so your watch can find the bike"
            WatchState.BluetoothOff -> "Turn on Bluetooth in Android settings"
            WatchState.Unsupported -> "This tablet cannot advertise over Bluetooth"
        },
        color = if (state == WatchState.Connected) Palette.Good else Palette.Faint,
    ) {
        if (state == WatchState.NeedsPermission) QuietButton("Allow", { request.launch(watchPermissions) })
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Text(title, style = Type.Heading)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun StatusRow(title: String, detail: String, color: androidx.compose.ui.graphics.Color, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body)
            Text(detail, style = Type.Small)
        }
        Spacer(Modifier.width(16.dp))
        action()
    }
}
