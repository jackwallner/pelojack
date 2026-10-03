package com.jackwallner.pelojack.hr

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.location.LocationManagerCompat
import com.jackwallner.pelojack.profile.SettingsStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private const val RECONNECT_DELAY_MS = 3_000L

/** Runtime permissions Bluetooth scanning needs on this Android version. */
val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/** Android 11 and older only return scan results while Location is switched on. */
fun Context.scanNeedsLocation(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
        getSystemService(LocationManager::class.java)?.let { !LocationManagerCompat.isLocationEnabled(it) } == true

fun Context.hasBluetoothPermissions(): Boolean =
    bluetoothPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/**
 * Any standard Bluetooth heart rate monitor: a chest strap, an arm band, or an Apple Watch through
 * the Pelojack iPhone app. The chosen monitor is remembered and reconnected automatically.
 */
@SuppressLint("MissingPermission")
class BleHeartRate(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : HeartRate {
    override val bpm = MutableStateFlow<Int?>(null)
    override val link = MutableStateFlow(HeartRateLink.Off)
    override val simulated = false

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var reconnect: Job? = null
    val available: Boolean get() = adapter != null

    /** Connects to the remembered monitor, if there is one and Bluetooth may be used. */
    fun resume() {
        val address = settings.current.heartRateAddress ?: return
        if (gatt != null || !context.hasBluetoothPermissions() || adapter?.isEnabled != true) return
        connect(address)
    }

    /** Monitors advertising the heart rate service nearby, updated as they are found. */
    fun scan(): Flow<List<HeartRateDevice>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !context.hasBluetoothPermissions()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val found = linkedMapOf<String, HeartRateDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: "Heart rate monitor"
                found[result.device.address] = HeartRateDevice(result.device.address, name)
                trySend(found.values.toList())
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build()
        val mode = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), mode, callback)
        trySend(emptyList())
        awaitClose { scanner.stopScan(callback) }
    }

    fun choose(device: HeartRateDevice) {
        settings.update { it.copy(heartRateAddress = device.address, heartRateName = device.name) }
        disconnect()
        connect(device.address)
    }

    fun forget() {
        settings.update { it.copy(heartRateAddress = null, heartRateName = null) }
        disconnect()
    }

    private fun connect(address: String) {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        link.value = HeartRateLink.Searching
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun disconnect() {
        reconnect?.cancel()
        gatt?.close()
        gatt = null
        bpm.value = null
        link.value = HeartRateLink.Off
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
                return
            }
            gatt.close()
            if (this@BleHeartRate.gatt == gatt) this@BleHeartRate.gatt = null
            bpm.value = null
            val address = settings.current.heartRateAddress ?: run {
                link.value = HeartRateLink.Off
                return
            }
            link.value = HeartRateLink.Searching
            reconnect = scope.launch {
                delay(RECONNECT_DELAY_MS)
                if (this@BleHeartRate.gatt == null) connect(address)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val measurement = gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT) ?: return
            gatt.setCharacteristicNotification(measurement, true)
            val config = measurement.getDescriptor(CLIENT_CONFIG) ?: return
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(config, enable)
            } else {
                @Suppress("DEPRECATION")
                config.value = enable
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(config)
            }
            link.value = HeartRateLink.Connected
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            bpm.value = parseHeartRate(value)
        }

        @Deprecated("Called before Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            bpm.value = characteristic.value?.let(::parseHeartRate)
        }
    }
}
