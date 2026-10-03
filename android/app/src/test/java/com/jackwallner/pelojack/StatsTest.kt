package com.jackwallner.pelojack

import com.jackwallner.pelojack.profile.Settings
import com.jackwallner.pelojack.profile.Stats
import com.jackwallner.pelojack.ride.RideSummary
import com.jackwallner.pelojack.ride.Totals
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private val zone = ZoneOffset.UTC

    private fun ride(date: String, minutes: Int, kj: Double, workout: String? = null, planned: Int? = null) = RideSummary(
        startedAt = LocalDate.parse(date).atTime(7, 0).toInstant(zone).toEpochMilli(),
        title = workout ?: "Just Ride",
        totals = Totals(seconds = minutes * 60, kilojoules = kj),
        workoutId = workout,
        plannedSeconds = planned,
    )

    @Test
    fun countsWeeklyStreaksMondayToSunday() {
        val rides = listOf(ride("2026-09-07", 20, 150.0), ride("2026-09-16", 20, 150.0), ride("2026-09-20", 20, 150.0), ride("2026-09-22", 20, 150.0))
        val stats = Stats(rides, zone)
        assertEquals(3, stats.weekStreak(LocalDate.parse("2026-09-24")))
        // A new week with no ride yet does not break the streak.
        assertEquals(3, stats.weekStreak(LocalDate.parse("2026-09-29")))
        assertEquals(0, stats.weekStreak(LocalDate.parse("2026-10-06")))
        assertEquals(2, stats.ridesInWeekOf(LocalDate.parse("2026-09-18")))
        assertEquals(3, stats.longestWeekStreak())
    }

    @Test
    fun recordsAreKeptPerClassLength() {
        val first = ride("2026-09-01", 20, 180.0, "intervals-20", 1200)
        val better = ride("2026-09-08", 20, 195.0, "intervals-20", 1200)
        val worse = ride("2026-09-10", 20, 170.0, "hills-20", 1200)
        val free = ride("2026-09-12", 23, 230.0)
        val stats = Stats(listOf(worse, first, better, free), zone)
        assertTrue(stats.isRecord(first))
        assertTrue(stats.isRecord(better))
        assertFalse(stats.isRecord(worse))
        // A 23 minute free ride counts toward 20 minutes.
        assertTrue(stats.isRecord(free))
        assertEquals(free, stats.records.single { it.minutes == 20 }.ride)
        assertEquals(better, stats.bestOf("intervals-20"))
        assertEquals(first, stats.bestOf("intervals-20", before = better.startedAt))
    }

    @Test
    fun unfinishedWorkoutsDoNotSetRecords() {
        val quit = ride("2026-09-01", 10, 120.0, "climb-30", 1800)
        assertEquals(null, Stats.bucket(quit))
        assertFalse(Stats(listOf(quit), zone).isRecord(quit))
        assertEquals(null, Stats.bucket(ride("2026-09-01", 4, 30.0)))
    }

    @Test
    fun badgesTrackMilestones() {
        val rides = (1..10).map { ride("2026-09-%02d".format(it), 20, 200.0) }
        val badges = Stats(rides, zone).badges(LocalDate.parse("2026-09-11")).associateBy { it.title }
        assertTrue(badges.getValue("First ride").earned)
        assertTrue(badges.getValue("10 rides").earned)
        assertFalse(badges.getValue("25 rides").earned)
        assertTrue(badges.getValue("2-week streak").earned)
        assertTrue(badges.getValue("1k kJ").earned)
    }

    @Test
    fun importedJustRidesDoNotSetRecords() {
        val justRide = ride("2026-09-01", 57, 833.0).copy(source = "peloton")
        val freeRide = ride("2026-09-02", 47, 400.0)
        val stats = Stats(listOf(justRide, freeRide), zone)
        assertFalse(stats.isRecord(justRide))
        assertTrue(stats.isRecord(freeRide))
        assertEquals(400.0, stats.records.single { it.minutes == 45 }.ride.totals.kilojoules, 0.0)
    }

    @Test
    fun settingsSurviveAJsonRoundTrip() {
        val settings = Settings(
            name = "J", ftp = 215, metric = true, erg = false, heartRateAddress = "AA:BB", heartRateName = "Strap",
            bookmarks = setOf("a", "b"), stack = listOf("c", "a"), programId = "p", programStartedAt = 42,
            hiddenOnRide = setOf("clock", "cadence"), hideSystemBars = false,
        )
        assertEquals(settings, Settings.fromJson(settings.toJson()))
        assertEquals(Settings(), Settings.fromJson(Settings().toJson()))
        assertEquals(Settings(), Settings.fromJson(org.json.JSONObject()))
    }
}
