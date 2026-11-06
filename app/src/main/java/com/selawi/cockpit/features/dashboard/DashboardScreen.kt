package com.selawi.cockpit.features.dashboard

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import com.selawi.cockpit.features.calls.CallBanner
import com.selawi.cockpit.features.calls.CallInfo
import com.selawi.cockpit.features.calls.CallState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.selawi.cockpit.data.local.Backdrop
import com.selawi.cockpit.data.local.ThemeMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.features.media.MediaCard
import com.selawi.cockpit.features.media.MediaControls
import com.selawi.cockpit.features.media.NowPlaying
import com.selawi.cockpit.features.media.rememberCoverTint
import com.selawi.cockpit.features.vehicle.DrivingState
import com.selawi.cockpit.features.vehicle.CarPaint
import com.selawi.cockpit.features.vehicle.VehiclePanel
import com.selawi.cockpit.features.weather.SkyIcon
import com.selawi.cockpit.features.weather.Weather
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the dashboard shows, gathered by the caller. */
class DashboardState(
    val weather: Weather?,
    val playing: NowPlaying,
    val driving: DrivingState = DrivingState(),
    val call: CallInfo = CallInfo(),
    /** Fixed time for screenshots; null shows the live clock. */
    val fixedTime: LocalDateTime? = null,
    val backdrop: Backdrop = Backdrop.CoverGlow,
    val carPaint: String = CarPaint.Pearl,
    val theme: ThemeMode = ThemeMode.Auto,
    /** The driver's accent, or null for the default; for the quick accent button. */
    val accent: Color? = null,
)

@Composable
fun DashboardScreen(
    compact: Boolean,
    state: DashboardState,
    controls: MediaControls,
    onOpenPhone: () -> Unit = {},
    onTheme: (ThemeMode) -> Unit = {},
    onCarPaint: (String, Boolean) -> Unit = { _, _ -> },
    onAccent: (Color?, Boolean) -> Unit = { _, _ -> },
) {
    if (compact) {
        CompactDashboard(state, controls, onOpenPhone)
    } else {
        WideDashboard(state, controls, onOpenPhone, QuickActions(onTheme, onCarPaint, onAccent))
    }
}

/** Slides in above the music while a call rings or runs. */
@Composable
private fun CallSlot(call: CallInfo, onOpenPhone: () -> Unit) {
    AnimatedVisibility(
        visible = call.state != CallState.Idle,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        CallBanner(call, onOpenPhone, Modifier.fillMaxWidth())
    }
}

/** The car panel on the left; time, music and the forecast on the right. */
@Composable
private fun WideDashboard(state: DashboardState, controls: MediaControls, onOpenPhone: () -> Unit, quick: QuickActions) {
    // The cover glow lies behind the whole screen; the car panel fades into it, so there is no seam.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // The car panel takes about two fifths of the width, never wider than on the Seal's own screen.
    val panelWidth = (maxWidth * 0.4f).coerceIn(360.dp, 500.dp)
    Row(Modifier.fillMaxSize().backdrop(state.backdrop, state.playing)) {
        VehiclePanel(state.driving, Modifier.width(panelWidth).fillMaxHeight(), weather = state.weather, paint = state.carPaint)
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header(state, Modifier.fillMaxWidth())
            CallSlot(state.call, onOpenPhone)
            var panel by remember { mutableStateOf(ColourPanel.None) }
            FlipCard(
                panel, state.carPaint, state.accent, quick,
                onClose = { panel = ColourPanel.None },
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                MediaCard(state.playing, controls, Modifier.fillMaxSize())
            }
            QuickControls(state.theme, state.carPaint, panel, { panel = it }, quick.onTheme, Modifier.fillMaxWidth())
        }
    }
    }
}

/**
 * A narrow window, as in the car's split screen: the car panel fills the top and the music card
 * always stays visible at the bottom. Time and weather are left to the car's own status bar,
 * which split screen shows anyway.
 */
