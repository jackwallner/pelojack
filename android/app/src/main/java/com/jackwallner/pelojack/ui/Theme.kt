package com.jackwallner.pelojack.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jackwallner.pelojack.R

object Palette {
    val Background = Color(0xFF0A0B0D)
    val Surface = Color(0xFF15171B)
    val Raised = Color(0xFF1F2228)
    val Line = Color(0xFF2B2F36)
    val Text = Color(0xFFF5F6F7)
    val Dim = Color(0xFF9AA0A8)
    val Faint = Color(0xFF656B73)
    val Accent = Color(0xFFFF6A2B)
    val OnAccent = Color(0xFF160800)
    val Good = Color(0xFF3FC98A)
    val Bad = Color(0xFFF0505A)
    val Heart = Color(0xFFF0505A)

    /** Power zones 1-7, in Peloton's order of blue to purple. */
    val Zones = listOf(
        Color(0xFF4F8FD9),
        Color(0xFF34B59A),
        Color(0xFFE3C043),
        Color(0xFFF08A3C),
        Color(0xFFE5484D),
        Color(0xFFD6508F),
        Color(0xFF8F63D9),
    )

    fun zone(zone: Int): Color = Zones[zone.coerceIn(1, 7) - 1]
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Fixed-width digits so live numbers do not jitter. */
val Numeric = TextStyle(
    fontFamily = Inter,
    fontFeatureSettings = "tnum",
    fontWeight = FontWeight.SemiBold,
    color = Palette.Text,
    letterSpacing = (-0.5).sp,
)

object Type {
    val Display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, letterSpacing = (-1).sp, color = Palette.Text)
    val Title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = (-0.5).sp, color = Palette.Text)
    val Heading = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = Palette.Text)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 34.sp, color = Palette.Text)
    val BodyDim = Body.copy(color = Palette.Dim)
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = Palette.Dim)
    val Small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = Palette.Dim)
}

@Composable
fun PelojackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.OnAccent,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Raised,
            onSurfaceVariant = Palette.Dim,
            outline = Palette.Line,
        ),
        typography = Typography(
            bodyLarge = Type.Body.copy(color = Color.Unspecified),
            bodyMedium = Type.Body.copy(color = Color.Unspecified),
            labelLarge = Type.Label.copy(color = Color.Unspecified),
        ),
        content = content,
    )
}
