package com.jackwallner.pelojack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.profile.Stats
import com.jackwallner.pelojack.ride.RideSummary
import com.jackwallner.pelojack.ride.Sample
import com.jackwallner.pelojack.ui.BackRow
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.Figure
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.LineChart
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.clock
import com.jackwallner.pelojack.workout.PowerZones

@Composable
fun RideDetailScreen(app: PelojackApp, nav: Navigator, startedAt: Long) {
    val rides by app.rides.rides.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val ride = rides.firstOrNull { it.startedAt == startedAt } ?: run {
        nav.back()
        return
    }
    val samples = remember(startedAt) { app.rides.samples(startedAt) }
    val record = Stats(rides).isRecord(ride)
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 64.dp, top = 40.dp, bottom = 40.dp)) {
        BackRow(nav::back)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ride.title, style = Type.Display)
                Text(listOfNotNull(formatDate(ride.startedAt, "EEEE, MMMM d, yyyy 'at' h:mm a"), rideOrigin(ride), if (record) "Personal record" else null).joinToString("  ·  "), style = Type.BodyDim)
            }
            QuietButton("Delete", { confirmDelete = true }, icon = Glyphs.Delete, color = Palette.Bad)
        }
        Spacer(Modifier.height(28.dp))
        RideFigures(ride, settings.metric)
        Spacer(Modifier.height(24.dp))
        RideCharts(samples, ride, settings.ftp, Modifier.weight(1f))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this ride?", style = Type.Heading) },
            text = { Text("It will be removed from your history, records and streaks.", style = Type.BodyDim) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    app.rides.delete(ride)
                    nav.back()
                }) { Text("Delete", style = Type.Body, color = Palette.Bad) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", style = Type.Body) } },
            containerColor = Palette.Raised,
        )
    }
}

@Composable
fun RideFigures(ride: RideSummary, metric: Boolean) {
    val totals = ride.totals
    Card(Modifier.fillMaxWidth()) {
        Row {
            Figure(clock(totals.seconds), "Time", Modifier.weight(1f))
            Figure(totals.kilojoules.toInt().toString(), "Total output", Modifier.weight(1f), unit = "kJ")
            val (value, unit) = distance(totals.miles, metric)
            Figure(value, "Distance", Modifier.weight(1f), unit = unit)
            Figure(totals.calories.toString(), "Calories", Modifier.weight(1f), unit = "kcal")
            Figure(totals.avgPower.toString(), "Avg output", Modifier.weight(1f), unit = "W")
            Figure(totals.avgCadence.toString(), "Avg cadence", Modifier.weight(1f), unit = "rpm")
            Figure(totals.avgResistance.toString(), "Avg resistance", Modifier.weight(1f))
            totals.avgHeartRate?.let { Figure(it.toString(), "Avg heart rate", Modifier.weight(1f), unit = "bpm") }
        }
    }
}

/** Output, cadence, resistance and heart rate over the ride, plus time in each power zone. */
@Composable
fun RideCharts(samples: List<Sample>, ride: RideSummary, ftp: Int, modifier: Modifier) {
    if (samples.size < 2) {
        Text("No detailed data for this ride.", style = Type.BodyDim)
        return
    }
    val heart = samples.mapNotNull { it.heartRate }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ChartCard("Output", "${ride.totals.avgPower} W avg  ·  ${ride.totals.maxPower} W max", Modifier.weight(1f)) {
                LineChart(samples.map { it.power }, Palette.Accent, Modifier.fillMaxSize(), average = ride.totals.avgPower)
            }
            if (heart.size > 1) {
                ChartCard("Heart rate", "${ride.totals.avgHeartRate} avg  ·  ${ride.totals.maxHeartRate} max", Modifier.weight(1f)) {
                    LineChart(samples.map { it.heartRate ?: 0 }, Palette.Heart, Modifier.fillMaxSize(), average = ride.totals.avgHeartRate, floor = 40)
                }
            } else {
                ChartCard("Resistance", "${ride.totals.avgResistance} avg", Modifier.weight(1f)) {
                    LineChart(samples.map { it.resistance }, Palette.zone(2), Modifier.fillMaxSize(), average = ride.totals.avgResistance)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ChartCard("Cadence", "${ride.totals.avgCadence} rpm avg", Modifier.weight(1f)) {
                LineChart(samples.map { it.cadence }, Palette.zone(1), Modifier.fillMaxSize(), average = ride.totals.avgCadence, floor = 40)
            }
            ChartCard("Time in power zones", "FTP $ftp W", Modifier.weight(1f)) {
                ZoneBars(samples, ftp)
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, detail: String, modifier: Modifier, chart: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = Type.Heading, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Text(detail, style = Type.Small)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) { chart() }
    }
}

/** Seconds spent in each power zone, as a column per zone. */
@Composable
private fun ZoneBars(samples: List<Sample>, ftp: Int) {
    val seconds = IntArray(7)
    samples.forEach { if (it.power > 0) seconds[PowerZones.zoneOf(it.power, ftp) - 1]++ }
    val longest = seconds.max().coerceAtLeast(1)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        seconds.forEachIndexed { index, value ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (value > 0) clock(value) else "", style = Type.Small, fontSize = 16.sp)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((value.toFloat() / longest).coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(6.dp)).background(if (value > 0) Palette.zone(index + 1) else Palette.Raised),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Z${index + 1}", style = Type.Small, fontSize = 16.sp)
            }
        }
    }
}
