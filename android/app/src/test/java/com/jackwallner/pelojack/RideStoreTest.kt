package com.jackwallner.pelojack

import com.jackwallner.pelojack.ride.RideStore
import com.jackwallner.pelojack.ride.RideSummary
import com.jackwallner.pelojack.ride.Sample
import com.jackwallner.pelojack.ride.Totals
import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideStoreTest {
    private fun summary(startedAt: Long, title: String = "Ride") =
        RideSummary(startedAt, title, Totals(seconds = 1200, kilojoules = 200.0), source = "peloton", instructor = "Coach")

    private fun importFolder(vararg rides: RideSummary): File {
        val folder = Files.createTempDirectory("import").toFile()
        File(folder, "rides.json").writeText(JSONArray(rides.map { it.toJson() }).toString())
        rides.forEach { File(folder, "${it.startedAt}.json").writeText("""{"power":[100,110],"cadence":[80,82],"resistance":[40,41],"heartRate":[0,120]}""") }
        return folder
    }

    @Test
    fun importMergesNewestFirstAndSkipsKnownRides() {
        val store = RideStore(Files.createTempDirectory("rides").toFile())
        store.save(summary(3_000, "Recorded"), listOf(Sample(150, 90, 45, null)))

        assertEquals(2, store.importFrom(importFolder(summary(1_000), summary(3_000), summary(2_000))))
        assertEquals(listOf(3_000L, 2_000L, 1_000L), store.rides.value.map { it.startedAt })
        assertEquals("Recorded", store.rides.value.first().title)
        assertEquals("Coach", store.rides.value.last().instructor)
        assertEquals(Sample(110, 82, 41, 120), store.samples(1_000)[1])

        val again = importFolder(summary(1_000))
        assertEquals(0, store.importFrom(again))
        assertTrue(again.listFiles()!!.isEmpty())
    }

    @Test
    fun importedRidesSurviveReopening() {
        val dir = Files.createTempDirectory("rides").toFile()
        RideStore(dir).importFrom(importFolder(summary(1_000)))
        assertEquals("peloton", RideStore(dir).rides.value.single().source)
    }
}
