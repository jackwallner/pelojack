package com.jackwallner.pelojack.game

/** Same 1920-wide design coordinates as the ride UI; the compact body is a horizontal strip. */
object OverlayLayout {
    const val DESIGN_WIDTH = 1440
    const val COMPACT_BODY_HEIGHT = 92

    fun windowWidth(displayWidth: Int): Int = displayWidth * DESIGN_WIDTH / 1920
}
