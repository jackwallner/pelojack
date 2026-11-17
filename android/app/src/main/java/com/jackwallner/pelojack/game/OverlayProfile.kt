package com.jackwallner.pelojack.game

import com.jackwallner.pelojack.workout.Segment
import com.jackwallner.pelojack.workout.Workout
import kotlin.math.roundToInt

/** Read-only workout projection for the ride overlay. */
data class OverlayProfile(val elapsed: Int, val current: Workout.Position?, val next: Segment?) {
    companion object {
        fun targets(segment: Segment): String = buildList {
            segment.cadence?.let { add("${it.first}–${it.last} rpm") }
            segment.resistance?.let { add("R ${it.first}–${it.last}") }
            if (segment.ftpShare != null) add("${(segment.ftpShare * 100).roundToInt()}% FTP")
            else segment.zone?.let { add("Z$it") }
        }.joinToString(" · ").ifEmpty { "No prescribed target" }

        fun at(workout: Workout, elapsed: Int): OverlayProfile {
            val bounded = elapsed.coerceIn(0, workout.totalSeconds.coerceAtLeast(0))
            val current = workout.positionAt(bounded.toDouble())
            return OverlayProfile(bounded, current, current?.let { workout.segments.getOrNull(it.index + 1) })
        }
    }
}
