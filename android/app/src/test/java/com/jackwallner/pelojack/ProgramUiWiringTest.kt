package com.jackwallner.pelojack

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Wiring guards, not a substitute for a device Compose/file-provider smoke test. */
class ProgramUiWiringTest {
    @Test fun programsExposeDocumentPickerAndExplicitPreviewConfirmation() {
        val action = File("src/main/java/com/jackwallner/pelojack/ui/screens/PlanImportAction.kt")
        assertTrue("Missing user-facing plan picker", action.isFile)
        val source = action.readText()
        assertTrue(source.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(source.contains("Import plan"))
        assertTrue(source.contains("AlertDialog("))
        assertTrue(source.contains("session.confirm()"))
        assertTrue(source.contains("WorkoutProfile("))
        assertFalse(source.contains("settings.update"))
        assertFalse(source.contains("app.ride.open"))
        val screen = File("src/main/java/com/jackwallner/pelojack/ui/screens/ProgramsScreen.kt").readText()
        assertTrue(screen.contains("PlanImportAction(app)"))
        assertTrue(screen.contains("programsState.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("horizontalScroll(rememberScrollState())"))
        assertTrue(screen.contains("Modifier.width(360.dp).fillMaxHeight()"))
    }
}
