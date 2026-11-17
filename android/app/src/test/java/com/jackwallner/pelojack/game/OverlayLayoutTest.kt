package com.jackwallner.pelojack.game

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class OverlayLayoutTest {
    @Test fun `overlay occupies three quarters of display instead of half`() {
        assertEquals(1440, OverlayLayout.windowWidth(1920))
        assertEquals(960, OverlayLayout.windowWidth(1280))
        assertEquals(1440, OverlayLayout.DESIGN_WIDTH)
        assertEquals(92, OverlayLayout.COMPACT_BODY_HEIGHT)
    }

    @Test fun `compact strip puts graph and both targets side by side while keeping live metrics`() {
        val strip = File("src/main/java/com/jackwallner/pelojack/game/RideStrip.kt").readText()
        assertTrue("Compact needs a horizontal body", strip.contains("CompactProfile("))
        val compact = strip.substringAfter("private fun CompactProfile(").substringBefore("private fun HeaderAction(")
        assertTrue(compact.contains("Row("))
        assertTrue(compact.contains("WorkoutProfile("))
        assertTrue(compact.contains("OverlayLayout.COMPACT_BODY_HEIGHT"))
        assertTrue("Wrapped targets must grow instead of clipping in a fixed-height row", compact.contains("heightIn(min = OverlayLayout.COMPACT_BODY_HEIGHT.dp)"))
        assertTrue(compact.contains("OverlayProfile.targets("))
        assertTrue(compact.contains("secondsLeft"))
        assertTrue(compact.contains("Next"))
        assertTrue(compact.contains("next.seconds"))
        assertTrue(strip.contains("it.telemetry.cadenceRpm"))
        assertTrue(strip.contains("it.telemetry.resistance"))
        assertTrue(strip.contains("it.telemetry.powerWatts"))
        assertTrue(strip.contains("it.heartRate"))
        assertTrue(strip.contains("clock(it.secondsLeft ?: elapsedSeconds)"))
        val window = File("src/main/java/com/jackwallner/pelojack/game/WorkoutOverlayWindow.kt").readText()
        assertTrue(window.contains("OverlayLayout.windowWidth(size().x)"))
        assertTrue(strip.contains("OverlayLayout.DESIGN_WIDTH.dp"))
    }
}
