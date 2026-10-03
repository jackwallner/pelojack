package com.jackwallner.pelojack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackwallner.pelojack.media.Track
import com.jackwallner.pelojack.ride.RideState
import com.jackwallner.pelojack.ride.speedMph
import com.jackwallner.pelojack.ui.ButtonShape
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.CardShape
import com.jackwallner.pelojack.ui.ChipShape
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.Inter
import com.jackwallner.pelojack.ui.LineChart
import com.jackwallner.pelojack.ui.MusicBar
import com.jackwallner.pelojack.ui.Numeric
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.RangeGauge
import com.jackwallner.pelojack.ui.RoundButton
import com.jackwallner.pelojack.ui.RidePiece as Piece
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.WorkoutProfile
import com.jackwallner.pelojack.ui.clock
import com.jackwallner.pelojack.ui.oneDecimal
import com.jackwallner.pelojack.ui.video.FileVideo
import com.jackwallner.pelojack.ui.video.YouTubeVideo
import com.jackwallner.pelojack.workout.PowerZones
import com.jackwallner.pelojack.workout.Video
import kotlin.math.abs
import kotlin.math.ceil

private const val RESISTANCE_BIG_STEP = 5

class RideActions(
    val togglePause: () -> Unit,
    val finish: () -> Unit,
    val nudge: (Int) -> Unit,
    /** Minimized pieces. Tapping the same piece expands it. */
    val hidden: Set<String>,
    val hide: (String) -> Unit,
    val setAutoFollow: (Boolean) -> Unit,
    val setErg: (Boolean) -> Unit,
    val music: MusicActions,
    val playGame: (() -> Unit)? = null,
    val openCamera: (() -> Unit)? = null,
)

class MusicActions(
    val track: Track?,
    val available: Boolean,
    val toggle: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val open: (() -> Unit)?,
)

@Composable
fun RideScreen(state: RideState, actions: RideActions, videoLocation: (Video.File) -> String, metric: Boolean) {
    val video = state.workout?.video
    val playing = !state.paused
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp)) {
        TopBar(state, actions)
        Spacer(Modifier.height(24.dp))
        if (video == null) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metrics(state, actions, compact = false)
            }
            StatsRow(state, metric, actions)
            BottomRow(state, actions)
        } else {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(16 / 9f, matchHeightConstraintsFirst = true).clip(CardShape).background(Color.Black)) {
                        when (video) {
                            is Video.YouTube -> YouTubeVideo(video.id, playing, Modifier.fillMaxSize())
                            is Video.File -> FileVideo(videoLocation(video), playing, Modifier.fillMaxSize())
                        }
                    }
                }
                Column(Modifier.width(440.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metrics(state, actions, compact = true)
                }
            }
            BottomRow(state, actions)
        }
    }
}

@Composable
private fun TopBar(state: RideState, actions: RideActions) {
    Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.title, style = Type.Heading, fontSize = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    state.notStarted -> "Clip in and press Start"
                    state.paused -> "Paused"
                    state.upNext != null -> "Up next in your stack: ${state.upNext.name}"
                    state.workout == null -> "Free ride"
                    Piece.CLOCK in actions.hidden -> "Following class targets"
                    else -> "${clock(state.secondsLeft ?: 0)} left"
                },
                style = Type.BodyDim,
                color = if (state.paused && !state.notStarted) Palette.Accent else Palette.Dim,
            )
        }
        if (Piece.CLOCK in actions.hidden) {
            QuietButton("Clock", { actions.hide(Piece.CLOCK) }, icon = Glyphs.Up)
        } else {
            Text(clock(state.totals.seconds), style = Numeric, fontSize = 96.sp,
                modifier = Modifier.clip(ButtonShape).clickable(onClickLabel = "Minimize clock") { actions.hide(Piece.CLOCK) })
        }
        Spacer(Modifier.width(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.openCamera?.let { QuietButton("Nanit", it) }
            actions.playGame?.let { QuietButton("RuneScape", it) }
            HeartPill(state.heartRate, state.maxHeartRate, Piece.HEART_RATE in actions.hidden) { actions.hide(Piece.HEART_RATE) }
            when {
                state.notStarted -> {
                    QuietButton("Cancel", actions.finish)
                    PrimaryButton("Start", actions.togglePause, icon = Glyphs.Play)
                }
                state.paused -> {
                    QuietButton("Finish", actions.finish)
                    PrimaryButton("Resume", actions.togglePause, icon = Glyphs.Play)
                }
                else -> QuietButton("Pause", actions.togglePause, icon = Glyphs.Pause)
            }
        }
    }
}

