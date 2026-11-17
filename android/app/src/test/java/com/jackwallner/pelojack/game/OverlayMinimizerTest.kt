package com.jackwallner.pelojack.game

import org.junit.Assert.*
import org.junit.Test

/** No Android runtime or ride controller: minimizing has presentation-only capabilities. */
class OverlayMinimizerTest {
    @Test fun `missing permission or external home or ride keeps controls in foreground`() {
        for ((ride, permission, home) in listOf(Triple(false, true, true), Triple(true, false, true), Triple(true, true, false))) {
            val events = mutableListOf<String>()
            val result = OverlayMinimizer.minimize(ride, permission, home,
                protect = { events += "protect"; true }, show = { events += "show"; true },
                openHome = { events += "home"; true }, hide = { events += "hide" })
            assertNotEquals("Minimized", result.name)
            assertTrue("Must not leave controls or open permission settings: $events", events.isEmpty())
        }
    }

    @Test fun `protection or overlay failure never launches home`() {
        for (protectOk in listOf(false, true)) {
            val events = mutableListOf<String>()
            val result = OverlayMinimizer.minimize(true, true, true,
                protect = { events += "protect"; protectOk }, show = { events += "show"; false },
                openHome = { events += "home"; true }, hide = { events += "hide" })
            assertNotEquals("Minimized", result.name)
            assertEquals(if (protectOk) listOf("protect", "show") else listOf("protect"), events)
        }
    }

    @Test fun `home launch failure removes overlay and leaves ride controls`() {
        val events = mutableListOf<String>()
        val result = OverlayMinimizer.minimize(true, true, true,
            protect = { events += "protect"; true }, show = { events += "show"; true },
            openHome = { events += "home"; false }, hide = { events += "hide" })
        assertNotEquals("Minimized", result.name)
        assertEquals(listOf("protect", "show", "home", "hide"), events)
    }

    @Test fun `only explicit minimize with controls ready opens home`() {
        val events = mutableListOf<String>()
        val result = OverlayMinimizer.minimize(
            hasRide = true, permission = true, externalHome = true,
            protect = { events += "protect"; true },
            show = { events += "show"; true },
            openHome = { events += "home"; true },
            hide = { events += "hide" },
        )
        assertEquals(OverlayMinimizer.Result.Minimized, result)
        assertEquals(listOf("protect", "show", "home"), events)
    }
}
