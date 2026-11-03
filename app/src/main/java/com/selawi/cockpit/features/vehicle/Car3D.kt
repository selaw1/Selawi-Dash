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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalInspectionMode
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.Space
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The car on the dashboard. Parked, it stands on a softly lit floor and turns under the driver's
 * finger. Driving, the camera swings behind it as it turns down the road; the road, its lane lines
 * and passing light streaks are drawn with the same camera as the 3D car, so the car sits on the
 * road and everything moves at the real GPS speed.
 */
@Composable
fun Car3D(state: DrivingState, modifier: Modifier = Modifier) {
    val control = remember { CarControl().also { it.drive = if (state.driving) 1f else 0f } }
    var touched by rememberSaveable { mutableStateOf(false) }
    val hint by animateFloatAsState(if (touched || state.driving) 0f else 1f, tween(600), label = "hint")
    val latest = rememberUpdatedState(state)

    // One clock for the whole scene: eases the drive value, follows bends and moves the road.
    var travel by remember { mutableFloatStateOf(0f) }
    var drive by remember { mutableFloatStateOf(control.drive) }
    var steer by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(control) {
        var last = 0L
        var lastHeading: Float? = null
        var turnRate = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                val s = latest.value
                // About 1.6 s from parked to driving and back.
                val target = if (s.driving) 1f else 0f
                drive += (target - drive).coerceIn(-dt / 1.6f, dt / 1.6f)
                // Bend from the change in GPS heading, smoothed so the wheels never twitch.
                val heading = s.headingDeg
                if (heading != null && lastHeading != null && dt > 0f) {
                    val delta = ((heading - lastHeading!! + 540f) % 360f) - 180f
                    turnRate += (delta * 4f - turnRate) * (1 - exp(-dt * 1.5f))
                }
                if (heading != lastHeading) lastHeading = heading
                turnRate *= exp(-dt * 0.8f)
                steer = (turnRate / 30f).coerceIn(-1f, 1f)
                travel += s.speedKmh / 3.6f / CarScene.METRES_PER_UNIT * dt
                control.drive = drive
                control.steer = steer
            }
        }
    }

    Box(modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) { drawScene(drive, travel, steer) }
        if (LocalInspectionMode.current) {
            CarStandIn(Modifier.fillMaxSize())
        } else {
            LiveCar(Modifier.fillMaxSize(), state.speedKmh, state.driving, control)
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
        if (hint > 0.01f) {
            Text(
                "Drag to turn the car",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextTertiary.copy(alpha = hint),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.s),
            )
        }
    }
}

/** Dragging across the whole card turns the car this far. */
private const val DEGREES_PER_WIDTH = 240f

private const val LANE = 0.62
private const val ROAD_EDGE = 1.25
private const val DASH = 0.55
private const val GAP = 1.45

/** Floor, contact shadow and, while driving, the road. Same camera as the 3D car. */
private fun DrawScope.drawScene(drive: Float, travel: Float, steer: Float) {
    val view = CarScene.view(size.width.toDouble() / size.height, drive)
    fun p(x: Double, z: Double, y: Double = 0.0) = CarScene.project(view, x, y, z, size.width, size.height)
    val parked = 1f - drive

    // Showroom floor glow under the parked car.
    p(0.0, 0.0)?.let { c ->
        drawCircle(
            Brush.radialGradient(listOf(CockpitColors.Accent.copy(alpha = 0.16f * parked + 0.05f), Color.Transparent), c, size.width * 0.55f),
            radius = size.width * 0.55f,
            center = c,
        )
    }

    if (drive > 0.01f) {
        // A gentle bend in the road ahead when the car is turning.
        val bend = steer * 0.012
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
                    listOf(Color.Transparent, CockpitColors.SurfaceHigh.copy(alpha = 0.9f * drive)),
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
        // Light streaks passing at the roadside, faster and fainter: a sense of speed.
        val streakPeriod = 2.6
        var sz = near + ((travel * 1.4f) % streakPeriod)
        while (sz > far * 0.6) {
            for (x in listOf(-ROAD_EDGE - 0.9, ROAD_EDGE + 0.9)) {
                val a = p(roadX(x, sz), sz, 0.35)
                val b = p(roadX(x, sz - 0.9), sz - 0.9, 0.35)
                if (a != null && b != null) {
                    val fade = ((sz - far * 0.6) / (near - far * 0.6)).toFloat().coerceIn(0f, 1f)
                    drawLine(CockpitColors.TextSecondary.copy(alpha = 0.35f * drive * fade), a, b, strokeWidth = 1.5f + 2f * fade, cap = StrokeCap.Round)
                }
            }
            sz -= streakPeriod
        }
    }

    // Contact shadow: grounds the car on the floor or the road.
    val shadow = Path()
    var first = true
    for (i in 0..32) {
        val a = 2 * Math.PI * i / 32
        val pt = p(0.62 * cos(a), 1.12 * sin(a)) ?: continue
        if (first) shadow.moveTo(pt.x, pt.y) else shadow.lineTo(pt.x, pt.y)
        first = false
    }
    shadow.close()
    val centre = p(0.0, 0.0) ?: Offset(size.width / 2, size.height / 2)
    drawPath(shadow, Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), centre, size.width * 0.32f))
}

/** Simple drawn car for screenshot tests, where the 3D engine cannot run. */
@Composable
private fun CarStandIn(modifier: Modifier) {
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
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFFE9EEF2), Color(0xFF9AA6B2)), startY = base - h * 1.15f, endY = base))
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
