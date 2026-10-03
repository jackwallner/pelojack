package com.jackwallner.pelojack.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val name: String = "Jack",
    val ftp: Int = 150,
    val maxHeartRate: Int = 185,
    val weightLb: Int = 180,
    val metric: Boolean = false,
    /** Move the resistance to each segment's target. */
    val autoFollow: Boolean = true,
    /** Hold the power target by adjusting resistance when a segment has one. */
    val erg: Boolean = true,
    val weeklyGoal: Int = 3,
    val heartRateAddress: String? = null,
    val heartRateName: String? = null,
    val bookmarks: Set<String> = emptySet(),
    /** Workouts queued to ride back to back. */
    val stack: List<String> = emptyList(),
    val programId: String? = null,
    val programStartedAt: Long = 0,
    /** Ride screen pieces the rider minimized, e.g. "cadence", "clock" or "music". */
    val hiddenOnRide: Set<String> = emptySet(),
    val hideSystemBars: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("ftp", ftp)
        .put("maxHeartRate", maxHeartRate)
        .put("weightLb", weightLb)
        .put("metric", metric)
        .put("autoFollow", autoFollow)
        .put("erg", erg)
        .put("weeklyGoal", weeklyGoal)
        .put("heartRateAddress", heartRateAddress ?: JSONObject.NULL)
        .put("heartRateName", heartRateName ?: JSONObject.NULL)
        .put("bookmarks", JSONArray(bookmarks.sorted()))
        .put("stack", JSONArray(stack))
        .put("programId", programId ?: JSONObject.NULL)
        .put("programStartedAt", programStartedAt)
        .put("hiddenOnRide", JSONArray(hiddenOnRide.sorted()))
        .put("hideSystemBars", hideSystemBars)

    companion object {
        fun fromJson(json: JSONObject): Settings {
            val defaults = Settings()
            return Settings(
                name = json.optString("name", defaults.name),
                ftp = json.optInt("ftp", defaults.ftp),
                maxHeartRate = json.optInt("maxHeartRate", defaults.maxHeartRate),
                weightLb = json.optInt("weightLb", defaults.weightLb),
                metric = json.optBoolean("metric", defaults.metric),
                autoFollow = json.optBoolean("autoFollow", defaults.autoFollow),
                erg = json.optBoolean("erg", defaults.erg),
                weeklyGoal = json.optInt("weeklyGoal", defaults.weeklyGoal),
                heartRateAddress = json.optNullableString("heartRateAddress"),
                heartRateName = json.optNullableString("heartRateName"),
                bookmarks = json.optJSONArray("bookmarks").strings().toSet(),
                stack = json.optJSONArray("stack").strings(),
                programId = json.optNullableString("programId"),
                programStartedAt = json.optLong("programStartedAt"),
                hiddenOnRide = json.optJSONArray("hiddenOnRide").strings().toSet(),
                hideSystemBars = json.optBoolean("hideSystemBars", defaults.hideSystemBars),
            )
        }

        private fun JSONObject.optNullableString(key: String): String? =
            if (isNull(key)) null else optString(key).ifBlank { null }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }
    }
}

/** Rider settings and library state, kept in one JSON preference. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pelojack", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<Settings> = mutableState
    val current: Settings get() = mutableState.value

    fun update(change: (Settings) -> Settings) {
        val next = change(mutableState.value)
        prefs.edit().putString(KEY, next.toJson().toString()).apply()
        mutableState.value = next
    }

    fun toggleRidePiece(piece: String) {
        update { it.copy(hiddenOnRide = if (piece in it.hiddenOnRide) it.hiddenOnRide - piece else it.hiddenOnRide + piece) }
    }

    private fun read(): Settings = try {
        prefs.getString(KEY, null)?.let { Settings.fromJson(JSONObject(it)) } ?: Settings()
    } catch (e: Exception) {
        Settings()
    }

    private companion object {
        const val KEY = "settings"
    }
}
