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
class WorkoutLibrary(private val context: Context) {
    val workoutsDir: File? get() = context.getExternalFilesDir(WORKOUT_DIR)
    val videosDir: File? get() = context.getExternalFilesDir("videos")

    private val mutableWorkouts = MutableStateFlow<List<Workout>>(emptyList())
    val workouts: StateFlow<List<Workout>> = mutableWorkouts

    val programs: List<Program> by lazy {
        context.assets.list(PROGRAM_DIR).orEmpty().mapNotNull { name ->
            orNull(name) { Program.parse(asset("$PROGRAM_DIR/$name")) }
        }
    }

    fun reload() {
        val builtIn = context.assets.list(WORKOUT_DIR).orEmpty().mapNotNull { name ->
            orNull(name) { Workout.parse(asset("$WORKOUT_DIR/$name")) }
        }
        val local = workoutsDir?.listFiles().orEmpty().sortedBy { it.name }.mapNotNull { file ->
            when (file.extension.lowercase()) {
                "json" -> orNull(file.name) { Workout.parse(file.readText(), custom = true) }
                "zwo" -> orNull(file.name) { file.inputStream().use { ZwoImporter.parse(it, "zwo-" + file.nameWithoutExtension) } }
                else -> null
            }
        }
        mutableWorkouts.value = (local + builtIn).distinctBy { it.id }
    }

    fun find(id: String?): Workout? = mutableWorkouts.value.firstOrNull { it.id == id }

    /** Writes a workout made on the tablet and returns it as loaded. */
    fun save(workout: Workout): Workout {
        val dir = checkNotNull(workoutsDir) { "storage unavailable" }
        File(dir, "${workout.id}.json").writeText(workout.toJson().toString(2))
        reload()
        return find(workout.id) ?: workout
    }

    fun delete(workout: Workout) {
        workoutsDir?.let { File(it, "${workout.id}.json").delete() }
        reload()
    }

    /** Resolves a video file name against the videos folder; addresses pass through. */
    fun videoLocation(video: Video.File): String =
        if (video.location.contains("://")) video.location else File(videosDir, video.location).toURI().toString()

    private fun asset(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }

    private fun <T> orNull(name: String, read: () -> T): T? = try {
        read()
    } catch (e: Exception) {
        Log.w("Pelojack", "Skipping $name: ${e.message}")
        null
    }
}
