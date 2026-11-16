package com.selawi.cockpit.features.vehicle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.selawi.cockpit.features.weather.Sky
import com.selawi.cockpit.features.weather.Weather
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalInspectionMode
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.Space
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.exp
import kotlin.math.sin

/**
 * The car on the dashboard. Parked, it stands on a softly lit floor and turns under the driver's
 * finger. Driving, the camera swings behind it as it turns down the road; the road, its lane lines
 * and passing light streaks are drawn with the same camera as the 3D car, so the car sits on the
 * road and everything moves at the real GPS speed.
 */
@Composable
fun Car3D(state: DrivingState, weather: Weather? = null, modifier: Modifier = Modifier, paint: String = CarPaint.Pearl) {
    val control = remember { CarControl().also { it.drive = if (state.driving) 1f else 0f } }
    var touched by rememberSaveable { mutableStateOf(false) }
    val latest = rememberUpdatedState(state)
    control.paint = paint

    // One clock for the whole scene: eases the drive value, follows bends and moves the road.
    var travel by remember { mutableFloatStateOf(0f) }
    var drive by remember { mutableFloatStateOf(control.drive) }
    var steer by remember { mutableFloatStateOf(0f) }
    var speedFx by remember { mutableFloatStateOf(0f) }
    // Start from the current state, so a screenshot taken mid-brake shows the glow.
    var brake by remember { mutableFloatStateOf(brakeLevel(state.motion)) }
    var boost by remember { mutableFloatStateOf(if (state.motion.motion == Motion.Accelerating) state.motion.level else 0f) }
    // The heading shown by the compass ring, turning smoothly between GPS fixes.
    var compass by remember { mutableFloatStateOf(state.headingDeg ?: 0f) }
    // Seconds since the scene started, for rain, snow and stars.
    var clock by remember { mutableFloatStateOf(0f) }
    // 0 car … 1 plane, and 0 on the ground … 1 in the air; both ease, so every change animates.
    var flight by remember { mutableFloatStateOf(if (state.flying) 1f else 0f) }
    var air by remember { mutableFloatStateOf(if (state.flying && state.airborne) 1f else 0f) }
    LaunchedEffect(control) {
        var last = 0L
        var lastHeading: Float? = null
        var lastHeadingAt = 0f
        var headingRate = 0f
        var turnRate = 0f
        var wasDriving = latest.value.driving
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                val s = latest.value
                // About 1.6 s from parked to driving; parking is a slower swing round to the front.
                val target = if (s.driving) 1f else 0f
                drive += (target - drive).coerceIn(-dt / 2.4f, dt / 1.6f)
                if (wasDriving && !s.driving) control.reset()
                wasDriving = s.driving
                clock += dt
                // The swap takes about 2.6 s; take-off and landing scenes about 4 s.
                flight += ((if (s.flying) 1f else 0f) - flight).coerceIn(-dt / 2.6f, dt / 2.6f)
                air += ((if (s.flying && s.airborne) 1f else 0f) - air).coerceIn(-dt / 4f, dt / 4f)
                // Bend from how fast the GPS heading turns, measured over at least 0.9 s however
                // often the GPS reports, and smoothed so the road and the wheels never twitch.
                val heading = s.headingDeg
                if (heading == null) {
                    lastHeading = null
                } else if (lastHeading == null) {
                    lastHeading = heading
                    lastHeadingAt = clock
                } else if (clock - lastHeadingAt >= 0.9f) {
                    val delta = ((heading - lastHeading!! + 540f) % 360f) - 180f
                    headingRate = if (clock - lastHeadingAt <= 3f) delta / (clock - lastHeadingAt) else 0f
                    lastHeading = heading
                    lastHeadingAt = clock
                }
                // No heading for a while (stopped): the road is straight again.
                if (heading == null || s.speedKmh < 5) headingRate = 0f
                turnRate += (headingRate - turnRate) * (1 - exp(-dt * 2.5f))
                // About 28° a second (a sharp city corner) bends the road fully; the slight curve
                // keeps small drifts gentler while full turns still bend fully.
                val bendAmount = (abs(turnRate) / 28f).coerceIn(0f, 1f).pow(1.08f)
                steer = if (turnRate < 0) -bendAmount else bendAmount
                if (heading != null) {
                    val d = ((heading - compass + 540f) % 360f) - 180f
                    compass = (compass + d * (1 - exp(-dt * 3f)) + 360f) % 360f
                }
                // The plane is drawn at about 16 m a unit (the car at 2.35), so the runway and the
                // clouds pass at the right pace for its size.
                val metresPerUnit = CarScene.METRES_PER_UNIT + (PLANE_METRES_PER_UNIT - CarScene.METRES_PER_UNIT) * flight
                travel += s.speedKmh / 3.6f / metresPerUnit * dt
                // Eased over about a second, so speed changes feel like acceleration, not jumps.
                speedFx += ((s.speedKmh / 120f).coerceIn(0f, 1f) - speedFx) * (1 - exp(-dt * 1.2f))
                // Brake lights come on fast and fade out more slowly, like real ones.
                val m = s.motion
                val brakeTarget = brakeLevel(m)
                brake += (brakeTarget - brake) * (1 - exp(-dt * if (brakeTarget > brake) 14f else 3f))
                val boostTarget = if (m.motion == Motion.Accelerating) m.level else 0f
                boost += (boostTarget - boost) * (1 - exp(-dt * 3f))
                control.drive = drive
                control.steer = steer
                control.speedFx = speedFx
                control.brake = brake
                control.flight = flight
                control.air = air
                control.climb = s.climbMps
            }
        }
    }

    val letters = rememberTextMeasurer()
    val letterStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    // Above the clouds the sky is always clear; without weather, day and night follow the theme.
    val sky = if (air > 0.5f) Sky.Clear else weather?.sky
    val isDay = weather?.isDay ?: !CockpitColors.isDark
    // The tail takes the car's colour; pearl white would leave the plane all white, so the accent.
    control.livery = (if (paint == CarPaint.Pearl) CockpitColors.Accent else CarPaint.swatch(paint)).toArgb()
    Box(modifier.clipToBounds()) {
        // Sky, road and compass in one layer whose edges fade into the panel, so the scene has no
        // box around it; the sky also fades well before the top.
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val view = CarScene.view(size.width.toDouble() / size.height, drive, speedFx, air)
            drawSky(view, if (air > 0.01f && sky == null) Sky.Clear else sky, isDay, clock)
            fadeTop()
            drawScene(view, drive, travel, steer, speedFx, brake, boost, wet = sky == Sky.Rain || sky == Sky.Storm, flight, air, isDay)
            drawCompass(view, compass, drive, letters, letterStyle)
            drawSwap(view, flight)
            fadeEdges()
        }
        if (LocalInspectionMode.current) {
            if (flight > 0.5f) PlaneStandIn(Modifier.fillMaxSize(), Color(control.livery), air)
            else CarStandIn(Modifier.fillMaxSize(), CarPaint.swatch(paint))
        } else {
            LiveCar(Modifier.fillMaxSize(), state.speedKmh, state.driving, control)
        }
        // Indicator: amber light blinking on the turning side of the car's rear while driving.
        if (state.signal != Turn.None && flight < 0.01f) {
            val inspection = LocalInspectionMode.current
            Canvas(Modifier.fillMaxSize()) {
                clock // Redraw every frame, so the blink keeps time.
                if (drive > 0.5f && (inspection || blinkOn())) drawIndicator(control, state.signal, drive, speedFx)
            }
        }
        // Rain and snow fall in front of the car.
        if ((sky == Sky.Rain || sky == Sky.Storm || sky == Sky.Snow) && air < 0.5f) {
            Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                drawPrecipitation(sky, clock, speedFx)
                fadeTop()
                fadeEdges()
            }
        }
        // Touch layer above the 3D view: drag to turn the car, double-tap to face it front again.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(control) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking(); touched = true },
                        onDragEnd = { control.release(tracker.calculateVelocity().x / size.width * DEGREES_PER_WIDTH) },
                        onDragCancel = { control.release(0f) },
                    ) { change, dx ->
                        if (control.drive < 0.5f) {
                            tracker.addPosition(change.uptimeMillis, change.position)
                            control.drag(dx / size.width * DEGREES_PER_WIDTH)
                        }
                    }
                }
                .pointerInput(control) { detectTapGestures(onDoubleTap = { control.reset() }) },
        )
    }
}

