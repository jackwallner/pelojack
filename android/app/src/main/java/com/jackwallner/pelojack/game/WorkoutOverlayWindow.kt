package com.jackwallner.pelojack.game

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** A bounded, non-modal window. Only its visible rectangle consumes touches. */
class WorkoutOverlayWindow(context: Context) {
    private val windowContext by lazy {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val displayContext = context.createDisplayContext(display)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else displayContext
    }
    private val windows by lazy { windowContext.getSystemService(WindowManager::class.java) }
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var positioned = false
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.LEFT; y = 14 }

    val designScale: Float get() = size().x / 1920f

    fun show(content: @Composable () -> Unit): Boolean {
        if (view != null) return true
        if (!Settings.canDrawOverlays(windowContext)) return false
        val lifecycleOwner = OverlayOwner()
        val overlay = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner.lifecycle))
            setContent(content)
        }
        lifecycleOwner.resume()
        params.width = OverlayLayout.windowWidth(size().x)
        if (!positioned) { params.x = (size().x - params.width) / 2; positioned = true }
        return runCatching {
            windows.addView(overlay, params)
            view = overlay
            owner = lifecycleOwner
            true
        }.getOrElse {
            overlay.disposeComposition()
            lifecycleOwner.destroy()
            android.util.Log.e("PelojackOverlay", "Could not attach overlay", it)
            false
        }
    }

    fun move(dx: Float, dy: Float) {
        val overlay = view ?: return
        val display = size()
        params.x = (params.x + dx.toInt()).coerceIn(0, (display.x - overlay.width).coerceAtLeast(0))
        params.y = (params.y + dy.toInt()).coerceIn(0, (display.y - overlay.height).coerceAtLeast(0))
        runCatching { windows.updateViewLayout(overlay, params) }
    }

    fun hide() {
        view?.let { runCatching { windows.removeViewImmediate(it) }; it.disposeComposition() }
        owner?.destroy()
        view = null
        owner = null
    }

    private fun size(): Point {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windows.maximumWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        @Suppress("DEPRECATION")
        return Point().also { windows.defaultDisplay.getRealSize(it) }
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        init { saved.performAttach(); saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
