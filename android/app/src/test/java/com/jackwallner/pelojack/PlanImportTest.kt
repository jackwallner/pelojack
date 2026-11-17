package com.jackwallner.pelojack

import com.jackwallner.pelojack.workout.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PlanImportTest {
    private fun plan(id: String = "demo", refs: String = "\"existing\"") =
        """{"formatVersion":1,"id":"$id","name":"Demo","description":"Demo plan","weeks":[[$refs]]}"""
    private fun bundle(vararg entries: Pair<String, String>): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bytes).use { zip -> entries.forEach { (name, text) ->
            zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
        } }
        return bytes.toByteArray()
    }
    private fun workout(id: String = "json-demo") = """{"id":"$id","name":"Demo workout","segments":[{"name":"Easy","seconds":60,"ftp":0.5}]}"""
    private val zwo = """<workout_file><name>Demo ZWO</name><workout><SteadyState Duration="60" Power="0.5"/></workout></workout_file>"""

    @Test fun importsBundleWithJsonAndZwoThenReloadsBoth() {
        val dir = Files.createTempDirectory("plans").toFile()
        try {
            val importer = PlanImporter(dir)
            val bytes = bundle("program.json" to plan(refs = "\"json-demo\",\"zwo-spin\""), "workouts/json-demo.json" to workout(), "workouts/spin.zwo" to zwo)
            val preview = importer.preview(bytes.inputStream(), true, emptySet(), emptySet())
            assertEquals(2, preview.workouts.size)
            assertEquals(120, preview.workouts.sumOf { it.totalSeconds })
            importer.commit(preview, emptySet(), emptySet())
            val reloaded = PlanImporter(dir)
            assertEquals(setOf("json-demo", "zwo-spin"), reloaded.loadWorkouts().map { it.id }.toSet())
            assertEquals(listOf("demo"), reloaded.loadPrograms(reloaded.loadWorkouts().map { it.id }.toSet()).map { it.id })
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsUnsafeZipArchivesWithoutPartialFiles() {
        val dir = Files.createTempDirectory("plans").toFile()
        try {
            val importer = PlanImporter(dir)
            val badNames = listOf("../x.json", "/tmp/x.json", "workouts/../x.json", "workouts\\x.json", "workouts/.hidden.json", "workouts/A.json", "nested/program.json", "workouts/a.txt")
            val unsafe = badNames.map { bundle("program.json" to plan(), it to workout()) }.toMutableList()
            unsafe += bundle("program.json" to plan(), "workouts/a.json" to workout(), "workouts/b.json" to workout()).let { bytes ->
                // Same-length names let us create a duplicate local + central record.
                bytes.toString(Charsets.ISO_8859_1).replace("workouts/b.json", "workouts/a.json").toByteArray(Charsets.ISO_8859_1)
            }
            unsafe += bundle("program.json" to plan()).let { bytes ->
                val copy = bytes.clone()
                val central = copy.indices.first { it + 4 <= copy.size && copy[it] == 0x50.toByte() && copy[it+1] == 0x4b.toByte() && copy[it+2] == 1.toByte() && copy[it+3] == 2.toByte() }
                copy[central + 5] = 3 // Unix creator
                copy[central + 40] = 0xff.toByte(); copy[central + 41] = 0xa1.toByte() // symlink mode
                copy
            }
            unsafe += bundle("program.json" to plan(), "workouts/a.json" to " ".repeat(1024 * 1024 + 1))
            unsafe += bundle(*((listOf("program.json" to plan()) + (1..129).map { "workouts/w$it.json" to workout("w$it") }).toTypedArray()))
            unsafe += bundle("program.json" to plan()).dropLast(10).toByteArray()
            unsafe.forEachIndexed { i, bytes ->
                assertThrows("unsafe archive $i", Exception::class.java) { importer.preview(bytes.inputStream(), true, setOf("existing"), emptySet()) }
                assertTrue(dir.listFiles()!!.isEmpty())
            }
            assertThrows(Exception::class.java) { importer.preview(" ".repeat(1024 * 1024 + 1).byteInputStream(), false, emptySet(), emptySet()) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsInvalidWorkoutsInsteadOfSilentlyDroppingOrClamping() {
        val dir = Files.createTempDirectory("plans").toFile()
        try {
            val importer = PlanImporter(dir)
            val invalidJson = listOf(
                workout().replace("json-demo", "../escape"), workout().replace("\"Demo workout\"", "\"\""),
                workout().replace("60", "0"), workout().replace("60", "1.5"), workout().replace("60", "2147483647"),
                workout().replace("0.5", "-1"), workout().replace("\"ftp\":0.5", "\"zone\":99"),
                workout().replace("\"ftp\":0.5", "\"resistance\":[50,40]"),
                workout().replace("\"ftp\":0.5", "\"cadence\":999"),
            )
            invalidJson.forEach { json ->
                assertThrows(json, Exception::class.java) {
                    importer.preview(bundle("program.json" to plan(refs = "\"json-demo\""), "workouts/a.json" to json).inputStream(), true, emptySet(), emptySet())
                }
            }
            val invalidZwo = listOf(
                zwo.replace("SteadyState", "Unsupported"), zwo.replace("60", "0"), zwo.replace("60", "2147483647"),
                zwo.replace("0.5", "NaN"), zwo.replace(" Power=\"0.5\"", ""), zwo.replace("0.5", "-1"),
                zwo.replace("</workout>", "<Bogus Duration=\"30\"/></workout>"),
                "<!DOCTYPE workout_file [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + zwo,
                zwo.replace("<SteadyState Duration=\"60\" Power=\"0.5\"/>", "<IntervalsT Repeat=\"999999999\" OnDuration=\"60\" OffDuration=\"30\" OnPower=\"1\" OffPower=\"0.5\"/>")
            )
            invalidZwo.forEach { xml ->
                assertThrows(xml, Exception::class.java) {
                    importer.preview(bundle("program.json" to plan(refs = "\"zwo-spin\""), "workouts/spin.zwo" to xml).inputStream(), true, emptySet(), emptySet())
                }
            }
            assertThrows(Exception::class.java) { importer.preview(bundle("program.json" to plan(), "workouts/a.json" to workout()).inputStream(), true, setOf("existing"), emptySet()) }
            assertThrows(Exception::class.java) { importer.preview(bundle("program.json" to plan(refs = "\"json-demo\""), "workouts/a.json" to workout(), "workouts/b.json" to workout()).inputStream(), true, emptySet(), emptySet()) }
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rechecksConflictsAtCommitAndLeavesExistingBytesUntouched() {
        val dir = Files.createTempDirectory("plans").toFile()
        try {
            val importer = PlanImporter(dir)
            val preview = importer.preview(plan().byteInputStream(), false, setOf("existing"), emptySet())
            assertThrows(Exception::class.java) { importer.commit(preview, setOf("existing"), setOf("demo")) }
            assertThrows(Exception::class.java) { importer.commit(preview, emptySet(), emptySet()) }
            assertTrue(dir.listFiles()!!.isEmpty())
            importer.commit(preview, setOf("existing"), emptySet())
            val original = File(dir, "demo/program.json").readBytes()
            assertThrows(Exception::class.java) { PlanImporter(dir).commit(preview, setOf("existing"), emptySet()) }
            assertArrayEquals(original, File(dir, "demo/program.json").readBytes())
            val bundlePreview = importer.preview(bundle("program.json" to plan("new-plan", "\"json-demo\""), "workouts/a.json" to workout()).inputStream(), true, emptySet(), emptySet())
            assertThrows(Exception::class.java) { importer.commit(bundlePreview, setOf("json-demo"), emptySet()) }
            assertEquals(listOf("demo"), dir.listFiles()!!.map { it.name })
        } finally { dir.deleteRecursively() }
    }

    @Test fun reloadsLooseProgramsAndIgnoresPendingUnsafeOrMissingReferences() {
        val dir = Files.createTempDirectory("plans").toFile()
        val other = Files.createTempDirectory("other").toFile()
        try {
            val importer = PlanImporter(dir)
            File(dir, "loose.json").writeText(plan("loose"))
            File(dir, "bad.json").writeText(plan("bad", "\"missing\""))
            File(dir, ".pending-crash").mkdir()
            File(dir, ".pending-crash/program.json").writeText(plan("pending"))
            File(other, "linked.json").writeText(plan("linked"))
            Files.createSymbolicLink(File(dir, "linked.json").toPath(), File(other, "linked.json").toPath())
            assertEquals(listOf("loose"), importer.loadPrograms(setOf("existing")).map { it.id })
            File(dir, "loose.json").delete()
            assertTrue(importer.loadPrograms(setOf("existing")).isEmpty())
            val preview = importer.preview(plan().byteInputStream(), false, setOf("existing"), emptySet())
            Files.createSymbolicLink(File(dir, "demo").toPath(), File(other, "nonexistent").toPath())
            assertThrows(Exception::class.java) { importer.commit(preview, setOf("existing"), emptySet()) }
            assertFalse(File(other, "nonexistent").exists())
        } finally { dir.deleteRecursively(); other.deleteRecursively() }
    }

    @Test fun workoutNamedProgramCannotReplaceBundleManifest() {
        val dir = Files.createTempDirectory("plans").toFile()
        try {
            val importer = PlanImporter(dir)
            val preview = importer.preview(bundle("program.json" to plan(refs = "\"program\""), "workouts/a.json" to workout("program")).inputStream(), true, emptySet(), emptySet())
            importer.commit(preview, emptySet(), emptySet())
            assertEquals("demo", importer.loadPrograms(setOf("program")).single().id)
            assertEquals("program", importer.loadWorkouts().single().id)
        } finally { dir.deleteRecursively() }
    }

    @Test fun refusesSymlinkStorageAndTamperedPreviewBeforeAnyWrites() {
        val root = Files.createTempDirectory("plans").toFile()
        val target = Files.createTempDirectory("target").toFile()
        try {
            val dir = File(root, "linked")
            Files.createSymbolicLink(dir.toPath(), target.toPath())
            val importer = PlanImporter(dir)
            val preview = importer.preview(plan().byteInputStream(), false, setOf("existing"), emptySet())
            assertThrows(Exception::class.java) { importer.commit(preview, setOf("existing"), emptySet()) }
            assertTrue(target.listFiles()!!.isEmpty())
            val safe = PlanImporter(File(root, "safe"))
            val forged = PlanImporter.Preview(Program("../outside", "Bad", "", listOf(listOf("existing"))), emptyList())
            assertThrows(Exception::class.java) { safe.commit(forged, setOf("existing"), emptySet()) }
            assertFalse(File(root, "outside").exists())
        } finally { root.deleteRecursively(); target.deleteRecursively() }
    }

    @Test fun documentedExampleBundleImportsAndReloadsWithoutExistingWorkouts() {
        val dir = Files.createTempDirectory("example-plan").toFile()
        try {
            val importer = PlanImporter(dir)
            val source = File("../../docs/examples/custom-plans/example-two-week-plan")
            val file = File("${source.path}.zip")
            assertTrue("Missing documented example bundle", file.isFile)
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                assertEquals(setOf("program.json", "workouts/example-easy-spin.json", "workouts/example-intervals.zwo"), entries.map { it.name }.toSet())
                assertEquals(3, entries.size)
                entries.forEach { entry ->
                    assertArrayEquals(File(source, entry.name).readBytes(), zip.getInputStream(entry).use { it.readBytes() })
                }
            }
            val preview = file.inputStream().use { importer.preview(it, true, emptySet(), emptySet()) }
            assertEquals("example-two-week-plan", preview.program.id)
            assertEquals(listOf(listOf("zwo-example-intervals"), listOf("example-easy-spin")), preview.program.weeks)
            assertEquals(setOf("zwo-example-intervals", "example-easy-spin"), preview.workouts.map { it.id }.toSet())
            val intervals = preview.workouts.first { it.id == "zwo-example-intervals" }
            assertEquals("Example intervals", intervals.name)
            assertEquals(600, intervals.totalSeconds)
            assertEquals(2, intervals.segments.count { it.name == "On" })
            val easy = preview.workouts.first { it.id == "example-easy-spin" }
            assertEquals("Example easy spin", easy.name)
            assertEquals(420, easy.totalSeconds)
            assertEquals(listOf(90, 240, 90), easy.segments.map { it.seconds })
            importer.commit(preview, emptySet(), emptySet())
            val reloaded = importer.loadWorkouts()
            assertEquals(preview.workouts.associateBy { it.id }, reloaded.associateBy { it.id })
            assertEquals(preview.program, importer.loadPrograms(reloaded.map { it.id }.toSet()).single())
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedPublishRemovesStagingAndExposesNeitherPlanNorWorkouts() {
        val dir = Files.createTempDirectory("publish-failure").toFile()
        try {
            var attempted = false
            val importer = PlanImporter(dir) { stage, destination ->
                attempted = true
                assertTrue(File(stage, "program.json").isFile)
                assertTrue(File(stage, "workouts/json-demo.json").isFile)
                assertEquals(File(dir, "demo"), destination)
                false // filesystem publication boundary fails after all staging writes
            }
            val preview = importer.preview(bundle("program.json" to plan(refs = "\"json-demo\""), "workouts/a.json" to workout()).inputStream(), true, emptySet(), emptySet())
            assertThrows(Exception::class.java) { importer.commit(preview, emptySet(), emptySet()) }
            assertTrue(attempted)
            assertTrue(dir.listFiles()!!.isEmpty())
            assertTrue(importer.loadWorkouts().isEmpty())
            assertTrue(importer.loadPrograms(emptySet()).isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsExcessiveJsonNestingBeforeRecursiveParsing() {
        val dir = Files.createTempDirectory("nested").toFile()
        try {
            val importer = PlanImporter(dir)
            val deep = "[".repeat(40) + "0" + "]".repeat(40)
            val nestedPlan = plan().dropLast(1) + ",\"extra\":" + deep + "}"
            assertThrows(Exception::class.java) { importer.preview(nestedPlan.byteInputStream(), false, setOf("existing"), emptySet()) }
            val nestedWorkout = workout().dropLast(1) + ",\"extra\":" + deep + "}"
            assertThrows(Exception::class.java) { importer.preview(bundle("program.json" to plan(refs = "\"json-demo\""), "workouts/a.json" to nestedWorkout).inputStream(), true, emptySet(), emptySet()) }
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsNormalizationThatWouldExceedReloadLimit() {
        val dir = Files.createTempDirectory("normalization").toFile()
        try {
            val root = org.json.JSONObject().put("id", "json-demo").put("name", "Large")
            val segments = org.json.JSONArray()
            repeat(4096) { segments.put(org.json.JSONObject().put("name", "a").put("seconds", 1).put("resistance", 50).put("cadence", 80)) }
            root.put("segments", segments)
            segments.getJSONObject(0).put("cue", "")
            val length = 1024 * 1024 - 100 - root.toString().toByteArray().size
            val random = java.util.Random(1)
            segments.getJSONObject(0).put("cue", CharArray(length) { ('a'.code + random.nextInt(26)).toChar() }.concatToString())
            assertTrue(root.toString().toByteArray().size < 1024 * 1024)
            assertThrows(Exception::class.java) {
                PlanImporter(dir).preview(bundle("program.json" to plan(refs = "\"json-demo\""), "workouts/a.json" to root.toString()).inputStream(), true, emptySet(), emptySet())
            }
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsMalformedOrConflictingPlansWithoutWriting() {
        val dir = Files.createTempDirectory("plans").toFile()
        try {
            val importer = PlanImporter(dir)
            val invalid = listOf(
                plan("../escape"), plan("demo", "\"missing\""), plan("demo", "\"existing\",\"existing\""),
                plan().replace("[[\"existing\"]]", "[]"), plan().replace("[[\"existing\"]]", "[[]]"),
                plan().replace("\"Demo\"", "\" \""), plan().replace("formatVersion\":1", "formatVersion\":2"),
                plan().replace("\"existing\"", "42"), plan().replace("\"id\":\"demo\"", "\"id\":42"),
            )
            invalid.forEach { json ->
                assertThrows(json, Exception::class.java) { importer.preview(json.byteInputStream(), false, setOf("existing"), emptySet()) }
            }
            assertThrows(Exception::class.java) { importer.preview(plan().byteInputStream(), false, setOf("existing"), setOf("demo")) }
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun previewsAndAtomicallyImportsJsonReferencingExistingWorkout() {
        val dir = Files.createTempDirectory("plans").toFile()
        try {
            val importer = PlanImporter(dir)
            val preview = importer.preview(plan().byteInputStream(), false, setOf("existing"), emptySet())
            assertEquals("demo", preview.program.id)
            assertTrue(dir.listFiles()!!.isEmpty())
            importer.commit(preview, setOf("existing"), emptySet())
            assertEquals(listOf("demo"), importer.loadPrograms(setOf("existing")).map { it.id })
            assertTrue(importer.loadWorkouts().isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
