package com.jackwallner.pelojack.ui

import android.os.Build
import android.util.Log
import android.view.Window
import android.view.WindowManager

/** Peloton's firmware forces navigation on unless the foreground window carries this flag. */
object PelotonWindow {
    // Verified in this Bike+ firmware. The constant is blocked from reflection, but the field
    // carrying it is allowed. This bit has a different meaning on other Android builds.
    private const val PELOTON_WINDOW = Int.MIN_VALUE
    private val privateFlags by lazy {
        if (Build.VERSION.SDK_INT != 29 || Build.MODEL != "PLTN-TTR01" ||
            !Build.MANUFACTURER.startsWith("Peloton", ignoreCase = true)) null
        else runCatching { WindowManager.LayoutParams::class.java.getField("privateFlags") }
            .onFailure { Log.w("PelojackWindow", "Peloton window flags unavailable", it) }.getOrNull()
    }

    fun hideNavigationBar(window: Window, hidden: Boolean) {
        val field = privateFlags ?: return
        runCatching {
            val attributes = window.attributes
            val before = field.getInt(attributes)
            val after = if (hidden) before or PELOTON_WINDOW else before and PELOTON_WINDOW.inv()
            if (before != after) {
                field.setInt(attributes, after)
                window.attributes = attributes
            }
        }.onFailure { Log.w("PelojackWindow", "Could not update Peloton navigation bar", it) }
    }
}
