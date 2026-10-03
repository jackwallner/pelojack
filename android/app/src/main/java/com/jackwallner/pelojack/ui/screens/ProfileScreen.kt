package com.jackwallner.pelojack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.profile.Badge
import com.jackwallner.pelojack.profile.Stats
import com.jackwallner.pelojack.ride.RideSummary
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.Divider
import com.jackwallner.pelojack.ui.EmptyState
import com.jackwallner.pelojack.ui.Figure
import com.jackwallner.pelojack.ui.FilterChip
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.MonthCalendar
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Numeric
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.RoundButton
import com.jackwallner.pelojack.ui.Route
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.clock
import com.jackwallner.pelojack.ui.thousands
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProfileScreen(app: PelojackApp, nav: Navigator) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val rides by app.rides.rides.collectAsStateWithLifecycle()
    var history by rememberSaveable { mutableStateOf(false) }
    val stats = Stats(rides)

    Page(
        title = settings.name,
        subtitle = if (rides.isEmpty()) "No rides yet" else "${rides.size} rides since ${formatDate(stats.rides.first().startedAt, "MMMM yyyy")}",
        actions = {
            FilterChip("Overview", !history, { history = false })
            FilterChip("History", history, { history = true })
        },
    ) {
        if (rides.isEmpty()) {
            EmptyState("Your stats start with your first ride", "Rides, records, streaks and badges will show up here.") {
                PrimaryButton("Just Ride", { app.ride.open(null) }, icon = Glyphs.Play)
            }
        } else if (history) {
            History(stats, settings.metric) { nav.push(Route.RideDetail(it.startedAt)) }
        } else {
            Overview(stats, settings.metric)
        }
    }
}

@Composable
private fun Overview(stats: Stats, metric: Boolean) {
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val shown = YearMonth.parse(month)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Row {
                    Figure(stats.rides.size.toString(), "Rides", Modifier.weight(1f))
                    Figure(hoursAndMinutes(stats.totalSeconds), "Time", Modifier.weight(1f))
                    Figure(thousands(stats.totalKilojoules.toInt()), "Output", Modifier.weight(1f), unit = "kJ")
                }
                Spacer(Modifier.height(24.dp))
                Row {
                    val (value, unit) = distance(stats.totalMiles, metric)
                    Figure(value, "Distance", Modifier.weight(1f), unit = unit)
                    Figure(thousands(stats.totalCalories), "Calories", Modifier.weight(1f), unit = "kcal")
                    Figure(stats.weekStreak(today).toString(), "Week streak", Modifier.weight(1f))
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shown.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), style = Type.Heading, modifier = Modifier.weight(1f))
                    val days = stats.rideDays.count { YearMonth.from(it) == shown }
                    Text(if (days == 1) "1 ride day" else "$days ride days", style = Type.Small)
                    Spacer(Modifier.width(20.dp))
                    RoundButton(Glyphs.Left, "Previous month", { month = shown.minusMonths(1).toString() }, size = 60.dp)
                    Spacer(Modifier.width(10.dp))
                    RoundButton(Glyphs.Right, "Next month", { month = shown.plusMonths(1).toString() }, size = 60.dp)
                }
                Spacer(Modifier.height(16.dp))
                MonthCalendar(shown, stats.rideDays, today, Modifier.fillMaxWidth())
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Text("Personal records", style = Type.Heading)
                Spacer(Modifier.height(8.dp))
                if (stats.records.isEmpty()) {
                    Text("Ride a full class, or free ride at least 5 minutes, to set one.", style = Type.BodyDim)
                }
                stats.records.forEach { record ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${record.minutes} min", style = Type.Body, modifier = Modifier.width(140.dp))
                        Text(record.ride.title, style = Type.Small, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(formatDate(record.ride.startedAt, "MMM d"), style = Type.Small)
                        Spacer(Modifier.width(24.dp))
                        Text("${record.ride.totals.kilojoules.toInt()} kJ", style = Numeric, fontSize = 26.sp)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                val badges = stats.badges(today)
                Text("Badges", style = Type.Heading)
                Text("${badges.count { it.earned }} of ${badges.size} earned", style = Type.Small)
                Spacer(Modifier.height(16.dp))
                badges.sortedByDescending { it.earned }.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { BadgeTile(it, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier) {
    Card(modifier, color = if (badge.earned) Palette.Raised else Palette.Background, padding = PaddingValues(18.dp)) {
        Icon(Glyphs.Trophy, null, Modifier.size(34.dp), tint = if (badge.earned) Palette.Accent else Palette.Line)
        Spacer(Modifier.height(10.dp))
        Text(badge.title, style = Type.Body, fontSize = 21.sp, color = if (badge.earned) Palette.Text else Palette.Faint, maxLines = 1)
        Text(badge.detail, style = Type.Small, fontSize = 16.sp, color = if (badge.earned) Palette.Dim else Palette.Faint, maxLines = 1)
    }
}

@Composable
private fun History(stats: Stats, metric: Boolean, onOpen: (RideSummary) -> Unit) {
    val newestFirst = stats.rides.reversed()
    LazyColumn(Modifier.fillMaxHeight()) {
        var lastMonth: String? = null
        newestFirst.forEach { ride ->
            val month = formatDate(ride.startedAt, "MMMM yyyy")
            if (month != lastMonth) {
                lastMonth = month
                item(key = "month-$month") {
                    Text(month, style = Type.Label, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                }
            }
            item(key = ride.startedAt) {
                HistoryRow(ride, stats.isRecord(ride), metric) { onOpen(ride) }
                Divider()
            }
        }
    }
}

@Composable
private fun HistoryRow(ride: RideSummary, record: Boolean, metric: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onClick, color = Palette.Background, padding = PaddingValues(vertical = 20.dp, horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(220.dp)) {
                Text(formatDate(ride.startedAt, "EEE, MMM d"), style = Type.Body)
                Text(formatDate(ride.startedAt, "h:mm a"), style = Type.Small)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ride.title, style = Type.Body, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (record) {
                        Spacer(Modifier.width(14.dp))
                        Icon(Glyphs.Trophy, "Personal record", Modifier.size(26.dp), tint = Palette.Accent)
                    }
                }
                rideOrigin(ride)?.let { Text(it, style = Type.Small, maxLines = 1) }
            }
            Stat(clock(ride.totals.seconds), "")
            Stat("${ride.totals.kilojoules.toInt()}", "kJ")
            Stat("${ride.totals.avgPower}", "avg W")
            val (value, unit) = distance(ride.totals.miles, metric)
            Stat(value, unit)
            ride.totals.avgHeartRate?.let { Stat(it.toString(), "bpm") } ?: Spacer(Modifier.width(170.dp))
        }
    }
}

@Composable
private fun Stat(value: String, unit: String) {
    Row(Modifier.width(170.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.End) {
        Text(value, style = Numeric, fontSize = 26.sp)
        Spacer(Modifier.width(6.dp))
        Text(unit, style = Type.Small, modifier = Modifier.padding(bottom = 2.dp))
    }
}
