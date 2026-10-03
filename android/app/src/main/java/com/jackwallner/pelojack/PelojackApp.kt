package com.jackwallner.pelojack

import android.app.Application
import com.jackwallner.pelojack.bike.Bike
import com.jackwallner.pelojack.bike.BikePlus
import com.jackwallner.pelojack.bike.SimulatedBike
import com.jackwallner.pelojack.bike.hasBikeService
import com.jackwallner.pelojack.devlink.DevLink
import com.jackwallner.pelojack.devlink.Owner
import com.jackwallner.pelojack.game.GameMode
import com.jackwallner.pelojack.hr.BleHeartRate
import com.jackwallner.pelojack.hr.GymKit
import com.jackwallner.pelojack.hr.HeartRate
import com.jackwallner.pelojack.hr.HeartRateHub
import com.jackwallner.pelojack.hr.SimulatedHeartRate
import com.jackwallner.pelojack.hr.WatchLink
import com.jackwallner.pelojack.media.NowPlaying
import com.jackwallner.pelojack.profile.SettingsStore
import com.jackwallner.pelojack.ride.RideController
import com.jackwallner.pelojack.ride.RidePhase
import com.jackwallner.pelojack.ride.RideStore
import com.jackwallner.pelojack.workout.WorkoutLibrary
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Builds every long-lived object once. */
class PelojackApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: SettingsStore
        private set
    lateinit var bike: Bike
        private set
    lateinit var heartRate: HeartRateHub
        private set
    lateinit var watch: WatchLink
        private set
    lateinit var gymKit: GymKit
        private set

    /** The real Bluetooth monitor; null when running on the simulated bike. */
    var bluetoothHeartRate: BleHeartRate? = null
        private set
    lateinit var nowPlaying: NowPlaying
        private set
    lateinit var rides: RideStore
        private set
    lateinit var library: WorkoutLibrary
        private set
    lateinit var ride: RideController
        private set
    lateinit var game: GameMode
        private set
    lateinit var devLink: DevLink
        private set

    override fun onCreate() {
        super.onCreate()
        Owner.keepHome(this)
        settings = SettingsStore(this)
        val strap: HeartRate
        if (hasBikeService()) {
            bike = BikePlus(this, scope)
            strap = BleHeartRate(this, settings, scope).also { bluetoothHeartRate = it }
        } else {
            bike = SimulatedBike(scope)
            // Debug builds can test real Bluetooth monitors off the bike: create files/force-bluetooth.
            strap = if (BuildConfig.DEBUG && File(filesDir, "force-bluetooth").exists()) {
                BleHeartRate(this, settings, scope).also { bluetoothHeartRate = it }
            } else {
                SimulatedHeartRate(bike, scope)
            }
        }
        watch = WatchLink(this, scope)
        gymKit = GymKit(this, scope)
        heartRate = HeartRateHub(watch, gymKit, strap, scope)
        nowPlaying = NowPlaying(this)
        rides = RideStore(File(filesDir, "rides"))
        library = WorkoutLibrary(this).apply { reload() }
        ride = RideController(bike, heartRate, rides, library, settings, scope)
        game = GameMode(this, ride, settings, scope, simulated = bike is SimulatedBike)
        watch.onWatchJoined = ride::syncWatch
        watch.onBluetoothOn = { bluetoothHeartRate?.resume() }
        gymKit.follow(ride.phase)
        bike.start()
        watch.start()
        gymKit.start()
        devLink = DevLink(this, ::importRides, canReboot = { ride.phase.value == RidePhase.Idle }).apply { start() }
    }

    /** Called whenever the app returns to the front. */
    fun resume() {
        library.reload()
        importRides()
        nowPlaying.refresh()
        bluetoothHeartRate?.resume()
        watch.start()
    }

    /** Rides dropped in the tablet's `import` folder (the Peloton history) join the history. */
    fun importRides() {
        val folder = getExternalFilesDir("import") ?: return
        if (!File(folder, "rides.json").exists()) return
        scope.launch {
            val added = withContext(Dispatchers.IO) { runCatching { rides.importFrom(folder) } }
            android.util.Log.i("Pelojack", "Imported rides: ${added.getOrElse { "failed, ${it.message}" }}")
        }
    }
}
