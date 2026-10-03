package com.jackwallner.pelojack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.bike.BikeLink
import com.jackwallner.pelojack.ride.RidePhase
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.PelojackTheme
import com.jackwallner.pelojack.ui.PelotonWindow
import com.jackwallner.pelojack.ui.Route
import com.jackwallner.pelojack.ui.Shell
import com.jackwallner.pelojack.ui.WatchBanner
import com.jackwallner.pelojack.ui.screens.AppsScreen
import com.jackwallner.pelojack.ui.screens.BikeScreen
import com.jackwallner.pelojack.ui.screens.BuilderScreen
import com.jackwallner.pelojack.ui.screens.ClassDetailScreen
import com.jackwallner.pelojack.ui.screens.ClassesScreen
import com.jackwallner.pelojack.ui.screens.HeartRatePairingScreen
import com.jackwallner.pelojack.ui.screens.HomeScreen
import com.jackwallner.pelojack.ui.screens.MusicActions
import com.jackwallner.pelojack.ui.screens.ProfileScreen
import com.jackwallner.pelojack.ui.screens.ProgramDetailScreen
import com.jackwallner.pelojack.ui.screens.ProgramsScreen
import com.jackwallner.pelojack.ui.screens.RideActions
import com.jackwallner.pelojack.ui.screens.RideDetailScreen
import com.jackwallner.pelojack.ui.screens.RideScreen
import com.jackwallner.pelojack.ui.screens.SettingsScreen
import com.jackwallner.pelojack.ui.screens.SummaryScreen

// Screens are laid out on a 1920 x 1080 canvas, the Bike+ tablet's resolution.
private const val DESIGN_WIDTH = 1920f
private const val DESIGN_HEIGHT = 1080f
private const val SPOTIFY = "com.spotify.music"

