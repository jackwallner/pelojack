package com.jackwallner.pelojack.hr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class HeartSource { None, Watch, Strap, Simulated }

/**
 * One heart rate for the app: the Apple Watch when it is sending (through GymKit or the Pelojack
 * watch app), otherwise a Bluetooth strap, otherwise (off the bike) a simulated one.
 */
class HeartRateHub(
    val watch: WatchLink,
    gymKit: GymKit,
    /** A real strap, or the simulated heart rate off the bike. */
    private val fallback: HeartRate,
    scope: CoroutineScope,
) : HeartRate {
    override val simulated: Boolean = fallback.simulated

    private val watchBpm: StateFlow<Int?> = combine(gymKit.bpm, watch.bpm) { tapped, app -> tapped ?: app }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val source: StateFlow<HeartSource> = combine(watchBpm, fallback.bpm) { fromWatch, other ->
        when {
            fromWatch != null -> HeartSource.Watch
            other != null -> if (fallback.simulated) HeartSource.Simulated else HeartSource.Strap
            else -> HeartSource.None
        }
    }.stateIn(scope, SharingStarted.Eagerly, HeartSource.None)

    override val bpm: StateFlow<Int?> = combine(watchBpm, fallback.bpm) { fromWatch, other -> fromWatch ?: other }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val link: StateFlow<HeartRateLink> = combine(watchBpm, fallback.link) { fromWatch, other ->
        if (fromWatch != null) HeartRateLink.Connected else other
    }.stateIn(scope, SharingStarted.Eagerly, HeartRateLink.Off)

    override fun send(command: RideCommand) = watch.send(command)
}
