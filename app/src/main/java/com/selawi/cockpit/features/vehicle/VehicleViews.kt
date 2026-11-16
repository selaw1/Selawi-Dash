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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
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
    paint: String = CarPaint.Pearl,
    planePaint: String = "Pearl",
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
            // Side padding is on the text rows only, so the car scene runs edge to edge.
            .padding(top = if (compact) 16.dp else 28.dp, bottom = if (compact) 4.dp else 24.dp),
    ) {
        val side = Modifier.padding(horizontal = 32.dp)
        Row(side, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val status = when {
                !state.flying -> if (state.driving) "DRIVING" else "PARKED"
                state.airborne -> "FLYING"
                state.speedKmh >= 60 -> "ON RUNWAY"
                state.driving -> "TAXIING"
                else -> "AT THE GATE"
            }
            Text(
                status,
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
            if (state.flying && state.airborne) {
                ClimbTag(state.climbMps)
            } else {
                GpsTag(state.gps)
            SignalTag(if (state.flying) Turn.None else state.signal)
                MotionTag(state.motion)
            }
        }
        // Narrow window (split screen): a smaller speed, so the car keeps its room.
        Row(verticalAlignment = Alignment.Bottom, modifier = side.fillMaxWidth().padding(top = 6.dp)) {
            // The speed turns brake-red while the car slows hard enough to count as braking.
            val braking = state.motion.motion == Motion.Braking || state.motion.motion == Motion.HardBraking
            // Accelerating turns it the accent colour; a steady pace keeps the normal text colour.
            val speedTarget = when {
                braking -> BrakeRed
                state.motion.motion == Motion.Accelerating -> CockpitColors.Accent
                else -> CockpitColors.TextPrimary
            }
            val speedColor by animateColorAsState(speedTarget, tween(250), label = "speedColour")
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
        Car3D(state, weather, Modifier.fillMaxWidth().weight(1f), paint, planePaint)
        // The trip row is left out of the narrow layout; the full screen shows it.
        if (compact) return@Column
        // A hairline that fades out at both ends, so it does not box the car in.
        Box(
            side.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, CockpitColors.Stroke, CockpitColors.Stroke, Color.Transparent)),
            ),
        )
        Spacer(Modifier.height(Space.m))
        val trip = state.trip
        // In flight mode the row shows the flight: distance and time in the air, height and speed.
        if (state.flying) {
            val flight = state.flight
            Row(side.fillMaxWidth()) {
                TripStat(StatFormat.km(flight.distanceKm), "KM FLOWN", Modifier.weight(1f))
                TripStat(StatFormat.duration(flight.airSeconds), "IN THE AIR", Modifier.weight(1f))
                // The altitude takes the climb's colour while the plane ascends or descends.
                TripStat(
                    state.altitudeM?.let { StatFormat.number(it.toLong()) } ?: "–", "ALT M", Modifier.weight(1f),
                    (if (state.airborne) climbColour(state.climbMps) else null) ?: CockpitColors.TextPrimary,
                )
                TripStat(StatFormat.number(flight.maxAltitudeM.toLong()), "MAX ALT M", Modifier.weight(1f))
                TripStat(StatFormat.number(flight.maxKmh.toLong()), "TOP KM/H", Modifier.weight(1f))
            }
            return@Column
        }
        Row(side.fillMaxWidth()) {
            TripStat(StatFormat.km(trip.distanceKm), "KM TRIP", Modifier.weight(1f))
            TripStat(StatFormat.duration(trip.movingSeconds), "DRIVING", Modifier.weight(1f))
            TripStat(StatFormat.number(trip.averageKmh.toLong()), "AVG KM/H", Modifier.weight(1f))
            TripStat(StatFormat.number(trip.maxKmh.toLong()), "TOP KM/H", Modifier.weight(1f))
            TripStat(StatFormat.number(trip.stops.toLong()), "STOPS", Modifier.weight(1f))
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

/** Colours for the climb, like the ones for speed: accent going up, brake red coming down. */
private val Descend = BrakeRed

private fun climbColour(climbMps: Float): Color? = when {
    climbMps >= 1.5f -> CockpitColors.Accent
    climbMps <= -1.5f -> Descend
    else -> null
}

/**
 * Why there is no position, while there is none: searching (with the satellites seen and used),
 * location switched off, approximate only, or no permission. Tapping opens the setting to fix it.
 */
@Composable
private fun GpsTag(gps: GpsInfo) {
    if (gps.state == GpsState.Fixed) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val (text, problem) = when (gps.state) {
        GpsState.Searching -> (if (gps.seen > 0) "SEARCHING GPS · ${gps.used}/${gps.seen} SATELLITES" else "SEARCHING GPS") to false
        GpsState.LocationOff -> "LOCATION IS OFF" to true
        GpsState.ApproximateOnly -> "TURN ON PRECISE LOCATION" to true
        else -> "ALLOW LOCATION" to true
    }
    Row(
        Modifier.clickable(enabled = problem) {
            val intent = if (gps.state == GpsState.LocationOff) {
                android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            } else {
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
            }
            runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(CockpitColors.TextTertiary))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (problem) BrakeRed else Color(0xFFFFB74D),
            maxLines = 1,
        )
    }
}

/**
 * In the air: ascending or descending, with the rate in metres a minute, shown the way braking
 * and accelerating are on the road.
 */
@Composable
private fun ClimbTag(climbMps: Float) {
    val colour = climbColour(climbMps)
    AnimatedVisibility(visible = colour != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(400))) {
        val up = climbMps > 0
        val shown = colour ?: CockpitColors.Accent
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(CockpitColors.TextTertiary))
            ClimbArrow(up, shown, Modifier.size(18.dp))
            Text(
                (if (up) "ASCENDING" else "DESCENDING") + String.format(Locale.US, " %+,d m/min", (climbMps * 60).toInt()),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = shown,
            )
        }
    }
}

/** An arrow in a circle, pointing up while climbing and down while descending. */
@Composable
private fun ClimbArrow(up: Boolean, colour: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = center
        val stroke = s * 0.1f
        val d = if (up) -1f else 1f
        drawCircle(colour, radius = s * 0.44f, center = c, style = Stroke(stroke))
        drawLine(colour, Offset(c.x, c.y - d * s * 0.22f), Offset(c.x, c.y + d * s * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(colour, Offset(c.x, c.y + d * s * 0.22f), Offset(c.x - s * 0.15f, c.y + d * s * 0.07f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(colour, Offset(c.x, c.y + d * s * 0.22f), Offset(c.x + s * 0.15f, c.y + d * s * 0.07f), strokeWidth = stroke, cap = StrokeCap.Round)
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
private fun TripStat(value: String, label: String, modifier: Modifier, colour: Color = CockpitColors.TextPrimary) {
    // Each number and its label sit centred in their part of the row, under the car, all at one
    // size. Values come from StatFormat, which keeps them to six characters, so they always fit.
    Column(modifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val shown by animateColorAsState(colour, tween(250), label = "statColour")
        Text(value, style = MaterialTheme.typography.headlineMedium, color = shown, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = CockpitColors.TextTertiary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** N, NE, E… from a heading in degrees. */
fun compassPoint(degrees: Float): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return points[(((degrees % 360) + 360) % 360 / 45f + 0.5f).toInt() % 8]
}
