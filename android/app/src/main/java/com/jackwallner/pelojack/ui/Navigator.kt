package com.jackwallner.pelojack.ui

import androidx.compose.runtime.mutableStateListOf

sealed interface Route {
    data object Home : Route
    data object Classes : Route
    data class ClassDetail(val id: String) : Route
    /** Makes a new workout, or edits the custom one with [id]. */
    data class Builder(val id: String? = null) : Route
    data object Programs : Route
    data class ProgramDetail(val id: String) : Route
    data object Profile : Route
    data class RideDetail(val startedAt: Long) : Route
    data object Settings : Route
    data object HeartRatePairing : Route
    data object Bike : Route
    data object Apps : Route
}

/** Back stack per rail tab; choosing a tab starts a fresh stack. */
class Navigator {
    private val stack = mutableStateListOf<Route>(Route.Home)

    val current: Route get() = stack.last()
    val tab: Route get() = stack.first()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        stack.add(route)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun select(tab: Route) {
        stack.clear()
        stack.add(tab)
    }
}
