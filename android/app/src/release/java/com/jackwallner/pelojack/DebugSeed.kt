package com.jackwallner.pelojack

/** Release builds never seed data. */
object DebugSeed {
    @Suppress("UNUSED_PARAMETER")
    fun rides(app: PelojackApp) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun music(context: android.content.Context) = Unit
}
