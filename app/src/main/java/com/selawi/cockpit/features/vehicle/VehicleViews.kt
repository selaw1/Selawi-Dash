package com.selawi.cockpit.features.vehicle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.drawscope.scale
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
fun VehiclePanel(
    state: DrivingState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    weather: com.selawi.cockpit.features.weather.Weather? = null,
) {
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
            SignalTag(state.signal)
            MotionTag(state.motion)
        }
        // Narrow window (split screen): a smaller speed, so the car keeps its room.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            // The speed turns brake-red while the car slows hard enough to count as braking.
            val braking = state.motion.motion == Motion.Braking || state.motion.motion == Motion.HardBraking
            val speedColor by animateColorAsState(if (braking) BrakeRed else CockpitColors.TextPrimary, tween(200), label = "speedColour")
            Text(
                "$speed",
                style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                color = speedColor,
            )
            Text(
                "km/h",
                style = MaterialTheme.typography.titleMedium,
                color = CockpitColors.TextSecondary,
                modifier = Modifier.padding(start = 10.dp, bottom = if (compact) 8.dp else 14.dp),
            )
            Spacer(Modifier.weight(1f))
            GForceMeter(state, Modifier.padding(bottom = 4.dp), if (compact) 64.dp else 84.dp)
        }
        Car3D(state, weather, Modifier.fillMaxWidth().weight(1f))
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

/**
 * G-force: the dot moves the way the body is pushed. Up when braking, down when accelerating,
 * outwards in corners. The outer ring is 0.8 g, about as hard as a road car brakes.
 */
@Composable
private fun GForceMeter(state: DrivingState, modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
    val g = 9.81f
    val forward = if (state.driving) state.motion.accel / g else 0f
    val side = if (state.driving) state.lateral / g else 0f
    val x by animateFloatAsState((-side / MAX_G).coerceIn(-1f, 1f), tween(350), label = "gx")
    val y by animateFloatAsState((forward / MAX_G).coerceIn(-1f, 1f), tween(350), label = "gy")
    val braking = state.motion.motion == Motion.Braking || state.motion.motion == Motion.HardBraking
    val dot by animateColorAsState(if (braking) BrakeRed else CockpitColors.Accent, tween(200), label = "gColour")
    val ring = CockpitColors.TextTertiary
    val total = kotlin.math.sqrt(forward * forward + side * side)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            drawCircle(ring.copy(alpha = 0.5f), radius = r - density, style = Stroke(1.5f * density))
            drawCircle(ring.copy(alpha = 0.3f), radius = r / 2f, style = Stroke(density))
            drawLine(ring.copy(alpha = 0.3f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = density)
            drawLine(ring.copy(alpha = 0.3f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), strokeWidth = density)
            // Braking pushes you forward: the dot moves up the screen.
            val p = Offset(center.x + x * (r - 6f * density), center.y + y * (r - 6f * density))
            drawCircle(Brush.radialGradient(listOf(dot.copy(alpha = 0.45f), Color.Transparent), p, 14f * density), radius = 14f * density, center = p)
            drawCircle(dot, radius = 5f * density, center = p)
        }
        Text(
            String.format(Locale.US, "G-force %.2f", total),
            style = MaterialTheme.typography.labelSmall,
            color = CockpitColors.TextSecondary,
        )
    }
}

private const val MAX_G = 0.8f

/** Brake-light red, the same by day and night so it always reads as braking. */
val BrakeRed = Color(0xFFFF3B3B)

/** Indicator green, as on the instrument cluster. */
private val SignalGreen = Color(0xFF22C55E)

/**
 * A blinking green arrow for the way the car is turning. The car does not tell apps when its
 * indicators are on, so this follows the turn itself, from GPS.
 */
@Composable
private fun SignalTag(turn: Turn) {
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(turn) {
        while (turn != Turn.None && !inspection) withFrameMillis { on = blinkOn() }
    }
    AnimatedVisibility(visible = turn != Turn.None, enter = fadeIn(tween(150)), exit = fadeOut(tween(300))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(CockpitColors.TextTertiary))
            Canvas(Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                // Arrow pointing right; mirrored for left.
                val arrow = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.08f, h * 0.36f)
                    lineTo(w * 0.5f, h * 0.36f)
                    lineTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.94f, h * 0.5f)
                    lineTo(w * 0.5f, h * 0.88f)
                    lineTo(w * 0.5f, h * 0.64f)
                    lineTo(w * 0.08f, h * 0.64f)
                    close()
                }
                val colour = if (on) SignalGreen else SignalGreen.copy(alpha = 0.18f)
                if (turn == Turn.Left) {
                    scale(-1f, 1f) { drawPath(arrow, colour) }
                } else {
                    drawPath(arrow, colour)
                }
            }
        }
    }
}

/** BRAKING / HARD BRAKE with a brake symbol, or ACCELERATING, beside the heading. */
@Composable
private fun MotionTag(m: MotionState) {
    AnimatedVisibility(visible = m.motion != Motion.Steady, enter = fadeIn(tween(150)), exit = fadeOut(tween(400))) {
        val braking = m.motion == Motion.Braking || m.motion == Motion.HardBraking
        val colour = if (braking) BrakeRed else CockpitColors.Accent
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(CockpitColors.TextTertiary))
            if (braking) BrakeSymbol(colour, Modifier.size(20.dp))
            Text(
                when (m.motion) {
                    Motion.HardBraking -> "HARD BRAKE"
                    Motion.Braking -> "BRAKING"
                    else -> "ACCELERATING"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = colour,
            )
        }
    }
}

/** The usual brake warning sign: an exclamation mark in a circle between two arcs. */
@Composable
private fun BrakeSymbol(colour: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = center
        val stroke = s * 0.09f
        drawCircle(colour, radius = s * 0.28f, center = c, style = Stroke(stroke))
        drawLine(colour, Offset(c.x, c.y - s * 0.14f), Offset(c.x, c.y + s * 0.04f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(colour, radius = stroke * 0.6f, center = Offset(c.x, c.y + s * 0.13f))
        val arc = Size(s * 0.86f, s * 0.86f)
        val topLeft = Offset(c.x - arc.width / 2, c.y - arc.height / 2)
        drawArc(colour, 140f, 80f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(colour, -40f, 80f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
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
