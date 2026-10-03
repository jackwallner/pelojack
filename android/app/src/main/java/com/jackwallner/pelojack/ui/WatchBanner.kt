package com.jackwallner.pelojack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.jackwallner.pelojack.hr.WatchState
import kotlinx.coroutines.delay

private const val SHOWN_MS = 3_500L

/** A brief "Apple Watch connected" pill, the moment the watch finds the bike. */
@Composable
fun WatchBanner(state: WatchState, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        val joined = state == WatchState.Connected && previous != WatchState.Connected
        previous = state
        if (joined) {
            visible = true
            delay(SHOWN_MS)
            visible = false
        }
    }
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Row(
            Modifier.padding(top = 28.dp).shadow(16.dp, ChipShape).clip(ChipShape).background(Palette.Raised)
                .padding(horizontal = 32.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyphs.Heart, null, Modifier.size(30.dp), tint = Palette.Heart)
            Spacer(Modifier.width(14.dp))
            Text("Apple Watch connected", style = Type.Heading, fontSize = Type.Body.fontSize)
        }
    }
}