@Composable
private fun HeartPill(bpm: Int?, max: Int, minimized: Boolean, onTap: () -> Unit) {
    if (bpm == null) return
    if (minimized) {
        QuietButton("HR", onTap, icon = Glyphs.Heart)
        return
    }
    val share = bpm.toFloat() / max
    val color = when {
        share >= 0.9f -> Palette.zone(5)
        share >= 0.8f -> Palette.zone(4)
        share >= 0.7f -> Palette.zone(3)
        share >= 0.6f -> Palette.zone(2)
        else -> Palette.zone(1)
    }
    Row(
        Modifier.height(84.dp).clip(ButtonShape).background(Palette.Surface)
            .clickable(onClickLabel = "Minimize heart rate", onClick = onTap).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.Heart, "Heart rate", Modifier.size(32.dp), tint = Palette.Heart)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(10.dp).clip(ChipShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(bpm.toString(), style = Numeric, fontSize = 36.sp)
    }
}

@Composable
private fun RowScope.Metrics(state: RideState, actions: RideActions, compact: Boolean) {
    MetricColumnOrRow(state, actions, compact) { minimized, content ->
        content(if (minimized) Modifier.width(240.dp).height(132.dp).align(Alignment.Bottom) else Modifier.weight(1f))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Metrics(state: RideState, actions: RideActions, compact: Boolean) {
    MetricColumnOrRow(state, actions, compact) { minimized, content ->
        content(if (minimized) Modifier.fillMaxWidth().height(100.dp) else Modifier.weight(1f))
    }
}

/** The three live metrics, laid out by the caller's scope. */
@Composable
private fun MetricColumnOrRow(
    state: RideState,
    actions: RideActions,
    compact: Boolean,
    place: @Composable (Boolean, @Composable (Modifier) -> Unit) -> Unit,
) {
    val segment = state.position?.segment
    val cadence = state.telemetry.cadenceRpm
    val power = state.telemetry.powerWatts
    val resistance = state.telemetry.resistance
    val cadenceTarget = segment?.cadence
    val resistanceTarget = segment?.resistance
    val wattTarget = state.targetWatts
    val targetZone = segment?.zone ?: wattTarget?.let { PowerZones.zoneOf(it, state.ftp) }
    val liveZone = PowerZones.zoneOf(power, state.ftp)
    val running = !state.paused

    place(Piece.CADENCE in actions.hidden) { modifier ->
        MetricTile(
            label = "Cadence",
            onHide = { actions.hide(Piece.CADENCE) },
            value = cadence.toString(),
            unit = "rpm",
            footer = cadenceTarget?.let { "Target ${it.first}–${it.last}" } ?: "Avg ${state.totals.avgCadence}",
            footerColor = if (cadenceTarget != null && cadence !in cadenceTarget && running) Palette.Accent else Palette.Dim,
            gauge = { RangeGauge(cadence, cadenceTarget, 40, 130, Modifier.fillMaxWidth()) },
            compact = compact,
            modifier = modifier,
            minimized = Piece.CADENCE in actions.hidden,
        )
    }
    place(Piece.OUTPUT in actions.hidden) { modifier ->
        MetricTile(
            label = "Output",
            onHide = { actions.hide(Piece.OUTPUT) },
            value = power.toString(),
            unit = "watts",
            footer = when {
                wattTarget != null -> "Target $wattTarget W"
                compact -> "Avg ${state.totals.avgPower} W  ·  ${state.totals.kilojoules.toInt()} kJ"
                else -> "Avg ${state.totals.avgPower} W"
            },
            footerColor = Palette.Dim,
            badge = { ZoneBadge(liveZone) },
            gauge = {
                val range = targetZone?.let { PowerZones.watts(it, state.ftp) }
                RangeGauge(power, range, 0, state.ftp * 2, Modifier.fillMaxWidth(), color = targetZone?.let(Palette::zone) ?: Palette.Accent)
            },
            toggle = if (segment?.powerShare != null) {
                { Toggle("ERG", state.erg) { actions.setErg(!state.erg) } }
            } else null,
            compact = compact,
            modifier = modifier,
            minimized = Piece.OUTPUT in actions.hidden,
        )
    }
    place(Piece.RESISTANCE in actions.hidden) { modifier ->
        MetricTile(
            label = "Resistance",
            onHide = { actions.hide(Piece.RESISTANCE) },
            value = resistance.toString(),
            unit = "%",
            footer = resistanceTarget?.let { if (it.first == it.last) "Target ${it.first}" else "Target ${it.first}–${it.last}" }
                ?: "Avg ${state.totals.avgResistance}",
            footerColor = if (resistanceTarget != null && resistance !in resistanceTarget && running && !state.autoFollow) Palette.Accent else Palette.Dim,
            gauge = { RangeGauge(resistance, resistanceTarget, 0, 100, Modifier.fillMaxWidth()) },
            before = { Nudge(-1, actions.nudge, compact) },
            after = { Nudge(1, actions.nudge, compact) },
            toggle = if (resistanceTarget != null) {
                { Toggle("Auto", state.autoFollow) { actions.setAutoFollow(!state.autoFollow) } }
            } else null,
            compact = compact,
            modifier = modifier,
            minimized = Piece.RESISTANCE in actions.hidden,
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    onHide: () -> Unit,
    value: String,
    unit: String,
    footer: String,
    footerColor: Color,
    gauge: @Composable () -> Unit,
    compact: Boolean,
    modifier: Modifier,
    minimized: Boolean,
    badge: @Composable () -> Unit = {},
    toggle: (@Composable () -> Unit)? = null,
    before: @Composable () -> Unit = {},
    after: @Composable () -> Unit = {},
) {
    if (minimized) {
        MinimizedTile(label, "$value $unit", modifier, onHide)
        return
    }
    val numeral: TextUnit = if (compact) 80.sp else 184.sp
    Column(
        modifier.fillMaxWidth().fillMaxHeight().clip(CardShape).background(Palette.Surface)
            .clickable(onClickLabel = "Minimize $label", onClick = onHide)
            .padding(horizontal = if (compact) 24.dp else 32.dp, vertical = if (compact) 18.dp else 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.Label)
            Spacer(Modifier.width(12.dp))
            badge()
            Spacer(Modifier.weight(1f))
            toggle?.invoke()
        }
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            before()
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, style = Numeric, fontSize = numeral, maxLines = 1)
                    if (!compact) {
                        Spacer(Modifier.width(8.dp))
                        Text(unit, style = Type.Label, modifier = Modifier.padding(bottom = 34.dp))
                    }
                }
            }
            after()
        }
        if (!compact) {
            gauge()
            Spacer(Modifier.height(16.dp))
        }
        Text(footer, style = Type.Label, color = footerColor, fontSize = if (compact) 20.sp else 24.sp)
    }
}

