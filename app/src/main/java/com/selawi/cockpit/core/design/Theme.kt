package com.selawi.cockpit.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.selawi.cockpit.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One full set of colours. The cockpit has a night set and a day set. */
class Palette(
    val dark: Boolean,
    val background: Color,
    val backgroundGlow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val stroke: Color,
    val accent: Color,
    val accentDim: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val warning: Color,
)

val NightPalette = Palette(
    dark = true,
    background = Color(0xFF040608),
    backgroundGlow = Color(0xFF0A2230),
    surface = Color(0xCC10161E),
    surfaceHigh = Color(0xFF1A232E),
    stroke = Color(0x33FFFFFF),
    accent = Color(0xFF3DDCFF),
    accentDim = Color(0xFF16485A),
    onAccent = Color(0xFF031015),
    textPrimary = Color(0xFFF2F5F8),
    textSecondary = Color(0xFF8B98A8),
    textTertiary = Color(0xFF566373),
    warning = Color(0xFFFFB74D),
)

val DayPalette = Palette(
    dark = false,
    background = Color(0xFFE6EBF0),
    backgroundGlow = Color(0xFFCDE8F4),
    surface = Color(0xD9FFFFFF),
    surfaceHigh = Color(0xFFE4EAF0),
    stroke = Color(0x1A0D141B),
    accent = Color(0xFF0088B8),
    accentDim = Color(0xFFBFE6F4),
    onAccent = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0D141B),
    textSecondary = Color(0xFF52606E),
    textTertiary = Color(0xFF8A96A3),
    warning = Color(0xFFB86E00),
)

/**
 * Colour tokens for the current theme. Reading them in a composable or a drawing block
 * subscribes to theme changes, so the whole cockpit switches at sunrise and sunset.
 */
object CockpitColors {
    internal var palette by mutableStateOf(NightPalette)

    val isDark get() = palette.dark
    val Background get() = palette.background
    val BackgroundGlow get() = palette.backgroundGlow
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val Stroke get() = palette.stroke
    val Accent get() = palette.accent
    val AccentDim get() = palette.accentDim
    val OnAccent get() = palette.onAccent
    val TextPrimary get() = palette.textPrimary
    val TextSecondary get() = palette.textSecondary
    val TextTertiary get() = palette.textTertiary
    val Warning get() = palette.warning
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

/** Space Grotesk for numbers and headings: wide, technical, easy to read at a glance. */
@OptIn(ExperimentalTextApi::class)
val DisplayFont = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(R.font.space_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** Inter for running text. */
@OptIn(ExperimentalTextApi::class)
val TextFont = FontFamily(
    listOf(400, 500, 600).map { w ->
        Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontSize = 112.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontSize = 44.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontSize = 30.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontFamily = TextFont, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = TextFont, fontSize = 19.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = TextFont, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = TextFont, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = TextFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = DisplayFont, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp),
)

/** Night or day theme. [dark] is decided by the caller from the time of day or the driver's choice. */
@Composable
fun CockpitTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) NightPalette else DayPalette
    if (CockpitColors.palette !== palette) CockpitColors.palette = palette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.background, onBackground = palette.textPrimary,
            surface = palette.surface, onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceHigh, onSurfaceVariant = palette.textSecondary,
            outline = palette.stroke,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.background, onBackground = palette.textPrimary,
            surface = palette.surface, onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceHigh, onSurfaceVariant = palette.textSecondary,
            outline = palette.stroke,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
