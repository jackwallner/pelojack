package com.jackwallner.pelojack.ride

import android.os.SystemClock
import com.jackwallner.pelojack.bike.Bike
import com.jackwallner.pelojack.bike.MAX_RESISTANCE
import com.jackwallner.pelojack.bike.MIN_RESISTANCE
import com.jackwallner.pelojack.bike.Telemetry
import com.jackwallner.pelojack.hr.HeartRate
import com.jackwallner.pelojack.hr.RideCommand
import com.jackwallner.pelojack.profile.SettingsStore
import com.jackwallner.pelojack.profile.Stats
import com.jackwallner.pelojack.workout.Segment
import com.jackwallner.pelojack.workout.Workout
import com.jackwallner.pelojack.workout.WorkoutLibrary
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TICK_MS = 200L
private const val ERG_INTERVAL_SECONDS = 1.0
private const val ERG_AVERAGE_SECONDS = 3
private const val RECENT_SECONDS = 300
private const val RELAY_INTERVAL_SECONDS = 2.0
private const val METERS_PER_MILE = 1609.344
private const val NUDGE_MEMORY_MS = 1_500L

data class RideState(
    val workout: Workout?,
    val position: Workout.Position?,
    val telemetry: Telemetry,
    val heartRate: Int?,
    val totals: Totals,
    val paused: Boolean,
    val autoFollow: Boolean,
    val erg: Boolean,
    val ftp: Int,
    val maxHeartRate: Int,
    /** Watts the current segment asks for, when it has a power target. */
    val targetWatts: Int?,
    /** Kilojoules ahead of (positive) or behind the best previous ride of this workout. */
    val versusBest: Double?,
    val upNext: Workout?,
    /** Output over the last few minutes, one value per second. */
    val recentPower: List<Int> = emptyList(),
) {
    val title: String get() = workout?.name ?: "Just Ride"

    /** True until the rider presses Start. */
    val notStarted: Boolean get() = paused && totals.seconds == 0

    val secondsLeft: Int? get() = workout?.let { (it.totalSeconds - totals.seconds).coerceAtLeast(0) }
}

data class RideResult(
    val summary: RideSummary,
    val samples: List<Sample>,
    val workout: Workout?,
    val record: Boolean,
    val previousBest: RideSummary?,
    val upNext: Workout?,
)

sealed interface RidePhase {
    data object Idle : RidePhase
    data class Riding(val state: RideState) : RidePhase
    data class Finished(val result: RideResult) : RidePhase
}

/**
 * Owns the ride in progress: timing, recording, auto-follow resistance, ERG, racing a previous
 * best, and the stack. Lives in the application so a ride survives the activity being recreated.
 * Must be driven from the main thread.
 */
