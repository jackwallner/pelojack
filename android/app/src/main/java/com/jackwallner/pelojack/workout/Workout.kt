package com.jackwallner.pelojack.workout

import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

enum class Category(val label: String, val key: String) {
    Intervals("Intervals", "intervals"),
    Climb("Climb", "climb"),
    Endurance("Endurance", "endurance"),
    PowerZone("Power Zone", "power_zone"),
    Hiit("HIIT", "hiit"),
    LowImpact("Low Impact", "low_impact"),
    WarmUp("Warm Up", "warm_up"),
    CoolDown("Cool Down", "cool_down"),
    FtpTest("FTP Test", "ftp_test"),
    Scenic("Scenic", "scenic"),
    Custom("Custom", "custom"),
    ;

    companion object {
        fun of(key: String?): Category = entries.firstOrNull { it.key == key } ?: Custom
    }
}

sealed interface Video {
    /** A YouTube video id, played in an embedded web player. */
    data class YouTube(val id: String) : Video

    /** An http(s) address, or a file name in the tablet's videos folder. */
    data class File(val location: String) : Video
}

data class Segment(
    val name: String,
    val seconds: Int,
    /** Resistance range; auto-follow moves the bike to its middle. */
    val resistance: IntRange? = null,
    val cadence: IntRange? = null,
    /** Power zone 1-7. */
    val zone: Int? = null,
    /** Exact power target as a share of FTP; wins over [zone]. */
    val ftpShare: Double? = null,
    /** Coaching line shown while the segment runs. */
    val cue: String? = null,
    /** The effort an FTP test measures. */
    val test: Boolean = false,
) {
    val powerShare: Double? get() = ftpShare ?: zone?.let(PowerZones::midShare)
    val resistanceTarget: Int? get() = resistance?.let { (it.first + it.last) / 2 }

    /** Best guess at this segment's intensity as a share of FTP. */
    val intensity: Double
        get() = powerShare
            ?: resistanceTarget?.let(PowerZones::shareForResistance)
            ?: if (test) 1.0 else 0.6

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("seconds", seconds)
        resistance?.let { put("resistance", JSONArray(listOf(it.first, it.last))) }
        cadence?.let { put("cadence", JSONArray(listOf(it.first, it.last))) }
        zone?.let { put("zone", it) }
        ftpShare?.let { put("ftp", it) }
        cue?.let { put("cue", it) }
        if (test) put("test", true)
    }
}

data class Workout(
    val id: String,
    val name: String,
    val description: String,
    val category: Category,
    val segments: List<Segment>,
    val video: Video? = null,
    /** Made or imported on the tablet, so it can be edited and deleted. */
    val custom: Boolean = false,
) {
    val totalSeconds: Int = segments.sumOf { it.seconds }
    val minutes: Int get() = (totalSeconds + 30) / 60

    /** 1-10, from the intensity profile, like Peloton's class difficulty. */
    val difficulty: Double
        get() {
            val meanSquare = segments.sumOf { it.intensity * it.intensity * it.seconds } / totalSeconds
            val factor = sqrt(meanSquare)
            return (((factor - 0.4) * 19).coerceIn(1.0, 10.0) * 10).roundToInt() / 10.0
        }

    val usesPower: Boolean get() = segments.any { it.powerShare != null }

    /** Where a rider [elapsedSeconds] into the workout is, or null once it is over. */
    fun positionAt(elapsedSeconds: Double): Position? {
        var start = 0
        segments.forEachIndexed { index, segment ->
            val end = start + segment.seconds
            if (elapsedSeconds < end) return Position(index, segment, start, end - elapsedSeconds)
            start = end
        }
        return null
    }

    data class Position(val index: Int, val segment: Segment, val startSecond: Int, val secondsLeft: Double)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("category", category.key)
        when (video) {
            is Video.YouTube -> put("video", JSONObject().put("youtube", video.id))
            is Video.File -> put("video", JSONObject().put("url", video.location))
            null -> Unit
        }
        put("segments", JSONArray(segments.map { it.toJson() }))
    }

    companion object {
        /** Throws if the JSON is malformed or has no usable segments. */
        fun parse(json: String, custom: Boolean = false): Workout {
            val root = JSONObject(json)
            val array = root.getJSONArray("segments")
            val segments = (0 until array.length()).map { i -> parseSegment(array.getJSONObject(i)) }
            require(segments.isNotEmpty() && segments.all { it.seconds > 0 }) { "workout needs timed segments" }
            return Workout(
                id = root.getString("id"),
                name = root.getString("name"),
                description = root.optString("description"),
                category = Category.of(root.optString("category", "custom")),
                segments = segments,
                video = root.optJSONObject("video")?.let(::parseVideo),
                custom = custom,
            )
        }

        private fun parseSegment(item: JSONObject) = Segment(
            name = item.getString("name"),
            seconds = item.getInt("seconds"),
            resistance = rangeOf(item, "resistance"),
            cadence = rangeOf(item, "cadence"),
            zone = if (item.has("zone")) item.getInt("zone").coerceIn(1, 7) else null,
            ftpShare = if (item.has("ftp")) item.getDouble("ftp") else null,
            cue = item.optString("cue").ifBlank { null },
            test = item.optBoolean("test"),
        )

        private fun parseVideo(item: JSONObject): Video? = when {
            item.has("youtube") -> Video.YouTube(youTubeId(item.getString("youtube")))
            item.has("url") -> Video.File(item.getString("url"))
            item.has("file") -> Video.File(item.getString("file"))
            else -> null
        }

        /** Accepts a single number or a two-number array. */
        private fun rangeOf(item: JSONObject, key: String): IntRange? {
            if (!item.has(key)) return null
            val array = item.optJSONArray(key) ?: return item.getInt(key).let { it..it }
            val low = array.getInt(0)
            val high = array.getInt(1)
            return minOf(low, high)..maxOf(low, high)
        }

        private val youTubePatterns = listOf(
            Regex("""(?:v=|youtu\.be/|embed/|shorts/|live/)([A-Za-z0-9_-]{11})"""),
            Regex("""^([A-Za-z0-9_-]{11})$"""),
        )

        /** Pulls the 11-character id out of any YouTube link, or returns the input unchanged. */
        fun youTubeId(link: String): String {
            val trimmed = link.trim()
            return youTubePatterns.firstNotNullOfOrNull { it.find(trimmed)?.groupValues?.get(1) } ?: trimmed
        }
    }
}
