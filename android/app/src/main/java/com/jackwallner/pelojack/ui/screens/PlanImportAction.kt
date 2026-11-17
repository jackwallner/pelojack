package com.jackwallner.pelojack.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.ui.PrimaryButton
import com.jackwallner.pelojack.ui.Type
import com.jackwallner.pelojack.ui.WorkoutProfile
import com.jackwallner.pelojack.workout.PlanImportSession
import kotlinx.coroutines.launch

/** SAF grants read access only to the chosen document; no storage permission needed. */
@Composable
fun PlanImportAction(app: PelojackApp) {
    val scope = rememberCoroutineScope()
    val session = remember(app) { PlanImportSession(app.library) { app.settings.state.value.programId } }
    val state by session.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            session.inspect { checkNotNull(app.contentResolver.openInputStream(uri)) { "Cannot open selected document" } }
        }
    }
    PrimaryButton(if (state.busy) "Reading plan…" else "Import plan", {
        // Some document providers label ZIP/JSON as octet-stream; validate content, not MIME/name.
        picker.launch(arrayOf("*/*"))
    }, enabled = !state.busy)

    state.preview?.let { preview ->
        val workouts = preview.program.workoutIds.mapNotNull { id -> preview.workouts.firstOrNull { it.id == id } ?: app.library.find(id) }
        AlertDialog(
            onDismissRequest = session::cancel,
            title = { Text(preview.program.name) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(preview.program.description, style = Type.BodyDim)
                    Text("${preview.program.weeks.size} weeks · ${workouts.size} rides · ${hoursAndMinutes(workouts.sumOf { it.totalSeconds })}")
                    Text("${preview.workouts.size} new workouts. Import adds this plan only. It will not join, replace or start anything.")
                    preview.program.weeks.forEachIndexed { week, ids ->
                        Text("Week ${week + 1}", style = Type.Heading)
                        ids.forEach { id ->
                            val workout = workouts.firstOrNull { it.id == id }
                            if (workout != null) {
                                Text("${workout.name} · ${hoursAndMinutes(workout.totalSeconds)}", style = Type.Small)
                                WorkoutProfile(workout, Modifier.fillMaxWidth().height(52.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { session.confirm() } }, enabled = !state.busy) { Text("Import plan") } },
            dismissButton = { TextButton(onClick = session::cancel, enabled = !state.busy) { Text("Cancel") } },
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = session::cancel,
            title = { Text(if (message.startsWith("Imported")) "Plan imported" else "Could not import plan") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = session::cancel) { Text("OK") } },
        )
    }
}