/** Dragging across the whole card turns the car this far. */
private const val DEGREES_PER_WIDTH = 240f

private const val LANE = 0.62
private const val ROAD_EDGE = 1.25
private const val DASH = 0.55
private const val GAP = 1.45

/** The jet model is about 32 m long and drawn 2 units long. */
private const val PLANE_METRES_PER_UNIT = 16f
private const val RUNWAY = 1.7

private fun brakeLevel(m: MotionState) =
    if (m.motion == Motion.Braking || m.motion == Motion.HardBraking) m.level else 0f

/** Floor, contact shadow and, while driving, the road. Same camera as the 3D car. */
private fun DrawScope.drawScene(
    view: CarScene.View,
    drive: Float,
    travel: Float,
    steer: Float,
    speed: Float,
    brake: Float,
    boost: Float,
    wet: Boolean,
    flight: Float = 0f,
    air: Float = 0f,
    isDay: Boolean = true,
) {
    fun p(x: Double, z: Double, y: Double = 0.0) = CarScene.project(view, x, y, z, size.width, size.height)
    val parked = 1f - drive
    // The road belongs to the car; the plane rolls on a runway that drops away after take-off.
    val road = drive * (1f - flight)
    val runway = drive * flight * (1f - CarScene.smooth(air))
    if (runway > 0.01f) drawRunway(view, travel, runway, -2.6 * CarScene.smooth(air))
    if (air > 0.01f) drawClouds(view, travel, air * drive.coerceAtLeast(0.6f), isDay)

    // Showroom floor glow under the parked car.
    p(0.0, 0.0)?.let { c ->
        drawCircle(
            Brush.radialGradient(listOf(CockpitColors.Accent.copy(alpha = 0.16f * parked + 0.05f), Color.Transparent), c, size.width * 0.55f),
            radius = size.width * 0.55f,
            center = c,
        )
    }

    if (road > 0.01f) {
        val drive = road
        // The road ahead bends the way the car is turning.
        val bend = steer * 0.024
        fun roadX(x: Double, z: Double) = x + bend * z * z
        val near = 3.0
        val far = -46.0
        // Road surface: a strip that fades into the distance.
        val surface = Path()
        val left = (0..24).mapNotNull { i -> val z = near + (far - near) * i / 24; p(roadX(-ROAD_EDGE, z), z) }
        val right = (0..24).mapNotNull { i -> val z = near + (far - near) * i / 24; p(roadX(ROAD_EDGE, z), z) }
        if (left.size > 2 && right.size > 2) {
            surface.moveTo(left.first().x, left.first().y)
            left.drop(1).forEach { surface.lineTo(it.x, it.y) }
            right.reversed().forEach { surface.lineTo(it.x, it.y) }
            surface.close()
            val top = left.last().y
            drawPath(
                surface,
                Brush.verticalGradient(
                    // Asphalt: dark at night, soft grey by day, fading into the distance.
                    listOf(Color.Transparent, (if (CockpitColors.isDark) Color(0xFF1C1F25) else Color(0xFFD6DAE0)).copy(alpha = drive)),
                    startY = top,
                    endY = size.height,
                ),
            )
            // Road edges.
            for (edge in listOf(left, right)) {
                for (i in 0 until edge.size - 1) {
                    val fade = 1f - i / edge.size.toFloat()
                    drawLine(CockpitColors.TextTertiary.copy(alpha = 0.6f * drive * fade), edge[i], edge[i + 1], strokeWidth = 2.5f)
                }
            }
        }
        // Lane dashes rushing towards the viewer.
        val period = DASH + GAP
        val offset = (travel % period).toDouble()
        var z = near + offset
        while (z > far) {
            val z0 = z
            val z1 = z - DASH
            for (x in listOf(-LANE, LANE)) {
                val a = p(roadX(x, z0), z0)
                val b = p(roadX(x, z1), z1)
                if (a != null && b != null) {
                    val fade = ((z0 - far) / (near - far)).toFloat().coerceIn(0f, 1f)
                    // A wet road mirrors each dash as a soft smear below it.
                    if (wet) {
                        drawLine(
                            CockpitColors.Accent.copy(alpha = 0.22f * drive * fade),
                            a, Offset(a.x, a.y + (8f + 40f * fade) * density),
                            strokeWidth = 4f + 12f * fade,
                            cap = StrokeCap.Round,
                        )
                    }
                    drawLine(
                        CockpitColors.Accent.copy(alpha = 0.85f * drive * fade),
                        a, b,
                        strokeWidth = 2f + 7f * fade,
                        cap = StrokeCap.Round,
                    )
                }
            }
            z -= period
        }
        // Light streaks passing at the roadside: almost none in slow traffic, longer, brighter and
        // denser as the speed rises, with a second, outer row on the open road.
        val intensity = CarScene.smooth(((speed - 0.12f) / 0.6f).coerceIn(0f, 1f))
        if (intensity > 0.01f) {
            val streakPeriod = 2.6 - 1.2 * intensity
            val streakLength = 0.5 + 1.8 * intensity
            val rows = buildList {
                add(-ROAD_EDGE - 0.9 to 0.35); add(ROAD_EDGE + 0.9 to 0.35)
                if (intensity > 0.45f) { add(-ROAD_EDGE - 2.1 to 0.8); add(ROAD_EDGE + 2.1 to 0.8) }
            }
            var sz = near + ((travel * 1.4f) % streakPeriod)
            while (sz > far * 0.6) {
                for ((x, y) in rows) {
                    val a = p(roadX(x, sz), sz, y)
                    val b = p(roadX(x, sz - streakLength), sz - streakLength, y)
                    if (a != null && b != null) {
                        val fade = ((sz - far * 0.6) / (near - far * 0.6)).toFloat().coerceIn(0f, 1f)
                        drawLine(
                            CockpitColors.TextSecondary.copy(alpha = (0.2f + 0.5f * intensity) * drive * fade),
                            a, b,
                            strokeWidth = 1.5f + 3f * fade * intensity,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                sz -= streakPeriod
            }
        }
    }

    // Braking: red light spills onto the road behind the car, stronger for harder braking.
    // Accelerating: a soft glow in the accent colour under the car.
    val ground = drive * (1f - air)
    if (ground > 0.01f) {
        val drive = ground
        p(0.0, 1.25)?.let { rear ->
            if (brake > 0.01f) {
                drawCircle(
                    Brush.radialGradient(listOf(BrakeRed.copy(alpha = 0.55f * brake * drive), Color.Transparent), rear, size.width * (0.28f + 0.12f * brake)),
                    radius = size.width * (0.28f + 0.12f * brake),
                    center = rear,
                )
            }
        }
        p(0.0, 0.0)?.let { under ->
            if (boost > 0.01f) {
                drawCircle(
                    Brush.radialGradient(listOf(CockpitColors.Accent.copy(alpha = 0.35f * boost * drive), Color.Transparent), under, size.width * 0.45f),
                    radius = size.width * 0.45f,
                    center = under,
                )
            }
        }
    }

    // Contact shadow: grounds the car on the floor or the road; the plane's is wider, and gone in
    // the air.
    if (air > 0.98f) return
    val shadow = Path()
    var first = true
    val sx = 0.62 + 0.4 * flight
    val sz = 1.12 - 0.1 * flight
    for (i in 0..32) {
        val a = 2 * Math.PI * i / 32
        val pt = p(sx * cos(a), sz * sin(a)) ?: continue
        if (first) shadow.moveTo(pt.x, pt.y) else shadow.lineTo(pt.x, pt.y)
        first = false
    }
    shadow.close()
    val centre = p(0.0, 0.0) ?: Offset(size.width / 2, size.height / 2)
    drawPath(shadow, Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f * (1f - air)), Color.Transparent), centre, size.width * (0.32f + 0.12f * flight)))
}

