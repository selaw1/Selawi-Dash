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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.selawi.cockpit.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One full set of colours. The dashboard has a night set and a day set. */
class Palette(
    val dark: Boolean,
    val background: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val stroke: Color,
    val accent: Color,
    val accentDim: Color,
    val onAccent: Color,
    val inverse: Color,
    val onInverse: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val warning: Color,
)

val NightPalette = Palette(
    dark = true,
    background = Color(0xFF0B0C0E),
    panelTop = Color(0xFF121417),
    panelBottom = Color(0xFF0A0B0D),
    surface = Color(0x0DFFFFFF),
    surfaceHigh = Color(0x17FFFFFF),
    stroke = Color(0x17FFFFFF),
    accent = Color(0xFF5B8CFF),
    accentDim = Color(0xFF1E2C55),
    onAccent = Color(0xFF0B0C0E),
    inverse = Color(0xFFF2F3F5),
    onInverse = Color(0xFF0B0C0E),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFFA1A7B0),
    textTertiary = Color(0xFF6E7580),
    warning = Color(0xFFFFB74D),
)

val DayPalette = Palette(
    dark = false,
    background = Color(0xFFF3F4F6),
    panelTop = Color(0xFFFAFAFB),
    panelBottom = Color(0xFFE2E5E9),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF0F1F3),
    stroke = Color(0xFFE5E7EB),
    accent = Color(0xFF2F6BFF),
    accentDim = Color(0xFFDCE6FF),
    onAccent = Color(0xFFFFFFFF),
    inverse = Color(0xFF111418),
    onInverse = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF111418),
    textSecondary = Color(0xFF4B5563),
    textTertiary = Color(0xFF6B7280),
    warning = Color(0xFFB45309),
)

/**
 * The same palette with the driver's accent colour. The hue is kept exactly; lightness is set
 * per theme so accent text and icons stay readable on white by day and on black at night.
 */
fun Palette.withAccent(chosen: Color): Palette {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(chosen.toArgb(), hsl)
    val hue = hsl[0]
    val sat = hsl[1].coerceAtLeast(0.15f)
    fun hsl(s: Float, l: Float) = Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hue, s.coerceIn(0f, 1f), l)))
    return Palette(
        dark = dark,
        background = background,
        panelTop = panelTop,
        panelBottom = panelBottom,
        surface = surface,
        surfaceHigh = surfaceHigh,
        stroke = stroke,
        accent = if (dark) hsl(sat, 0.68f) else hsl(sat, 0.45f),
        accentDim = if (dark) hsl(sat * 0.5f, 0.2f) else hsl(sat * 0.8f, 0.92f),
        onAccent = if (dark) Color(0xFF0B0C0E) else Color.White,
        inverse = inverse,
        onInverse = onInverse,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textTertiary = textTertiary,
        warning = warning,
    )
}

/**
 * Colour tokens for the current theme. Reading them in a composable or a drawing block
 * subscribes to theme changes, so the whole dashboard switches at sunrise and sunset.
 */
object CockpitColors {
    internal var palette by mutableStateOf(NightPalette)

    val isDark get() = palette.dark
    val Background get() = palette.background
    val PanelTop get() = palette.panelTop
    val PanelBottom get() = palette.panelBottom
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val Stroke get() = palette.stroke
    val Accent get() = palette.accent
    val AccentDim get() = palette.accentDim
    val OnAccent get() = palette.onAccent
    /** Primary buttons: near-black by day, near-white at night. */
    val Inverse get() = palette.inverse
    val OnInverse get() = palette.onInverse
    val TextPrimary get() = palette.textPrimary
    val TextSecondary get() = palette.textSecondary
    val TextTertiary get() = palette.textTertiary
    val Warning get() = palette.warning
}

/** Spacing and size tokens. Touch targets stay at least 64 dp for use in a moving car. */
object Space {
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val cardRadius = 20.dp
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
    displayLarge = TextStyle(fontFamily = DisplayFont, fontSize = 96.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp, lineHeight = 96.sp),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontSize = 28.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontSize = 24.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontFamily = TextFont, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontFamily = TextFont, fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = TextFont, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = TextFont, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = TextFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = DisplayFont, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp),
    labelSmall = TextStyle(fontFamily = TextFont, fontSize = 12.sp),
)

/** Night or day theme. [dark] is decided by the caller from the time of day or the driver's choice. */
@Composable
fun CockpitTheme(dark: Boolean = true, accent: Color? = null, content: @Composable () -> Unit) {
    // Remembered, so the shared palette only changes when the theme or the accent does.
    val palette = androidx.compose.runtime.remember(dark, accent) {
        val base = if (dark) NightPalette else DayPalette
        if (accent == null) base else base.withAccent(accent)
    }
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
