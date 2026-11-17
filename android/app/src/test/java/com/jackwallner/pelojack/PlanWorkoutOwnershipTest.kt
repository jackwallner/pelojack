package com.jackwallner.pelojack

import com.jackwallner.pelojack.workout.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PlanWorkoutOwnershipTest {
    private fun bundle(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            listOf(
                "program.json" to """{"formatVersion":1,"id":"owned","name":"Owned","weeks":[["fresh"]]}""",
                "workouts/fresh.json" to """{"id":"fresh","name":"Fresh","segments":[{"name":"Easy","seconds":17}]}"""
            ).forEach { (name, text) -> zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test fun classDetailHidesPlanOwnedDeleteAndCleansUserStateOnlyOnSuccess() {
        // Static Compose wiring guard; not rendered Android UI execution.
        val source = File("src/main/java/com/jackwallner/pelojack/ui/screens/ClassDetailScreen.kt").readText()
        assertTrue(source.contains("if (app.library.canDelete(workout)) {"))
        assertTrue(source.contains("if (confirmDelete && app.library.canDelete(workout)) {"))
        assertTrue(source.contains("if (app.library.delete(workout)) {"))
        val success = source.substringAfter("if (app.library.delete(workout)) {").substringBefore("\n                    }")
        assertTrue(success.contains("app.settings.update"))
        assertTrue(success.contains("nav.back()"))
    }

    @Test fun standaloneDeleteSucceedsButLateReferenceOnlyPlanProtectsWorkout() {
        val root = Files.createTempDirectory("reference-ownership").toFile()
        try {
            val library = WorkoutLibrary(File(root, "workouts").apply { mkdir() }, File(root, "programs").apply { mkdir() }, null, { emptyList() }, { error("no assets") })
            val standalone = Workout("loose", "Loose", "", Category.Custom, listOf(Segment("Easy", 10)), custom = true)
            library.save(standalone)
            assertTrue(library.canDelete(standalone))
            assertTrue(library.delete(standalone))
            assertNull(library.find("loose"))
            assertFalse(library.delete(standalone))
            library.save(standalone)
            // External plan arriving while a delete confirmation is open, without prior reload.
            val bytes = File(root, "workouts/loose.json").readBytes()
            File(root, "programs/reference.json").writeText("""{"formatVersion":1,"id":"reference","name":"Reference","weeks":[["loose"]]}""")
            assertFalse(library.delete(standalone))
            assertFalse(library.canDelete(standalone))
            assertArrayEquals(bytes, File(root, "workouts/loose.json").readBytes())
            assertEquals(listOf("loose"), library.programs.single().workoutIds)
        } finally { root.deleteRecursively() }
    }

    @Test fun bundledWorkoutAndEditedOverrideCannotBeDeletedOrReverted() {
        val root = Files.createTempDirectory("plan-ownership").toFile()
        try {
            val library = WorkoutLibrary(File(root, "workouts").apply { mkdir() }, File(root, "programs").apply { mkdir() }, null, { emptyList() }, { error("no assets") })
            library.importPlan(library.previewPlan(bundle().inputStream(), true))
            val original = library.find("fresh")!!
            assertFalse(library.canDelete(original))
            // Existing public delete must refuse: no silent success followed by resurrection.
            assertEquals(false, library.delete(original))
            assertEquals(original, library.find("fresh"))
            val edited = library.save(original.copy(name = "Edited"))
            assertFalse(library.canDelete(edited))
            val bytes = File(root, "workouts/fresh.json").readBytes()
            assertEquals(false, library.delete(edited))
            assertArrayEquals(bytes, File(root, "workouts/fresh.json").readBytes())
            library.reload()
            assertEquals(edited, library.find("fresh"))
            assertEquals(listOf("fresh"), library.programs.single().workoutIds)
        } finally { root.deleteRecursively() }
    }
}
