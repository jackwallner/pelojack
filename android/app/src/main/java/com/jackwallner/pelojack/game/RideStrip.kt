package com.jackwallner.pelojack.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.RidePiece
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.clock

/** A saddle-readable strip that leaves the game's minimap and chat clear. */
@Composable
fun RideStrip(state: RideState, simulated: Boolean, designScale: Float, onTap: () -> Unit, onDrag: (Float, Float) -> Unit,
    openCamera: (() -> Unit)? = null, hidden: Set<String> = emptySet(), togglePiece: (String) -> Unit = {}) {
    val flash = remember { Animatable(0f) }
    val position = state.position
    LaunchedEffect(position?.index) {
        if (position != null) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(2500))
        }
    }
    CompositionLocalProvider(LocalDensity provides Density(designScale, fontScale = 1f)) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.width(960.dp)
                .background(Palette.Surface, shape)
                .border(2.dp, Palette.Accent.copy(alpha = 0.25f + flash.value * 0.75f), shape)
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        onDrag(amount.x, amount.y)
                    }
                }
                .clickable(onClickLabel = "Return to ride controls", onClick = onTap)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StripMetric(clock(state.secondsLeft ?: state.totals.seconds), if (state.secondsLeft == null) "Time" else "Time left", Modifier.weight(1.2f),
                    minimized = RidePiece.CLOCK in hidden, onTap = { togglePiece(RidePiece.CLOCK) })
                StripMetric("${state.telemetry.cadenceRpm}", "Cadence", Modifier.weight(1f),
                    position?.segment?.cadence?.let { "${it.first}–${it.last} rpm" },
                    minimized = RidePiece.CADENCE in hidden, onTap = { togglePiece(RidePiece.CADENCE) })
                StripMetric("${state.telemetry.resistance}", "Resistance", Modifier.weight(1f),
                    position?.segment?.resistance?.let { "${it.first}–${it.last}" },
                    minimized = RidePiece.RESISTANCE in hidden, onTap = { togglePiece(RidePiece.RESISTANCE) })
                StripMetric("${state.telemetry.powerWatts}", "Output (W)", Modifier.weight(1f), state.targetWatts?.let { "$it W" },
                    minimized = RidePiece.OUTPUT in hidden, onTap = { togglePiece(RidePiece.OUTPUT) })
                StripMetric(state.heartRate?.toString() ?: "–", "Heart rate", Modifier.weight(1f),
                    minimized = RidePiece.HEART_RATE in hidden, onTap = { togglePiece(RidePiece.HEART_RATE) })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val cue = when {
                    state.paused -> "Paused"
                    position != null -> position.segment.name +
                        (if (RidePiece.CLOCK in hidden) "" else " · ${clock(position.secondsLeft.toInt())}") +
                        (position.segment.cue?.let { " · $it" } ?: "")
                    else -> "Just Ride"
                }
                Text((if (simulated) "Simulated bike · " else "") + cue, style = Type.Small,
                    color = Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Text("Tap for controls", style = Type.Small)
                openCamera?.let {
                    Spacer(Modifier.width(16.dp))
                    QuietButton("Nanit", it)
                }
            }
            position?.let { current ->
                state.workout?.segments?.getOrNull(current.index + 1)?.let { next ->
                    Text("Next: ${next.name}", style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StripMetric(value: String, label: String, modifier: Modifier, target: String? = null,
    minimized: Boolean, onTap: () -> Unit) {
    Column(modifier.clickable(onClickLabel = "${if (minimized) "Expand" else "Minimize"} $label", onClick = onTap)) {
        Text(label, style = Type.Small, fontSize = 18.sp, maxLines = 1)
        if (minimized) Text("Show", style = Type.Small, fontSize = 20.sp)
        else {
            Text(value, style = Numeric, fontSize = 38.sp, maxLines = 1)
            if (target != null) Text("Target $target", style = Type.Small, fontSize = 16.sp, color = Palette.Accent, maxLines = 1)
        }
    }
}
