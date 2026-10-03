package com.jackwallner.pelojack

import com.jackwallner.pelojack.bike.Telemetry
import com.jackwallner.pelojack.bike.PowerCurve
import com.jackwallner.pelojack.bike.SpikeFilter
import com.jackwallner.pelojack.bike.telemetryOf
import com.jackwallner.pelojack.hr.parseHeartRate
import com.jackwallner.pelojack.ride.ErgController
import com.jackwallner.pelojack.ride.RideRecorder
import com.jackwallner.pelojack.ride.RideSummary
import com.jackwallner.pelojack.ride.speedMph
import kotlin.math.abs
import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRecorderTest {
    @Test
    fun accumulatesWorkTimeAndAverages() {
        val recorder = RideRecorder()
        repeat(300) { recorder.add(Telemetry(cadenceRpm = 90, powerWatts = 200, resistance = 45), 150, 0.2) }
        repeat(300) { recorder.add(Telemetry(cadenceRpm = 70, powerWatts = 100, resistance = 35), 130, 0.2) }

        val totals = recorder.totals()
        assertEquals(120, totals.seconds)
        assertEquals(18.0, totals.kilojoules, 0.01)
        assertEquals(150, totals.avgPower)
        assertEquals(80, totals.avgCadence)
        assertEquals(40, totals.avgResistance)
        assertEquals(200, totals.maxPower)
        assertEquals(140, totals.avgHeartRate)
        assertEquals(150, totals.maxHeartRate)
        assertEquals(18, totals.calories)
    }

    @Test
    fun heartRateIsOptional() {
        val recorder = RideRecorder()
        repeat(10) { recorder.add(Telemetry(80, 150, 40), null, 1.0) }
        assertNull(recorder.totals().avgHeartRate)
        assertNull(recorder.samples.first().heartRate)
    }

    @Test
    fun keepsOneSamplePerSecondAndAveragesWindows() {
        val recorder = RideRecorder()
        repeat(52) { recorder.add(Telemetry(80, 150, 40), null, 0.2) }
        repeat(5) { recorder.add(Telemetry(80, 250, 40), null, 1.0) }
        assertEquals(15, recorder.samples.size)
        assertEquals(250, recorder.averagePower(10, 15))
        assertEquals(150, recorder.averagePower(0, 10))
        assertEquals(0, recorder.averagePower(40, 50))
    }

    @Test
    fun distanceFollowsThePelotonSpeedCurve() {
        assertEquals(0.0, speedMph(0), 0.0)
        assertEquals(16.2, speedMph(100), 0.05)
        assertEquals(21.3, speedMph(200), 0.05)

        val recorder = RideRecorder()
        repeat(3600) { recorder.add(Telemetry(90, 200, 45), null, 1.0) }
        assertEquals(speedMph(200), recorder.totals().miles, 0.01)
    }

    @Test
    fun serviceValuesAreConvertedAndClamped() {
        assertEquals(Telemetry(85, PowerCurve.watts(85, 42), 42), telemetryOf(rpm = 85, resistance = 42))
        assertEquals(Telemetry(0, 0, 100), telemetryOf(rpm = -3, resistance = 140))
    }

    @Test
    fun summarySurvivesAJsonRoundTrip() {
        val recorder = RideRecorder().apply { add(Telemetry(88, 176, 41), 142, 754.0) }
        val summary = RideSummary(1_790_000_000_000, "20 min Intervals", recorder.totals(), "intervals-20", "intervals", 1200, "prog", 210)
        assertEquals(summary, RideSummary.fromJson(summary.toJson()))
        val free = RideSummary(1_790_000_000_000, "Just Ride", RideRecorder().apply { add(Telemetry(88, 176, 41), null, 60.0) }.totals())
        assertEquals(free, RideSummary.fromJson(free.toJson()))
    }

    /**
     * Rides [seconds] against a bike whose motor moves [ramp] points a second and whose power
     * reading lags by about [lag] seconds, the way RideController drives the controller.
     * Returns the power read each second.
     */
    private fun ergRide(target: Int, startResistance: Int, ramp: Double, lag: Double, gain: Double, seconds: Int = 120): List<Int> {
        val erg = ErgController()
        var commanded = startResistance
        var motor = startResistance.toDouble()
        var reading = 40 * exp(gain * motor)
        val samples = mutableListOf<Double>()
        val dt = 0.2
        repeat(seconds) { second ->
            repeat(5) {
                motor += (commanded - motor).coerceIn(-ramp * dt, ramp * dt)
                reading += (40 * exp(gain * motor) - reading) * minOf(1.0, dt / lag)
            }
            samples += reading
            val average = samples.takeLast(3).average().toInt()
            erg.next(second + 1, commanded, average, 85, target)?.let { commanded = it }
        }
        return samples.map { it.toInt() }
    }

    @Test
    fun ergSettlesWithoutSwingingOnSlowOrFastBikes() {
        for ((ramp, lag) in listOf(4.0 to 1.0, 2.0 to 2.0, 1.0 to 3.0)) {
            for (gain in listOf(0.03, 0.045, 0.06)) {
                for ((target, start) in listOf(250 to 30, 110 to 45, 300 to 20)) {
                    val settled = ergRide(target, start, ramp, lag, gain).drop(60)
                    val worst = settled.maxOf { abs(it - target) / target.toDouble() }
                    assertTrue("ramp $ramp lag $lag gain $gain target $target: off by ${(worst * 100).toInt()}%", worst < 0.06)
                }
            }
        }
    }

    @Test
    fun ergWaitsForTheBikeAfterEachChange() {
        val erg = ErgController()
        assertEquals(46, erg.next(1, 40, 150, 85, 220))
        // Still reading the old power: no second step on top of the first.
        for (second in 2..6) assertNull(erg.next(second, 46, 150, 85, 220))
        assertEquals(52, erg.next(7, 46, 150, 85, 220))
        // A new target is acted on at once.
        assertEquals(46, erg.next(8, 52, 220, 85, 140))
    }

    @Test
    fun outputMatchesPelotonsPublishedNumbers() {
        // Peloton's output at resistance 30 to 50, 80 and 100 rpm, and 50 at 85 and 90 rpm.
        val published = listOf(
            Triple(80, 30, 60), Triple(80, 35, 84), Triple(80, 40, 113), Triple(80, 45, 145), Triple(80, 50, 190),
            Triple(100, 30, 90), Triple(100, 35, 122), Triple(100, 40, 162), Triple(100, 45, 217), Triple(100, 50, 262),
            Triple(85, 50, 222), Triple(90, 50, 245),
        )
        for ((cadence, resistance, watts) in published) {
            val estimate = PowerCurve.watts(cadence, resistance)
            assertTrue("$cadence rpm at $resistance: $estimate W vs $watts W", abs(estimate - watts) <= watts * 0.12)
        }
        assertEquals(0, PowerCurve.watts(0, 60))
        // Above the published range it keeps rising, but a sprint stays believable.
        assertTrue(PowerCurve.watts(80, 60) > PowerCurve.watts(80, 50))
        assertTrue(PowerCurve.watts(120, 90) in 800..2000)
    }

    @Test
    fun resistanceIgnoresSingleSampleSpikes() {
        val filter = SpikeFilter()
        assertEquals(listOf(30, 30, 30, 30, 30, 30, 45), listOf(30, 30, 88, 30, 45, 45, 45).map(filter::add))
    }

    @Test
    fun ergLeavesResistanceAloneWhenCoasting() {
        assertNull(ErgController().next(1, 40, 0, 0, 200))
        assertNull(ErgController().next(1, 40, 150, 30, 200))
        assertNull(ErgController().next(1, 40, 200, 85, 200))
    }

    @Test
    fun ergStepsAreBounded() {
        assertEquals(46, ErgController().next(1, 40, 50, 90, 400))
        assertEquals(34, ErgController().next(1, 40, 400, 90, 50))
        assertEquals(100, ErgController().next(1, 98, 50, 90, 400))
    }

    @Test
    fun parsesHeartRateMeasurements() {
        assertEquals(72, parseHeartRate(byteArrayOf(0x00, 72)))
        assertNull(parseHeartRate(byteArrayOf(0x01, 44, 1)))
        assertEquals(172, parseHeartRate(byteArrayOf(0x01, 172.toByte(), 0)))
        assertNull(parseHeartRate(byteArrayOf(0x00)))
        assertNull(parseHeartRate(byteArrayOf(0x00, 0)))
    }
}
