package com.jackwallner.pelojack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jackwallner.pelojack.media.Track

/**
 * Now playing with transport controls. [onOpen] launches the music app when nothing plays. With
 * [onHide], tapping the bar hides it and only the note opens the music app.
 */
@Composable
fun MusicBar(
    track: Track?,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
) {
    val openFromArt = onHide != null && track == null && onOpen != null
    Card(modifier, onClick = onHide ?: if (track == null) onOpen else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Raised)
                    .then(if (openFromArt) Modifier.clickable { onOpen?.invoke() } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val art = track?.art
                if (art != null) {
                    Image(art.asImageBitmap(), null, Modifier.size(72.dp), contentScale = ContentScale.Crop)
                } else {
                    Icon(Glyphs.Music, null, Modifier.size(36.dp), tint = Palette.Dim)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(track?.title?.ifBlank { null } ?: "Nothing playing", style = Type.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track?.artist?.ifBlank { null } ?: when {
                        onOpen == null -> "Start music in any app"
                        openFromArt -> "Tap the note to open Spotify"
                        else -> "Tap to open Spotify"
                    },
                    style = Type.Small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (track != null) {
                Spacer(Modifier.width(12.dp))
                RoundButton(Glyphs.Previous, "Previous", onPrevious, size = 64.dp, color = Palette.Surface)
                RoundButton(if (track.playing) Glyphs.Pause else Glyphs.Play, if (track.playing) "Pause" else "Play", onToggle, size = 72.dp, color = Palette.Text, tint = Palette.Background)
                RoundButton(Glyphs.Next, "Next", onNext, size = 64.dp, color = Palette.Surface)
            }
        }
    }
}
