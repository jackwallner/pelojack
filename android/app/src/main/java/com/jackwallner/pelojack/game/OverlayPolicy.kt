package com.jackwallner.pelojack.game

/** Presentation/protection only: switching apps never issues ride commands. */
object OverlayPolicy {
    data class Decision(val protect: Boolean, val show: Boolean)

    fun decide(inFront: Boolean, permission: Boolean, hasRide: Boolean): Decision {
        return Decision(hasRide, hasRide && !inFront && permission)
    }
}
