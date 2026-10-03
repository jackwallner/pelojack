package com.jackwallner.pelojack

import com.jackwallner.pelojack.workout.Category
import com.jackwallner.pelojack.workout.PowerZones
import com.jackwallner.pelojack.workout.Program
import com.jackwallner.pelojack.workout.Video
import com.jackwallner.pelojack.workout.Workout
import com.jackwallner.pelojack.workout.ZwoImporter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTest {
    private val workout = Workout.parse(
        """
        {"id": "t", "name": "Test", "category": "climb", "video": {"youtube": "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=4"},
         "segments": [
          {"name": "Warm up", "seconds": 60, "resistance": 30, "cadence": [90, 80]},
          {"name": "Push", "seconds": 30, "resistance": [45, 50], "cue": "Go"},
          {"name": "Zone", "seconds": 20, "zone": 4},
          {"name": "Spin", "seconds": 10, "ftp": 0.5, "test": true}
        ]}
        """,
    )

    @Test
    fun parsesTargetsVideoAndCategory() {
        assertEquals(120, workout.totalSeconds)
        assertEquals(Category.Climb, workout.category)
        assertEquals(Video.YouTube("dQw4w9WgXcQ"), workout.video)
        assertEquals(30..30, workout.segments[0].resistance)
        assertEquals(80..90, workout.segments[0].cadence)
        assertEquals(47, workout.segments[1].resistanceTarget)
        assertEquals("Go", workout.segments[1].cue)
        assertEquals(0.98, workout.segments[2].powerShare!!, 1e-9)
        assertEquals(0.5, workout.segments[3].powerShare!!, 1e-9)
        assertTrue(workout.segments[3].test)
        assertNull(workout.segments[1].powerShare)
    }

    @Test
    fun survivesAJsonRoundTrip() {
        assertEquals(workout, Workout.parse(workout.toJson().toString()))
    }

    @Test
    fun findsTheSegmentForAnElapsedTime() {
        assertEquals(0, workout.positionAt(0.0)?.index)
        assertEquals(0.5, workout.positionAt(59.5)!!.secondsLeft, 1e-9)
        assertEquals(60, workout.positionAt(60.0)!!.startSecond)
        assertEquals(3, workout.positionAt(119.9)?.index)
        assertNull(workout.positionAt(120.0))
    }

    @Test
    fun rejectsWorkoutsWithoutTimedSegments() {
        assertThrows(Exception::class.java) { Workout.parse("""{"id": "x", "name": "X", "segments": []}""") }
        assertThrows(Exception::class.java) {
            Workout.parse("""{"id": "x", "name": "X", "segments": [{"name": "A", "seconds": 0}]}""")
        }
    }

    @Test
    fun extractsYouTubeIds() {
        listOf(
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ?rel=0",
            "https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "dQw4w9WgXcQ",
        ).forEach { assertEquals(it, "dQw4w9WgXcQ", Workout.youTubeId(it)) }
    }

    @Test
    fun powerZonesMatchPeloton() {
        assertEquals(1, PowerZones.zoneOf(80, 200))
        assertEquals(2, PowerZones.zoneOf(110, 200))
        assertEquals(4, PowerZones.zoneOf(200, 200))
        assertEquals(7, PowerZones.zoneOf(400, 200))
        assertEquals(182..211, PowerZones.watts(4, 200))
        assertEquals(302..400, PowerZones.watts(7, 200))
    }

    @Test
    fun difficultyRisesWithIntensity() {
        val easy = Workout.parse(File("src/main/assets/workouts/low-impact-20.json").readText())
        val hard = Workout.parse(File("src/main/assets/workouts/pz-max-30.json").readText())
        assertTrue("${easy.difficulty} < ${hard.difficulty}", easy.difficulty < hard.difficulty)
        assertTrue(easy.difficulty in 1.0..10.0 && hard.difficulty in 1.0..10.0)
    }

    @Test
    fun builtInWorkoutsAreConsistent() {
        val files = File("src/main/assets/workouts").listFiles().orEmpty()
        assertTrue(files.size >= 10)
        files.forEach { file ->
            val builtIn = Workout.parse(file.readText())
            assertEquals(file.nameWithoutExtension, builtIn.id)
            assertEquals("${builtIn.totalSeconds / 60} min", builtIn.name.split(' ').take(2).joinToString(" "))
            assertTrue(builtIn.id, builtIn.category != Category.Custom)
            builtIn.segments.forEach { segment ->
                segment.resistance?.let { assertTrue("${builtIn.id}: ${segment.name}", it.first >= 15 && it.last <= 70) }
            }
        }
    }

    @Test
    fun programsOnlyReferenceBuiltInWorkouts() {
        val ids = File("src/main/assets/workouts").listFiles().orEmpty().map { it.nameWithoutExtension }.toSet()
        File("src/main/assets/programs").listFiles().orEmpty().forEach { file ->
            val program = Program.parse(file.readText())
            assertEquals(file.nameWithoutExtension, program.id)
            program.workoutIds.forEach { assertTrue("${program.id}: $it", it in ids) }
        }
    }

    @Test
    fun programsNumberWeeksAndRides() {
        val program = Program("p", "P", "", listOf(listOf("a", "b"), listOf("c", "d", "e")))
        assertEquals(1 to 1, program.weekAndRide(0))
        assertEquals(1 to 2, program.weekAndRide(1))
        assertEquals(2 to 1, program.weekAndRide(2))
        assertEquals(2 to 3, program.weekAndRide(4))
    }

    @Test
    fun importsZwiftWorkouts() {
        val zwo = """
            <workout_file>
              <name>Sweet spot</name>
              <description>Two by ten.</description>
              <workout>
                <Warmup Duration="300" PowerLow="0.4" PowerHigh="0.7"/>
                <IntervalsT Repeat="2" OnDuration="600" OffDuration="120" OnPower="0.9" OffPower="0.5" Cadence="90"/>
                <FreeRide Duration="60"/>
                <Cooldown Duration="90" PowerLow="0.6" PowerHigh="0.3"/>
              </workout>
            </workout_file>
        """.trimIndent()
        val imported = ZwoImporter.parse(zwo.byteInputStream(), "zwo-sweet")
        assertEquals("Sweet spot", imported.name)
        assertEquals(300 + 2 * 720 + 60 + 90, imported.totalSeconds)
        assertEquals(5, imported.segments.count { it.name == "Warm up" })
        assertEquals(0.43, imported.segments.first().powerShare!!, 1e-9)
        assertEquals(0.67, imported.segments[4].powerShare!!, 1e-9)
        val on = imported.segments.first { it.name == "On" }
        assertEquals(0.9, on.powerShare!!, 1e-9)
        assertEquals(85..95, on.cadence)
        assertNull(imported.segments.first { it.name == "Free ride" }.powerShare)
        assertTrue(imported.custom)
    }
}
