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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.jackwallner.pelojack.ui.BackRow
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.Divider
import com.jackwallner.pelojack.ui.Figure
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Numeric
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.RemoteImage
import com.jackwallner.pelojack.ui.thumbnailUrls
import com.jackwallner.pelojack.ui.RoundButton
import com.jackwallner.pelojack.ui.Route
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.WorkoutProfile
import com.jackwallner.pelojack.ui.clock
import com.jackwallner.pelojack.ui.displayZone
import com.jackwallner.pelojack.ui.oneDecimal
import com.jackwallner.pelojack.workout.Category
import com.jackwallner.pelojack.workout.PowerZones
import com.jackwallner.pelojack.workout.Segment
import com.jackwallner.pelojack.workout.Video
import com.jackwallner.pelojack.workout.Workout

@Composable
fun ClassDetailScreen(app: PelojackApp, nav: Navigator, id: String) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val rides by app.rides.rides.collectAsStateWithLifecycle()
    val workouts by app.library.workouts.collectAsStateWithLifecycle()
    val workout = workouts.firstOrNull { it.id == id } ?: run {
        nav.back()
        return
    }
    val mine = rides.filter { it.workoutId == id }
    val best = Stats(rides).bestOf(id)
    val bookmarked = id in settings.bookmarks
    val stacked = id in settings.stack
    var confirmDelete by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 64.dp, top = 40.dp, bottom = 40.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackRow(nav::back)
                Spacer(Modifier.weight(1f))
                if (workout.custom) {
                    QuietButton("Edit", { nav.push(Route.Builder(id)) }, icon = Glyphs.Edit)
                    Spacer(Modifier.width(12.dp))
                    QuietButton("Delete", { confirmDelete = true }, icon = Glyphs.Delete, color = Palette.Bad)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(workout.category.label.uppercase(), style = Type.Label, color = Palette.Accent)
            Spacer(Modifier.height(10.dp))
            Text(workout.name, style = Type.Display, fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Meta("${workout.minutes} min")
                Meta("Difficulty ${oneDecimal(workout.difficulty)}")
                if (workout.video != null) Meta(if (workout.video is Video.YouTube) "YouTube video" else "Video", Glyphs.Video)
            }
            if (workout.description.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text(workout.description, style = Type.Body, color = Palette.Dim)
            }
            Spacer(Modifier.height(20.dp))
            Text(targetsLine(workout, settings.ftp), style = Type.Small)
            if (mine.isNotEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text("Your rides", style = Type.Heading)
                Spacer(Modifier.height(8.dp))
                val bestKj = best?.totals?.kilojoules ?: 1.0
                mine.sortedByDescending { it.startedAt }.take(4).forEach { ride ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDate(ride.startedAt, "MMM d"), style = Type.Body, modifier = Modifier.width(110.dp))
                        Box(Modifier.weight(1f).height(14.dp)) {
                            Box(
                                Modifier.fillMaxWidth((ride.totals.kilojoules / bestKj).toFloat().coerceIn(0.05f, 1f)).fillMaxHeight()
                                    .background(if (ride == best) Palette.Accent else Palette.Raised, RoundedCornerShape(7.dp)),
                            )
                        }
                        Text("${ride.totals.kilojoules.toInt()} kJ", style = Numeric, fontSize = 24.sp, modifier = Modifier.width(130.dp).padding(start = 20.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Card(Modifier.fillMaxWidth()) {
                Row {
                    Figure(workout.segments.size.toString(), "Segments", Modifier.weight(1f))
                    Figure(mine.size.toString(), "Times ridden", Modifier.weight(1f))
                    Figure(best?.totals?.kilojoules?.toInt()?.toString() ?: "–", "Best output", Modifier.weight(1f), unit = best?.let { "kJ" })
                    Figure(mine.maxOfOrNull { it.startedAt }?.let { formatDate(it, "MMM d") } ?: "–", "Last ridden", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(28.dp))
            if (app.game.installed) {
                QuietButton("Ride with RuneScape", { app.game.play(workout) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Start class", { app.ride.open(workout) }, icon = Glyphs.Play, modifier = Modifier.weight(1f))
                QuietButton(
                    if (stacked) "In stack" else "Add to stack",
                    { app.settings.update { it.copy(stack = if (stacked) it.stack - id else it.stack + id) } },
                    icon = if (stacked) Glyphs.Check else Glyphs.AddToStack,
                )
                RoundButton(
                    if (bookmarked) Glyphs.Bookmark else Glyphs.BookmarkOutline,
                    if (bookmarked) "Remove bookmark" else "Bookmark",
                    { app.settings.update { it.copy(bookmarks = if (bookmarked) it.bookmarks - id else it.bookmarks + id) } },
                    size = 84.dp,
                    tint = if (bookmarked) Palette.Accent else Palette.Text,
                )
            }
        }
        Spacer(Modifier.width(56.dp))
        Card(Modifier.weight(1.15f).fillMaxHeight()) {
            workout.video?.thumbnailUrls()?.let { thumbnail ->
                RemoteImage(thumbnail, Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.height(24.dp))
            }
            WorkoutProfile(workout, Modifier.fillMaxWidth().height(if (workout.video != null) 110.dp else 170.dp))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("0:00", style = Type.Small)
                Spacer(Modifier.weight(1f))
                Text(clock(workout.totalSeconds), style = Type.Small)
            }
            Spacer(Modifier.height(20.dp))
            Divider()
            LazyColumn(Modifier.weight(1f)) {
                var start = 0
                val starts = workout.segments.map { segment -> start.also { start += segment.seconds } }
                itemsIndexed(workout.segments) { index, segment -> SegmentRow(starts[index], segment, settings.ftp) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${workout.name}?", style = Type.Heading) },
            text = { Text("Your rides of it stay in your history.", style = Type.BodyDim) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    app.library.delete(workout)
                    app.settings.update { it.copy(stack = it.stack - id, bookmarks = it.bookmarks - id) }
                    nav.back()
                }) { Text("Delete", style = Type.Body, color = Palette.Bad) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", style = Type.Body) } },
            containerColor = Palette.Raised,
        )
    }
}

@Composable
private fun Meta(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(26.dp), tint = Palette.Dim)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.Body, color = Palette.Text)
    }
}

@Composable
private fun SegmentRow(start: Int, segment: Segment, ftp: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(clock(start), style = Numeric, fontSize = 20.sp, color = Palette.Dim, modifier = Modifier.width(84.dp))
        Box(Modifier.width(6.dp).height(44.dp).background(Palette.zone(segment.displayZone()), RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(segment.name, style = Type.Body, fontSize = 23.sp)
            segment.cue?.let { Text(it, style = Type.Small, maxLines = 1) }
        }
        Text(segmentTargets(segment, ftp), style = Type.Small, color = Palette.Text)
        Spacer(Modifier.width(20.dp))
        Text(clock(segment.seconds), style = Numeric, fontSize = 20.sp, modifier = Modifier.width(72.dp))
    }
}

fun segmentTargets(segment: Segment, ftp: Int): String = listOfNotNull(
    segment.zone?.let { zone -> PowerZones.watts(zone, ftp).let { "Zone $zone  ${it.first}–${it.last} W" } }
        ?: segment.ftpShare?.let { "${(it * ftp).toInt()} W" },
    segment.resistance?.let { if (it.first == it.last) "R ${it.first}" else "R ${it.first}–${it.last}" },
    segment.cadence?.let { "${it.first}–${it.last} rpm" },
    if (segment.test) "Test" else null,
).joinToString("   ")

private fun targetsLine(workout: Workout, ftp: Int): String = when {
    workout.category == Category.FtpTest -> "Ends with an FTP estimate you can save. Your current FTP is $ftp W."
    workout.usesPower -> "Power targets use your FTP of $ftp W. ERG mode holds them for you."
    workout.segments.any { it.resistance != null } -> "Auto-follow moves the resistance for each segment."
    else -> "Ride it at your own resistance."
}
