package com.selawi.cockpit.features.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import com.selawi.cockpit.features.settings.AccentPicker
import com.selawi.cockpit.features.settings.CarPaintPicker
import com.selawi.cockpit.features.vehicle.CarPaint
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.data.local.ThemeMode
import kotlinx.coroutines.delay

/**
 * Quick controls under the music card: music volume, screen brightness and the theme. Room is left
 * for more buttons later.
 */
/** What the quick buttons change; the same settings as in the Settings screen. */
class QuickActions(
    val onTheme: (ThemeMode) -> Unit = {},
    val onCarPaint: (String, Boolean) -> Unit = { _, _ -> },
    val onAccent: (Color?, Boolean) -> Unit = { _, _ -> },
)

private enum class Picker { None, Car, Accent }

@Composable
fun QuickControls(theme: ThemeMode, carPaint: String, accent: Color?, actions: QuickActions, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(Picker.None) }
    Box(modifier) {
        CockpitCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VolumeSlider(Modifier.weight(1f))
                BrightnessSlider(Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(36.dp).background(CockpitColors.Stroke))
                ColourButton("Car", CarPaint.swatch(carPaint)) { open = if (open == Picker.Car) Picker.None else Picker.Car }
                ColourButton("Accent", CockpitColors.Accent) { open = if (open == Picker.Accent) Picker.None else Picker.Accent }
                ThemeButton(theme, actions.onTheme)
            }
        }
        // The picker opens above the strip without dimming the screen, so the car and the
        // dashboard can be seen changing while you choose.
        if (open != Picker.None) {
            Popup(
                alignment = Alignment.BottomEnd,
                offset = IntOffset(0, with(LocalDensity.current) { -(84.dp).roundToPx() }),
                onDismissRequest = { open = Picker.None },
                properties = PopupProperties(focusable = true),
            ) {
                CockpitCard(padding = 20.dp) {
                    Text(
                        if (open == Picker.Car) "Car colour" else "Accent colour",
                        style = MaterialTheme.typography.titleMedium,
                        color = CockpitColors.TextPrimary,
                    )
                    Spacer(Modifier.height(14.dp))
                    if (open == Picker.Car) {
                        CarPaintPicker(carPaint, actions.onCarPaint, preview = false)
                    } else {
                        AccentPicker(accent, actions.onAccent)
                    }
                }
            }
        }
    }
}

/** A round swatch with a label: opens that colour's picker. */
@Composable
private fun ColourButton(label: String, colour: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CockpitColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(colour).border(1.dp, CockpitColors.Stroke, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = CockpitColors.TextPrimary)
    }
}

/** Media volume, the same one the car's volume buttons change; it follows them too. */
@Composable
private fun VolumeSlider(modifier: Modifier) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val audio = remember { if (inspection) null else context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val max = remember { audio?.let { runCatching { it.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrNull() } ?: 15 }
    var level by remember { mutableFloatStateOf(audio?.let { runCatching { it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() }.getOrNull() } ?: (max * 0.4f)) }
    var dragging by remember { mutableStateOf(false) }
    // Picks up changes from the steering wheel or the car's own controls.
    LaunchedEffect(audio) {
        while (audio != null) {
            if (!dragging) runCatching { level = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() }
            delay(800)
        }
    }
    ControlSlider(CockpitIcons.Volume, "Volume", level, 0f..max.toFloat(), modifier, steps = (max - 1).coerceAtLeast(0), onChange = {
        dragging = true
        level = it
        runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }
    }, onDone = { dragging = false })
}

/** This app's screen brightness while it is open; the car's own setting returns when you leave. */
@Composable
private fun BrightnessSlider(modifier: Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var level by remember {
        val window = activity?.window?.attributes?.screenBrightness ?: -1f
        val system = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrNull()
        mutableFloatStateOf(if (window >= 0f) window else system ?: 0.6f)
    }
    ControlSlider(CockpitIcons.Brightness, "Brightness", level, 0.05f..1f, modifier, onChange = {
        level = it
        activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = it } }
    })
}

@Composable
private fun ControlSlider(
    icon: ImageVector,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier,
    steps: Int = 0,
    onChange: (Float) -> Unit,
    onDone: () -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = description, tint = CockpitColors.TextSecondary, modifier = Modifier.size(24.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            // Volume moves in the car's own whole steps; brightness is smooth.
            steps = if (steps in 1..30) steps else 0,
            colors = SliderDefaults.colors(
                thumbColor = CockpitColors.TextPrimary,
                activeTrackColor = CockpitColors.Accent,
                inactiveTrackColor = CockpitColors.SurfaceHigh,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Auto → Day → Night: the same setting as in Settings, one tap away. */
@Composable
private fun ThemeButton(theme: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val (icon, label) = when (theme) {
        ThemeMode.Auto -> CockpitIcons.AutoTheme to "Auto"
        ThemeMode.Light -> CockpitIcons.Brightness to "Day"
        ThemeMode.Dark -> CockpitIcons.Moon to "Night"
    }
    val next = when (theme) {
        ThemeMode.Auto -> ThemeMode.Light
        ThemeMode.Light -> ThemeMode.Dark
        ThemeMode.Dark -> ThemeMode.Auto
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CockpitColors.SurfaceHigh)
            .clickable { onTheme(next) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = CockpitColors.Accent, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = CockpitColors.TextPrimary)
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
