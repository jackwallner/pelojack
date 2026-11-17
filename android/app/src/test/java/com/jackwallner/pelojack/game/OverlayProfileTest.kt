package com.jackwallner.pelojack.game

import com.jackwallner.pelojack.workout.Category
import com.jackwallner.pelojack.workout.Segment
import com.jackwallner.pelojack.workout.Workout
import org.junit.Assert.*
import org.junit.Test

class OverlayProfileTest {
    private val workout = Workout("imported", "Imported class", "", Category.Intervals, listOf(
        Segment("Warm up", 30, resistance = 20..30, cadence = 70..80),
        Segment("Push", 60, zone = 4, cadence = 85..95),
    ))

    @Test fun `target line includes all available targets without inventing missing metrics`() {
        assertEquals("70–80 rpm · R 20–30", OverlayProfile.targets(workout.segments[0]))
        assertEquals("85–95 rpm · Z4", OverlayProfile.targets(workout.segments[1]))
        assertEquals("No prescribed target", OverlayProfile.targets(Segment("Easy", 10)))
    }

    @Test fun `fractional FTP targets round to nearest percent with half up`() {
        assertEquals("73% FTP", OverlayProfile.targets(Segment("Tempo", 10, ftpShare = .725)))
    }

    @Test fun `profile clamps elapsed and switches current and next exactly at interval boundary`() {
        val before = OverlayProfile.at(workout, -2)
        assertEquals(0, before.elapsed)
        assertEquals("Warm up", before.current?.segment?.name)
        assertEquals("Push", before.next?.name)
        assertEquals(30.0, before.current!!.secondsLeft, 0.0)
        val boundary = OverlayProfile.at(workout, 30)
        assertEquals("Push", boundary.current?.segment?.name)
        assertEquals(60.0, boundary.current!!.secondsLeft, 0.0)
        assertNull(boundary.next)
        val ended = OverlayProfile.at(workout, 999)
        assertEquals(90, ended.elapsed)
        assertNull(ended.current)
        assertNull(ended.next)
    }
}