/** A runway under the plane: wide asphalt, a white dashed centre line and edge lights. */
private fun DrawScope.drawRunway(view: CarScene.View, travel: Float, alpha: Float, y: Double) {
    fun p(x: Double, z: Double) = CarScene.project(view, x, y, z, size.width, size.height)
    val near = 3.0
    val far = -70.0
    val left = (0..28).mapNotNull { i -> val z = near + (far - near) * i / 28; p(-RUNWAY, z) }
    val right = (0..28).mapNotNull { i -> val z = near + (far - near) * i / 28; p(RUNWAY, z) }
    if (left.size > 2 && right.size > 2) {
        val surface = Path()
        surface.moveTo(left.first().x, left.first().y)
        left.drop(1).forEach { surface.lineTo(it.x, it.y) }
        right.reversed().forEach { surface.lineTo(it.x, it.y) }
        surface.close()
        val asphalt = if (CockpitColors.isDark) Color(0xFF15181D) else Color(0xFF9AA1AA)
        drawPath(surface, Brush.verticalGradient(listOf(Color.Transparent, asphalt.copy(alpha = alpha)), startY = left.last().y, endY = size.height))
        for (edge in listOf(left, right)) {
            for (i in 0 until edge.size - 1) {
                val fade = 1f - i / edge.size.toFloat()
                drawLine(Color.White.copy(alpha = 0.7f * alpha * fade), edge[i], edge[i + 1], strokeWidth = 2f * density)
            }
        }
    }
    // Centre line: long white dashes rushing past.
    val period = 3.2
    var z = near + (travel % period.toFloat())
    while (z > far) {
        val a = p(0.0, z)
        val b = p(0.0, z - 1.5)
        if (a != null && b != null) {
            val fade = ((z - far) / (near - far)).toFloat().coerceIn(0f, 1f)
            drawLine(Color.White.copy(alpha = 0.9f * alpha * fade), a, b, strokeWidth = (2f + 6f * fade) * density / 2f, cap = StrokeCap.Butt)
        }
        z -= period
    }
    // Edge lights, warm white, with a small glow.
    val lightPeriod = 2.4
    var lz = near + (travel % lightPeriod.toFloat())
    while (lz > far) {
        val fade = ((lz - far) / (near - far)).toFloat().coerceIn(0f, 1f)
        for (x in listOf(-RUNWAY - 0.15, RUNWAY + 0.15)) {
            val c = p(x, lz) ?: continue
            val r = (1.2f + 3.5f * fade) * density
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1C9).copy(alpha = alpha * fade), Color.Transparent), c, r * 3f), radius = r * 3f, center = c)
            drawCircle(Color(0xFFFFF6E0).copy(alpha = alpha * fade), radius = r * 0.5f, center = c)
        }
        lz -= lightPeriod
    }
}

