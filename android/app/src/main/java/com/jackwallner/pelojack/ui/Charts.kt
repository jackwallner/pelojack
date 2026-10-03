package com.jackwallner.pelojack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackwallner.pelojack.workout.PowerZones
import com.jackwallner.pelojack.workout.Segment
import com.jackwallner.pelojack.workout.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Zone a segment trains in, for colouring: its power zone, or one estimated from resistance. */
fun Segment.displayZone(): Int = zone ?: PowerZones.zoneOf((intensity * 100).toInt(), 100)

/**
 * The class plan as bars: width is duration, height is intensity, colour is zone.
 * [elapsed] dims what is done; null shows the plan untouched.
 */
@Composable
fun WorkoutProfile(workout: Workout, modifier: Modifier = Modifier, elapsed: Double? = null) {
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val usable = size.width - gap * (workout.segments.size - 1)
        val peak = workout.segments.maxOf { it.intensity }.coerceAtLeast(1.2)
        var x = 0f
        var start = 0
        workout.segments.forEach { segment ->
            val width = usable * segment.seconds / workout.totalSeconds
            val height = size.height * (0.14f + 0.86f * (segment.intensity / peak).toFloat())
            val end = start + segment.seconds
            val color = Palette.zone(segment.displayZone())
            val done = elapsed != null && elapsed >= end
            val current = elapsed != null && elapsed >= start && elapsed < end
            drawRoundRect(
                color = if (done) color.copy(alpha = 0.35f) else if (elapsed == null || current) color else color.copy(alpha = 0.75f),
                topLeft = Offset(x, size.height - height),
                size = Size(width.coerceAtLeast(1f), height),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            x += width + gap
            start = end
        }
        if (elapsed != null) {
            val playhead = (size.width * (elapsed / workout.totalSeconds)).toFloat().coerceIn(0f, size.width)
            drawLine(Palette.Text, Offset(playhead, 0f), Offset(playhead, size.height), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Line of per-second values with an optional dashed average. */
@Composable
fun LineChart(values: List<Int>, color: Color, modifier: Modifier = Modifier, average: Int? = null, floor: Int = 0, ceiling: Int = 0) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val top = maxOf(values.max().coerceAtLeast(average ?: 0) * 1.1f, ceiling.toFloat(), floor + 1f)
        fun y(v: Float) = size.height * (1f - (v - floor) / (top - floor))
        // Downsample to about one point per two pixels.
        val stride = (values.size / (size.width / 2f)).toInt().coerceAtLeast(1)
        val points = values.indices.step(stride).map { i ->
            val window = values.subList(i, minOf(values.size, i + stride))
            Offset(size.width * i / (values.size - 1), y(window.average().toFloat()))
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.14f))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        if (average != null) {
            val ay = y(average.toFloat())
            drawLine(
                Palette.Dim,
                Offset(0f, ay),
                Offset(size.width, ay),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
    }
}

/** Horizontal track with the target range shaded and a marker at the live value. */
@Composable
fun RangeGauge(value: Int, target: IntRange?, min: Int, max: Int, modifier: Modifier = Modifier, color: Color = Palette.Accent) {
    Canvas(modifier.height(20.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Palette.Raised, cornerRadius = radius)
        fun x(v: Int) = size.width * ((v - min).toFloat() / (max - min)).coerceIn(0f, 1f)
        if (target != null) {
            val left = x(target.first)
            val right = maxOf(x(target.last), left + 6.dp.toPx())
            drawRoundRect(color.copy(alpha = 0.45f), Offset(left, 0f), Size(right - left, size.height), radius)
        }
        val marker = x(value)
        val inRange = target == null || value in target
        drawCircle(
            if (inRange) Palette.Text else color,
            radius = size.height * 0.75f,
            center = Offset(marker.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2),
        )
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, color: Color = Palette.Accent, stroke: Dp = 12.dp) {
    Canvas(modifier) {
        val width = stroke.toPx()
        val inset = width / 2
        val arcSize = Size(size.width - width, size.height - width)
        drawArc(Palette.Raised, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(width, cap = StrokeCap.Round))
    }
}

/** Month grid, Monday first, with ride days filled. */
@Composable
fun MonthCalendar(month: YearMonth, rideDays: Set<LocalDate>, today: LocalDate, modifier: Modifier = Modifier) {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = Type.Small, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(56.dp), contentAlignment = Alignment.Center) {
                        if (day != null) CalendarDay(day, day in rideDays, day == today)
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarDay(day: LocalDate, rode: Boolean, today: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            when {
                rode -> drawCircle(Palette.Accent)
                today -> drawCircle(Palette.Line, style = Stroke(2.dp.toPx()))
            }
        }
        Text(
            day.dayOfMonth.toString(),
            style = Numeric,
            fontSize = 20.sp,
            color = if (rode) Palette.OnAccent else if (today) Palette.Text else Palette.Dim,
        )
    }
}
