package com.jackwallner.pelojack.hr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.jackwallner.pelojack.ride.RidePhase
import com.jackwallner.pelojack.ride.RideState
import com.jackwallner.pelojack.ride.speedMph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PelojackGymKit"
private const val PACKAGE = "com.onepeloton.gymkit"
private const val INTERFACE = "com.onepeloton.gymkit.IAppleWatchInterface"
private const val CALLBACK = "com.onepeloton.gymkit.IAppleWatchCallback"
private const val IN_CLASS = "onepeloton.intent.action.IN_CLASS_STATUS"

// IAppleWatchInterface transaction codes, from the Bike+ GymKit service (com.onepeloton.gymkit).
private const val START_WORKOUT = 2
private const val END_WORKOUT = 3
private const val RESET_WORKOUT = 5
private const val WRITE_DATA = 7
private const val REGISTER_CALLBACK = 8

// IAppleWatchCallback transaction codes.
private const val ON_CONNECTED = 1
private const val ON_DISCONNECTED = 3
private const val ON_DATA = 8

private const val STALE_AFTER_MS = 12_000L
private const val REANNOUNCE_MS = 5_000L

/**
 * Apple's GymKit on the Bike+: hold the watch to the bike's NFC reader during a ride and the
 * watch's own Workout app pairs, shows the bike's numbers, and saves an indoor cycle.
 *
 * Peloton's GymKit service does the pairing and the Apple protocol. It only accepts a watch while
 * it has been told a class is on (`IN_CLASS_STATUS`), and the app in charge of the ride starts and
 * ends the watch workout and streams bike data to it. Pelojack plays that part, following the ride,
 * and gets the watch's heart rate back.
 */
class GymKit(private val context: Context, private val scope: CoroutineScope) {
    val bpm = MutableStateFlow<Int?>(null)
    val connected = MutableStateFlow(false)

    @Volatile
    private var service: IBinder? = null
    private var lastBeat = 0L
    private var announced = false
    private var started = false
    private var lastSent = -1
    private var announcedAt = 0L

    val available: Boolean get() = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun start() {
        if (!available) return
        val intent = Intent(INTERFACE).setPackage(PACKAGE)
            .putExtra("com.onepeloton.gymkit.PlatformType", "titan")
            .putExtra("com.onepeloton.gymkit.ShouldEnable", true)
        val bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        Log.i(TAG, "bind GymKit service: $bound")
        scope.launch {
            while (isActive) {
                delay(1_000)
                if (bpm.value != null && SystemClock.elapsedRealtime() - lastBeat > STALE_AFTER_MS) bpm.value = null
            }
        }
    }

    /** Mirrors the ride into GymKit: class on while a ride is open, workout started with the ride. */
    fun follow(phase: StateFlow<RidePhase>) {
        scope.launch {
            phase.collect { current ->
                when (current) {
                    is RidePhase.Riding -> riding(current.state)
                    else -> closed()
                }
            }
        }
    }

    private fun riding(state: RideState) {
        if (!announced) {
            announced = true
            started = false
            lastSent = -1
            inClass(true)
            announcedAt = SystemClock.elapsedRealtime()
            // Arms the watch workout; a watch tapped before Start waits paused until the ride starts.
            call(START_WORKOUT)
        }
        // Peloton's own apps announce "out of class" when their screens change; say it again.
        val now = SystemClock.elapsedRealtime()
        if (now - announcedAt > REANNOUNCE_MS) {
            announcedAt = now
            inClass(true)
        }
        if (!started && !state.notStarted) {
            started = true
            // Peloton's "class is running" signal: starts a waiting watch, or one tapped later.
            call(RESET_WORKOUT)
        }
        if (started && !state.paused && state.totals.seconds != lastSent) {
            lastSent = state.totals.seconds
            write(state)
        }
    }

    private fun closed() {
        if (!announced) return
        announced = false
        // Also ends a watch that was tapped but never started, so it does not wait forever.
        call(END_WORKOUT)
        started = false
        inClass(false)
    }

    private fun inClass(on: Boolean) {
        context.sendBroadcast(Intent(IN_CLASS).putExtra("status", if (on) "in_class" else "out_of_class"))
    }

    private fun write(state: RideState) {
        val totals = state.totals
        val minutes = totals.seconds / 60.0
        call(WRITE_DATA) {
            writeInt(1)
            writeDouble(totals.calories.toDouble())
            writeDouble(totals.miles)
            writeLong(totals.seconds.toLong())
            writeDouble(state.telemetry.cadenceRpm.toDouble())
            writeDouble(totals.avgCadence.toDouble())
            writeDouble(state.telemetry.resistance.toDouble())
            writeDouble(state.telemetry.powerWatts.toDouble())
            writeDouble(totals.avgPower.toDouble())
            writeDouble(speedMph(state.telemetry.powerWatts))
            writeDouble(if (minutes > 0) totals.miles / (minutes / 60) else 0.0)
        }
    }

    private fun call(code: Int, body: Parcel.() -> Unit = {}) {
        val binder = service ?: return
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(INTERFACE)
            data.body()
            binder.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } catch (e: Exception) {
            Log.w(TAG, "GymKit call $code failed", e)
        } finally {
            data.recycle()
        }
    }

    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK)
                return true
            }
            data.enforceInterface(CALLBACK)
            when (code) {
                ON_CONNECTED -> {
                    connected.value = true
                    // A watch workout left over from a ride that is gone (e.g. the app restarted)
                    // keeps reconnecting with no data; end it so the watch saves and stops.
                    scope.launch { if (!announced) call(END_WORKOUT) }
                }
                ON_DISCONNECTED -> {
                    connected.value = false
                    bpm.value = null
                }
                ON_DATA -> if (data.readInt() != 0) {
                    val heartRate = data.readLong().toInt()
                    if (heartRate in 25..250) {
                        bpm.value = heartRate
                        lastBeat = SystemClock.elapsedRealtime()
                    }
                }
            }
            Log.d(TAG, "callback $code")
            return true
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder
            call(REGISTER_CALLBACK) { writeStrongBinder(callback) }
            Log.i(TAG, "GymKit service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connected.value = false
        }
    }
}
