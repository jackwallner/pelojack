package com.jackwallner.pelojack.ui.screens

import com.jackwallner.pelojack.profile.Settings
import com.jackwallner.pelojack.ride.RideSummary
import com.jackwallner.pelojack.ui.WorkoutHistory
import com.jackwallner.pelojack.workout.Program
import com.jackwallner.pelojack.workout.Workout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun historyByWorkout(rides: List<RideSummary>): Map<String, WorkoutHistory> =
    rides.filter { it.workoutId != null }.groupBy { it.workoutId!! }.mapValues { (_, list) ->
        WorkoutHistory(list.size, list.maxOf { it.totals.kilojoules }.toInt())
    }

/** Rides completed in the active program since it was started. */
fun programProgress(program: Program, settings: Settings, rides: List<RideSummary>): Int =
    if (settings.programId != program.id) 0
    else rides.count { it.programId == program.id && it.startedAt >= settings.programStartedAt }
        .coerceAtMost(program.workoutIds.size)

/** Bookmarked first, then never ridden, then least recently ridden. */
fun recommend(workouts: List<Workout>, rides: List<RideSummary>, bookmarks: Set<String>): List<Workout> {
    val lastRidden = rides.filter { it.workoutId != null }.groupBy { it.workoutId }.mapValues { (_, list) -> list.maxOf { it.startedAt } }
    return workouts
        .filter { it.totalSeconds >= 15 * 60 }
        .sortedWith(compareByDescending<Workout> { it.id in bookmarks }.thenBy { lastRidden[it.id] ?: 0L })
        .take(8)
}

/** "Hannah Corbin · Peloton" for an imported ride; null for one Pelojack recorded. */
fun rideOrigin(ride: RideSummary): String? =
    listOfNotNull(ride.instructor, if (ride.source == "peloton") "Peloton" else null)
        .joinToString("  ·  ").ifEmpty { null }

fun formatDate(epochMillis: Long, pattern: String = "EEE, MMM d"): String =
    SimpleDateFormat(pattern, Locale.US).format(Date(epochMillis))

fun distance(miles: Double, metric: Boolean): Pair<String, String> =
    if (metric) com.jackwallner.pelojack.ui.oneDecimal(miles * 1.609344) to "km"
    else com.jackwallner.pelojack.ui.oneDecimal(miles) to "mi"

fun hoursAndMinutes(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
