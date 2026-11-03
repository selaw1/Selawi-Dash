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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.features.media.MediaCard
import com.selawi.cockpit.features.media.MediaControls
import com.selawi.cockpit.features.media.NowPlaying
import com.selawi.cockpit.features.media.rememberCoverTint
import com.selawi.cockpit.features.vehicle.DrivingState
import com.selawi.cockpit.features.vehicle.VehiclePanel
import com.selawi.cockpit.features.weather.ForecastStrip
import com.selawi.cockpit.features.weather.SkyIcon
import com.selawi.cockpit.features.weather.Weather
import kotlinx.coroutines.delay
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
)

@Composable
fun DashboardScreen(compact: Boolean, state: DashboardState, controls: MediaControls, onOpenPhone: () -> Unit = {}) {
    if (compact) CompactDashboard(state, controls, onOpenPhone) else WideDashboard(state, controls, onOpenPhone)
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
private fun WideDashboard(state: DashboardState, controls: MediaControls, onOpenPhone: () -> Unit) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    // The cover glow lies behind the whole screen; the car panel fades into it, so there is no seam.
    Row(Modifier.fillMaxSize().coverWash(state.playing)) {
        VehiclePanel(state.driving, Modifier.width(500.dp).fillMaxHeight())
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header(state, Modifier.fillMaxWidth())
            CallSlot(state.call, onOpenPhone)
            MediaCard(state.playing, controls, Modifier.fillMaxWidth().weight(1f))
            ForecastStrip(state.weather, Modifier.fillMaxWidth(), is24)
        }
    }
}

/** A narrow window (for example in the car's split screen): one scrolling column. */
@Composable
private fun CompactDashboard(state: DashboardState, controls: MediaControls, onOpenPhone: () -> Unit) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        VehiclePanel(state.driving, Modifier.fillMaxWidth(), carHeight = 240.dp)
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Header(state, Modifier.fillMaxWidth())
            CallSlot(state.call, onOpenPhone)
            MediaCard(state.playing, controls, Modifier.fillMaxWidth(), compact = true)
            ForecastStrip(state.weather, Modifier.fillMaxWidth(), is24)
        }
    }
}

/** At night, the cover's colour washes softly across the background, as in the mockup. */
@Composable
private fun Modifier.coverWash(playing: NowPlaying): Modifier {
    val tint = rememberCoverTint(playing)
    val strength = if (!playing.hasTrack) 0f else if (CockpitColors.isDark) 0.30f else 0.10f
    // Centred on the music side and wide enough to drift softly over the car panel's fading edge.
    return drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(tint.copy(alpha = strength), tint.copy(alpha = strength * 0.35f), Color.Transparent),
                center = Offset(size.width * 0.62f, size.height * 0.48f),
                radius = size.width * 0.62f,
            ),
        )
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

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(time, style = MaterialTheme.typography.displayMedium, color = CockpitColors.TextPrimary)
            Text(date, style = MaterialTheme.typography.bodyLarge, color = CockpitColors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (weather != null) {
            SkyIcon(weather.sky, weather.isDay, Modifier.size(28.dp), size = 28.dp)
            Text("${weather.temperature}°", style = MaterialTheme.typography.displaySmall, color = CockpitColors.TextPrimary)
            Text(weather.condition, style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextSecondary)
        }
    }
}