/**
 * The view from cruising height: a blue glow along the horizon (deep blue at night) and a layer of
 * clouds far below, each a cluster of soft puffs, drifting past slower than the plane flies.
 */
private fun DrawScope.drawClouds(view: CarScene.View, travel: Float, alpha: Float, isDay: Boolean) {
    val dark = CockpitColors.isDark
    val horizon = CarScene.project(view, 0.0, -3.0, -120.0, size.width, size.height)?.y ?: (size.height * 0.4f)
    val glow = if (isDay) Color(0xFF6FA8FF) else Color(0xFF2B3F7A)
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, glow.copy(alpha = alpha * (if (isDay) 0.35f else 0.4f)), Color.Transparent),
            startY = horizon - size.height * 0.35f,
            endY = horizon + size.height * 0.3f,
        ),
    )
    val tint = when {
        isDay && !dark -> Color.White
        isDay -> Color(0xFFE4EAF2)
        else -> Color(0xFF6A7A96)
    }
    val depth = 90.0
    for (i in 0 until 30) {
        val x = (hash(i, 1) - 0.5) * 36.0
        val z = 4.0 - ((hash(i, 2) * depth + travel * 0.35) % depth)
        val y = -3.6 - hash(i, 3) * 1.4
        // In from the distance, gone before it reaches the camera.
        val fade = (((z + depth - 4.0) / 26.0).coerceIn(0.0, 1.0) * ((-2.0 - z) / 6.0).coerceIn(0.0, 1.0)).toFloat()
        if (fade <= 0.01f) continue
        val puffs = 3 + (hash(i, 6) * 3).toInt()
        for (k in 0 until puffs) {
            val px = x + (hash(i * 7 + k, 8) - 0.5) * 3.2
            val pz = z + (hash(i * 7 + k, 9) - 0.5) * 1.6
            val w = 0.7 + 1.1 * hash(i * 7 + k, 10)
            val c = CarScene.project(view, px, y + w * 0.15, pz, size.width, size.height) ?: continue
            val edge = CarScene.project(view, px + w, y + w * 0.15, pz, size.width, size.height) ?: continue
            val r = abs(edge.x - c.x)
            if (r < 1f) continue
            val a = alpha * fade * (0.55f + 0.3f * hash(i * 7 + k, 11))
            withTransform({ scale(1f, 0.5f, pivot = c) }) {
                drawCircle(
                    Brush.radialGradient(0f to tint.copy(alpha = a), 0.55f to tint.copy(alpha = a * 0.75f), 1f to Color.Transparent, center = c, radius = r),
                    radius = r,
                    center = c,
                )
            }
        }
    }
}

