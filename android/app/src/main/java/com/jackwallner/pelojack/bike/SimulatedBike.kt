package com.jackwallner.pelojack.bike

import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun simulatedPower(cadenceRpm: Int, resistance: Int): Int = PowerCurve.watts(cadenceRpm, resistance)

/** Stands in for the bike on an emulator or any non-Peloton device. */
class SimulatedBike(private val scope: CoroutineScope) : Bike {
    override val label = "Simulated bike"
    override val telemetry = MutableStateFlow(Telemetry(resistance = 30))
    override val link = MutableStateFlow(BikeLink.Connecting)
    override val lastError = MutableStateFlow<String?>(null)

    @Volatile
    private var target = 30

    override fun start() {
        link.value = BikeLink.Live
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(100)
                tick++
                val current = telemetry.value.resistance
                val resistance = current + (target - current).coerceIn(-1, 1)
                val cadence = (86 + 4 * sin(tick / 25.0)).toInt()
                telemetry.value = Telemetry(cadence, simulatedPower(cadence, resistance), resistance)
            }
        }
    }

    override fun setResistance(target: Int) {
        this.target = target.coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)
    }
}
