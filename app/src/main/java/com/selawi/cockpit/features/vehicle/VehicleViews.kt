package com.selawi.cockpit.features.vehicle

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.Space
import java.util.Locale

/**
 * The left panel: driving status, speed, the 3D car and today's trip. A gradient floor instead of a
 * card, so the car stands in open space.
 */
@Composable
fun VehiclePanel(state: DrivingState, modifier: Modifier = Modifier, compact: Boolean = false) {
    val speed by animateIntAsState(state.speedKmh, tween(600), label = "speed")
    Column(
        modifier
            // Solid behind the speed and the car, fading out over the last third so the screen's
            // background and the cover glow flow in without a hard edge.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(CockpitColors.PanelTop, CockpitColors.PanelBottom)))
                drawRect(
                    Brush.horizontalGradient(0.62f to Color.Transparent, 1f to Color.Black),
                    blendMode = BlendMode.DstOut,
                )
            }
            .padding(start = 32.dp, end = 32.dp, top = if (compact) 16.dp else 28.dp, bottom = if (compact) 4.dp else 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (state.driving) "DRIVING" else "PARKED",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.driving) CockpitColors.Accent else CockpitColors.TextSecondary,
            )
            state.headingDeg?.let { heading ->
                Box(Modifier.size(4.dp).clip(CircleShape).background(CockpitColors.TextTertiary))
                Text(
                    (if (state.driving) "Heading " else "Facing ") + compassPoint(heading),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                    color = CockpitColors.TextSecondary,
                )
            }
        }
        // Narrow window (split screen): a smaller speed, so the car keeps its room.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                "$speed",
                style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                color = CockpitColors.TextPrimary,
            )
            Text(
                "km/h",
                style = MaterialTheme.typography.titleMedium,
                color = CockpitColors.TextSecondary,
                modifier = Modifier.padding(start = 10.dp, bottom = if (compact) 8.dp else 14.dp),
            )
        }
        Car3D(state, Modifier.fillMaxWidth().weight(1f))
        // The trip row is left out of the narrow layout; the full screen shows it.
        if (compact) return@Column
        Spacer(Modifier.height(Space.m))
        Box(Modifier.fillMaxWidth().height(1.dp).background(CockpitColors.Stroke))
        Spacer(Modifier.height(Space.m))
        val trip = state.trip
        Row(Modifier.fillMaxWidth()) {
            TripStat(String.format(Locale.US, "%.1f", trip.distanceKm), "km today", Modifier.weight(1f))
            TripStat(minutes(trip.movingSeconds), "driving", Modifier.weight(1f))
            TripStat("${trip.averageKmh}", "avg km/h", Modifier.weight(1f))
            TripStat("${trip.maxKmh}", "top km/h", Modifier.weight(1f))
        }
    }
}

@Composable
private fun TripStat(value: String, label: String, modifier: Modifier) {
    // Each number and its label sit centred in their quarter of the row, under the car.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = CockpitColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CockpitColors.TextSecondary)
    }
}

private fun minutes(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${seconds}s"
    }
}

/** N, NE, E… from a heading in degrees. */
fun compassPoint(degrees: Float): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return points[(((degrees % 360) + 360) % 360 / 45f + 0.5f).toInt() % 8]
}
