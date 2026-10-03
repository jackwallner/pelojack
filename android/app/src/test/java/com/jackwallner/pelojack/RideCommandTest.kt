package com.jackwallner.pelojack

import com.jackwallner.pelojack.hr.RideCommand
import org.junit.Assert.assertEquals
import org.junit.Test

/** The same byte strings are asserted by the iPhone relay's tests; change both together. */
class RideCommandTest {
    private fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }

    @Test
    fun encodesCommandsForTheRelay() {
        assertEquals("01", RideCommand.Start.encode().hex())
        assertEquals("04", RideCommand.Pause.encode().hex())
        assertEquals("05", RideCommand.Resume.encode().hex())
        assertEquals("02 d7 00 58 d2 04 00 00 d7 11 00 00", RideCommand.Metrics(215, 88, 123.4, 4567).encode().hex())
        assertEquals("03 08 07 00 00 bd 0b 00 00 e0 2e 00 00", RideCommand.End(1800, 300.5, 12_000).encode().hex())
    }
}