/** The moment of the swap: an accent glow and a ring spreading out from under the car. */
private fun DrawScope.drawSwap(view: CarScene.View, flight: Float) {
    if (flight <= 0.12f || flight >= 0.88f) return
    val phase = ((flight - 0.12f) / 0.76f).coerceIn(0f, 1f)
    val pulse = sin(Math.PI * phase).toFloat()
    val c = CarScene.project(view, 0.0, 0.35, 0.0, size.width, size.height) ?: return
    val glow = size.width * (0.18f + 0.22f * pulse)
    drawCircle(Brush.radialGradient(listOf(CockpitColors.Accent.copy(alpha = 0.55f * pulse), Color.Transparent), c, glow), radius = glow, center = c)
    val ring = size.width * (0.06f + 0.42f * phase)
    withTransform({ scale(1f, 0.38f, pivot = c) }) {
        drawCircle(CockpitColors.Accent.copy(alpha = 0.8f * (1f - phase)), radius = ring, center = c, style = Stroke((1f + 3f * (1f - phase)) * density))
    }
}


/** Compass radius: just beyond the bumpers (the car is about 2 units long), inside the fade. */
private const val RING = 1.3

/** A compass on the road around the car while driving: ticks every 10°, N in the accent colour. */
private fun DrawScope.drawCompass(view: CarScene.View, heading: Float, drive: Float, text: TextMeasurer, style: TextStyle) {
    if (drive < 0.01f) return
    // Driving, the car faces away from the camera (-z) and east is to its right (+x).
    fun at(bearing: Float, r: Double): Offset? {
        val a = Math.toRadians((bearing - heading).toDouble())
        return CarScene.project(view, r * sin(a), 0.0, -r * cos(a), size.width, size.height)
    }
    val ring = Path()
    var first = true
    for (i in 0..90) {
        val pt = at(i * 4f, RING) ?: continue
        if (first) ring.moveTo(pt.x, pt.y) else ring.lineTo(pt.x, pt.y)
        first = false
    }
    drawPath(ring, CockpitColors.TextSecondary.copy(alpha = 0.55f * drive), style = Stroke(1.5f * density))
    for (deg in 0 until 360 step 10) {
        val major = deg % 90 == 0
        val inner = at(deg.toFloat(), RING - if (major) 0.17 else if (deg % 30 == 0) 0.11 else 0.06) ?: continue
        val outer = at(deg.toFloat(), RING) ?: continue
        val colour = if (deg == 0) CockpitColors.Accent else CockpitColors.TextSecondary
        drawLine(colour.copy(alpha = (if (major) 0.9f else 0.5f) * drive), inner, outer, strokeWidth = (if (major) 2.5f else 1.2f) * density, cap = StrokeCap.Round)
    }
    for ((deg, name) in listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")) {
        val pt = at(deg.toFloat(), RING + 0.24) ?: continue
        val colour = if (deg == 0) CockpitColors.Accent else CockpitColors.TextSecondary
        val layout = text.measure(name, style.copy(color = colour.copy(alpha = drive)))
        drawText(layout, topLeft = Offset(pt.x - layout.size.width / 2f, pt.y - layout.size.height / 2f))
    }
}

