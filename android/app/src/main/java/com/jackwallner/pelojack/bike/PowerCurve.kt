package com.jackwallner.pelojack.bike

import kotlin.math.exp
import kotlin.math.pow

/**
 * Output in watts from cadence and resistance, matching Peloton's numbers.
 *
 * The Bike+ reports power from a load cell, but on Jack's bike that reading is 0 at any steady
 * cadence (it only blips while the flywheel speeds up), so Pelojack works output out the way the
 * original Peloton Bike does. The curve is fitted to Peloton's published outputs at resistance
 * 30 to 50 and 80 to 100 rpm (within about 5%). Above 50 there is no published table; growth is
 * slowed so a hard sprint lands in the range Peloton riders report rather than running away.
 */
object PowerCurve {
    private const val BASE = 11.28          // watts at 80 rpm and resistance 0
    private const val CADENCE_EXPONENT = 1.665
    private const val GROWTH = 0.0570       // per resistance point, up to 50
    private const val HIGH_GROWTH = 0.035   // per resistance point above 50
    private const val KNEE = 50

    fun watts(cadenceRpm: Int, resistance: Int): Int {
        if (cadenceRpm <= 0) return 0
        val r = resistance.coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)
        val exponent = GROWTH * minOf(r, KNEE) + HIGH_GROWTH * maxOf(r - KNEE, 0)
        return (BASE * (cadenceRpm / 80.0).pow(CADENCE_EXPONENT) * exp(exponent)).toInt()
    }
}
