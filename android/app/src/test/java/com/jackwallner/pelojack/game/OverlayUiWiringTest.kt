package com.jackwallner.pelojack.game

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring guards only; actual Android HOME/lifecycle/Compose need device validation. */
class OverlayUiWiringTest {
    private fun source(path: String) = File("src/main/java/com/jackwallner/pelojack/$path").readText()

    @Test fun publicContributionDoesNotShipColdStartPreviewEntryPoints() {
        // Removal guard, not a claim of Android runtime/startup isolation.
        val previewNames = listOf("Overlay" + "PreviewActivity", "Overlay" + "PreviewService")
        val shippedSources = listOf("main", "debug", "release").flatMap { variant ->
            File("src/$variant").walkTopDown().filter { it.isFile && it.extension in listOf("kt", "xml") }.toList()
        }
        for (file in shippedSources) {
            val text = file.readText()
            assertFalse("Preview entry point retained in $file", previewNames.any { it in text })
        }
        val remnants = mutableListOf<String>()
        for (file in shippedSources) {
            val text = file.readText()
            if (text.contains("has" + "Preview") || text.contains("isolated debug" + " preview")) {
                remnants += "Preview-only policy/comment retained in $file"
            }
        }
        val strip = source("game/RideStrip.kt")
        val parameters = strip.substringAfter("fun RideStrip(").substringBefore(") {")
        if (!Regex("state: RideState\\s*,").containsMatchIn(parameters) ||
            Regex("(?:workout|elapsedSeconds):").containsMatchIn(parameters)) {
            remnants += "RideStrip must require ride state without independent workout/elapsed inputs"
        }
        val policyTests = File("src/test/java/com/jackwallner/pelojack/game/OverlayPolicyTest.kt").readText()
        if (policyTests.contains("preview", ignoreCase = true)) remnants += "Preview-only policy tests retained"
        assertTrue(remnants.joinToString("\n"), remnants.isEmpty())
        val docs = File("../../docs/workout-overlay.md").readText()
        assertFalse(docs.contains("Debug-only profile preview"))
        assertFalse(previewNames.any { it in docs })
    }

    @Test fun `ride exposes explicit minimize wired to default home without ride commands`() {
        val screen = source("ui/screens/RideScreen.kt")
        assertTrue("Obvious minimize action is missing", screen.contains("QuietButton(\"Minimize to overlay\""))
        assertTrue(screen.contains("actions.minimizeToOverlay"))
        assertTrue(source("MainActivity.kt").contains("minimizeToOverlay = app.game::minimizeToOverlay"))
        val game = source("game/GameMode.kt")
        val action = game.substringAfter("fun minimizeToOverlay()").substringBefore("fun watchCamera()")
        assertTrue(action.contains("resolveActivity("))
        assertTrue(action.contains("queryIntentActivities("))
        assertTrue(action.contains("MATCH_DEFAULT_ONLY"))
        assertTrue(action.contains("Intent.CATEGORY_HOME"))
        assertTrue(action.contains("Intent.ACTION_MAIN"))
        assertTrue(action.contains("component = home"))
        assertTrue(action.contains("it.packageName == context.packageName"))
        assertTrue(action.contains("OverlayMinimizer.minimize("))
        for (forbidden in listOf("ride.open(", "ride.togglePause(", "ride.finish(", "nudge", "createChooser", "requestPermission(")) {
            assertFalse("Minimize must not call $forbidden", action.contains(forbidden))
        }
        assertTrue(File("src/main/AndroidManifest.xml").readText().substringBefore("<application").contains("android.intent.category.HOME"))
    }
}
