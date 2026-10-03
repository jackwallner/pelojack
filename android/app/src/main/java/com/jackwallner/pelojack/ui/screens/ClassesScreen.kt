package com.jackwallner.pelojack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.ui.EmptyState
import com.jackwallner.pelojack.ui.FilterChip
import com.jackwallner.pelojack.ui.Glyphs
import com.jackwallner.pelojack.ui.Navigator
import com.jackwallner.pelojack.ui.OutlineButton
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.QuietButton
import com.jackwallner.pelojack.ui.Route
import com.jackwallner.pelojack.ui.WorkoutCard
import com.jackwallner.pelojack.ui.WorkoutHistory
import com.jackwallner.pelojack.workout.Workout

private enum class Length(val label: String, val matches: (Int) -> Boolean) {
    Any("Any length", { true }),
    Short("Up to 15 min", { it <= 15 }),
    Twenty("20 min", { it in 16..25 }),
    Thirty("30 min", { it in 26..37 }),
    Long("45 min +", { it >= 38 }),
}

@Composable
fun ClassesScreen(app: PelojackApp, nav: Navigator) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val rides by app.rides.rides.collectAsStateWithLifecycle()
    val workouts by app.library.workouts.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var length by rememberSaveable { mutableStateOf(Length.Any) }
    var bookmarkedOnly by rememberSaveable { mutableStateOf(false) }
    val history = historyByWorkout(rides)

    val categories = workouts.map { it.category }.distinct().sortedBy { it.ordinal }
    val shown = workouts
        .filter { category == null || it.category.key == category }
        .filter { length.matches(it.minutes) }
        .filter { !bookmarkedOnly || it.id in settings.bookmarks }
        .sortedWith(compareBy<Workout> { it.category.ordinal }.thenBy { it.totalSeconds })

    Page(
        title = "Classes",
        subtitle = "${workouts.size} classes, built in and your own",
        actions = { OutlineButton("Create", { nav.push(Route.Builder()) }, icon = Glyphs.Plus) },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FilterChip("All", category == null && !bookmarkedOnly, { category = null; bookmarkedOnly = false }) }
            item { FilterChip("Bookmarked", bookmarkedOnly, { bookmarkedOnly = !bookmarkedOnly }) }
            items(categories) { FilterChip(it.label, category == it.key, { category = if (category == it.key) null else it.key }) }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Length.entries) { FilterChip(it.label, length == it, { length = it }) }
        }
        Spacer(Modifier.height(28.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    EmptyState("No classes match", "Try a different filter, or make your own.") {
                        QuietButton("Clear filters", { category = null; length = Length.Any; bookmarkedOnly = false })
                    }
                }
            }
            items(shown, key = { it.id }) { workout ->
                WorkoutCard(
                    workout,
                    history[workout.id] ?: WorkoutHistory(),
                    workout.id in settings.bookmarks,
                    { nav.push(Route.ClassDetail(workout.id)) },
                )
            }
        }
    }
}