/** Indicator amber, as on the car. */
private val SignalAmber = Color(0xFFFFA726)

private fun DrawScope.drawIndicator(control: CarControl, turn: Turn, drive: Float, speed: Float) {
    val view = CarScene.view(size.width.toDouble() / size.height, drive, speed)
    fun p(x: Float, y: Float, z: Float) = CarScene.project(view, x.toDouble(), y.toDouble(), z.toDouble(), size.width, size.height)
    // The renderer's light positions; without them (screenshots), roughly where they sit.
    val e = if (control.hasSignalEnds) control.signalEnds else floatArrayOf(-0.4f, 0.62f, 1f, 0.4f, 0.62f, 1f)
    val a = p(e[0], e[1], e[2]) ?: return
    val b = p(e[3], e[4], e[5]) ?: return
    // Driving, the car faces away from us, so its left side is on the left of the screen.
    val left = a.x < b.x
    val light = if ((turn == Turn.Left) == left) a else b
    val world = if (light == a) 0 else 3
    val r = 26f * density
    drawCircle(Brush.radialGradient(listOf(SignalAmber.copy(alpha = 0.85f), SignalAmber.copy(alpha = 0.25f), Color.Transparent), light, r), radius = r, center = light)
    drawCircle(Color(0xFFFFE0B2), radius = 3.5f * density, center = light)
    // A little of its light on the road below.
    p(e[world], 0f, e[world + 2] + 0.25f)?.let { ground ->
        val g = 40f * density
        drawCircle(Brush.radialGradient(listOf(SignalAmber.copy(alpha = 0.25f), Color.Transparent), ground, g), radius = g, center = ground)
    }
}

/** Fades what is already drawn to nothing at the left, right and bottom edges. */
private fun DrawScope.fadeEdges() {
    // An oval fade centred on the car: solid in the middle, gone at every edge, so the scene has no
    // straight line anywhere that could read as a box. The circle is stretched to the area's shape,
    // and beyond its edge the gradient stays clear, so the corners vanish too.
    val r = size.width / 2f
    withTransform({ scale(1f, size.height / size.width, pivot = center) }) {
        drawRect(
            Brush.radialGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent, center = center, radius = r),
            topLeft = Offset(0f, center.y - r),
            size = Size(size.width, size.width),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** Fades what is already drawn to nothing at the top edge. */
private fun DrawScope.fadeTop() {
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black), blendMode = BlendMode.DstIn)
}

