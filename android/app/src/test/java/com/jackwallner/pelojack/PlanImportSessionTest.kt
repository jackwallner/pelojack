package com.jackwallner.pelojack

import com.jackwallner.pelojack.workout.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlanImportSessionTest {
    @Test fun pickerPreviewCancelConfirmAndErrorsAreAddOnly() = runBlocking {
        val root = Files.createTempDirectory("import-ui").toFile()
        try {
            val assets = File("src/main/assets")
            val library = WorkoutLibrary(File(root, "workouts").apply { mkdir() }, File(root, "programs").apply { mkdir() }, null,
                { File(assets, it).list().orEmpty().toList() }, { File(assets, it).readText() })
            library.reload()
            var active = "unchanged-active"
            val selected = library.workouts.value.first().id
            val json = """{"formatVersion":1,"id":"picked","name":"Picked plan","weeks":[["$selected"]]}"""
            val session = PlanImportSession(library) { active }
            session.inspect { json.byteInputStream() }
            assertEquals("picked", session.state.value.preview!!.program.id)
            assertFalse(library.programs.any { it.id == "picked" })
            session.cancel()
            assertNull(session.state.value.preview)
            session.inspect { json.byteInputStream() }
            active = "picked" // changed while confirmation is open
            session.confirm()
            assertTrue(session.state.value.message!!.contains("already exists"))
            assertFalse(library.programs.any { it.id == "picked" })
            active = "unchanged-active"
            session.inspect { json.byteInputStream() }
            session.confirm()
            assertNull(session.state.value.preview)
            assertTrue(session.state.value.message!!.contains("Imported"))
            assertEquals("unchanged-active", active)
            assertTrue(library.programs.any { it.id == "picked" })
            session.inspect { error("Provider unavailable") }
            assertTrue(session.state.value.message!!.contains("Provider unavailable"))
            assertFalse(session.state.value.busy)
        } finally { root.deleteRecursively() }
    }
}
