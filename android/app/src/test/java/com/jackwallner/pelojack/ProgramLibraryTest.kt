package com.jackwallner.pelojack

import com.jackwallner.pelojack.workout.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ProgramLibraryTest {
    @Test fun importPreviewAndConfirmationNeverReplaceExistingIdsOrActivePlan() {
        val root = Files.createTempDirectory("library").toFile()
        try {
            val assets = File("src/main/assets")
            val library = WorkoutLibrary(File(root, "workouts").apply { mkdir() }, File(root, "programs").apply { mkdir() }, null,
                { File(assets, it).list().orEmpty().toList() }, { File(assets, it).readText() })
            library.reload()
            val existing = library.workouts.value.first().id
            fun json(id: String) = """{"formatVersion":1,"id":"$id","name":"New","weeks":[["$existing"]]}"""
            assertThrows(Exception::class.java) { library.previewPlan(json("active-missing").byteInputStream(), false, "active-missing") }
            assertThrows(Exception::class.java) { library.previewPlan(json(library.programs.first().id).byteInputStream(), false) }
            val preview = library.previewPlan(json("new").byteInputStream(), false)
            assertFalse(library.programs.any { it.id == "new" })
            assertThrows(Exception::class.java) { library.importPlan(preview, "new") }
            library.importPlan(preview, "other-active")
            assertTrue(library.programsState.value.any { it.id == "new" })
            assertEquals(existing, library.find(existing)!!.id)
            assertThrows(Exception::class.java) { library.importPlan(preview) }
        } finally { root.deleteRecursively() }
    }

    @Test fun reloadPublishesExternalPlansAndPreservesBuiltInsAndWorkoutSelection() {
        val root = Files.createTempDirectory("library").toFile()
        try {
            val workouts = File(root, "workouts").apply { mkdir() }
            val programs = File(root, "programs").apply { mkdir() }
            val assets = File("src/main/assets")
            val library = WorkoutLibrary(workouts, programs, null,
                { path -> File(assets, path).list().orEmpty().toList() },
                { path -> File(assets, path).readText() })
            library.reload()
            val builtIn = library.programs.toList()
            val selected = library.workouts.value.first()
            File(programs, "local.json").writeText("""{"formatVersion":1,"id":"local","name":"Local","weeks":[["${selected.id}"]]}""")
            library.reload()
            assertEquals(builtIn.map { it.id }.toSet() + "local", library.programsState.value.map { it.id }.toSet())
            assertEquals(selected, library.find(selected.id))
            val protected = builtIn.first()
            File(programs, "collision.json").writeText("""{"formatVersion":1,"id":"${protected.id}","name":"Replacement","weeks":[["${selected.id}"]]}""")
            library.reload()
            assertEquals(protected, library.programs.first { it.id == protected.id })
            File(programs, "local.json").delete()
            library.reload()
            assertFalse(library.programsState.value.any { it.id == "local" })
        } finally { root.deleteRecursively() }
    }
}
