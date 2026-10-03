package com.jackwallner.pelojack.profile

import com.jackwallner.pelojack.ride.RideSummary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Class lengths Peloton keeps personal records for, in minutes. */
val PR_DURATIONS = listOf(5, 10, 15, 20, 30, 45, 60, 75, 90)

data class PersonalRecord(val minutes: Int, val ride: RideSummary)

data class Badge(val title: String, val detail: String, val earned: Boolean)

/** Everything the profile shows, derived from the ride history. [rides] may be in any order. */
class Stats(rides: List<RideSummary>, private val zone: ZoneId = ZoneId.systemDefault()) {
    /** Oldest first. */
    val rides: List<RideSummary> = rides.sortedBy { it.startedAt }

    val totalSeconds: Int = this.rides.sumOf { it.totals.seconds }
    val totalKilojoules: Double = this.rides.sumOf { it.totals.kilojoules }
    val totalMiles: Double = this.rides.sumOf { it.totals.miles }
    val totalCalories: Int = this.rides.sumOf { it.totals.calories }

    fun dateOf(ride: RideSummary): LocalDate = Instant.ofEpochMilli(ride.startedAt).atZone(zone).toLocalDate()

    val rideDays: Set<LocalDate> = this.rides.map(::dateOf).toSet()

    fun ridesInWeekOf(day: LocalDate): Int {
        val start = weekStart(day)
        return this.rides.count { dateOf(it) in start..start.plusDays(6) }
    }

    /** Consecutive weeks with at least one ride. The current week does not break it until it ends. */
    fun weekStreak(today: LocalDate): Int {
        val weeks = rideDays.map(::weekStart).toSet()
        var week = weekStart(today)
        if (week !in weeks) week = week.minusWeeks(1)
        var streak = 0
        while (week in weeks) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    /** Best output per class length. */
    val records: List<PersonalRecord> = PR_DURATIONS.mapNotNull { minutes ->
        this.rides.filter { bucket(it) == minutes }.maxByOrNull { it.totals.kilojoules }?.let { PersonalRecord(minutes, it) }
    }

    /** Start times of rides that beat every earlier ride of the same length. */
    private val recordStarts: Set<Long> by lazy {
        val best = mutableMapOf<Int, Double>()
        this.rides.filter { ride ->
            val minutes = bucket(ride) ?: return@filter false
            val beaten = best[minutes]
            if (beaten == null || ride.totals.kilojoules > beaten) {
                best[minutes] = ride.totals.kilojoules
                true
            } else {
                false
            }
        }.map { it.startedAt }.toSet()
    }

    /** True when [ride] beat every earlier ride of the same length. */
    fun isRecord(ride: RideSummary): Boolean = ride.startedAt in recordStarts

    /** Best previous ride of the same workout, for racing against. */
    fun bestOf(workoutId: String?, before: Long = Long.MAX_VALUE): RideSummary? =
        if (workoutId == null) null
        else this.rides.filter { it.workoutId == workoutId && it.startedAt < before }.maxByOrNull { it.totals.kilojoules }

    fun badges(today: LocalDate): List<Badge> {
        val count = this.rides.size
        val streak = longestWeekStreak()
        val records = recordStarts.size
        return listOf(1, 10, 25, 50, 100, 250, 500, 1000).map { Badge(rideTitle(it), if (it == 1) "Your first ride" else "$it rides", count >= it) } +
            listOf(2, 4, 8, 12, 26, 52).map { Badge("$it-week streak", "$it weeks in a row", streak >= it) } +
            listOf(1, 5, 10, 25).map { Badge(if (it == 1) "First PR" else "$it PRs", if (it == 1) "A personal record" else "$it records", records >= it) } +
            listOf(1_000, 5_000, 10_000, 25_000).map {
                Badge("${it / 1000}k kJ", "%,d kJ in total".format(it), totalKilojoules >= it)
            }
    }

    fun longestWeekStreak(): Int {
        val weeks = rideDays.map(::weekStart).toSortedSet()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (week in weeks) {
            run = if (previous != null && previous.plusWeeks(1) == week) run + 1 else 1
            best = maxOf(best, run)
            previous = week
        }
        return best
    }

    private fun rideTitle(count: Int) = when (count) {
        1 -> "First ride"
        100 -> "Century"
        else -> "$count rides"
    }

    companion object {
        fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /**
         * The PR bucket a ride counts toward: a workout's own length, or for a free ride the
         * longest class length it covered.
         */
        fun bucket(ride: RideSummary): Int? {
            val planned = ride.plannedSeconds
            // Peloton never counted a Just Ride toward records, so imported ones stay out too.
            if (planned == null && ride.source == "peloton") return null
            if (planned == null) return PR_DURATIONS.lastOrNull { it <= ride.totals.seconds / 60 }
            // A workout only counts toward its length once it is finished.
            if (ride.totals.seconds < planned - 5) return null
            return PR_DURATIONS.firstOrNull { it == (planned + 30) / 60 }
        }
    }
}
