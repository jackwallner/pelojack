package com.jackwallner.pelojack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Home, "Home", Glyphs.Home),
    Tab(Route.Classes, "Classes", Glyphs.Classes),
    Tab(Route.Programs, "Programs", Glyphs.Programs),
    Tab(Route.Profile, "Profile", Glyphs.Profile),
    Tab(Route.Settings, "Settings", Glyphs.Settings),
)

/** Left navigation rail with live bike and heart rate status at the bottom. */
@Composable
fun Shell(
    selected: Route,
    onSelect: (Route) -> Unit,
    bikeColor: Color,
    heartRate: Int?,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(132.dp).fillMaxHeight().background(Palette.Background).padding(vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark()
            Spacer(Modifier.height(44.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tabs.forEach { tab -> RailItem(tab, tab.route == selected) { onSelect(tab.route) } }
            }
            Spacer(Modifier.weight(1f))
            RailItem(Tab(Route.Apps, "Apps", Glyphs.Apps), selected == Route.Apps) { onSelect(Route.Apps) }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(bikeColor, 12.dp)
                Icon(Glyphs.Heart, null, Modifier.size(20.dp), tint = if (heartRate != null) Palette.Heart else Palette.Faint)
                if (heartRate != null) Text(heartRate.toString(), style = Numeric, fontSize = 18.sp)
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Surface))
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

@Composable
private fun RailItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(108.dp)
            .clip(CardShape)
            .background(if (selected) Palette.Surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = null, Modifier.size(34.dp), tint = if (selected) Palette.Accent else Palette.Dim)
        Spacer(Modifier.height(6.dp))
        Text(
            tab.label,
            fontFamily = Inter,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Palette.Text else Palette.Dim,
        )
    }
}

/** The app mark: a wheel. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(52.dp)) {
        val stroke = size.width * 0.13f
        drawCircle(Palette.Accent, radius = size.width / 2 - stroke / 2, style = Stroke(stroke))
        drawCircle(Palette.Text, radius = size.width * 0.11f)
    }
}

@Composable
fun BackRow(onBack: () -> Unit) {
    Row(
        Modifier.clip(ButtonShape).clickable(onClick = onBack).padding(end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.Back, contentDescription = "Back", Modifier.size(32.dp), tint = Palette.Dim)
        Spacer(Modifier.width(10.dp))
        Text("Back", style = Type.Label)
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
