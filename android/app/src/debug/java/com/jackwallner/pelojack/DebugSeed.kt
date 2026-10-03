package com.jackwallner.pelojack

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.jackwallner.pelojack.bike.Telemetry
import com.jackwallner.pelojack.bike.simulatedPower
import com.jackwallner.pelojack.ride.RideRecorder
import com.jackwallner.pelojack.ride.RideSummary
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.random.Random

/** Fills an emulator with six weeks of plausible rides so every screen has something to show. */
object DebugSeed {
    private var session: MediaSession? = null

    /** Publishes a playing media session so the ride screen's music bar has something to show. */
    fun music(context: Context) {
        if (session != null) return
        session = MediaSession(context, "pelojack-debug").apply {
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Midnight City")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "M83")
                    .build(),
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build(),
            )
            isActive = true
        }
    }

    fun rides(app: PelojackApp) {
        if (app.rides.rides.value.isNotEmpty()) return
        val random = Random(7)
        val ids = listOf("intervals-20", "climb-30", "pz-30", "hills-20", "endurance-45", null, "pz-endurance-30", "hiit-20")
        val now = System.currentTimeMillis()
        var day = 42
        var ride = 0
        while (day > 0) {
            val workout = ids[ride % ids.size]?.let(app.library::find)
            val seconds = workout?.totalSeconds ?: (20 * 60 + random.nextInt(900))
            val recorder = RideRecorder()
            var heart = 95.0
            val fitness = 1.0 + (42 - day) * 0.004
            for (second in 0 until seconds) {
                val segment = workout?.positionAt(second.toDouble())?.segment
                val resistance = segment?.resistanceTarget ?: segment?.powerShare?.let { (30 + (it - 0.55) * 46).toInt() } ?: 38
                val cadence = (84 + 6 * sin(second / 40.0) + random.nextInt(-3, 4)).toInt()
                val power = (simulatedPower(cadence, resistance) * fitness).toInt()
                heart += (90 + power * 0.33 - heart) * 0.03
                recorder.add(Telemetry(cadence, power, resistance), heart.toInt(), 1.0)
            }
            val startedAt = now - TimeUnit.DAYS.toMillis(day.toLong()) + TimeUnit.HOURS.toMillis(random.nextLong(6, 20))
            app.rides.save(
                RideSummary(
                    startedAt = startedAt,
                    title = workout?.name ?: "Just Ride",
                    totals = recorder.totals(),
                    workoutId = workout?.id,
                    category = workout?.category?.key,
                    plannedSeconds = workout?.totalSeconds,
                ),
                recorder.samples,
            )
            day -= random.nextInt(1, 4)
            ride++
        }
    }
}
