package com.jackwallner.pelojack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val ButtonShape = RoundedCornerShape(18.dp)
val CardShape = RoundedCornerShape(24.dp)
val ChipShape = RoundedCornerShape(999.dp)

/** Standard page: title row with optional actions, then content. */
@Composable
fun Page(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().padding(start = 56.dp, end = 64.dp, top = 48.dp, bottom = 40.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.Display, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = Type.BodyDim)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Spacer(Modifier.height(36.dp))
        content()
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Palette.Surface,
    padding: PaddingValues = PaddingValues(28.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fontSize: TextUnit = 26.sp,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(84.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Accent,
            contentColor = Palette.OnAccent,
            disabledContainerColor = Palette.Raised,
            disabledContentColor = Palette.Faint,
        ),
        contentPadding = PaddingValues(horizontal = 40.dp),
    ) {
        ButtonLabel(text, icon, fontSize)
    }
}

@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Palette.Text,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(84.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Raised,
            contentColor = color,
            disabledContainerColor = Palette.Surface,
            disabledContentColor = Palette.Faint,
        ),
        contentPadding = PaddingValues(horizontal = 36.dp),
    ) {
        ButtonLabel(text, icon, 26.sp)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(84.dp),
        shape = ButtonShape,
        border = BorderStroke(2.dp, Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text),
        contentPadding = PaddingValues(horizontal = 36.dp),
    ) {
        ButtonLabel(text, icon, 26.sp)
    }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?, fontSize: TextUnit) {
    if (icon != null) {
        Icon(icon, contentDescription = null, Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
    }
    Text(text, fontFamily = Inter, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1, color = LocalContentColor.current)
}

/** Round icon-only button, for transport controls and steppers. */
@Composable
fun RoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    color: Color = Palette.Raised,
    tint: Color = Palette.Text,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, Modifier.size(size * 0.46f), tint = tint)
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(60.dp)
            .clip(ChipShape)
            .background(if (selected) Palette.Text else Palette.Surface)
            .border(2.dp, if (selected) Palette.Text else Palette.Line, ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Inter,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Palette.Background else Palette.Text,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.Heading, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Settings row with a switch. */
@Composable
fun ToggleRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body)
            if (detail != null) Text(detail, style = Type.Small)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Accent,
                checkedThumbColor = Palette.Text,
                uncheckedTrackColor = Palette.Raised,
                uncheckedThumbColor = Palette.Dim,
                uncheckedBorderColor = Palette.Line,
            ),
        )
    }
}

/** A number chosen with − and + instead of a keyboard. */
@Composable
fun StepperRow(
    title: String,
    detail: String?,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body)
            if (detail != null) Text(detail, style = Type.Small)
        }
        Stepper(value, onChange, range, step, format)
    }
}

@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    width: Dp = 150.dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Glyphs.Minus, "Less", { onChange((value - step).coerceIn(range)) }, size = 64.dp)
        Text(
            format(value),
            style = Numeric,
            fontSize = 30.sp,
            modifier = Modifier.width(width),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RoundButton(Glyphs.Plus, "More", { onChange((value + step).coerceIn(range)) }, size = 64.dp)
    }
}

/** Big number with its unit and a label underneath. */
@Composable
fun Figure(value: String, label: String, modifier: Modifier = Modifier, unit: String? = null, size: TextUnit = 44.sp, color: Color = Palette.Text) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Numeric, fontSize = size, color = color)
            if (unit != null) {
                Spacer(Modifier.width(8.dp))
                Text(unit, style = Type.Label, modifier = Modifier.padding(bottom = (size.value * 0.12f).dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = Type.Small)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 14.dp) = Box(Modifier.size(size).background(color, CircleShape))

@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Type.Heading)
        Spacer(Modifier.height(10.dp))
        Text(detail, style = Type.BodyDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        action()
    }
}

@Composable
fun BoxScope.Scrim() = Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))

/** 754 -> "12:34", 3754 -> "1:02:34". */
fun clock(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, rest)
    }
}

fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

fun thousands(value: Int): String = String.format(Locale.US, "%,d", value)
