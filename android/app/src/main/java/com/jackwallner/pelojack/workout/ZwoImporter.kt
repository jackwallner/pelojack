package com.jackwallner.pelojack.workout

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil
import org.w3c.dom.Element

/**
 * Converts a Zwift `.zwo` workout into a power-targeted workout.
 * Ramps become one-minute steps so ERG mode can follow them.
 */
object ZwoImporter {
    fun parse(input: InputStream, id: String): Workout {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        val root = document.documentElement
        val name = root.childText("name") ?: id
        val description = root.childText("description").orEmpty()
        val workout = root.getElementsByTagName("workout").item(0) as? Element
            ?: error("no <workout> element")

        val segments = buildList {
            val nodes = workout.childNodes
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                addAll(convert(element))
            }
        }
        require(segments.isNotEmpty()) { "no workout steps" }
        return Workout(
            id = id,
            name = name.trim(),
            description = description.trim().lineSequence().firstOrNull().orEmpty(),
            category = if (segments.any { (it.powerShare ?: 0.0) >= 1.06 }) Category.Intervals else Category.Endurance,
            segments = segments,
            custom = true,
        )
    }

    private fun convert(step: Element): List<Segment> {
        val seconds = step.int("Duration")
        val cadence = step.int("Cadence").takeIf { it > 0 }?.let { (it - 5)..(it + 5) }
        return when (step.tagName) {
            "SteadyState" -> listOf(Segment("Steady", seconds, cadence = cadence, ftpShare = step.double("Power")))
            "Warmup" -> ramp("Warm up", seconds, step.double("PowerLow"), step.double("PowerHigh"), cadence)
            "Cooldown" -> ramp("Cool down", seconds, step.double("PowerLow"), step.double("PowerHigh"), cadence)
            "Ramp" -> ramp("Ramp", seconds, step.double("PowerLow"), step.double("PowerHigh"), cadence)
            "FreeRide" -> listOf(Segment("Free ride", seconds, cadence = cadence))
            "MaxEffort" -> listOf(Segment("All out", seconds, cadence = cadence, zone = 7))
            "IntervalsT" -> {
                val onCadence = step.int("Cadence").takeIf { it > 0 }?.let { (it - 5)..(it + 5) }
                val offCadence = step.int("CadenceResting").takeIf { it > 0 }?.let { (it - 5)..(it + 5) }
                val on = Segment("On", step.int("OnDuration"), cadence = onCadence, ftpShare = step.double("OnPower"))
                val off = Segment("Off", step.int("OffDuration"), cadence = offCadence, ftpShare = step.double("OffPower"))
                List(step.int("Repeat").coerceAtLeast(1)) { listOf(on, off) }.flatten()
            }
            else -> emptyList()
        }.filter { it.seconds > 0 }
    }

    private fun ramp(name: String, seconds: Int, from: Double, to: Double, cadence: IntRange?): List<Segment> {
        val steps = ceil(seconds / 60.0).toInt().coerceAtLeast(1)
        return List(steps) { i ->
            val start = seconds * i / steps
            val end = seconds * (i + 1) / steps
            val share = from + (to - from) * (i + 0.5) / steps
            Segment(name, end - start, cadence = cadence, ftpShare = Math.round(share * 100) / 100.0)
        }
    }

    private fun Element.int(name: String): Int = getAttribute(name).toDoubleOrNull()?.toInt() ?: 0
    private fun Element.double(name: String): Double = getAttribute(name).toDoubleOrNull() ?: 0.0
    private fun Element.childText(name: String): String? =
        (getElementsByTagName(name).item(0) as? Element)?.textContent?.takeIf { it.isNotBlank() }
}
