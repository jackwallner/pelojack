package com.jackwallner.pelojack.hr

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** Heart rate older than this is treated as gone, e.g. the watch left the room. */
private const val STALE_AFTER_MS = 12_000L

enum class WatchState { Unsupported, NeedsPermission, BluetoothOff, Waiting, Connected }

/** Runtime permissions the tablet needs to be found by the watch. */
val watchPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }

fun Context.hasWatchPermissions(): Boolean =
    watchPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/**
 * The tablet side of the Apple Watch link. The tablet advertises a small Bluetooth service; the
 * Pelojack watch app finds it as soon as it opens, writes heart rate into it, and listens for ride
 * events so its workout starts, pauses and ends with the ride. An Apple Watch cannot advertise
 * itself, which is why the tablet is the one that waits to be found.
 */
@SuppressLint("MissingPermission")
class WatchLink(private val context: Context, private val scope: CoroutineScope) {
    val bpm = MutableStateFlow<Int?>(null)
    val state = MutableStateFlow(WatchState.Waiting)

    /** Called when a watch subscribes, so a ride already in progress can be replayed to it. */
    var onWatchJoined: (() -> Unit)? = null

    private val manager get() = context.getSystemService(BluetoothManager::class.java)
    private var server: BluetoothGattServer? = null
    private var ride: BluetoothGattCharacteristic? = null
    private val connected = mutableSetOf<BluetoothDevice>()
    private val subscribers = mutableSetOf<BluetoothDevice>()
    private var lastBeat = 0L
    private var staleCheck: Job? = null
    private var listening = false

    /** Called when Bluetooth turns on, e.g. a few seconds after the tablet boots into Pelojack. */
    var onBluetoothOn: (() -> Unit)? = null

    private val adapterState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    start()
                    onBluetoothOn?.invoke()
                }
                BluetoothAdapter.STATE_OFF -> {
                    server?.close()
                    server = null
                    ride = null
                    synchronized(subscribers) {
                        connected.clear()
                        subscribers.clear()
                    }
                    bpm.value = null
                    state.value = WatchState.BluetoothOff
                }
            }
        }
    }

    /** Starts advertising. Safe to call repeatedly, e.g. after permission is granted. */
    fun start() {
        if (!listening) {
            listening = true
            context.registerReceiver(adapterState, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        }
        if (server != null) return
        val adapter = manager?.adapter
        state.value = when {
            adapter == null -> WatchState.Unsupported
            !context.hasWatchPermissions() -> WatchState.NeedsPermission
            !adapter.isEnabled -> WatchState.BluetoothOff
            else -> WatchState.Waiting
        }
        if (state.value != WatchState.Waiting) return
        if (adapter?.bluetoothLeAdvertiser == null) {
            state.value = WatchState.Unsupported
            return
        }
        val gatt = manager.openGattServer(context, callback) ?: run {
            state.value = WatchState.Unsupported
            return
        }
        val rideEvents = BluetoothGattCharacteristic(
            BIKE_RIDE,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(CLIENT_CONFIG, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE),
            )
        }
        val heart = BluetoothGattCharacteristic(
            BIKE_HEART_RATE,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        gatt.addService(
            BluetoothGattService(BIKE_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                addCharacteristic(heart)
                addCharacteristic(rideEvents)
            },
        )
        server = gatt
        ride = rideEvents
        if (staleCheck == null) {
            staleCheck = scope.launch {
                while (isActive) {
                    delay(1_000)
                    if (bpm.value != null && SystemClock.elapsedRealtime() - lastBeat > STALE_AFTER_MS) bpm.value = null
                }
            }
        }
    }

    // Advertising waits for the service to be registered, so a watch never connects to an empty server.
    private fun advertise() {
        val advertiser = manager?.adapter?.bluetoothLeAdvertiser ?: return stop()
        advertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build(),
            AdvertiseData.Builder().addServiceUuid(ParcelUuid(BIKE_SERVICE)).setIncludeDeviceName(false).build(),
            advertising,
        )
    }

    /** Gives up on this attempt; the next [start] (the app coming to the front) tries again. */
    private fun stop() {
        server?.close()
        server = null
        ride = null
        state.value = WatchState.Unsupported
    }

    fun send(command: RideCommand) {
        val gatt = server ?: return
        val characteristic = ride ?: return
        val value = command.encode()
        synchronized(subscribers) { subscribers.toList() }.forEach { device ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.notifyCharacteristicChanged(device, characteristic, false, value)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                gatt.notifyCharacteristicChanged(device, characteristic, false)
            }
        }
    }

    private val advertising = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            // Already advertising (a restarted server) is fine; anything else is a real failure.
            if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) scope.launch { stop() }
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            scope.launch { if (status == BluetoothGatt.GATT_SUCCESS) advertise() else stop() }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            synchronized(subscribers) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connected += device
                } else {
                    connected -= device
                    subscribers -= device
                }
                state.value = if (connected.isEmpty()) WatchState.Waiting else WatchState.Connected
            }
            if (newState != BluetoothProfile.STATE_CONNECTED && state.value == WatchState.Waiting) bpm.value = null
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic.uuid == BIKE_HEART_RATE && value != null) {
                parseHeartRate(value)?.let {
                    bpm.value = it
                    lastBeat = SystemClock.elapsedRealtime()
                }
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val subscribing = value != null && value.isNotEmpty() && value[0].toInt() != 0
            val joined = synchronized(subscribers) {
                if (subscribing) subscribers.add(device) else subscribers.remove(device).let { false }
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            if (joined) scope.launch { onWatchJoined?.invoke() }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0))
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val on = synchronized(subscribers) { device in subscribers }
            val value = if (on) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }
    }
}