class RideController(
    private val bike: Bike,
    private val heart: HeartRate,
    private val store: RideStore,
    private val library: WorkoutLibrary,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val mutablePhase = MutableStateFlow<RidePhase>(RidePhase.Idle)
    val phase: StateFlow<RidePhase> = mutablePhase

    private var job: Job? = null
    private var recorder = RideRecorder()
    private var workout: Workout? = null
    private var programId: String? = null
    private var fromStack = false
    private var segmentIndex = -1
    private var paused = true
    private var startedAt = 0L
    private var autoFollow = true
    private var erg = true
    private var sinceErg = 0.0
    private var sinceRelay = 0.0
    private var ergController = ErgController()
    private var bestSamples: List<Double> = emptyList()
    private var nudged: Int? = null
    private var nudgedAt = 0L

    /** Opens the ride screen, waiting on the rider to press Start. */
    fun open(workout: Workout?, programId: String? = null, fromStack: Boolean = false) {
        if (job != null) return
        this.workout = workout
        this.programId = programId
        this.fromStack = fromStack
        recorder = RideRecorder()
        segmentIndex = -1
        paused = true
        startedAt = System.currentTimeMillis()
        autoFollow = settings.current.autoFollow
        erg = settings.current.erg
        sinceErg = 0.0
        ergController = ErgController()
        bestSamples = cumulativeKilojoules(Stats(store.rides.value).bestOf(workout?.id))
        workout?.segments?.firstOrNull()?.resistanceTarget?.takeIf { autoFollow }?.let(bike::setResistance)
        publish()
        job = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val seconds = (now - last) / 1000.0
                last = now
                if (!paused && !advance(seconds)) {
                    finish()
                    return@launch
                }
                publish()
            }
        }
    }

    /** Rides the stack from the top. */
    fun playStack() {
        val first = settings.current.stack.firstNotNullOfOrNull(library::find) ?: return
        open(first, fromStack = true)
    }

    fun togglePause() {
        if (job == null) return
        val starting = recorder.elapsed == 0.0
        if (starting) startedAt = System.currentTimeMillis()
        paused = !paused
        heart.send(
            when {
                starting -> RideCommand.Start
                paused -> RideCommand.Pause
                else -> RideCommand.Resume
            },
        )
        publish()
    }

    /** Replays the ride's state to an Apple Watch that connects mid-ride. */
    fun syncWatch() {
        if (job == null || recorder.elapsed == 0.0) return
        heart.send(RideCommand.Start)
        heart.send(if (paused) RideCommand.Pause else RideCommand.Resume)
    }

    fun setAutoFollow(on: Boolean) {
        autoFollow = on
        if (on) currentSegment()?.resistanceTarget?.let(bike::setResistance)
        publish()
    }

    fun setErg(on: Boolean) {
        erg = on
        if (!on && autoFollow) currentSegment()?.resistanceTarget?.let(bike::setResistance)
        publish()
    }

    fun nudgeResistance(delta: Int) {
        // Quick taps build on the last request, since the reading lags the motor by a moment.
        val now = SystemClock.elapsedRealtime()
        val base = nudged?.takeIf { now - nudgedAt < NUDGE_MEMORY_MS } ?: bike.telemetry.value.resistance
        val target = (base + delta).coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)
        nudged = target
        nudgedAt = now
        bike.setResistance(target)
    }

    /** Ends the ride and shows its summary, or returns home if it never started. */
    fun finish() {
        job?.cancel()
        job = null
        if (recorder.elapsed < 1) {
            mutablePhase.value = RidePhase.Idle
            return
        }
        val totals = recorder.totals()
        heart.send(RideCommand.End(totals.seconds, totals.kilojoules, (totals.miles * METERS_PER_MILE).toInt()))
        val plan = workout
        val summary = RideSummary(
            startedAt = startedAt,
            title = plan?.name ?: "Just Ride",
            totals = recorder.totals(),
            workoutId = plan?.id,
            category = plan?.category?.key,
            plannedSeconds = plan?.totalSeconds,
            programId = programId,
            ftpEstimate = ftpEstimate(plan),
        )
        val history = Stats(store.rides.value + summary)
        mutablePhase.value = RidePhase.Finished(
            RideResult(
                summary = summary,
                samples = recorder.samples.toList(),
                workout = plan,
                record = history.isRecord(summary),
                previousBest = if (plan != null && recorder.elapsed >= plan.totalSeconds - 5) history.bestOf(plan.id, before = startedAt) else null,
                upNext = nextInStack(),
            ),
        )
    }

    fun save() {
        val finished = mutablePhase.value as? RidePhase.Finished ?: return
        store.save(finished.result.summary, finished.result.samples)
        afterSummary()
    }

    fun discard() {
        if (mutablePhase.value is RidePhase.Finished) afterSummary()
    }

    /** Leaves the summary: home, or the next workout when riding the stack. */
    private fun afterSummary() {
        mutablePhase.value = RidePhase.Idle
        if (!fromStack) return
        settings.update { it.copy(stack = it.stack.drop(1)) }
        settings.current.stack.firstNotNullOfOrNull(library::find)?.let { open(it, fromStack = true) }
    }

    /** Records [seconds] of riding. Returns false once the workout is complete. */
    private fun advance(seconds: Double): Boolean {
        val telemetry = bike.telemetry.value
        recorder.add(telemetry, heart.bpm.value, seconds)
        sinceRelay += seconds
        if (sinceRelay >= RELAY_INTERVAL_SECONDS) {
            sinceRelay = 0.0
            heart.send(
                RideCommand.Metrics(telemetry.powerWatts, telemetry.cadenceRpm, recorder.joules / 1000, (recorder.totals().miles * METERS_PER_MILE).toInt()),
            )
        }
        val plan = workout ?: return true
        val position = plan.positionAt(recorder.elapsed) ?: return false
        val segment = position.segment
        if (position.index != segmentIndex) {
            segmentIndex = position.index
            if (autoFollow && !(erg && segment.powerShare != null)) segment.resistanceTarget?.let(bike::setResistance)
        }
        sinceErg += seconds
        if (erg && sinceErg >= ERG_INTERVAL_SECONDS) {
            sinceErg = 0.0
            targetWatts(segment)?.let { target ->
                val second = recorder.elapsed.toInt()
                val power = recorder.averagePower(second - ERG_AVERAGE_SECONDS, second)
                ergController.next(second, telemetry.resistance, power, telemetry.cadenceRpm, target)?.let(bike::setResistance)
            }
        }
        return true
    }

    private fun publish() {
        val position = workout?.positionAt(recorder.elapsed)
        val totals = recorder.totals()
        mutablePhase.value = RidePhase.Riding(
            RideState(
                workout = workout,
                position = position,
                telemetry = bike.telemetry.value,
                heartRate = heart.bpm.value,
                totals = totals,
                paused = paused,
                autoFollow = autoFollow,
                erg = erg,
                ftp = settings.current.ftp,
                maxHeartRate = settings.current.maxHeartRate,
                targetWatts = position?.segment?.let(::targetWatts),
                versusBest = bestSamples.getOrNull(totals.seconds - 1)?.let { recorder.joules / 1000 - it },
                upNext = if (fromStack) nextInStack() else null,
                recentPower = recorder.samples.takeLast(RECENT_SECONDS).map { it.power },
            ),
        )
    }

    private fun currentSegment() = workout?.positionAt(recorder.elapsed)?.segment

    private fun targetWatts(segment: Segment): Int? =
        segment.powerShare?.let { (it * settings.current.ftp).roundToInt() }

    private fun nextInStack(): Workout? =
        if (fromStack) settings.current.stack.drop(1).firstNotNullOfOrNull(library::find) else null

    /** 95% of the average power over the test segments, when the whole test was ridden. */
    private fun ftpEstimate(plan: Workout?): Int? {
        plan ?: return null
        var start = 0
        var watts = 0L
        var seconds = 0
        for (segment in plan.segments) {
            val end = start + segment.seconds
            if (segment.test) {
                if (recorder.elapsed < end) return null
                watts += recorder.averagePower(start, end).toLong() * segment.seconds
                seconds += segment.seconds
            }
            start = end
        }
        return if (seconds == 0) null else (watts.toDouble() / seconds * 0.95).roundToInt()
    }

    private fun cumulativeKilojoules(best: RideSummary?): List<Double> {
        best ?: return emptyList()
        var total = 0.0
        return store.samples(best.startedAt).map { total += it.power / 1000.0; total }
    }
}
