package com.selawi.cockpit.features.vehicle

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The 3D car's paint, saved as text: one of the model's own three paints by name, or "#AARRGGBB"
 * for a colour the driver picked. Custom colours repaint the body only; glass, lights, tyres and
 * trim stay as they are.
 */
object CarPaint {
    const val Pearl = "Pearl"
    const val Carmine = "Carmine"
    const val Graphite = "Graphite"
    val builtIn = listOf(Pearl, Carmine, Graphite)

    fun custom(color: Color) = "#%08X".format(color.toArgb())

    /** The colour this paint looks like, for swatches and the screenshot stand-in. */
    fun swatch(paint: String): Color = when (paint) {
        Pearl -> Color(0xFFE9EEF2)
        Carmine -> Color(0xFFB0121B)
        Graphite -> Color(0xFF3A3F45)
        else -> customColor(paint) ?: Color(0xFFE9EEF2)
    }

    fun customColor(paint: String): Color? =
        if (paint.startsWith("#")) runCatching { Color(paint.substring(1).toLong(16).toInt()) }.getOrNull() else null

    fun label(paint: String) = when (paint) {
        Pearl -> "Pearl white"
        Carmine -> "Carmine red"
        Graphite -> "Graphite"
        else -> "Your colour"
    }
}