class MainActivity : ComponentActivity() {
    private val app get() = application as PelojackApp
    private val nav = Navigator()
    private var hideSystemBars = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handleDebugIntent(intent)
        handleLinkIntent(intent)
        app.devLink.window = java.lang.ref.WeakReference(window.decorView)
        setContent {
            PelojackTheme {
                DesignCanvas {
                    Box(Modifier.fillMaxSize().background(Palette.Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Content()
                        val watch by app.watch.state.collectAsStateWithLifecycle()
                        WatchBanner(watch, Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.devLink.window = java.lang.ref.WeakReference(window.decorView)
        app.game.setInFront(true)
        app.resume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onPause() {
        app.game.setInFront(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on Pelojack: back to the Home tab.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) nav.select(Route.Home)
        handleDebugIntent(intent)
        handleLinkIntent(intent)
    }

    /**
     * From `scripts/setup.sh` over USB: `--es devlink-token <new>` (plus `--es devlink-current <old>`
     * to change it), and `--es release-owner <token>` before Pelojack is uninstalled.
     */
    private fun handleLinkIntent(intent: Intent) {
        intent.getStringExtra("devlink-token")?.let { app.devLink.setToken(it, intent.getStringExtra("devlink-current")) }
        intent.getStringExtra("release-owner")?.let { if (app.devLink.matches(it)) com.jackwallner.pelojack.devlink.Owner.release(this) }
    }

    /**
     * Debug builds only, for driving the emulator:
     * `--es ride <workout id or "just">`, `--ez start true`, `--es open <home|classes|programs|profile|settings>`,
     * `--es detail <workout id>`, `--es seed rides`, `--ez fakemusic true`.
     */
    private fun handleDebugIntent(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (intent.getStringExtra("seed") == "rides") DebugSeed.rides(app)
        if (intent.getBooleanExtra("fakemusic", false)) DebugSeed.music(this)
        if (intent.getBooleanExtra("nanit", false)) app.game.watchCamera(intent.getBooleanExtra("camera-test", false))
        when (intent.getStringExtra("open")) {
            "home" -> nav.select(Route.Home)
            "classes" -> nav.select(Route.Classes)
            "programs" -> nav.select(Route.Programs)
            "profile" -> nav.select(Route.Profile)
            "settings" -> nav.select(Route.Settings)
            "history" -> nav.select(Route.Profile)
        }
        intent.getStringExtra("detail")?.let { nav.push(Route.ClassDetail(it)) }
        intent.getStringExtra("program")?.let { nav.push(Route.ProgramDetail(it)) }
        if (intent.getBooleanExtra("builder", false)) nav.push(Route.Builder())
        if (intent.getBooleanExtra("lastride", false)) app.rides.rides.value.firstOrNull()?.let { nav.push(Route.RideDetail(it.startedAt)) }
        val id = intent.getStringExtra("ride") ?: return
        app.library.reload()
        app.ride.open(app.library.find(id))
        if (intent.getBooleanExtra("start", false)) app.ride.togglePause()
        if (intent.getBooleanExtra("game", false)) app.game.play()
    }

    @Composable
    private fun Content() {
        val phase by app.ride.phase.collectAsStateWithLifecycle()
        val settings by app.settings.state.collectAsStateWithLifecycle()
        AppWindow(settings.hideSystemBars, phase is RidePhase.Riding)
        when (val current = phase) {
            is RidePhase.Riding -> {
                BackHandler {}
                val track by app.nowPlaying.track.collectAsStateWithLifecycle()
                val musicAccess by app.nowPlaying.hasAccess.collectAsStateWithLifecycle()
                val spotify = packageManager.getLaunchIntentForPackage(SPOTIFY)
                RideScreen(
                    state = current.state,
                    actions = RideActions(
                        togglePause = app.ride::togglePause,
                        finish = app.ride::finish,
                        nudge = app.ride::nudgeResistance,
                        hidden = settings.hiddenOnRide,
                        hide = app.settings::toggleRidePiece,
                        setAutoFollow = app.ride::setAutoFollow,
                        setErg = app.ride::setErg,
                        music = MusicActions(
                            track = track,
                            available = musicAccess,
                            toggle = app.nowPlaying::togglePlay,
                            next = { app.nowPlaying.next() },
                            previous = { app.nowPlaying.previous() },
                            open = spotify?.let { intent -> { startActivity(intent) } },
                        ),
                        playGame = if (app.game.installed) ({ app.game.play() }) else null,
                        openCamera = app.game::watchCamera,
                    ),
                    videoLocation = app.library::videoLocation,
                    metric = settings.metric,
                )
            }
            is RidePhase.Finished -> {
                BackHandler {}
                SummaryScreen(
                    result = current.result,
                    ftp = settings.ftp,
                    metric = settings.metric,
                    onSave = app.ride::save,
                    onDiscard = app.ride::discard,
                    onSaveFtp = { ftp -> app.settings.update { it.copy(ftp = ftp) } },
                )
            }
            RidePhase.Idle -> Browse()
        }
    }

    @Composable
    private fun Browse() {
        val link by app.bike.link.collectAsStateWithLifecycle()
        val bpm by app.heartRate.bpm.collectAsStateWithLifecycle()
        // Pelojack is the home screen, so back at a tab's root goes nowhere.
        BackHandler { nav.back() }
        Shell(
            selected = nav.tab,
            onSelect = nav::select,
            bikeColor = when (link) {
                BikeLink.Live -> Palette.Good
                BikeLink.Connecting -> Palette.Faint
                BikeLink.Unavailable -> Palette.Bad
            },
            heartRate = bpm,
        ) {
            when (val route = nav.current) {
                Route.Home -> HomeScreen(app, nav)
                Route.Classes -> ClassesScreen(app, nav)
                is Route.ClassDetail -> ClassDetailScreen(app, nav, route.id)
                is Route.Builder -> BuilderScreen(app, nav, route.id)
                Route.Programs -> ProgramsScreen(app, nav)
                is Route.ProgramDetail -> ProgramDetailScreen(app, nav, route.id)
                Route.Profile -> ProfileScreen(app, nav)
                is Route.RideDetail -> RideDetailScreen(app, nav, route.startedAt)
                Route.Settings -> SettingsScreen(app, nav)
                Route.HeartRatePairing -> HeartRatePairingScreen(app, nav)
                Route.Bike -> BikeScreen(app, nav)
                Route.Apps -> AppsScreen()
            }
        }
    }

    /** Keep a ride awake and apply the rider's saved full-screen preference throughout the app. */
    @Composable
    private fun AppWindow(hidden: Boolean, riding: Boolean) {
        DisposableEffect(hidden, riding) {
            hideSystemBars = hidden
            applySystemBars()
            window.decorView.keepScreenOn = riding
            onDispose {
                window.decorView.keepScreenOn = false
            }
        }
    }

    private fun applySystemBars() {
        PelotonWindow.hideNavigationBar(window, hideSystemBars)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideSystemBars) bars.hide(WindowInsetsCompat.Type.systemBars())
        else bars.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** Scales dp and sp so the window always measures at least the design canvas. */
@Composable
private fun DesignCanvas(content: @Composable () -> Unit) {
    val window = LocalWindowInfo.current.containerSize
    val scale = minOf(window.width / DESIGN_WIDTH, window.height / DESIGN_HEIGHT)
    CompositionLocalProvider(LocalDensity provides Density(density = scale, fontScale = 1f), content = content)
}
