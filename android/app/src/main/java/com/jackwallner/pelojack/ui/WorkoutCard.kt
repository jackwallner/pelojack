package com.jackwallner.pelojack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackwallner.pelojack.workout.Workout

/** What the card needs to know about the rider's history with a workout. */
data class WorkoutHistory(val timesRidden: Int = 0, val bestKilojoules: Int? = null)

@Composable
fun WorkoutCard(
    workout: Workout,
    history: WorkoutHistory,
    bookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier, onClick = onClick, padding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(156.dp).background(Palette.Raised)) {
            val thumbnail = workout.video?.thumbnailUrls()
            if (thumbnail != null) {
                RemoteImage(thumbnail, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.45f to Color.Transparent, 1f to Palette.Background.copy(alpha = 0.85f)),
                    ),
                )
                WorkoutProfile(workout, Modifier.fillMaxWidth().height(22.dp).align(Alignment.BottomCenter).padding(horizontal = 22.dp).padding(bottom = 0.dp))
            } else {
                WorkoutProfile(workout, Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 64.dp, bottom = 20.dp))
            }
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${workout.minutes} min",
                    style = Numeric,
                    fontSize = 20.sp,
                    modifier = Modifier.clip(ChipShape).background(Palette.Background.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                if (workout.video != null) Icon(Glyphs.Video, "Has video", Modifier.size(26.dp), tint = Palette.Text)
                if (bookmarked) {
                    Spacer(Modifier.width(10.dp))
                    Icon(Glyphs.Bookmark, "Bookmarked", Modifier.size(26.dp), tint = Palette.Accent)
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(workout.name, style = Type.Heading, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${workout.category.label}  ·  ${oneDecimal(workout.difficulty)}", style = Type.Small, modifier = Modifier.weight(1f))
                if (history.timesRidden > 0) {
                    Icon(Glyphs.Check, null, Modifier.size(22.dp), tint = Palette.Good)
                    Spacer(Modifier.width(6.dp))
                    Text("${history.timesRidden}×", style = Type.Small)
                }
            }
        }
    }
}
