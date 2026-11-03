package com.selawi.cockpit.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colour tokens. Dark first: the cockpit is used at night as much as by day. */
object CockpitColors {
    val Background = Color(0xFF07090C)
    val Surface = Color(0xFF10151C)
    val SurfaceHigh = Color(0xFF18202A)
    val Stroke = Color(0xFF222C38)
    val Accent = Color(0xFF3DDCFF)
    val AccentDim = Color(0xFF1B5E6E)
    val TextPrimary = Color(0xFFF2F5F8)
    val TextSecondary = Color(0xFF8B98A8)
    val TextTertiary = Color(0xFF5B6878)
    val Warning = Color(0xFFFFB74D)
}

/** Spacing and size tokens. Touch targets stay at least 64 dp for use in a moving car. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val cardRadius = 28.dp
    val touch = 64.dp
}

private val typography = Typography(
    displayLarge = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
)

private val colors = darkColorScheme(
    primary = CockpitColors.Accent,
    onPrimary = Color.Black,
    background = CockpitColors.Background,
    onBackground = CockpitColors.TextPrimary,
    surface = CockpitColors.Surface,
    onSurface = CockpitColors.TextPrimary,
    surfaceVariant = CockpitColors.SurfaceHigh,
    onSurfaceVariant = CockpitColors.TextSecondary,
    outline = CockpitColors.Stroke,
)

@Composable
fun CockpitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
