package com.jackwallner.pelojack.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackwallner.pelojack.ride.RideState
import com.jackwallner.pelojack.ui.Numeric
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.WorkoutProfile
import com.jackwallner.pelojack.ui.clock
import com.jackwallner.pelojack.workout.Workout
import kotlin.math.ceil

/** The exact class-card graph stays visible in BOTH sizes; drag only the header. */
@Composable
fun RideStrip(
    state: RideState,
    simulated: Boolean = false,
    designScale: Float,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    initiallyCollapsed: Boolean = false,
    onCollapse: (Boolean) -> Unit = {},
) {
    var collapsed by remember { mutableStateOf(initiallyCollapsed) }
    val workout = state.workout
    val elapsedSeconds = state.totals.seconds
    val profile = workout?.let { OverlayProfile.at(it, elapsedSeconds) }
    val current = profile?.current
    val next = profile?.next
    CompositionLocalProvider(LocalDensity provides Density(designScale, fontScale = 1f)) {
        val shape = RoundedCornerShape(16.dp)
        Column(Modifier.width(OverlayLayout.DESIGN_WIDTH.dp).background(Palette.Surface, shape)
            .border(2.dp, Palette.Accent.copy(alpha = .6f), shape)
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text((if (simulated) "Simulated bike · " else "") +
                    (workout?.name ?: "Just Ride"),
                    modifier = Modifier.weight(1f).pointerInput(Unit) {
                        detectDragGestures { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
                    }.padding(vertical = 6.dp),
                    style = Type.Small, color = Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                HeaderAction(if (collapsed) "Expand" else "Compact") {
                    collapsed = !collapsed; onCollapse(collapsed)
                }
                HeaderAction("Controls", onTap)
            }
            val status = when {
                state.notStarted -> "Not started"
                state.paused -> "Paused"
                else -> "Now"
            }
            if (collapsed) {
                CompactProfile(workout, profile, status, elapsedSeconds, state.targetWatts)
            } else {
                workout?.let {
                    WorkoutProfile(it, Modifier.fillMaxWidth().height(126.dp),
                        elapsed = (profile?.elapsed ?: 0).toDouble())
                }
                Text("$status · " + (current?.let { "${it.segment.name} · ${clock(ceil(it.secondsLeft).toInt())} left" }
                    ?: if (workout == null) "Free ride · ${clock(elapsedSeconds)}" else "Class complete"),
                    style = Type.Small, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                current?.let {
                    Text(OverlayProfile.targets(it.segment) + (state.targetWatts?.let { watts -> " · $watts W" } ?: ""),
                        style = Type.Small, color = Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(next?.let { "Next · ${it.name} · ${clock(it.seconds)} · ${OverlayProfile.targets(it)}" }
                    ?: if (workout == null) "No class targets" else "Next · Last interval",
                    style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            state.let {
                if (collapsed) {
                    Text("${it.telemetry.cadenceRpm} rpm  ·  R ${it.telemetry.resistance}  ·  ${it.telemetry.powerWatts} W  ·  HR ${it.heartRate ?: "–"}  ·  ${clock(it.secondsLeft ?: elapsedSeconds)} ${if (it.secondsLeft == null) "elapsed" else "left"}",
                        style = Type.Small, fontSize = 22.sp)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Cadence", "${it.telemetry.cadenceRpm}", "rpm")
                    Metric("Resistance", "${it.telemetry.resistance}", "%")
                    Metric("Output", "${it.telemetry.powerWatts}", "W")
                    Metric("Heart rate", it.heartRate?.toString() ?: "–", "bpm")
                    Metric("Time left", clock(it.secondsLeft ?: elapsedSeconds), "")
                }
            }
        }
    }
}

/** Compact body trades stacked rows for graph/current/next columns, not lost workout detail. */
@Composable private fun CompactProfile(
    workout: Workout?,
    profile: OverlayProfile?,
    status: String,
    elapsedSeconds: Int,
    targetWatts: Int?,
) {
    val current = profile?.current
    val next = profile?.next
    Row(Modifier.fillMaxWidth().heightIn(min = OverlayLayout.COMPACT_BODY_HEIGHT.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        workout?.let {
            WorkoutProfile(it, Modifier.weight(1f).height(OverlayLayout.COMPACT_BODY_HEIGHT.dp), elapsed = (profile?.elapsed ?: 0).toDouble())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(status, style = Type.Small, color = Palette.Accent)
                Text(current?.let { "${clock(ceil(it.secondsLeft).toInt())} left" } ?: clock(elapsedSeconds),
                    style = Numeric, fontSize = 22.sp)
            }
            Text(current?.segment?.name ?: if (workout == null) "Free ride" else "Class complete",
                style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(current?.let { OverlayProfile.targets(it.segment) + (targetWatts?.let { " · $it W" } ?: "") }
                ?: "No class targets", style = Type.Small, color = Palette.Accent,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(next?.let { "Next · ${clock(next.seconds)}" } ?: "Next", style = Type.Small, color = Palette.Accent)
            Text(next?.name ?: if (workout == null) "No class targets" else "Last interval",
                style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            next?.let { Text(OverlayProfile.targets(it), style = Type.Small, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable private fun HeaderAction(label: String, action: () -> Unit) {
    Text(label, style = Type.Small, color = Palette.Accent,
        modifier = Modifier.clickable(onClickLabel = label, onClick = action).padding(horizontal = 14.dp, vertical = 10.dp))
}

@Composable private fun Metric(label: String, value: String, unit: String) {
    Column {
        Text(label, style = Type.Small, fontSize = 18.sp)
        Text("$value $unit".trim(), style = Numeric, fontSize = 32.sp, maxLines = 1)
    }
}
