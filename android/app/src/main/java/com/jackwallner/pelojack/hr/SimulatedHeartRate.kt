package com.jackwallner.pelojack.hr

import com.jackwallner.pelojack.bike.Bike
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Follows the simulated bike's effort so heart rate screens have data off the bike. */
class SimulatedHeartRate(private val bike: Bike, scope: CoroutineScope) : HeartRate {
    override val bpm = MutableStateFlow<Int?>(null)
    override val link = MutableStateFlow(HeartRateLink.Connected)
    override val simulated = true

    init {
        scope.launch {
            var rate = 95.0
            while (isActive) {
                delay(1000)
                val target = 90 + bike.telemetry.value.powerWatts * 0.35
                rate += (target - rate) * 0.08
                bpm.value = rate.toInt()
            }
        }
    }
}
