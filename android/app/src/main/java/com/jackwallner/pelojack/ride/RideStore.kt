package com.jackwallner.pelojack.ride

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RideSummary(
    /** Wall-clock start in epoch milliseconds; also the ride's id. */
    val startedAt: Long,
    val title: String,
    val totals: Totals,
    val workoutId: String? = null,
    val category: String? = null,
    /** Length of the workout ridden; null for a free ride. */
    val plannedSeconds: Int? = null,
    val programId: String? = null,
    /** From an FTP test: 95% of the 20-minute effort. */
    val ftpEstimate: Int? = null,
    /** Where a ride came from when Pelojack did not record it ("peloton" for the history import). */
    val source: String? = null,
    val instructor: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("startedAt", startedAt)
        .put("title", title)
        .put("seconds", totals.seconds)
        .put("kilojoules", totals.kilojoules)
        .put("miles", totals.miles)
        .put("avgPower", totals.avgPower)
        .put("avgCadence", totals.avgCadence)
        .put("avgResistance", totals.avgResistance)
        .put("maxPower", totals.maxPower)
        .putOpt("avgHeartRate", totals.avgHeartRate)
        .putOpt("maxHeartRate", totals.maxHeartRate)
        .putOpt("workoutId", workoutId)
        .putOpt("category", category)
        .putOpt("plannedSeconds", plannedSeconds)
        .putOpt("programId", programId)
        .putOpt("ftpEstimate", ftpEstimate)
        .putOpt("source", source)
        .putOpt("instructor", instructor)

    companion object {
        fun fromJson(json: JSONObject) = RideSummary(
            startedAt = json.getLong("startedAt"),
            title = json.getString("title"),
            totals = Totals(
                seconds = json.getInt("seconds"),
                kilojoules = json.getDouble("kilojoules"),
                miles = json.getDouble("miles"),
                avgPower = json.getInt("avgPower"),
                avgCadence = json.getInt("avgCadence"),
                avgResistance = json.optInt("avgResistance"),
                maxPower = json.getInt("maxPower"),
                avgHeartRate = json.optIntOrNull("avgHeartRate"),
                maxHeartRate = json.optIntOrNull("maxHeartRate"),
            ),
            workoutId = json.optStringOrNull("workoutId"),
            category = json.optStringOrNull("category"),
            plannedSeconds = json.optIntOrNull("plannedSeconds"),
            programId = json.optStringOrNull("programId"),
            ftpEstimate = json.optIntOrNull("ftpEstimate"),
            source = json.optStringOrNull("source"),
            instructor = json.optStringOrNull("instructor"),
        )

        private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
        private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    }
}

/** Ride history on disk: one index of summaries, one samples file per ride. */
class RideStore(private val dir: File) {
    private val index = File(dir, "rides.json")
    private val mutableRides = MutableStateFlow(readIndex())

    /** Newest first. */
    val rides: StateFlow<List<RideSummary>> = mutableRides

    @Synchronized
    fun save(summary: RideSummary, samples: List<Sample>) {
        dir.mkdirs()
        val columns = JSONObject()
            .put("power", JSONArray(samples.map { it.power }))
            .put("cadence", JSONArray(samples.map { it.cadence }))
            .put("resistance", JSONArray(samples.map { it.resistance }))
            .put("heartRate", JSONArray(samples.map { it.heartRate ?: 0 }))
        File(dir, "${summary.startedAt}.json").writeText(columns.toString())
        writeIndex(listOf(summary) + mutableRides.value)
    }

    @Synchronized
    fun delete(summary: RideSummary) {
        File(dir, "${summary.startedAt}.json").delete()
        writeIndex(mutableRides.value.filterNot { it.startedAt == summary.startedAt })
    }

    /**
     * Merges rides from an import folder: `rides.json` (summaries, as in the index) plus one
     * samples file per ride, named by its start time. Rides already in the history are skipped,
     * so importing twice adds nothing. The folder is emptied afterwards. Returns rides added.
     */
    @Synchronized
    fun importFrom(source: File): Int {
        val list = File(source, "rides.json")
        if (!list.exists()) return 0
        val array = JSONArray(list.readText())
        val known = mutableRides.value.map { it.startedAt }.toHashSet()
        val added = (0 until array.length())
            .map { RideSummary.fromJson(array.getJSONObject(it)) }
            .filter { known.add(it.startedAt) }
        dir.mkdirs()
        for (ride in added) {
            val samples = File(source, "${ride.startedAt}.json")
            if (samples.exists()) samples.copyTo(File(dir, samples.name), overwrite = true)
        }
        if (added.isNotEmpty()) writeIndex((mutableRides.value + added).sortedByDescending { it.startedAt })
        source.listFiles()?.forEach { it.delete() }
        return added.size
    }

    /** Per-second samples of a saved ride; empty if the file is missing. */
    fun samples(startedAt: Long): List<Sample> = try {
        val json = JSONObject(File(dir, "$startedAt.json").readText())
        val power = json.getJSONArray("power")
        val cadence = json.getJSONArray("cadence")
        val resistance = json.getJSONArray("resistance")
        val heart = json.optJSONArray("heartRate")
        (0 until power.length()).map { i ->
            Sample(power.getInt(i), cadence.getInt(i), resistance.getInt(i), heart?.optInt(i)?.takeIf { it > 0 })
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeIndex(rides: List<RideSummary>) {
        dir.mkdirs()
        index.writeText(JSONArray(rides.map { it.toJson() }).toString())
        mutableRides.value = rides
    }

    private fun readIndex(): List<RideSummary> = try {
        val array = JSONArray(index.readText())
        (0 until array.length()).map { RideSummary.fromJson(array.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }
}