/** Repeatable "random" number 0…1, so drops and stars keep their places between frames. */
private fun hash(i: Int, k: Int): Float {
    val v = sin(i * 12.9898 + k * 78.233) * 43758.5453
    return (v - kotlin.math.floor(v)).toFloat()
}

/**
 * The sky over the road, from the live weather: sun glare on clear days, stars at night, a grey
 * haze under cloud and rain, and fog that swallows the far road.
 */
private fun DrawScope.drawSky(view: CarScene.View, sky: Sky?, isDay: Boolean, clock: Float) {
    if (sky == null) return
    val horizon = CarScene.project(view, 0.0, 0.0, -90.0, size.width, size.height)?.y ?: (size.height * 0.4f)
    val dark = CockpitColors.isDark
    val clear = sky == Sky.Clear || sky == Sky.PartlyCloudy
    if (clear && isDay) {
        val strength = if (sky == Sky.Clear) 1f else 0.55f
        val sun = Offset(size.width * 0.84f, horizon - size.height * 0.2f)
        val warm = Color(0xFFFFD99A)
        drawCircle(
            Brush.radialGradient(listOf(warm.copy(alpha = (if (dark) 0.32f else 0.55f) * strength), Color.Transparent), sun, size.width * 0.55f),
            radius = size.width * 0.55f,
            center = sun,
        )
    } else if (clear) {
        // Night: a deep blue glow on the horizon and a few twinkling stars above it.
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF3B5BDB).copy(alpha = 0.22f), Color.Transparent),
                startY = horizon - size.height * 0.3f,
                endY = horizon + size.height * 0.12f,
            ),
        )
        for (i in 0 until 26) {
            val y = hash(i, 2) * (horizon - 12f * density)
            if (y <= 0f) continue
            val twinkle = 0.35f + 0.45f * (0.5f + 0.5f * sin(clock * (1.2f + 2f * hash(i, 3)) + i))
            drawCircle(Color.White.copy(alpha = twinkle * 0.8f), radius = (0.8f + 1.2f * hash(i, 4)) * density, center = Offset(hash(i, 1) * size.width, y))
        }
    } else {
        // Cloud, rain, storm, snow and fog: the horizon greys out.
        val haze = (if (dark) Color(0xFF7A8494) else Color(0xFFA3ADBA)).copy(alpha = if (dark) 0.2f else 0.3f)
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, haze, Color.Transparent),
                startY = horizon - size.height * 0.28f,
                endY = horizon + size.height * 0.22f,
            ),
        )
    }
    if (sky == Sky.Fog) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, CockpitColors.Background.copy(alpha = 0.9f), CockpitColors.Background.copy(alpha = 0.0f)),
                startY = horizon - size.height * 0.15f,
                endY = horizon + size.height * 0.4f,
            ),
        )
    }
}

/** Rain streaks (slanting more as the car speeds up), lightning in storms, drifting snow. */
private fun DrawScope.drawPrecipitation(sky: Sky, clock: Float, speed: Float) {
    val dark = CockpitColors.isDark
    if (sky == Sky.Snow) {
        val flake = if (dark) Color.White.copy(alpha = 0.75f) else Color(0xFF8A97A8).copy(alpha = 0.8f)
        for (i in 0 until 70) {
            val fall = 0.07f + 0.06f * hash(i, 3) + 0.25f * speed
            val y = ((hash(i, 2) + clock * fall) % 1f) * size.height
            val x = hash(i, 1) * size.width + sin(clock * 1.3f + hash(i, 5) * 6f) * 10f * density
            drawCircle(flake, radius = (1.4f + 2f * hash(i, 4)) * density, center = Offset(x, y))
        }
        return
    }
    val drop = if (dark) Color(0xFFB8C4D6).copy(alpha = 0.3f) else Color(0xFF5B6B80).copy(alpha = 0.32f)
    val slant = 0.12f + 0.55f * speed
    val count = if (sky == Sky.Storm) 120 else 80
    for (i in 0 until count) {
        val fall = 1.3f + 0.8f * hash(i, 3)
        val len = (14f + 20f * hash(i, 4)) * density
        val y = ((hash(i, 2) + clock * fall) % 1f) * (size.height + len)
        val x = hash(i, 1) * (size.width * (1f + slant)) - y * slant
        drawLine(drop, Offset(x, y), Offset(x + len * slant, y - len), strokeWidth = 1.3f * density, cap = StrokeCap.Round)
    }
    if (sky == Sky.Storm) {
        // Two quick flashes every seven seconds.
        val t = clock % 7f
        if (t < 0.07f || t in 0.16f..0.21f) drawRect(Color.White.copy(alpha = 0.22f))
    }
}

