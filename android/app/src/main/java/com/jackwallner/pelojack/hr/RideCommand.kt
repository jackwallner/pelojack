package com.jackwallner.pelojack.hr

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.roundToLong

/** The tablet's own Bluetooth service, which the Pelojack watch app finds and connects to. */
val BIKE_SERVICE: UUID = UUID.fromString("7a1f0010-5c3b-4e7e-9c8a-50e1a0c0de01")

/** The watch writes heart rate here, in the standard Heart Rate Measurement format. */
val BIKE_HEART_RATE: UUID = UUID.fromString("7a1f0011-5c3b-4e7e-9c8a-50e1a0c0de01")

/** The tablet notifies ride events here, encoded as [RideCommand]. */
val BIKE_RIDE: UUID = UUID.fromString("7a1f0012-5c3b-4e7e-9c8a-50e1a0c0de01")

/**
 * What the tablet tells the watch, which starts, pauses and ends its workout and shows live bike
 * numbers. Little-endian, and short enough for one BLE packet.
 */
sealed interface RideCommand {
    data object Start : RideCommand
    data object Pause : RideCommand
    data object Resume : RideCommand
    data class Metrics(val power: Int, val cadence: Int, val kilojoules: Double, val meters: Int) : RideCommand
    data class End(val seconds: Int, val kilojoules: Double, val meters: Int) : RideCommand

    fun encode(): ByteArray = when (this) {
        Start -> byteArrayOf(1)
        Pause -> byteArrayOf(4)
        Resume -> byteArrayOf(5)
        is Metrics -> buffer(12).put(2).putShort(power.coerceIn(0, 65_535).toShort()).put(cadence.coerceIn(0, 255).toByte())
            .putInt(tenths(kilojoules)).putInt(meters).array()
        is End -> buffer(13).put(3).putInt(seconds).putInt(tenths(kilojoules)).putInt(meters).array()
    }

    private companion object {
        fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        fun tenths(kilojoules: Double): Int = (kilojoules * 10).roundToLong().toInt()
    }
}
