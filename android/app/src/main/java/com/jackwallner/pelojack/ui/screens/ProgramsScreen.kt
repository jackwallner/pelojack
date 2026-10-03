package com.jackwallner.pelojack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.ui.BackRow
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Numeric
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.ProgressRing
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.Route
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.WorkoutProfile
import com.jackwallner.pelojack.ui.displayZone
import com.jackwallner.pelojack.workout.Program
import com.jackwallner.pelojack.workout.Workout

@Composable
fun ProgramsScreen(app: PelojackApp, nav: Navigator) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val rides by app.rides.rides.collectAsStateWithLifecycle()
    Page("Programs", subtitle = "Multi-week plans. Ride them in order and track your progress.") {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            app.library.programs.forEach { program ->
                val workouts = program.workoutIds.mapNotNull(app.library::find)
                val active = settings.programId == program.id
                val done = programProgress(program, settings, rides)
                Card(Modifier.fillMaxWidth(), onClick = { nav.push(Route.ProgramDetail(program.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (active) Text("IN PROGRESS", style = Type.Label, color = Palette.Accent)
                            Text(program.name, style = Type.Title)
                            Spacer(Modifier.height(6.dp))
                            Text(program.description, style = Type.BodyDim)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "${program.weeks.size} weeks  ·  ${workouts.size} rides  ·  ${hoursAndMinutes(workouts.sumOf { it.totalSeconds })}",
                                style = Type.Small,
                            )
                        }
                        Spacer(Modifier.width(32.dp))
                        ProgramStrip(workouts, if (active) done else -1, Modifier.width(560.dp).height(90.dp))
                    }
                }
            }
        }
    }
}

/** One block per ride: width is length, height is difficulty, colour is the zone it mostly trains. */
@Composable
private fun ProgramStrip(workouts: List<Workout>, done: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        workouts.forEachIndexed { index, workout ->
            val color = Palette.zone(workout.mainZone())
            Box(
                Modifier
                    .weight(workout.totalSeconds.toFloat())
                    .fillMaxHeight((0.25f + 0.75f * (workout.difficulty / 10).toFloat()).coerceAtMost(1f))
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(if (index < done) color.copy(alpha = 0.35f) else color),
            )
        }
    }
}

/** The zone a workout spends the most time in. */
fun Workout.mainZone(): Int =
    segments.groupBy { it.displayZone() }.maxBy { (_, list) -> list.sumOf { it.seconds } }.key

@Composable
fun ProgramDetailScreen(app: PelojackApp, nav: Navigator, id: String) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val rides by app.rides.rides.collectAsStateWithLifecycle()
    val program: Program = app.library.programs.firstOrNull { it.id == id } ?: run {
        nav.back()
        return
    }
    val active = settings.programId == id
    val done = programProgress(program, settings, rides)
    val next = program.workoutIds.getOrNull(done)?.let(app.library::find)

    Column(Modifier.padding(start = 56.dp, end = 64.dp, top = 40.dp, bottom = 40.dp)) {
        BackRow(nav::back)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(program.name, style = Type.Display, fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text(program.description, style = Type.BodyDim)
            }
            if (active) {
                Box(contentAlignment = Alignment.Center) {
                    ProgressRing(done.toFloat() / program.workoutIds.size, Modifier.size(120.dp))
                    Text("$done/${program.workoutIds.size}", style = Numeric, fontSize = 28.sp)
                }
                Spacer(Modifier.width(32.dp))
                QuietButton("Leave", { app.settings.update { it.copy(programId = null) } })
                if (next != null) {
                    Spacer(Modifier.width(16.dp))
                    PrimaryButton("Continue", { app.ride.open(next, programId = id) }, icon = Glyphs.Play)
                }
            } else {
                PrimaryButton("Start program", {
                    app.settings.update { it.copy(programId = id, programStartedAt = System.currentTimeMillis()) }
                }, icon = Glyphs.Play)
            }
        }
        Spacer(Modifier.height(36.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            val firstIndex = program.weeks.runningFold(0) { total, week -> total + week.size }
            program.weeks.forEachIndexed { week, ids ->
                Card(Modifier.weight(1f).fillMaxHeight()) {
                    Text("Week ${week + 1}", style = Type.Heading)
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ids.forEachIndexed { offset, workoutId ->
                            val position = firstIndex[week] + offset
                            val workout = app.library.find(workoutId) ?: return@forEachIndexed
                            ProgramRide(
                                workout,
                                state = when {
                                    active && position < done -> RideState.Done
                                    active && position == done -> RideState.Next
                                    else -> RideState.Later
                                },
                            ) { nav.push(Route.ClassDetail(workout.id)) }
                        }
                    }
                }
            }
        }
    }
}

private enum class RideState { Done, Next, Later }

@Composable
private fun ProgramRide(workout: Workout, state: RideState, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onClick, color = if (state == RideState.Next) Palette.Raised else Palette.Background) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(
                    when (state) {
                        RideState.Done -> Palette.Good
                        RideState.Next -> Palette.Accent
                        RideState.Later -> Palette.Raised
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (state == RideState.Done) Icon(Glyphs.Check, null, Modifier.size(26.dp), tint = Palette.Background)
                if (state == RideState.Next) Icon(Glyphs.Play, null, Modifier.size(26.dp), tint = Palette.OnAccent)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = Type.Body, maxLines = 1)
                Text(workout.category.label, style = Type.Small)
            }
        }
        Spacer(Modifier.height(14.dp))
        WorkoutProfile(workout, Modifier.fillMaxWidth().height(44.dp))
    }
}