/** Simple drawn jet from behind for screenshot tests, where the 3D engine cannot run. */
@Composable
private fun PlaneStandIn(modifier: Modifier, livery: Color, air: Float) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val base = size.height * (0.62f - 0.06f * air)
        val span = size.width * 0.4f
        val body = Color(0xFFEDEFF2)
        // Swept wings with raked winglets.
        for (side in listOf(-1f, 1f)) {
            val tip = Offset(cx + side * span / 2, base - span * 0.04f)
            drawLine(Color(0xFFD5DAE0), Offset(cx, base + span * 0.03f), tip, strokeWidth = span * 0.03f, cap = StrokeCap.Round)
            drawLine(livery, tip, Offset(tip.x + side * span * 0.03f, tip.y - span * 0.09f), strokeWidth = span * 0.018f, cap = StrokeCap.Round)
            // Engines on the rear body.
            val engine = Offset(cx + side * span * 0.12f, base - span * 0.035f)
            drawCircle(Color(0xFFC9CED6), radius = span * 0.038f, center = engine)
            drawCircle(Color(0xFF16181C), radius = span * 0.024f, center = engine)
        }
        drawCircle(body, radius = span * 0.06f, center = Offset(cx, base))
        // T-tail: the fin in the livery colour with the tailplane across its top.
        val top = Offset(cx, base - span * 0.26f)
        drawLine(livery, Offset(cx, base - span * 0.04f), top, strokeWidth = span * 0.026f, cap = StrokeCap.Round)
        drawLine(body, Offset(cx - span * 0.17f, top.y + span * 0.01f), Offset(cx + span * 0.17f, top.y + span * 0.01f), strokeWidth = span * 0.018f, cap = StrokeCap.Round)
        if (air < 0.5f) {
            for (side in listOf(-1f, 1f)) drawCircle(Color(0xFF0E1216), radius = span * 0.018f, center = Offset(cx + side * span * 0.09f, base + span * 0.09f))
        }
    }
}

/** Simple drawn car for screenshot tests, where the 3D engine cannot run. */
@Composable
private fun CarStandIn(modifier: Modifier, paint: Color) {
    Canvas(modifier) {
        val w = size.width * 0.62f
        val h = w * 0.3f
        val left = (size.width - w) / 2f
        val base = size.height * 0.64f
        val body = Path().apply {
            moveTo(left, base)
            lineTo(left, base - h * 0.45f)
            quadraticTo(left + w * 0.05f, base - h * 0.62f, left + w * 0.22f, base - h * 0.66f)
            quadraticTo(left + w * 0.38f, base - h * 1.15f, left + w * 0.58f, base - h * 1.1f)
            quadraticTo(left + w * 0.78f, base - h * 1.0f, left + w * 0.9f, base - h * 0.62f)
            quadraticTo(left + w, base - h * 0.55f, left + w, base - h * 0.3f)
            lineTo(left + w, base)
            close()
        }
        val shade = Color(paint.red * 0.62f, paint.green * 0.62f, paint.blue * 0.66f)
        drawPath(body, Brush.verticalGradient(listOf(paint, shade), startY = base - h * 1.15f, endY = base))
        val glass = Path().apply {
            moveTo(left + w * 0.3f, base - h * 0.7f)
            quadraticTo(left + w * 0.42f, base - h * 1.02f, left + w * 0.58f, base - h * 1.0f)
            quadraticTo(left + w * 0.72f, base - h * 0.94f, left + w * 0.8f, base - h * 0.7f)
            close()
        }
        drawPath(glass, Color(0xFF1C2732))
        for (x in listOf(0.2f, 0.8f)) {
            drawCircle(Color(0xFF0E1216), radius = h * 0.3f, center = Offset(left + w * x, base))
            drawCircle(Color(0xFF6E7A86), radius = h * 0.17f, center = Offset(left + w * x, base))
        }
        drawRect(Color(0x553DDCFF), topLeft = Offset(left + w * 0.93f, base - h * 0.5f), size = Size(w * 0.06f, h * 0.08f))
    }
}
