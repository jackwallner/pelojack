package com.jackwallner.pelojack.hr

import kotlinx.coroutines.flow.StateFlow

enum class HeartRateLink { Off, Searching, Connected }

data class HeartRateDevice(val address: String, val name: String)

interface HeartRate {
    /** Beats per minute, or null when no monitor is sending. */
    val bpm: StateFlow<Int?>
    val link: StateFlow<HeartRateLink>
    val simulated: Boolean

    /** Tells a connected Apple Watch about the ride, so its workout follows along. */
    fun send(command: RideCommand) = Unit
}

/**
 * Decodes a Bluetooth Heart Rate Measurement (0x2A37) value. Bit 0 of the flags says whether the
 * rate is one byte or two (little endian).
 */
fun parseHeartRate(value: ByteArray): Int? {
    if (value.size < 2) return null
    val wide = value[0].toInt() and 0x01 != 0
    if (wide && value.size < 3) return null
    val bpm = if (wide) {
        (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
    } else {
        value[1].toInt() and 0xFF
    }
    return bpm.takeIf { it in 25..250 }
}
