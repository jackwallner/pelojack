package com.jackwallner.pelojack.game

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.MainActivity
import com.jackwallner.pelojack.camera.NanitActivity
import com.jackwallner.pelojack.profile.SettingsStore
import com.jackwallner.pelojack.ride.RideController
import com.jackwallner.pelojack.ride.RidePhase
import com.jackwallner.pelojack.ui.PelojackTheme
import com.jackwallner.pelojack.workout.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

const val RUNESCAPE = "com.jagex.oldscape.android"
const val NANIT = "com.nanit.baby"

/** Application-owned protection and overlay for an existing ride, over ANY other app. */
class GameMode(
    private val context: Context,
    private val ride: RideController,
    private val settings: SettingsStore,
    scope: CoroutineScope,
    private val simulated: Boolean,
) {
    private val mutableActive = MutableStateFlow(false)
    private val inFront = MutableStateFlow(true)
    val foreground: StateFlow<Boolean> = inFront
    private val overlay = WorkoutOverlayWindow(context)
    private var protecting = false
    private var collapsed = false
    val active: StateFlow<Boolean> = mutableActive
    val installed: Boolean get() = context.packageManager.getLaunchIntentForPackage(RUNESCAPE) != null
    val cameraAvailable: Boolean get() = true

    init {
        scope.launch { ride.phase.collect { sync() } }
    }

    private fun sync() {
        val decision = OverlayPolicy.decide(inFront.value, Settings.canDrawOverlays(context),
            hasRide = ride.phase.value is RidePhase.Riding)
        mutableActive.value = decision.protect
        if (decision.protect && !protecting) {
            // Start while the activity is still visible, not after background execution is restricted.
            runCatching { RideService.start(context) }.onSuccess { protecting = true }
                .onFailure { Log.e("PelojackOverlay", "Ride protection unavailable", it); message("Ride protection unavailable. Keep Pelojack open.") }
        } else if (!decision.protect && protecting) {
            RideService.stop(context)
            protecting = false
        }
        if (decision.show) {
            if (!show()) message("Overlay unavailable. Return to Pelojack for ride controls.")
        } else overlay.hide()
        if (!decision.protect) collapsed = false
    }

    /** Existing explicit RuneScape action retains its Start behavior; lifecycle never calls it. */
    fun play(workout: Workout? = null) {
        val launch = context.packageManager.getLaunchIntentForPackage(RUNESCAPE) ?: run { message("Install RuneScape first"); return }
        if (!requestPermission("RuneScape")) return
        if (ride.phase.value == RidePhase.Idle) ride.open(workout)
        val state = (ride.phase.value as? RidePhase.Riding)?.state ?: return
        sync()
        runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (state.notStarted) ride.togglePause()
        }.onFailure { message("Could not open RuneScape. Your ride is still here.") }
    }

    /** Permission and a generic installed-app picker; never opens, starts, or resumes a ride. */
    fun watchElsewhere() {
        if (ride.phase.value !is RidePhase.Riding) return
        if (!requestPermission("Watch in another app")) return
        sync()
        val launchers = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val chooser = Intent.createChooser(launchers, "Watch in another app")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }.onFailure {
            message("Use the tablet's app switcher to open any app. Your ride stays here.")
        }
    }

    /** User-only action: validate the actual default HOME and overlay BEFORE leaving controls. */
    fun minimizeToOverlay() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val home = runCatching {
            val pm = context.packageManager
            val resolved = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                ?: return@runCatching null
            // A resolver/chooser is not the user's default launcher. Never select an arbitrary app.
            val candidates = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (candidates.none { it.activityInfo.packageName == resolved.packageName && it.activityInfo.name == resolved.name }) null
            else ComponentName(resolved.packageName, resolved.name)
        }.getOrNull()?.takeUnless { it.packageName == context.packageName }
        val result = OverlayMinimizer.minimize(
            hasRide = ride.phase.value is RidePhase.Riding,
            permission = Settings.canDrawOverlays(context),
            externalHome = home != null,
            protect = { sync(); protecting },
            show = { collapsed = true; runCatching { show() }.getOrDefault(false) },
            openHome = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .apply { component = home }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
            },
            hide = overlay::hide,
        )
        when (result) {
            OverlayMinimizer.Result.Minimized, OverlayMinimizer.Result.NoRide -> Unit
            OverlayMinimizer.Result.PermissionRequired -> message("Allow Display over other apps for Pelojack in Android Settings, then tap Minimize to overlay again. Your ride controls stay here.")
            OverlayMinimizer.Result.NoExternalHome -> message("Choose another default Home app in Android Settings first. Your ride controls stay here.")
            OverlayMinimizer.Result.ProtectionUnavailable -> message("Ride protection unavailable. Keep Pelojack open.")
            OverlayMinimizer.Result.OverlayUnavailable -> message("Overlay unavailable. Your ride controls stay here.")
            OverlayMinimizer.Result.HomeUnavailable -> message("Could not open the default Home app. Your ride controls stay here.")
        }
    }

    fun watchCamera() = watchCamera(testFeed = false)
    internal fun watchCamera(testFeed: Boolean) {
        if (ride.phase.value is RidePhase.Riding && !requestPermission("Nanit")) return
        sync()
        context.startActivity(Intent(context, NanitActivity::class.java)
            .putExtra("camera-test", testFeed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun requestPermission(label: String): Boolean {
        if (Settings.canDrawOverlays(context)) return true
        message("Allow Pelojack to display over other apps, then tap $label again. Switching apps will not start or resume your ride.")
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { message("Open Android Settings → Special app access → Display over other apps → Pelojack") }
        return false
    }

    fun setInFront(front: Boolean) {
        inFront.value = front
        sync()
    }

    fun backToPelojack() {
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun show(): Boolean = overlay.show {
        val phase by ride.phase.collectAsStateWithLifecycle()
        val state = (phase as? RidePhase.Riding)?.state ?: return@show
        PelojackTheme {
            RideStrip(state = state, simulated = simulated, designScale = overlay.designScale,
                onTap = ::backToPelojack, onDrag = overlay::move,
                initiallyCollapsed = collapsed, onCollapse = { collapsed = it })
        }
    }

    private fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
