package com.jackwallner.pelojack.bike

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val BIKE_SERVICE_PACKAGE = "com.onepeloton.affernetservice"
private const val BIKE_INTERFACE = "com.onepeloton.affernetservice.IBikeInterface"

// Transaction codes of Peloton's IBikeInterface, taken from Grupetto and Switchback.
private const val TRANSACTION_SET_RESISTANCE = 7
private const val TRANSACTION_GET_BIKE_DATA = 14

private const val POLL_INTERVAL_MS = 100L
private const val MIN_WRITE_INTERVAL_MS = 500L
private const val ERRORS_BEFORE_UNAVAILABLE = 20
private const val BIND_ATTEMPTS = 24
private const val BIND_RETRY_MS = 5_000L

fun Context.hasBikeService(): Boolean = try {
    packageManager.getPackageInfo(BIKE_SERVICE_PACKAGE, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

/**
 * Converts the raw service values into [Telemetry]. Output comes from [PowerCurve]: the service's
 * own power field (hundredths of a watt, from the load cell) reads 0 at a steady cadence on the
 * Bike+, so it is only shown on the Bike screen.
 */
fun telemetryOf(rpm: Long, resistance: Int): Telemetry {
    val cadence = rpm.coerceIn(0, 250).toInt()
    val clamped = resistance.coerceIn(MIN_RESISTANCE, MAX_RESISTANCE)
    return Telemetry(cadenceRpm = cadence, powerWatts = PowerCurve.watts(cadence, clamped), resistance = clamped)
}

/**
 * The resistance reading occasionally spikes for a single sample (Switchback notes the same), so
 * the lowest of the last three readings is used.
 */
class SpikeFilter(private val size: Int = 3) {
    private val recent = ArrayDeque<Int>()

    fun add(value: Int): Int {
        recent.addLast(value)
        if (recent.size > size) recent.removeFirst()
        return recent.min()
    }
}

/** The Bike+ sensor and resistance motor, reached through Peloton's own system service. */
class BikePlus(
    private val context: Context,
    private val scope: CoroutineScope,
) : Bike {
    override val label = "Bike+"
    override val telemetry = MutableStateFlow(Telemetry())
    override val link = MutableStateFlow(BikeLink.Connecting)
    override val lastError = MutableStateFlow<String?>(null)

    /** The last reading as the service returned it, for the Bike screen. */
    val raw = MutableStateFlow<String?>(null)

    private val resistanceFilter = SpikeFilter()

    @Volatile
    private var binder: IBinder? = null
    private val targets = Channel<Int>(Channel.CONFLATED)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            link.value = BikeLink.Connecting
        }

        override fun onNullBinding(name: ComponentName?) = fail("Bike service returned no binder")

        // The service process was replaced (e.g. a Peloton update): bind again from scratch.
        override fun onBindingDied(name: ComponentName?) {
            binder = null
            link.value = BikeLink.Connecting
            lastError.value = "Bike service binding died, reconnecting"
            runCatching { context.unbindService(this) }
            scope.launch {
                delay(BIND_RETRY_MS)
                bind()
            }
        }
    }

    override fun start() {
        scope.launch(Dispatchers.IO) { poll() }
        scope.launch(Dispatchers.IO) { write() }
        scope.launch { bind() }
    }

    // Right after the tablet boots into Pelojack the bike service may not be ready, so binding
    // is retried for a while before the bike is reported unavailable for good.
    private suspend fun bind() {
        val intent = Intent(BIKE_INTERFACE).setPackage(BIKE_SERVICE_PACKAGE)
        repeat(BIND_ATTEMPTS) {
            val bound = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                fail("Not allowed to bind: ${e.message}")
                return
            }
            if (bound) return
            runCatching { context.unbindService(connection) }
            lastError.value = "Bike service not found, retrying"
            delay(BIND_RETRY_MS)
        }
        fail("Bike service not found")
    }

    override fun setResistance(target: Int) {
        targets.trySend(target.coerceIn(MIN_RESISTANCE, MAX_RESISTANCE))
    }

    private suspend fun poll() {
        var errors = 0
        while (scope.isActive) {
            delay(POLL_INTERVAL_MS)
            val service = binder ?: continue
            try {
                telemetry.value = read(service)
                link.value = BikeLink.Live
                errors = 0
            } catch (e: Exception) {
                errors++
                lastError.value = "Read failed: ${e.javaClass.simpleName} ${e.message.orEmpty()}"
                if (errors >= ERRORS_BEFORE_UNAVAILABLE) link.value = BikeLink.Unavailable
            }
        }
    }

    // The motor only needs the latest target, so writes are conflated and spaced out.
    private suspend fun write() {
        for (target in targets) {
            val service = binder ?: continue
            try {
                send(service, target)
            } catch (e: Exception) {
                lastError.value = "Resistance write failed: ${e.javaClass.simpleName} ${e.message.orEmpty()}"
            }
            delay(MIN_WRITE_INTERVAL_MS)
        }
    }

    private fun read(service: IBinder): Telemetry {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(BIKE_INTERFACE)
            service.transact(TRANSACTION_GET_BIKE_DATA, data, reply, 0)
            reply.readException()
            check(reply.readInt() != 0) { "empty bike data" }
            // BikeData starts with rpm, power, stepper position, load cell, current and target
            // resistance. Grupetto and Switchback both show the target field as the bike's
            // resistance, so that is the one read here.
            val rpm = reply.readLong()
            val centiwatts = reply.readLong()
            reply.readLong()
            reply.readLong()
            val current = reply.readInt()
            val target = reply.readInt()
            val text = "rpm $rpm, load cell power $centiwatts cW, current $current, target $target"
            if (text != raw.value) Log.d("PelojackBike", text)
            raw.value = text
            return telemetryOf(rpm, resistanceFilter.add(target))
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun send(service: IBinder, target: Int) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(BIKE_INTERFACE)
            data.writeInt(target)
            check(service.transact(TRANSACTION_SET_RESISTANCE, data, null, IBinder.FLAG_ONEWAY)) {
                "transaction not accepted"
            }
        } finally {
            data.recycle()
        }
    }

    private fun fail(message: String) {
        lastError.value = message
        link.value = BikeLink.Unavailable
    }
}
