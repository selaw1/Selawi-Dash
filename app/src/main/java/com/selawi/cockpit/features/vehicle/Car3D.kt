package com.selawi.cockpit.features.vehicle

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.sp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.SectionLabel
import com.selawi.cockpit.core.design.Space

/**
 * The car on the dashboard. Parked it turns slowly on a lit floor; driving it shows from behind,
 * with road lines moving at the GPS speed. Motion is slow and low-contrast so it never draws the
 * driver's eye.
 */
@Composable
fun Car3D(state: DrivingState, modifier: Modifier = Modifier, showFront: Boolean = true) {
    val drive by animateFloatAsState(if (state.driving) 1f else 0f, tween(1200), label = "drive")
    Box(modifier) {
        RoadBackdrop(drive, state.speedKmh, Modifier.fillMaxSize())
        if (LocalInspectionMode.current) {
            CarStandIn(Modifier.fillMaxSize())
        } else {
            LiveCar(Modifier.fillMaxSize(), state.speedKmh, state.driving, showFront)
        }
        Column(Modifier.align(Alignment.TopStart).padding(Space.l)) {
            SectionLabel(if (state.driving) "Driving" else "Parked")
            if (state.driving) {
                Text(
                    "${state.speedKmh}",
                    style = MaterialTheme.typography.displayMedium,
                    color = CockpitColors.TextPrimary,
                )
                Text("km/h", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = CockpitColors.TextSecondary)
            }
        }
    }
}

/** Floor glow when parked, perspective road lines when driving. */
@Composable
private fun RoadBackdrop(drive: Float, speedKmh: Int, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "road")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "phase",
    )
    // Lines advance faster with speed; the infinite clock only provides smooth time.
    val travel = (phase * (0.4f + speedKmh / 60f)) % 1f
    Canvas(modifier) {
        val floorY = size.height * 0.66f
        drawRect(
            Brush.radialGradient(
                listOf(CockpitColors.Accent.copy(alpha = 0.16f + 0.06f * drive), Color.Transparent),
                center = Offset(size.width / 2f, floorY),
                radius = size.width * 0.42f,
            ),
        )
        if (drive > 0.01f) drawRoad(drive, travel, size.height * 0.48f)
    }
}

private fun DrawScope.drawRoad(alpha: Float, travel: Float, horizon: Float) {
    val cx = size.width / 2f
    val bottom = size.height
    val edge = CockpitColors.TextTertiary.copy(alpha = 0.55f * alpha)
    drawLine(edge, Offset(cx - size.width * 0.04f, horizon), Offset(cx - size.width * 0.46f, bottom), strokeWidth = 3f)
    drawLine(edge, Offset(cx + size.width * 0.04f, horizon), Offset(cx + size.width * 0.46f, bottom), strokeWidth = 3f)
    // Dashed centre lines rushing towards the viewer.
    for (i in 0 until 6) {
        val t0 = ((i + travel) / 6f)
        val t1 = t0 + 0.07f
        if (t1 > 1f) continue
        val y0 = horizon + (bottom - horizon) * t0 * t0
        val y1 = horizon + (bottom - horizon) * t1 * t1
        val w = 2f + 8f * t1
        for (lane in listOf(-0.16f, 0.16f)) {
            val x0 = cx + size.width * lane * t0 * t0 * 1.6f
            val x1 = cx + size.width * lane * t1 * t1 * 1.6f
            drawLine(CockpitColors.Accent.copy(alpha = 0.5f * alpha), Offset(x0, y0), Offset(x1, y1), strokeWidth = w)
        }
    }
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
