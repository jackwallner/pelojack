package com.jackwallner.pelojack.workout

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val WORKOUT_DIR = "workouts"
private const val PROGRAM_DIR = "programs"

/**
 * Built-in workouts and programs from assets, plus workouts made on the tablet or pushed to its
 * `workouts` folder (JSON, or Zwift `.zwo`). A tablet file with a built-in id replaces it.
 */
class WorkoutLibrary internal constructor(
    val workoutsDir: File?,
    val programsDir: File?,
    val videosDir: File?,
    private val assetNames: (String) -> List<String>,
    private val assetText: (String) -> String,
    private val report: (String, Exception) -> Unit = { _, _ -> },
) {
    constructor(context: Context) : this(
        context.getExternalFilesDir(WORKOUT_DIR), context.getExternalFilesDir(PROGRAM_DIR), context.getExternalFilesDir("videos"),
        { context.assets.list(it).orEmpty().toList() },
        { context.assets.open(it).bufferedReader().use { reader -> reader.readText() } },
        { name, e -> Log.w("Pelojack", "Skipping $name: ${e.message}"); Unit },
    )

    private var planWorkoutIds: Set<String> = emptySet()
    private val mutableWorkouts = MutableStateFlow<List<Workout>>(emptyList())
    val workouts: StateFlow<List<Workout>> = mutableWorkouts

    private val mutablePrograms = MutableStateFlow<List<Program>>(emptyList())
    val programsState: StateFlow<List<Program>> = mutablePrograms
    val programs: List<Program> get() = programsState.value

    @Synchronized fun reload() {
        val builtIn = assetNames(WORKOUT_DIR).mapNotNull { name ->
            orNull(name) { Workout.parse(asset("$WORKOUT_DIR/$name")) }
        }
        val local = workoutsDir?.listFiles().orEmpty().sortedBy { it.name }.mapNotNull { file ->
            when (file.extension.lowercase()) {
                "json" -> orNull(file.name) { Workout.parse(file.readText(), custom = true) }
                "zwo" -> orNull(file.name) { file.inputStream().use { ZwoImporter.parse(it, "zwo-" + file.nameWithoutExtension) } }
                else -> null
            }
        }
        val importer = programsDir?.let(::PlanImporter)
        val bundled = importer?.loadWorkouts().orEmpty()
        val loadedWorkouts = (local + builtIn + bundled).distinctBy { it.id }
        val builtInPrograms = assetNames(PROGRAM_DIR).mapNotNull { name ->
            orNull(name) { Program.parse(asset("$PROGRAM_DIR/$name")) }
        }
        val loadedPrograms = (builtInPrograms + importer?.loadPrograms(loadedWorkouts.map { it.id }.toSet()).orEmpty()).distinctBy { it.id }
        // Ownership survives a loose edited override. Also protect reference-only plans.
        planWorkoutIds = bundled.map { it.id }.toSet() + loadedPrograms.flatMap { it.workoutIds }
        mutableWorkouts.value = loadedWorkouts
        mutablePrograms.value = loadedPrograms
    }

    @Synchronized fun previewPlan(input: java.io.InputStream, zip: Boolean, activeProgramId: String? = null): PlanImporter.Preview {
        reload()
        return PlanImporter(checkNotNull(programsDir) { "Plan storage unavailable" }).preview(
            input, zip, workouts.value.map { it.id }.toSet(), programs.map { it.id }.toSet() + listOfNotNull(activeProgramId),
        )
    }
    /** Add only. Joining a plan is a separate, existing Start program action. */
    @Synchronized fun importPlan(preview: PlanImporter.Preview, activeProgramId: String? = null) {
        reload()
        PlanImporter(checkNotNull(programsDir) { "Plan storage unavailable" }).commit(
            preview, workouts.value.map { it.id }.toSet(), programs.map { it.id }.toSet() + listOfNotNull(activeProgramId),
        )
        reload()
    }

    fun find(id: String?): Workout? = mutableWorkouts.value.firstOrNull { it.id == id }

    /** Writes a workout made on the tablet and returns it as loaded. */
    fun save(workout: Workout): Workout {
        val dir = checkNotNull(workoutsDir) { "storage unavailable" }
        File(dir, "${workout.id}.json").writeText(workout.toJson().toString(2))
        reload()
        return find(workout.id) ?: workout
    }

    /** Plan-owned/referenced workouts are editable, but not deletable or revertible here. */
    @Synchronized fun canDelete(workout: Workout): Boolean = workout.custom && workout.id !in planWorkoutIds

    /** True only when the workout is gone; callers must retain user state on refusal/failure. */
    @Synchronized fun delete(workout: Workout): Boolean {
        reload() // Recheck ownership if a plan arrived after the confirmation dialog opened.
        val current = find(workout.id) ?: return false
        if (!canDelete(current)) return false
        val file = workoutsDir?.let { File(it, "${workout.id}.json") } ?: return false
        if (!file.delete()) return false
        reload()
        return find(workout.id) == null
    }

    /** Resolves a video file name against the videos folder; addresses pass through. */
    fun videoLocation(video: Video.File): String =
        if (video.location.contains("://")) video.location else File(videosDir, video.location).toURI().toString()

    private fun asset(path: String) = assetText(path)

    private fun <T> orNull(name: String, read: () -> T): T? = try {
        read()
    } catch (e: Exception) {
        report(name, e)
        null
    }
}
