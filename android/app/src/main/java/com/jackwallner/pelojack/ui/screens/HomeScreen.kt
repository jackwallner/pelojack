package com.jackwallner.pelojack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.jackwallner.pelojack.profile.Stats
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.CardShape
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Numeric
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.ProgressRing
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.Route
import com.jackwallner.pelojack.ui.SectionLabel
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.WorkoutCard
import com.jackwallner.pelojack.ui.WorkoutHistory
import com.jackwallner.pelojack.ui.WorkoutProfile
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(app: PelojackApp, nav: Navigator) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val rides by app.rides.rides.collectAsStateWithLifecycle()
    val workouts by app.library.workouts.collectAsStateWithLifecycle()
    val stats = Stats(rides)
    val today = LocalDate.now()
    val history = historyByWorkout(rides)
    val stack = settings.stack.mapNotNull(app.library::find)
    val program = app.library.programs.firstOrNull { it.id == settings.programId }

    Page(
        title = "${greeting()}, ${settings.name}",
        subtitle = formatDate(System.currentTimeMillis(), "EEEE, MMMM d"),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().height(248.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            WeekCard(stats.ridesInWeekOf(today), settings.weeklyGoal, Modifier.weight(1f).fillMaxHeight())
            StreakCard(stats.weekStreak(today), stats.ridesInWeekOf(today) > 0, Modifier.weight(1f).fillMaxHeight())
            JustRideCard(Modifier.weight(1.25f).fillMaxHeight()) { app.ride.open(null) }
        }

        if (app.game.cameraAvailable) {
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nanit camera", style = Type.Heading)
                        Text("Open the live feed, then tap Float camera to watch while riding or playing.", style = Type.BodyDim)
                    }
                    QuietButton("Open camera", app.game::watchCamera)
                }
            }
        }

        if (app.game.installed) {
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ride with RuneScape", style = Type.Heading)
                        Text("Play with live ride metrics. Choose a class to follow its targets.", style = Type.BodyDim)
                    }
                    QuietButton("Choose a class", { nav.select(Route.Classes) })
                    Spacer(Modifier.width(16.dp))
                    PrimaryButton("Just Ride", { app.game.play() }, icon = Glyphs.Play)
                }
            }
        }

        if (stack.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyphs.Stack, null, Modifier.size(40.dp), tint = Palette.Accent)
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your stack", style = Type.Heading)
                        Text(
                            "${stack.size} ${if (stack.size == 1) "class" else "classes"}  ·  ${stack.sumOf { it.minutes }} min  ·  " +
                                stack.joinToString(", ") { it.name },
                            style = Type.BodyDim,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    QuietButton("Clear", { app.settings.update { it.copy(stack = emptyList()) } })
                    Spacer(Modifier.width(16.dp))
                    PrimaryButton("Ride stack", app.ride::playStack, icon = Glyphs.Play)
                }
            }
        }

        if (program != null) {
            val done = programProgress(program, settings, rides)
            val next = program.workoutIds.getOrNull(done)?.let(app.library::find)
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth(), onClick = { nav.push(Route.ProgramDetail(program.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(done.toFloat() / program.workoutIds.size, Modifier.size(88.dp), stroke = 9.dp)
                        Text("$done/${program.workoutIds.size}", style = Numeric, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(program.name, style = Type.Heading)
                        Text(
                            if (next == null) "Program complete" else {
                                val (week, ride) = program.weekAndRide(done)
                                "Week $week, ride $ride  ·  ${next.name}"
                            },
                            style = Type.BodyDim,
                        )
                    }
                    if (next != null) {
                        WorkoutProfile(next, Modifier.width(260.dp).height(60.dp))
                        Spacer(Modifier.width(28.dp))
                        PrimaryButton("Continue", { app.ride.open(next, programId = program.id) }, icon = Glyphs.Play)
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
        SectionLabel("Recommended for you") {
            Text("All classes", style = Type.Label, modifier = Modifier.clip(CardShape).clickable { nav.select(Route.Classes) }.padding(8.dp))
        }
        Spacer(Modifier.height(20.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(end = 20.dp)) {
            items(recommend(workouts, rides, settings.bookmarks), key = { it.id }) { workout ->
                WorkoutCard(
                    workout,
                    history[workout.id] ?: WorkoutHistory(),
                    workout.id in settings.bookmarks,
                    { nav.push(Route.ClassDetail(workout.id)) },
                    Modifier.width(430.dp),
                )
            }
        }

        if (rides.isNotEmpty()) {
            Spacer(Modifier.height(40.dp))
            SectionLabel("Recent rides") {
                Text("All history", style = Type.Label, modifier = Modifier.clip(CardShape).clickable { nav.select(Route.Profile) }.padding(8.dp))
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                rides.take(3).forEach { ride ->
                    Card(Modifier.weight(1f), onClick = { nav.push(Route.RideDetail(ride.startedAt)) }) {
                        Text(formatDate(ride.startedAt, "EEEE, MMM d"), style = Type.Small)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ride.title, style = Type.Heading, fontSize = 24.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            if (stats.isRecord(ride)) {
                                Spacer(Modifier.width(10.dp))
                                Icon(Glyphs.Trophy, "Personal record", Modifier.size(24.dp), tint = Palette.Accent)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            Text(com.jackwallner.pelojack.ui.clock(ride.totals.seconds), style = Numeric, fontSize = 26.sp)
                            Text("${ride.totals.kilojoules.toInt()} kJ", style = Numeric, fontSize = 26.sp)
                            Text("${ride.totals.avgPower} W avg", style = Numeric, fontSize = 26.sp, color = Palette.Dim)
                        }
                    }
                }
                repeat(3 - rides.take(3).size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WeekCard(rides: Int, goal: Int, modifier: Modifier) {
    Card(modifier) {
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(rides.toFloat() / goal, Modifier.size(150.dp), color = if (rides >= goal) Palette.Good else Palette.Accent)
                Text("$rides/$goal", style = Numeric, fontSize = 36.sp)
            }
            Spacer(Modifier.width(28.dp))
            Column {
                Text("This week", style = Type.Label)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        rides >= goal -> "Goal met"
                        rides == 0 -> "No rides yet"
                        else -> "${goal - rides} to go"
                    },
                    style = Type.Heading,
                )
            }
        }
    }
}

@Composable
private fun StreakCard(weeks: Int, rodeThisWeek: Boolean, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.Flame, null, Modifier.size(40.dp), tint = if (weeks > 0) Palette.Accent else Palette.Faint)
                Spacer(Modifier.width(12.dp))
                Text("$weeks", style = Numeric, fontSize = 64.sp)
                Spacer(Modifier.width(12.dp))
                Text(if (weeks == 1) "week" else "weeks", style = Type.Heading, color = Palette.Dim)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    weeks == 0 -> "Ride this week to start a streak"
                    rodeThisWeek -> "Weekly streak"
                    else -> "Ride this week to keep it going"
                },
                style = Type.BodyDim,
            )
        }
    }
}

@Composable
private fun JustRideCard(modifier: Modifier, onClick: () -> Unit) {
    Card(modifier, onClick = onClick, color = Palette.Accent) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(Glyphs.Bike, null, Modifier.size(56.dp), tint = Palette.OnAccent)
            Column {
                Text("Just Ride", style = Type.Display, color = Palette.OnAccent)
                Text("Free ride with live metrics", style = Type.Body, color = Palette.OnAccent.copy(alpha = 0.75f))
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
