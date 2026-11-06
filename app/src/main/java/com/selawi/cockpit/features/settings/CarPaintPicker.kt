package com.selawi.cockpit.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.features.vehicle.Car3D
import com.selawi.cockpit.features.vehicle.CarPaint
import com.selawi.cockpit.features.vehicle.DrivingState

/** Common car colours, as one-tap picks after the model's own three paints. */
private val carColours = listOf(
    Color(0xFF15171A), // black
    Color(0xFFB8BEC6), // silver
    Color(0xFF6B7280), // grey
    Color(0xFF1F4E8C), // blue
    Color(0xFF2F5D50), // green
    Color(0xFF8A9A5B), // sage
)

/**
 * The 3D car's colour: the model's own paints, common colours and a colour wheel, beside a live
 * preview of the car. [onChange] runs live while dragging ([final] false) and once on release.
 */
@Composable
fun CarPaintPicker(paint: String, onChange: (paint: String, final: Boolean) -> Unit, preview: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
        ColorWheel(
            CarPaint.customColor(paint) ?: Color(0xFF1F4E8C),
            { colour, final -> if (colour != null) onChange(CarPaint.custom(colour), final) },
            Modifier.size(200.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(CarPaint.label(paint), style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CarPaint.builtIn.forEach { name ->
                    Swatch(CarPaint.swatch(name), selected = paint == name) { onChange(name, true) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                carColours.forEach { colour ->
                    val value = CarPaint.custom(colour)
                    Swatch(colour, selected = paint == value) { onChange(value, true) }
                }
            }
        }
        // Live preview: the same 3D car as on the home screen, parked. Left out where the home
        // screen's own car is already in view.
        if (preview) Car3D(DrivingState(), modifier = Modifier.weight(1f).height(220.dp), paint = paint)
    }
}

@Composable
private fun Swatch(colour: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colour)
            .border(if (selected) 3.dp else 1.dp, if (selected) CockpitColors.TextPrimary else CockpitColors.Stroke, CircleShape)
            .clickable(onClick = onClick),
    )
}
