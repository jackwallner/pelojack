package com.jackwallner.pelojack.workout

import kotlin.math.roundToInt

/** Peloton's seven power zones, as shares of FTP. */
object PowerZones {
    private val lowerBounds = listOf(0.0, 0.55, 0.76, 0.91, 1.06, 1.21, 1.51)
    private val midpoints = listOf(0.45, 0.65, 0.83, 0.98, 1.13, 1.35, 1.65)
    val names = listOf("Active Recovery", "Endurance", "Tempo", "Threshold", "VO2 Max", "Anaerobic", "Neuromuscular")

    fun zoneOf(watts: Int, ftp: Int): Int {
        if (ftp <= 0) return 1
        val share = watts.toDouble() / ftp
        return lowerBounds.indexOfLast { share >= it } + 1
    }

    /** Share of FTP to hold when a workout asks for [zone] without an exact number. */
    fun midShare(zone: Int): Double = midpoints[zone.coerceIn(1, 7) - 1]

    /** Watts range of [zone] for a rider with [ftp]. Zone 7 is open ended, so it is capped for display. */
    fun watts(zone: Int, ftp: Int): IntRange {
        val index = zone.coerceIn(1, 7) - 1
        val low = (lowerBounds[index] * ftp).roundToInt()
        val high = if (index == 6) (2.0 * ftp).roundToInt() else (lowerBounds[index + 1] * ftp).roundToInt() - 1
        return low..high
    }

    /** Rough share of FTP a resistance level demands at a normal cadence; used to rate workouts. */
    fun shareForResistance(resistance: Int): Double = (0.55 + (resistance - 30) * 0.0215).coerceIn(0.3, 1.8)
}
