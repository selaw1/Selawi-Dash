package com.selawi.cockpit.features.dashboard

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.features.media.MediaCard
import com.selawi.cockpit.features.media.MediaControls
import com.selawi.cockpit.features.media.NowPlaying
import com.selawi.cockpit.features.vehicle.Car3D
import com.selawi.cockpit.features.vehicle.DrivingState
import com.selawi.cockpit.features.vehicle.SpeedCard
import com.selawi.cockpit.features.vehicle.TripCard
import com.selawi.cockpit.features.weather.SkyIcon
import com.selawi.cockpit.features.weather.Weather
import com.selawi.cockpit.features.weather.WeatherCard
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the dashboard shows, gathered by the caller. */
class DashboardState(
    val weather: Weather?,
    val playing: NowPlaying,
    val driving: DrivingState = DrivingState(),
    /** Fixed time for screenshots; null shows the live clock. */
    val fixedTime: LocalDateTime? = null,
)

@Composable
fun DashboardScreen(compact: Boolean, state: DashboardState, controls: MediaControls) {
    if (compact) CompactDashboard(state, controls) else WideDashboard(state, controls)
}

/**
 * Full screen: the car in the middle, driving data on the left, music and weather on the right,
 * time and conditions across the top.
 */
@Composable
private fun WideDashboard(state: DashboardState, controls: MediaControls) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        TopBar(state, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Column(Modifier.width(272.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                SpeedCard(state.driving, Modifier.fillMaxWidth().weight(1f))
                TripCard(state.driving.trip, Modifier.fillMaxWidth())
            }
            Car3D(state.driving, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                MediaCard(state.playing, controls, Modifier.fillMaxWidth().weight(1f))
                WeatherCard(state.weather, Modifier.fillMaxWidth(), is24)
            }
        }
    }
}

/** A narrow window (for example in the car's split screen): one scrolling column. */
@Composable
private fun CompactDashboard(state: DashboardState, controls: MediaControls) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        TopBar(state, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            SpeedCard(state.driving, Modifier.weight(1f).fillMaxHeight())
            TripCard(state.driving.trip, Modifier.weight(1f).fillMaxHeight())
        }
        Car3D(state.driving, Modifier.fillMaxWidth().height(260.dp))
        MediaCard(state.playing, controls, Modifier.fillMaxWidth(), compact = true)
        WeatherCard(state.weather, Modifier.fillMaxWidth(), is24)
    }
}

/** Greeting, time and date on the left; current weather on the right. No card: it sits on the background. */
@Composable
private fun TopBar(state: DashboardState, modifier: Modifier) {
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
    val amPm = if (is24) "" else now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    val weather = state.weather

    Row(modifier.padding(horizontal = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = MaterialTheme.typography.displayMedium, color = CockpitColors.TextPrimary)
            if (amPm.isNotEmpty()) {
                Text(
                    amPm,
                    style = MaterialTheme.typography.titleMedium,
                    color = CockpitColors.TextSecondary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                )
            }
        }
        Column(Modifier.padding(start = Space.l).weight(1f)) {
            Text(greeting.uppercase(), style = MaterialTheme.typography.labelMedium, color = CockpitColors.Accent)
            Text(date, style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextSecondary)
        }
        if (weather != null) {
            SkyIcon(weather.sky, weather.isDay, Modifier.size(48.dp), size = 48.dp)
            Text(
                "${weather.temperature}°",
                style = MaterialTheme.typography.displaySmall,
                color = CockpitColors.TextPrimary,
                modifier = Modifier.padding(horizontal = Space.s),
            )
            Column {
                Text(weather.condition, style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextPrimary)
                Text(
                    "${weather.place} · H ${weather.high}°  L ${weather.low}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CockpitColors.TextSecondary,
                )
            }
        }
    }
}
