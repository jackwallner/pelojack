package com.jackwallner.pelojack.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.hr.HeartRateDevice
import com.jackwallner.pelojack.hr.bluetoothPermissions
import com.jackwallner.pelojack.hr.hasBluetoothPermissions
import com.jackwallner.pelojack.hr.scanNeedsLocation
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.EmptyState
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.Type

@Composable
fun HeartRatePairingScreen(app: PelojackApp, nav: Navigator) {
    val monitor = app.bluetoothHeartRate ?: run {
        nav.back()
        return
    }
    val context = LocalContext.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    var permitted by remember { mutableStateOf(context.hasBluetoothPermissions()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = context.hasBluetoothPermissions()
    }
    LaunchedEffect(Unit) { if (!permitted) request.launch(bluetoothPermissions) }
    // Rechecked whenever the screen comes back from Android settings.
    var needsLocation by remember { mutableStateOf(context.scanNeedsLocation()) }
    LifecycleResumeEffect(Unit) {
        needsLocation = context.scanNeedsLocation()
        onPauseOrDispose {}
    }
    val devices by remember(permitted, needsLocation) { monitor.scan() }.collectAsStateWithLifecycle(emptyList())

    Page(
        title = "Heart rate strap",
        subtitle = "Wake your strap or arm band. Your Apple Watch does not need pairing here.",
        actions = {
            if (settings.heartRateAddress != null) QuietButton("Forget ${settings.heartRateName ?: "monitor"}", { monitor.forget() })
            QuietButton("Done", nav::back)
        },
    ) {
        when {
            !permitted -> EmptyState("Bluetooth permission needed", "Pelojack uses Bluetooth only to find heart rate monitors.") {
                PrimaryButton("Allow Bluetooth", { request.launch(bluetoothPermissions) })
            }
            !monitor.available -> EmptyState("No Bluetooth", "This tablet did not report a Bluetooth adapter.")
            needsLocation -> EmptyState("Turn on Location", "This version of Android only finds Bluetooth monitors while Location is on.") {
                PrimaryButton("Open settings", { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Palette.Accent, strokeWidth = 3.dp)
                    Spacer(Modifier.width(18.dp))
                    Text("Searching", style = Type.BodyDim)
                }
                devices.forEach { device -> DeviceRow(device, device.address == settings.heartRateAddress) { monitor.choose(device); nav.back() } }
                if (devices.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Text("Nothing yet", style = Type.Heading)
                        Spacer(Modifier.height(8.dp))
                        Text("Chest straps and arm bands appear here once they are on and sensing a pulse.", style = Type.BodyDim)
                        Text("For your Apple Watch, just open Pelojack on the watch near the bike.", style = Type.BodyDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: HeartRateDevice, chosen: Boolean, onChoose: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onChoose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyphs.Heart, null, Modifier.size(34.dp), tint = Palette.Heart)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = Type.Body)
                Text(device.address, style = Type.Small)
            }
            if (chosen) Icon(Glyphs.Check, "Current monitor", Modifier.size(32.dp).padding(end = 4.dp), tint = Palette.Good)
        }
    }
}