/** The same metric stays live in a small card with a direct way to restore it. */
@Composable
private fun MinimizedTile(label: String, value: String?, modifier: Modifier, onExpand: () -> Unit) {
    Column(modifier.clip(CardShape).background(Palette.Surface)
        .clickable(onClickLabel = "Expand $label", onClick = onExpand).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Glyphs.Up, "Expand $label", Modifier.size(24.dp), tint = Palette.Dim)
        }
        value?.let { Text(it, style = Numeric, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** One point per tap, with a five-point button tucked just below. */
@Composable
private fun Nudge(direction: Int, nudge: (Int) -> Unit, compact: Boolean) {
    val icon = if (direction < 0) Glyphs.Minus else Glyphs.Plus
    val word = if (direction < 0) "Less" else "More"
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)) {
        RoundButton(icon, "$word resistance", { nudge(direction) }, size = if (compact) 64.dp else 96.dp)
        Box(
            Modifier.clip(ChipShape).background(Palette.Raised).clickable { nudge(direction * RESISTANCE_BIG_STEP) }
                .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 6.dp else 10.dp),
        ) {
            Text(
                (if (direction < 0) "−" else "+") + RESISTANCE_BIG_STEP,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 18.sp else 24.sp,
                color = Palette.Text,
            )
        }
    }
}

@Composable
private fun ZoneBadge(zone: Int) {
    Text(
        "Z$zone",
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = Palette.Background,
        modifier = Modifier.clip(ChipShape).background(Palette.zone(zone)).padding(horizontal = 12.dp, vertical = 3.dp),
    )
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(ChipShape).background(if (on) Palette.Accent else Palette.Raised).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.Bolt, null, Modifier.size(20.dp), tint = if (on) Palette.OnAccent else Palette.Dim)
        Spacer(Modifier.width(6.dp))
        Text("$label ${if (on) "on" else "off"}", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = if (on) Palette.OnAccent else Palette.Dim)
    }
}

