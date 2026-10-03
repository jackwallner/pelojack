package com.jackwallner.pelojack.bike

import kotlinx.coroutines.flow.StateFlow

const val MIN_RESISTANCE = 0
const val MAX_RESISTANCE = 100

data class Telemetry(
    val cadenceRpm: Int = 0,
    val powerWatts: Int = 0,
    val resistance: Int = 0,
)

enum class BikeLink { Connecting, Live, Unavailable }

interface Bike {
    /** Shown on the home screen so a simulated bike is never mistaken for the real one. */
    val label: String
    val telemetry: StateFlow<Telemetry>
    val link: StateFlow<BikeLink>
    val lastError: StateFlow<String?>

    fun start()

    /** Asks the resistance motor to move to [target]. Out-of-range values are clamped. */
    fun setResistance(target: Int)
}
