package com.jackwallner.pelojack.ride

import com.jackwallner.pelojack.bike.Telemetry
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Peloton's power-to-speed curve, in mph.
 * https://ihaque.org/posts/2020/12/25/pelomon-part-ib-computing-speed/
 */
fun speedMph(powerWatts: Int): Double {
    if (powerWatts <= 0) return 0.0
    val root = sqrt(powerWatts.toDouble())
    return if (powerWatts < 26) {
        0.057 - 0.172 * root + 0.759 * root.pow(2) - 0.079 * root.pow(3)
    } else {
        -1.635 + 2.325 * root - 0.064 * root.pow(2) + 0.001 * root.pow(3)
    }
}

data class Totals(
    val seconds: Int = 0,
    val kilojoules: Double = 0.0,
    val miles: Double = 0.0,
    val avgPower: Int = 0,
    val avgCadence: Int = 0,
    val avgResistance: Int = 0,
    val maxPower: Int = 0,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
) {
    // Mechanical work over a rider efficiency of about 24% lands at roughly 1 kcal per kJ.
    val calories: Int get() = kilojoules.toInt()
}

data class Sample(val power: Int, val cadence: Int, val resistance: Int, val heartRate: Int?)

/** Accumulates a ride from telemetry readings. Time only advances through [add]. */
class RideRecorder {
    var elapsed = 0.0
        private set
    var joules = 0.0
        private set
    private var miles = 0.0
    private var cadenceSeconds = 0.0
    private var resistanceSeconds = 0.0
    private var heartSeconds = 0.0
    private var heartTime = 0.0
    private var maxPower = 0
    private var maxHeart = 0
    private val mutableSamples = mutableListOf<Sample>()

    /** One reading per elapsed second. */
    val samples: List<Sample> get() = mutableSamples

    fun add(telemetry: Telemetry, heartRate: Int?, seconds: Double) {
        elapsed += seconds
        joules += telemetry.powerWatts * seconds
        miles += speedMph(telemetry.powerWatts) * seconds / 3600
        cadenceSeconds += telemetry.cadenceRpm * seconds
        resistanceSeconds += telemetry.resistance * seconds
        maxPower = maxOf(maxPower, telemetry.powerWatts)
        if (heartRate != null && heartRate > 0) {
            heartSeconds += heartRate * seconds
            heartTime += seconds
            maxHeart = maxOf(maxHeart, heartRate)
        }
        while (mutableSamples.size < elapsed.toInt()) {
            mutableSamples += Sample(telemetry.powerWatts, telemetry.cadenceRpm, telemetry.resistance, heartRate)
        }
    }

    fun totals() = Totals(
        seconds = elapsed.toInt(),
        kilojoules = joules / 1000,
        miles = miles,
        avgPower = average(joules),
        avgCadence = average(cadenceSeconds),
        avgResistance = average(resistanceSeconds),
        maxPower = maxPower,
        avgHeartRate = if (heartTime > 0) (heartSeconds / heartTime).roundToInt() else null,
        maxHeartRate = maxHeart.takeIf { it > 0 },
    )

    /** Mean watts over the samples of seconds [from] until [until]. */
    fun averagePower(from: Int, until: Int): Int {
        val slice = mutableSamples.subList(from.coerceIn(0, mutableSamples.size), until.coerceIn(0, mutableSamples.size))
        return if (slice.isEmpty()) 0 else slice.sumOf { it.power } / slice.size
    }

    private fun average(sum: Double) = if (elapsed > 0) (sum / elapsed).roundToInt() else 0
}
