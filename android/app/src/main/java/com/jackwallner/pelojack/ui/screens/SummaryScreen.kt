package com.jackwallner.pelojack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jackwallner.pelojack.ride.RideResult
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.oneDecimal
import kotlin.math.abs

@Composable
fun SummaryScreen(
    result: RideResult,
    ftp: Int,
    metric: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onSaveFtp: (Int) -> Unit,
) {
    val summary = result.summary
    var ftpSaved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RIDE COMPLETE", style = Type.Label, color = Palette.Accent)
                Text(summary.title, style = Type.Display)
                Text(formatDate(summary.startedAt, "EEEE, MMMM d 'at' h:mm a"), style = Type.BodyDim)
            }
            QuietButton("Discard", onDiscard)
            Spacer(Modifier.width(16.dp))
            PrimaryButton(result.upNext?.let { "Save, then ${it.name}" } ?: "Save ride", onSave, icon = Glyphs.Check)
        }
        Spacer(Modifier.height(24.dp))
        val highlights = buildList<@Composable () -> Unit> {
            if (result.record) {
                add { Highlight(Glyphs.Trophy, Palette.Accent, "Personal record", "Your best output for a ${(summary.plannedSeconds ?: summary.totals.seconds) / 60}-minute ride") }
            }
            result.previousBest?.let { best ->
                val delta = summary.totals.kilojoules - best.totals.kilojoules
                add {
                    Highlight(
                        Glyphs.Bolt,
                        if (delta >= 0) Palette.Good else Palette.Dim,
                        "${if (delta >= 0) "+" else "−"}${oneDecimal(abs(delta))} kJ vs your best",
                        "Best was ${best.totals.kilojoules.toInt()} kJ on ${formatDate(best.startedAt, "MMM d")}",
                    )
                }
            }
            summary.ftpEstimate?.let { estimate ->
                add {
                    Card(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Glyphs.Bolt, null, Modifier.size(40.dp), tint = Palette.zone(4))
                            Spacer(Modifier.width(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text("New FTP estimate: $estimate W", style = Type.Heading)
                                Text("95% of your average output during the test. Your FTP is now $ftp W.", style = Type.BodyDim)
                            }
                            if (!ftpSaved && estimate != ftp) {
                                PrimaryButton("Use $estimate W", { onSaveFtp(estimate); ftpSaved = true })
                            }
                        }
                    }
                }
            }
        }
        if (highlights.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                highlights.forEach { Column(Modifier.weight(1f)) { it() } }
            }
            Spacer(Modifier.height(20.dp))
        }
        RideFigures(summary, metric)
        Spacer(Modifier.height(20.dp))
        RideCharts(result.samples, summary, ftp, Modifier.weight(1f))
    }
}

@Composable
private fun Highlight(icon: ImageVector, color: Color, title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(40.dp), tint = color)
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, style = Type.Heading)
                Text(detail, style = Type.BodyDim)
            }
        }
    }
}
