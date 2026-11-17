package com.jackwallner.pelojack.game

import org.junit.Assert.*
import org.junit.Test

class OverlayPolicyTest {
    @Test fun `only an existing ride warrants protection`() {
        for (front in listOf(false, true)) for (permission in listOf(false, true)) {
            val idle = OverlayPolicy.decide(front, permission, hasRide = false)
            assertFalse(idle.protect)
            assertFalse(idle.show)
            val riding = OverlayPolicy.decide(front, permission, hasRide = true)
            assertTrue(riding.protect)
            assertEquals(!front && permission, riding.show)
        }
    }
}
