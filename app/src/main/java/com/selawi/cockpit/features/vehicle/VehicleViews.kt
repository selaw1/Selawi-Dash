package com.selawi.cockpit.features.vehicle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.SectionLabel
import com.selawi.cockpit.core.design.Space
import java.util.Locale

private const val GAUGE_MAX_KMH = 180f
private const val SWEEP = 260f
private const val START = 140f

/** Speed arc from GPS. Parked, it rests at zero and shows P. */
@Composable
fun SpeedCard(state: DrivingState, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState((state.speedKmh / GAUGE_MAX_KMH).coerceIn(0f, 1f), tween(700), label = "speed")
    val accent = CockpitColors.Accent
    val track = CockpitColors.SurfaceHigh
    CockpitCard(modifier, padding = Space.m) {
        SectionLabel("Speed")
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().aspectRatio(1f, matchHeightConstraintsFirst = true)) {
                val stroke = size.minDimension * 0.07f
                val inset = stroke / 2 + 4f
                val arcSize = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
                val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
                drawArc(track, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction > 0.002f) {
                    drawArc(
                        Brush.sweepGradient(listOf(accent.copy(alpha = 0.4f), accent, accent), center),
                        START, SWEEP * fraction, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.driving || state.speedKmh > 0) "${state.speedKmh}" else "P",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    color = CockpitColors.TextPrimary,
                )
                Text("km/h", style = MaterialTheme.typography.labelMedium, color = CockpitColors.TextSecondary)
            }
        }
    }
}

/** Distance, time and speeds since the trip began. */
@Composable
fun TripCard(trip: Trip, modifier: Modifier = Modifier) {
    CockpitCard(modifier, padding = Space.m) {
        SectionLabel("Trip")
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(String.format(Locale.US, "%.1f", trip.distanceKm), "km")
            Stat(duration(trip.movingSeconds), "driving")
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${trip.averageKmh}", "avg km/h")
            Stat("${trip.maxKmh}", "top km/h")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal), color = CockpitColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = CockpitColors.TextTertiary)
    }
}

private fun duration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m} min"
}

/** N, NE, E… from a heading in degrees. */
fun compassPoint(degrees: Float): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return points[(((degrees % 360) + 360) % 360 / 45f + 0.5f).toInt() % 8]
}
