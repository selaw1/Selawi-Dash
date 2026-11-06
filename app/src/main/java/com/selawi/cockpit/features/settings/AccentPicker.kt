package com.selawi.cockpit.features.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.Space
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The original blue, shown when the driver has not chosen a colour. */
val DefaultAccent = Color(0xFF2F6BFF)

private val presets = listOf(
    Color(0xFF2F6BFF), Color(0xFF14B8A6), Color(0xFF22C55E), Color(0xFFF59E0B),
    Color(0xFFF97316), Color(0xFFEF4444), Color(0xFFEC4899), Color(0xFF8B5CF6),
)

/**
 * Accent colour: a colour wheel (hue around, strength outwards) plus quick picks.
 * [onChange] is called live while dragging ([final] false) and once more when the finger lifts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(current: Color?, onChange: (color: Color?, final: Boolean) -> Unit, wheelSize: Dp = 220.dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
        ColorWheel(current ?: DefaultAccent, onChange, Modifier.size(wheelSize))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(CockpitColors.Accent))
                Text(
                    if (current == null) "Default blue" else "Your colour",
                    style = MaterialTheme.typography.titleMedium,
                    color = CockpitColors.TextPrimary,
                )
            }
            // Wraps onto a second line when the space is narrow, instead of running off the edge.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                presets.forEach { color ->
                    val selected = current?.toArgb() == color.toArgb() || (current == null && color == DefaultAccent)
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (selected) 3.dp else 0.dp, CockpitColors.TextPrimary, CircleShape)
                            .clickable { onChange(if (color == DefaultAccent) null else color, true) },
                    )
                }
            }
            PillButton("Default", onClick = { onChange(null, true) })
        }
    }
}

@Composable
internal fun ColorWheel(selected: Color?, onChange: (Color?, Boolean) -> Unit, modifier: Modifier) {
    // No marker when the current colour is not one the wheel can make (a dark preset, say).
    val hsv = selected?.let { c -> FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) } }
    val report = rememberUpdatedState(onChange)
    Canvas(
        modifier
            .pointerInput(Unit) {
                fun pick(at: Offset): Color {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = min(size.width, size.height) / 2f
                    val d = at - c
                    // Screen y points down, so this angle runs clockwise, like the hue sweep drawn below.
                    val hue = ((Math.toDegrees(atan2(d.y, d.x).toDouble()) + 360) % 360).toFloat()
                    val strength = (sqrt(d.x * d.x + d.y * d.y) / r).coerceIn(0.15f, 1f)
                    return Color.hsv(hue, strength, 1f)
                }
                var last: Color? = null
                detectDragGestures(
                    onDragStart = { last = pick(it); report.value(last, false) },
                    // Save once, when the finger lifts.
                    onDragEnd = { last?.let { report.value(it, true) } },
                ) { change, _ ->
                    last = pick(change.position)
                    report.value(last, false)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = min(size.width, size.height) / 2f
                    val d = at - c
                    val hue = ((Math.toDegrees(atan2(d.y, d.x).toDouble()) + 360) % 360).toFloat()
                    val strength = (sqrt(d.x * d.x + d.y * d.y) / r).coerceIn(0.15f, 1f)
                    report.value(Color.hsv(hue, strength, 1f), true)
                }
            },
    ) {
        val r = size.minDimension / 2f
        drawCircle(
            Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center),
            radius = r,
        )
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), radius = r)
        // Knob where the current colour sits.
        if (hsv == null) {
            drawCircle(Color.Black.copy(alpha = 0.1f), radius = r, style = Stroke(1.dp.toPx()))
            return@Canvas
        }
        val angle = Math.toRadians(hsv[0].toDouble())
        val dist = hsv[1] * r
        val knob = Offset(center.x + (cos(angle) * dist).toFloat(), center.y + (sin(angle) * dist).toFloat())
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = 17.dp.toPx(), center = knob + Offset(0f, 2f))
        drawCircle(Color.White, radius = 16.dp.toPx(), center = knob)
        drawCircle(Color.hsv(hsv[0], hsv[1], 1f), radius = 12.dp.toPx(), center = knob)
        drawCircle(Color.Black.copy(alpha = 0.1f), radius = r, style = Stroke(1.dp.toPx()))
    }
}
