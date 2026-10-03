package com.jackwallner.pelojack.ride

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Holds a power target on a resistance bike. Power grows roughly exponentially with resistance,
 * so the correction is the log of the power ratio, damped so a cadence surge does not overshoot.
 * After each change it waits for the motor and the averaged power to catch up before judging
 * again; correcting on stale power is what makes a resistance loop swing back and forth.
 */
class ErgController(
    /** How much power changes per resistance point (d ln P / d resistance). */
    private val sensitivity: Double = 0.045,
    private val damping: Double = 0.8,
    private val maxStep: Int = 6,
    private val settleSeconds: Int = 6,
) {
    private var changedAt: Int? = null
    private var target = 0

    /** Returns the next resistance to set at ride second [second], or null to leave it alone. */
    fun next(second: Int, resistance: Int, averagePower: Int, cadence: Int, targetWatts: Int): Int? {
        // A new target is judged straight away: the averaged power is settled on the old one.
        if (targetWatts != target) {
            target = targetWatts
            changedAt = null
        }
        changedAt?.let { if (second - it < settleSeconds) return null }
        // Coasting or barely pedalling: any change would be wrong when the rider starts again.
        if (cadence < 40 || averagePower < 15 || targetWatts <= 0) return null
        val error = ln(targetWatts.toDouble() / averagePower)
        // Inside about 3% of the target counts as on target.
        if (abs(error) < 0.03) return null
        val step = (error / sensitivity * damping).roundToInt().coerceIn(-maxStep, maxStep)
        val next = (resistance + step).coerceIn(0, 100)
        if (next == resistance) return null
        changedAt = second
        return next
    }
}
