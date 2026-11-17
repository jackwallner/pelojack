package com.jackwallner.pelojack.game

/** Explicit presentation action. No ride start/pause/finish or resistance capabilities. */
object OverlayMinimizer {
    enum class Result { Minimized, NoRide, PermissionRequired, NoExternalHome, ProtectionUnavailable, OverlayUnavailable, HomeUnavailable }

    fun minimize(
        hasRide: Boolean,
        permission: Boolean,
        externalHome: Boolean,
        protect: () -> Boolean,
        show: () -> Boolean,
        openHome: () -> Boolean,
        hide: () -> Unit,
    ): Result {
        if (!hasRide) return Result.NoRide
        if (!permission) return Result.PermissionRequired
        if (!externalHome) return Result.NoExternalHome
        if (!protect()) return Result.ProtectionUnavailable
        if (!show()) return Result.OverlayUnavailable
        if (!openHome()) {
            hide()
            return Result.HomeUnavailable
        }
        return Result.Minimized
    }
}
