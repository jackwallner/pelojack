package com.jackwallner.pelojack.workout

import org.json.JSONObject

/** A multi-week plan: an ordered list of workouts, grouped by week. */
data class Program(
    val id: String,
    val name: String,
    val description: String,
    val weeks: List<List<String>>,
) {
    val workoutIds: List<String> get() = weeks.flatten()

    /** Week and ride numbers (1-based) of the [index]th ride in the program. */
    fun weekAndRide(index: Int): Pair<Int, Int> {
        var remaining = index
        weeks.forEachIndexed { week, rides ->
            if (remaining < rides.size) return (week + 1) to (remaining + 1)
            remaining -= rides.size
        }
        return weeks.size to (weeks.lastOrNull()?.size ?: 0)
    }

    companion object {
        fun parse(json: String): Program {
            val root = JSONObject(json)
            val weeks = root.getJSONArray("weeks")
            return Program(
                id = root.getString("id"),
                name = root.getString("name"),
                description = root.optString("description"),
                weeks = (0 until weeks.length()).map { w ->
                    val rides = weeks.getJSONArray(w)
                    (0 until rides.length()).map { rides.getString(it) }
                },
            )
        }
    }
}