@Composable
private fun CompactDashboard(state: DashboardState, controls: MediaControls, onOpenPhone: () -> Unit) {
    Column(Modifier.fillMaxSize().backdrop(state.backdrop, state.playing)) {
        VehiclePanel(state.driving, Modifier.fillMaxWidth().weight(1f), compact = true, weather = state.weather, paint = state.carPaint)
        Column(
            Modifier.padding(start = Space.l, end = Space.l, bottom = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            CallSlot(state.call, onOpenPhone)
            MediaCard(state.playing, controls, Modifier.fillMaxWidth(), compact = true)
        }
    }
}

/**
 * The chosen background, drawn softly behind the whole home screen. While a song plays it comes
 * alive: the colour pulses at a steady 120 beats a minute, swells and drifts slowly. The car does
 * not share its audio with apps, so this is a rhythm, not the song's own beat.
 */
@Composable
private fun Modifier.backdrop(backdrop: Backdrop, playing: NowPlaying): Modifier {
    val tint = rememberCoverTint(playing)
    val accent = CockpitColors.Accent
    val dark = CockpitColors.isDark
    // The cover glow only shows while something is playing.
    val strength = if (backdrop == Backdrop.CoverGlow && !playing.hasTrack) 0f else if (dark) 0.30f else 0.10f
    val motion by animateFloatAsState(if (playing.isPlaying) 1f else 0f, tween(1_200), label = "musicMotion")
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing.isPlaying) {
        var last = 0L
        while (playing.isPlaying || motion > 0f) {
            withFrameNanos { now ->
                if (last != 0L) clock += ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
            }
        }
    }
    return drawBehind { drawBackdrop(backdrop, tint, accent, strength, motion, clock) }
}

/**
 * Soft colour washes. Centred on the music side and wide enough to drift over the car panel's
 * fading edge, so there is no seam. [motion] (0 … 1) brings in the pulse and drift at time [t].
 * Also used for the previews in Settings.
 */
fun DrawScope.drawBackdrop(
    backdrop: Backdrop,
    coverTint: Color,
    accent: Color,
    strength: Float,
    motion: Float = 0f,
    t: Float = 0f,
) {
    if (strength <= 0f) return
    val beat = exp(-((t * 2f) % 1f) * 4f)
    var index = 0
    fun blob(colour: Color, x: Float, y: Float, r: Float, k: Float = 1f) {
        val i = index++
        // Each patch of colour wanders on its own slow loop and breathes with the beat.
        val cx = x + motion * 0.07f * sin(t * 0.35f + i * 2.1f)
        val cy = y + motion * 0.09f * cos(t * 0.27f + i * 1.7f)
        val swell = 0.5f + 0.5f * sin(t * 0.6f + i)
        val a = strength * k * (1f + motion * (0.45f * beat + 0.2f * swell))
        val radius = size.width * r * (1f + motion * (0.06f * beat + 0.04f * swell))
        drawRect(
            Brush.radialGradient(
                listOf(colour.copy(alpha = a.coerceAtMost(1f)), colour.copy(alpha = (a * 0.35f).coerceAtMost(1f)), Color.Transparent),
                center = Offset(size.width * cx, size.height * cy),
                radius = radius,
            ),
        )
    }
    when (backdrop) {
        Backdrop.Plain -> Unit
        Backdrop.CoverGlow -> blob(coverTint, 0.62f, 0.48f, 0.62f)
        Backdrop.Accent -> blob(accent, 0.62f, 0.48f, 0.62f)
        Backdrop.Dusk -> {
            blob(Color(0xFF7C3AED), 0.78f, 0.1f, 0.55f)
            blob(Color(0xFFF97316), 0.42f, 1.05f, 0.5f, 0.8f)
        }
        Backdrop.Ocean -> {
            blob(Color(0xFF14B8A6), 0.82f, 0.12f, 0.5f)
            blob(Color(0xFF2563EB), 0.4f, 1.0f, 0.55f)
        }
        Backdrop.Aurora -> {
            blob(Color(0xFF22C55E), 0.3f, 0.0f, 0.5f, 0.8f)
            blob(Color(0xFF06B6D4), 0.7f, 0.2f, 0.45f)
            blob(Color(0xFFA855F7), 0.9f, 0.95f, 0.45f, 0.8f)
        }
    }
}

/** Time and date on the left; current weather on the right. */
@Composable
private fun Header(state: DashboardState, modifier: Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(state.fixedTime ?: LocalDateTime.now()) }
    LaunchedEffect(state.fixedTime) {
        while (state.fixedTime == null) {
            now = LocalDateTime.now()
            // Wake at the start of the next minute instead of polling every second.
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val is24 = DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    val weather = state.weather

    BoxWithConstraints(modifier) {
        // Narrower screens drop the weather words, then the date, before anything gets squeezed.
        val showCondition = maxWidth >= 620.dp
        val showDate = maxWidth >= 480.dp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(time, style = MaterialTheme.typography.displayMedium, color = CockpitColors.TextPrimary, maxLines = 1)
                if (showDate) {
                    Text(
                        date,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CockpitColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            if (weather != null) {
                SkyIcon(weather.sky, weather.isDay, Modifier.size(28.dp), size = 28.dp)
                Text("${weather.temperature}°", style = MaterialTheme.typography.displaySmall, color = CockpitColors.TextPrimary)
                if (showCondition) {
                    Text(weather.condition, style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}
