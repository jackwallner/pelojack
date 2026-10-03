package com.jackwallner.pelojack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.CardShape
import com.jackwallner.pelojack.ui.FilterChip
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Numeric
import com.jackwallner.pelojack.ui.OutlineButton
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.RoundButton
import com.jackwallner.pelojack.ui.Route
import com.jackwallner.pelojack.ui.StepperRow
import com.jackwallner.pelojack.ui.ToggleRow
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

private val starterSegments = listOf(
    Segment("Warm up", 300, resistance = 28..32, cadence = 80..90),
    Segment("Main set", 900, resistance = 40..45, cadence = 80..90),
    Segment("Cool down", 300, resistance = 25..30, cadence = 75..85),
)

private enum class TargetKind(val label: String) { Resistance("Resistance"), Zone("Power zone"), Free("Free") }

private fun Segment.kind() = when {
    zone != null || ftpShare != null -> TargetKind.Zone
    resistance != null -> TargetKind.Resistance
    else -> TargetKind.Free
}

/** Makes a class on the tablet, or edits one made here before. */
@Composable
fun BuilderScreen(app: PelojackApp, nav: Navigator, id: String?) {
    val existing = remember(id) { id?.let(app.library::find) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: Category.Custom) }
    var videoLink by remember {
        mutableStateOf(
            when (val video = existing?.video) {
                is Video.YouTube -> "https://youtu.be/${video.id}"
                is Video.File -> video.location
                null -> ""
            },
        )
    }
    val segments = remember { (existing?.segments ?: starterSegments).toMutableStateList() }
    var selected by remember { mutableIntStateOf(0) }

    fun draft() = Workout(
        id = existing?.id ?: "custom-${System.currentTimeMillis()}",
        name = name.trim(),
        description = description.trim(),
        category = category,
        segments = segments.toList(),
        video = videoLink.trim().takeIf { it.isNotEmpty() }?.let { link ->
            if (link.contains("youtu") || Regex("^[A-Za-z0-9_-]{11}$").matches(link)) Video.YouTube(Workout.youTubeId(link)) else Video.File(link)
        },
        custom = true,
    )

    Page(
        title = if (existing == null) "New class" else "Edit class",
        actions = {
            QuietButton("Cancel", nav::back)
            PrimaryButton(
                "Save",
                {
                    val saved = app.library.save(draft())
                    nav.back()
                    if (existing == null) nav.push(Route.ClassDetail(saved.id))
                },
                enabled = name.isNotBlank() && segments.isNotEmpty(),
            )
        },
    ) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1.15f).fillMaxHeight()) {
                Field("Name", name, { name = it }, "Saturday climb")
                Spacer(Modifier.height(16.dp))
                Field("YouTube link or video file (optional)", videoLink, { videoLink = it }, "https://youtu.be/…")
                Spacer(Modifier.height(20.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Category.entries.filter { it != Category.FtpTest }) { FilterChip(it.label, category == it, { category = it }) }
                }
                Spacer(Modifier.height(24.dp))
                if (segments.isNotEmpty()) {
                    val preview = draft()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(clock(preview.totalSeconds), style = Numeric, fontSize = 40.sp)
                        Spacer(Modifier.width(16.dp))
                        Text("${segments.size} segments  ·  difficulty ${oneDecimal(preview.difficulty)}", style = Type.BodyDim, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    WorkoutProfile(preview, Modifier.fillMaxWidth().height(96.dp))
                }
                Spacer(Modifier.height(20.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(segments) { index, segment ->
                        SegmentListRow(index, segment, index == selected, app.settings.current.ftp) { selected = index }
                    }
                    item {
                        OutlineButton(
                            "Add segment",
                            {
                                segments.add(segments.lastOrNull()?.copy(name = "Segment ${segments.size + 1}", test = false) ?: starterSegments[1])
                                selected = segments.lastIndex
                            },
                            icon = Glyphs.Plus,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
            segments.getOrNull(selected)?.let { segment ->
                SegmentEditor(
                    index = selected,
                    segment = segment,
                    ftp = app.settings.current.ftp,
                    onChange = { segments[selected] = it },
                    onMove = { delta ->
                        val target = selected + delta
                        if (target in segments.indices) {
                            segments.add(target, segments.removeAt(selected))
                            selected = target
                        }
                    },
                    onDuplicate = {
                        segments.add(selected + 1, segment)
                        selected += 1
                    },
                    onDelete = {
                        segments.removeAt(selected)
                        selected = selected.coerceAtMost(segments.lastIndex).coerceAtLeast(0)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun SegmentListRow(index: Int, segment: Segment, selected: Boolean, ftp: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) Palette.Raised else Palette.Surface)
            .border(2.dp, if (selected) Palette.Accent else Palette.Surface, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", style = Numeric, fontSize = 20.sp, color = Palette.Dim, modifier = Modifier.width(40.dp))
        Box(Modifier.width(6.dp).height(40.dp).background(Palette.zone(segment.displayZone()), RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(18.dp))
        Text(segment.name, style = Type.Body, modifier = Modifier.weight(1f), maxLines = 1)
        Text(segmentTargets(segment, ftp), style = Type.Small)
        Spacer(Modifier.width(20.dp))
        Text(clock(segment.seconds), style = Numeric, fontSize = 22.sp)
    }
}

@Composable
private fun SegmentEditor(
    index: Int,
    segment: Segment,
    ftp: Int,
    onChange: (Segment) -> Unit,
    onMove: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    Card(modifier) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Segment ${index + 1}", style = Type.Heading)
            Spacer(Modifier.height(16.dp))
            Field("Name", segment.name, { onChange(segment.copy(name = it)) }, "Climb")
            Spacer(Modifier.height(8.dp))
            StepperRow("Length", null, segment.seconds, { onChange(segment.copy(seconds = it)) }, 10..3600, step = 15, format = ::clock)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TargetKind.entries.forEach { kind ->
                    FilterChip(kind.label, segment.kind() == kind, {
                        onChange(
                            when (kind) {
                                TargetKind.Resistance -> segment.copy(resistance = segment.resistance ?: 35..40, zone = null, ftpShare = null)
                                TargetKind.Zone -> segment.copy(zone = segment.zone ?: 2, ftpShare = null, resistance = null)
                                TargetKind.Free -> segment.copy(resistance = null, zone = null, ftpShare = null)
                            },
                        )
                    })
                }
            }
            val resistance = segment.resistance
            val zone = segment.zone
            if (resistance != null) {
                StepperRow("Resistance from", null, resistance.first, {
                    onChange(segment.copy(resistance = it..maxOf(it, resistance.last)))
                }, 0..100)
                StepperRow("Resistance to", null, resistance.last, {
                    onChange(segment.copy(resistance = minOf(it, resistance.first)..it))
                }, 0..100)
            }
            if (zone != null) {
                StepperRow(
                    "Zone $zone",
                    "${PowerZones.names[zone - 1]}  ·  ${PowerZones.watts(zone, ftp).let { "${it.first}–${it.last} W" }}",
                    zone,
                    { onChange(segment.copy(zone = it)) },
                    1..7,
                )
            }
            val cadence = segment.cadence
            ToggleRow("Cadence target", null, cadence != null) { on ->
                onChange(segment.copy(cadence = if (on) 80..90 else null))
            }
            if (cadence != null) {
                StepperRow("Cadence from", null, cadence.first, {
                    onChange(segment.copy(cadence = it..maxOf(it, cadence.last)))
                }, 40..140, step = 5)
                StepperRow("Cadence to", null, cadence.last, {
                    onChange(segment.copy(cadence = minOf(it, cadence.first)..it))
                }, 40..140, step = 5)
            }
            Spacer(Modifier.height(8.dp))
            Field("Coaching cue (optional)", segment.cue.orEmpty(), { onChange(segment.copy(cue = it.ifBlank { null })) }, "Out of the saddle")
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundButton(Glyphs.Up, "Move up", { onMove(-1) }, size = 72.dp)
            RoundButton(Glyphs.Down, "Move down", { onMove(1) }, size = 72.dp)
            Spacer(Modifier.weight(1f))
            QuietButton("Duplicate", onDuplicate)
            QuietButton("Delete", onDelete, color = Palette.Bad)
        }
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = Type.Small) },
        placeholder = { Text(placeholder, style = Type.Body, color = Palette.Faint) },
        singleLine = true,
        textStyle = Type.Body,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Accent,
            unfocusedBorderColor = Palette.Line,
            focusedLabelColor = Palette.Accent,
            cursorColor = Palette.Accent,
            focusedContainerColor = Palette.Surface,
            unfocusedContainerColor = Palette.Surface,
        ),
    )
}
