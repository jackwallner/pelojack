package com.jackwallner.pelojack.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.bike.BikePlus
import com.jackwallner.pelojack.bike.hasBikeService
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.Type
import kotlinx.coroutines.flow.MutableStateFlow

/** Connection details and a manual resistance test, for checking a new tablet or OS update. */
@Composable
fun BikeScreen(app: PelojackApp, nav: Navigator) {
    val context = LocalContext.current
    val telemetry by app.bike.telemetry.collectAsStateWithLifecycle()
    val link by app.bike.link.collectAsStateWithLifecycle()
    val error by app.bike.lastError.collectAsStateWithLifecycle()
    val raw by ((app.bike as? BikePlus)?.raw ?: remember { MutableStateFlow<String?>(null) }).collectAsStateWithLifecycle()

    Page("Bike", actions = {
        QuietButton("Resistance −5", { app.bike.setResistance(telemetry.resistance - 5) })
        QuietButton("Resistance +5", { app.bike.setResistance(telemetry.resistance + 5) })
        QuietButton("Back", nav::back)
    }) {
        Card(Modifier.fillMaxWidth()) {
            Fact("Source", app.bike.label)
            Fact("Status", link.name)
            Fact("Cadence", "${telemetry.cadenceRpm} rpm")
            Fact("Output", "${telemetry.powerWatts} watts")
            Fact("Resistance", telemetry.resistance.toString())
            Fact("Tablet", "${Build.BRAND} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Fact("Bike service", if (context.hasBikeService()) "Installed" else "Not installed")
            raw?.let { Fact("Raw reading", it) }
            Fact("Last error", error ?: "None")
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, Modifier.width(320.dp), style = Type.BodyDim)
        Text(value, style = Type.Body)
    }
}
