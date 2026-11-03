package com.selawi.cockpit.features.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors

/** Lets a screen act on the map once it is ready. */
class MapController {
    var recenter: (() -> Unit)? = null
}

/**
 * The map used across the cockpit. On the car it is the live MapLibre map; in screenshot tests and
 * previews, where the native map engine cannot load, it is a drawn stand-in.
 */
@Composable
fun CarMap(modifier: Modifier = Modifier, interactive: Boolean = true, controller: MapController? = null) {
    if (LocalInspectionMode.current) {
        MapPlaceholder(modifier)
    } else {
        FreeMap(modifier, interactive, controller, dark = CockpitColors.isDark)
    }
}

/** Stand-in drawn like a dim street grid, used where the real map cannot run. */
@Composable
private fun MapPlaceholder(modifier: Modifier) {
    Canvas(modifier.background(Color(0xFFE8E4DC))) {
        val step = 56.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Color.White, Offset(x, 0f), Offset(x + size.height * 0.3f, size.height), strokeWidth = 10f)
            x += step * 2.3f
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White, Offset(0f, y), Offset(size.width, y - size.width * 0.1f), strokeWidth = 8f)
            y += step * 1.7f
        }
        drawLine(Color(0xFFF5C26B), Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.38f), strokeWidth = 18f)
        drawCircle(Color(0xFF3DDCFF), radius = 14.dp.toPx(), center = center)
        drawCircle(Color.White, radius = 6.dp.toPx(), center = center)
    }
}