@Composable
private fun StatsRow(state: RideState, metric: Boolean, actions: RideActions) {
    val hidden = actions.hidden
    val best = state.versusBest
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        val mph = speedMph(state.telemetry.powerWatts)
        Stat("Speed", oneDecimal(if (metric) mph * 1.609344 else mph), if (metric) "km/h" else "mph",
            minimized = Piece.SPEED in hidden) { actions.hide(Piece.SPEED) }
        run {
            val (distance, unit) = distance(state.totals.miles, metric)
            Stat("Distance", distance, unit, minimized = Piece.DISTANCE in hidden) { actions.hide(Piece.DISTANCE) }
        }
        Stat("Total output", state.totals.kilojoules.toInt().toString(), "kJ", minimized = Piece.WORK in hidden) { actions.hide(Piece.WORK) }
        Stat("Calories", state.totals.calories.toString(), "kcal", minimized = Piece.CALORIES in hidden) { actions.hide(Piece.CALORIES) }
        if (best != null) {
            Stat(
                "vs your best",
                (if (best >= 0) "+" else "−") + oneDecimal(abs(best)),
                "kJ",
                color = if (best >= 0) Palette.Good else Palette.Accent,
                minimized = Piece.BEST in hidden,
            ) { actions.hide(Piece.BEST) }
        }
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String, unit: String?, color: Color = Palette.Text,
    minimized: Boolean, onHide: () -> Unit) {
    if (minimized) {
        MinimizedTile(label, "$value ${unit.orEmpty()}".trim(), Modifier.width(200.dp).height(94.dp), onHide)
        return
    }
    Row(
        Modifier.weight(1f).clip(CardShape).background(Palette.Surface).clickable(onClick = onHide).padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, Modifier.weight(1f).padding(bottom = 6.dp), style = Type.Label)
        Text(value, style = Numeric, fontSize = 44.sp, color = color)
        if (unit != null) {
            Spacer(Modifier.width(8.dp))
            Text(unit, Modifier.padding(bottom = 6.dp), style = Type.Label)
        }
    }
}

@Composable
private fun BottomRow(state: RideState, actions: RideActions) {
    val workout = state.workout
    val position = state.position
    val music = actions.music
    val showPlan = workout == null || position != null
    val showMusic = music.available
    if (!showPlan && !showMusic) return
    Row(Modifier.fillMaxWidth().padding(top = 20.dp).height(148.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!showPlan) {
            Spacer(Modifier.weight(1f))
        } else if (Piece.PLAN in actions.hidden) {
            MinimizedTile(if (workout == null) "Free ride" else "Class plan", position?.segment?.name,
                Modifier.width(300.dp).height(100.dp).align(Alignment.Bottom)) { actions.hide(Piece.PLAN) }
            Spacer(Modifier.weight(1f))
        } else if (workout != null && position != null) {
            val next = workout.segments.getOrNull(position.index + 1)
            Card(Modifier.weight(1f).fillMaxHeight(), onClick = { actions.hide(Piece.PLAN) }) {
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(430.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(position.segment.name, style = Type.Heading, fontSize = 32.sp, maxLines = 1)
                            if (Piece.CLOCK !in actions.hidden) {
                                Spacer(Modifier.width(18.dp))
                                Text(clock(ceil(position.secondsLeft).toInt()), style = Numeric, fontSize = 32.sp, color = Palette.Accent)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            position.segment.cue ?: next?.let { "Next: ${it.name}" } ?: "Last segment",
                            style = Type.BodyDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(28.dp))
                    WorkoutProfile(workout, Modifier.weight(1f).fillMaxHeight(), elapsed = state.totals.seconds.toDouble())
                }
            }
        } else if (state.workout == null) {
            Card(Modifier.weight(1f).fillMaxHeight(), onClick = { actions.hide(Piece.PLAN) }) {
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(360.dp)) {
                        Text("Free ride", style = Type.Heading, fontSize = 32.sp)
                        Text("Max ${state.totals.maxPower} W  ·  last 5 minutes", style = Type.BodyDim)
                    }
                    Spacer(Modifier.width(28.dp))
                    if (state.recentPower.size > 1) {
                        LineChart(state.recentPower, Palette.Accent, Modifier.weight(1f).fillMaxHeight(), average = state.totals.avgPower, ceiling = state.ftp * 3 / 2)
                    }
                }
            }
        }
        if (showMusic) {
            if (Piece.MUSIC in actions.hidden) {
                MinimizedTile("Music", music.track?.title, Modifier.width(300.dp).height(100.dp).align(Alignment.Bottom)) {
                    actions.hide(Piece.MUSIC)
                }
            } else {
                MusicBar(
                    music.track, music.toggle, music.next, music.previous, music.open, Modifier.width(600.dp).fillMaxHeight(),
                    onHide = { actions.hide(Piece.MUSIC) },
                )
            }
        }
    }
}
