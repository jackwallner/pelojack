package com.jackwallner.pelojack

import com.jackwallner.pelojack.workout.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Bounded independent-review regressions; no Application, network or hardware. */
class ImportBoundaryRegressionTest {
    private fun plan(ref: String = "existing") = """{"formatVersion":1,"id":"regression","name":"Regression","weeks":[["$ref"]]}"""
    private val workout = """{"id":"fresh","name":"Fresh","segments":[{"name":"Easy","seconds":10}]}"""
    private fun bundle(program: String, content: String, suffix: String = "json"): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            listOf("program.json" to program, "workouts/fresh.$suffix" to content).forEach { (name, text) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun jsonDepthBoundaryCountsContainersNotQuotedBrackets() {
        val dir = Files.createTempDirectory("import-depth-boundary").toFile()
        try {
            val importer = PlanImporter(dir)
            for (arrays in listOf(31, 32)) {
                val plainExtra = ",\"extra\":" + "[".repeat(arrays) + "0" + "]".repeat(arrays)
                if (arrays == 31) {
                    importer.preview((plan().dropLast(1) + plainExtra + "}").byteInputStream(), false, setOf("existing"), emptySet())
                    importer.preview(bundle(plan("fresh"), workout.dropLast(1) + plainExtra + "}").inputStream(), true, emptySet(), emptySet())
                } else {
                    assertThrows(Exception::class.java) { importer.preview((plan().dropLast(1) + plainExtra + "}").byteInputStream(), false, setOf("existing"), emptySet()) }
                    assertThrows(Exception::class.java) { importer.preview(bundle(plan("fresh"), workout.dropLast(1) + plainExtra + "}").inputStream(), true, emptySet(), emptySet()) }
                }
            }
            val strictValues = """, "extra":{"a'b":[true,false,null,-1,1.2e-3,"[ ] { } ' \" \\ \u005b"]}"""
            importer.preview((plan().dropLast(1) + strictValues + "}").byteInputStream(), false, setOf("existing"), emptySet())
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun lowZwoCadenceNormalizesWithinBoundsAndCommitsIdentically() {
        for (cadence in listOf(0, 1, 4, 5, 6, 244, 245)) {
            val dir = Files.createTempDirectory("import-cadence").toFile()
            try {
                val xml = """<workout_file><name>Cadence</name><workout><SteadyState Duration="10" Power="0.4" Cadence="$cadence"/><IntervalsT Repeat="1" OnDuration="10" OffDuration="10" OnPower="0.7" OffPower="0.4" Cadence="$cadence" CadenceResting="$cadence"/></workout></workout_file>"""
                val importer = PlanImporter(dir)
                val preview = importer.preview(bundle(plan("zwo-fresh"), xml, "zwo").inputStream(), true, emptySet(), emptySet())
                val expected = if (cadence == 0) null else maxOf(0, cadence - 5)..(cadence + 5)
                preview.workouts.single().segments.forEach { assertEquals("cadence=$cadence", expected, it.cadence) }
                assertTrue(dir.listFiles()!!.isEmpty())
                importer.commit(preview, emptySet(), emptySet())
                assertEquals(preview.workouts, importer.loadWorkouts())
                assertEquals(preview.program, importer.loadPrograms(setOf("zwo-fresh")).single())
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun rejectsLenientSyntaxAndDepthBypassesInBothJsonInputsBeforeWriting() {
        val dir = Files.createTempDirectory("import-syntax").toFile()
        try {
            val importer = PlanImporter(dir)
            val deep = "[".repeat(40) + "0" + "]".repeat(40)
            // Reviewer repro plus syntax accepted by Android's permissive JSONTokener.
            val extras = listOf(
                ",a'b:$deep", ",unquoted:0", ",'single':0", ",\"extra\":$deep",
                ",\"extra\":/* comment */0", ",\"extra\":[0;1]", ",\"extra\":01",
                ",\"extra\":+1", ",\"extra\":.5", ",\"extra\":NaN", ",\"extra\":[,]",
                ",\"extra\":\"bad\\x20\"", ",\"extra\":\"bad\nline\"", ","
            )
            for (extra in extras) {
                val badPlan = plan().dropLast(1) + extra + "}"
                assertThrows("program $extra", Exception::class.java) {
                    importer.preview(badPlan.byteInputStream(), false, setOf("existing"), emptySet())
                }
                val badWorkout = workout.dropLast(1) + extra + "}"
                assertThrows("workout $extra", Exception::class.java) {
                    importer.preview(bundle(plan("fresh"), badWorkout).inputStream(), true, emptySet(), emptySet())
                }
            }
            for (tail in listOf("{}", "garbage", "// comment", "/*comment*/")) {
                assertThrows(Exception::class.java) { importer.preview((plan() + tail).byteInputStream(), false, setOf("existing"), emptySet()) }
                assertThrows(Exception::class.java) { importer.preview(bundle(plan("fresh"), workout + tail).inputStream(), true, emptySet(), emptySet()) }
            }
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
