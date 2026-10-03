package com.jackwallner.pelojack.game

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

const val RUNESCAPE = "com.jagex.oldscape.android"
const val NANIT = "com.nanit.baby"

/**
 * Riding while using RuneScape or Nanit. The other app runs full screen and Pelojack draws
 * a ride strip on top of it. The camera viewer's picture in picture can stay over the ride or game. The ride
 * itself (timing, auto-follow resistance, ERG, the watch) keeps running in the application.
 */
class GameMode(
    private val context: Context,
    private val ride: RideController,
    private val settings: SettingsStore,
    scope: CoroutineScope,
    private val simulated: Boolean,
) {
    private val mutableActive = MutableStateFlow(false)
    private val inFront = MutableStateFlow(true)
    private val windowContext by lazy {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val displayContext = context.createDisplayContext(display)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else displayContext
    }
    private val windows by lazy { windowContext.getSystemService(WindowManager::class.java) }
    private var strip: ComposeView? = null
    private var owner: StripOwner? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        y = 14
    }
    private var positioned = false

    /** True while a ride continues in another app, until the ride ends. */
    val active: StateFlow<Boolean> = mutableActive

    val installed: Boolean get() = context.packageManager.getLaunchIntentForPackage(RUNESCAPE) != null
    val cameraAvailable: Boolean get() = true

    init {
        scope.launch {
            combine(mutableActive, inFront, ride.phase) { _, _, _ -> Unit }.collect {
                // A launch or finish can update all three flows before this collector runs.
                val active = mutableActive.value
                val front = inFront.value
                val phase = ride.phase.value
                if (active && phase !is RidePhase.Riding) end(showSummary = phase is RidePhase.Finished)
                else if (active && !front) show() else hide()
            }
        }
    }

    /** Starts the ride if it has not started, then opens the game with the ride strip over it. */
    fun play(workout: Workout? = null) {
        openApp(RUNESCAPE, "RuneScape", startRide = true, workout = workout)
    }

    /** Opens the landscape camera viewer without starting or resuming a ride. */
    fun watchCamera() = watchCamera(testFeed = false)

    internal fun watchCamera(testFeed: Boolean) {
        openActivity(Intent(context, NanitActivity::class.java).putExtra("camera-test", testFeed), "Nanit", startRide = false)
    }

    private fun openApp(packageName: String, label: String, startRide: Boolean, workout: Workout? = null) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: run {
            message("Install $label first")
            return
        }
        openActivity(launch, label, startRide, workout)
    }

    private fun openActivity(launch: Intent, label: String, startRide: Boolean, workout: Workout? = null) {
        val needsStrip = startRide || ride.phase.value is RidePhase.Riding
        if (needsStrip && !Settings.canDrawOverlays(context)) {
            message("Allow Pelojack to display over other apps, then tap $label again")
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (startRide && ride.phase.value == RidePhase.Idle) ride.open(workout)
        val state = (ride.phase.value as? RidePhase.Riding)?.state
        if (state == null && ride.phase.value != RidePhase.Idle) return
        runCatching {
            if (state != null) {
                // Protect recording before the other app moves the activity into the background.
                RideService.start(context)
                mutableActive.value = true
            }
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (startRide && state?.notStarted == true) ride.togglePause()
        }.onFailure {
            end(showSummary = false)
            Log.e("PelojackGame", "Could not open $label", it)
            message("Could not open $label. Your ride is still here.")
        }
    }

    /** Pelojack's own screen is showing, so the strip steps aside. */
    fun setInFront(front: Boolean) {
        inFront.value = front
    }

    private fun end(showSummary: Boolean) {
        mutableActive.value = false
        hide()
        RideService.stop(context)
        if (showSummary) backToPelojack()
    }

    fun backToPelojack() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        if (strip != null || !Settings.canDrawOverlays(context)) return
        params.width = displaySize().x / 2
        val stripOwner = StripOwner()
        val view = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(stripOwner)
            setViewTreeSavedStateRegistryOwner(stripOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(stripOwner.lifecycle))
            setContent {
                val phase by ride.phase.collectAsStateWithLifecycle()
                val preferences by settings.state.collectAsStateWithLifecycle()
                val state = (phase as? RidePhase.Riding)?.state ?: return@setContent
                PelojackTheme { RideStrip(
                    state = state,
                    simulated = simulated,
                    designScale = displaySize().x / 1920f,
                    onTap = ::backToPelojack,
                    openCamera = ::watchCamera,
                    hidden = preferences.hiddenOnRide,
                    togglePiece = settings::toggleRidePiece,
                    onDrag = { dx, dy ->
                        move(dx, dy)
                    },
                ) }
            }
        }
        stripOwner.resume()
        if (!positioned) {
            params.x = displaySize().x / 4
            positioned = true
        }
        runCatching { windows.addView(view, params) }.onSuccess {
            strip = view
            owner = stripOwner
        }.onFailure {
            stripOwner.destroy()
            view.disposeComposition()
            Log.e("PelojackGame", "Could not show ride strip", it)
            message("Ride strip unavailable. Returning to your ride.")
            backToPelojack()
        }
    }

    private fun move(dx: Float, dy: Float) {
        val view = strip ?: return
        val display = displaySize()
        params.x = (params.x + dx.toInt()).coerceIn(0, (display.x - view.width).coerceAtLeast(0))
        params.y = (params.y + dy.toInt()).coerceIn(0, (display.y - view.height).coerceAtLeast(0))
        windows.updateViewLayout(view, params)
    }

    /** Use the display, since the game's splash screen can temporarily narrow app resources. */
    private fun displaySize(): Point {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windows.maximumWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        @Suppress("DEPRECATION")
        return Point().also { windows.defaultDisplay.getRealSize(it) }
    }

    private fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    private fun hide() {
        strip?.let {
            // Cancel any queued window attachment before destroying its Compose lifecycle.
            runCatching { windows.removeViewImmediate(it) }
            it.disposeComposition()
        }
        owner?.destroy()
        strip = null
        owner = null
    }

    /** Compose needs a lifecycle and saved state even outside an activity. */
    private class StripOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        init {
            saved.performAttach()
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
