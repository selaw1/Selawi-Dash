package com.selawi.cockpit.features.dashboard

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.selawi.cockpit.core.design.RoundIconButton
import com.selawi.cockpit.features.navigation.CarMap
import com.selawi.cockpit.features.navigation.MapController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.features.media.MediaCard
import com.selawi.cockpit.features.media.MediaControls
import com.selawi.cockpit.features.media.NowPlaying
import com.selawi.cockpit.features.vehicle.Car3D
import com.selawi.cockpit.features.vehicle.DrivingState
import com.selawi.cockpit.features.weather.Weather
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardActions(
    val onOpenAssistant: () -> Unit,
)

/** Everything the dashboard shows, gathered by the caller. */
class DashboardState(
    val weather: Weather?,
    val playing: NowPlaying,
    val driving: DrivingState = DrivingState(),
    /** Fixed time for screenshots; null shows the live clock. */
    val fixedTime: LocalDateTime? = null,
)

@Composable
fun DashboardScreen(compact: Boolean, state: DashboardState, controls: MediaControls, actions: DashboardActions) {
    if (compact) CompactDashboard(state, controls, actions) else WideDashboard(state, controls, actions)
}

/** Full screen: the car fills the left; time, weather, music and the assistant stack on the right. */
@Composable
private fun WideDashboard(state: DashboardState, controls: MediaControls, actions: DashboardActions) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        VehicleCard(state.driving, actions, Modifier.weight(1.5f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            TimeWeatherCard(state, Modifier.fillMaxWidth())
            MediaCard(state.playing, controls, Modifier.fillMaxWidth().weight(1f))
            AssistantCard(actions, Modifier.fillMaxWidth())
        }
    }
}

/** Beside DiAuto in split screen: the player first, as Android Auto does. */
@Composable
private fun CompactDashboard(state: DashboardState, controls: MediaControls, actions: DashboardActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        TimeWeatherCard(state, Modifier.fillMaxWidth())
        MediaCard(state.playing, controls, Modifier.fillMaxWidth(), compact = true)
        AssistantCard(actions, Modifier.fillMaxWidth())
    }
}

/** Time, date and weather in one strip. */
@Composable
private fun TimeWeatherCard(state: DashboardState, modifier: Modifier) {
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
    val weather = state.weather

    CockpitCard(modifier, padding = Space.l) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
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
                Text(date, style = MaterialTheme.typography.bodyLarge, color = CockpitColors.TextSecondary)
            }
            if (weather != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${weather.temperature}°", style = MaterialTheme.typography.displaySmall, color = CockpitColors.Accent)
                    Text(weather.condition, style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextPrimary)
                    Text(
                        "H ${weather.high}°  L ${weather.low}°",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CockpitColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/**
 * The big left card: the 3D car, or the live map. Map swaps the car for the map in place;
 * Front/Back turns the parked car round.
 */
@Composable
private fun VehicleCard(driving: DrivingState, actions: DashboardActions, modifier: Modifier) {
    val shape = RoundedCornerShape(Space.cardRadius)
    var showMap by rememberSaveable { mutableStateOf(false) }
    var showFront by rememberSaveable { mutableStateOf(true) }
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(CockpitColors.Surface, CockpitColors.BackgroundGlow, CockpitColors.Surface)),
            )
            .border(1.dp, CockpitColors.Stroke, shape),
    ) {
        Crossfade(targetState = showMap, animationSpec = tween(350), label = "car-or-map") { map ->
            if (map) {
                val controller = remember { MapController() }
                Box(Modifier.fillMaxSize()) {
                    CarMap(Modifier.fillMaxSize(), interactive = true, controller = controller)
                    RoundIconButton(
                        CockpitIcons.MyLocation,
                        "Centre on the car",
                        modifier = Modifier.align(Alignment.TopEnd).padding(Space.l),
                    ) { controller.recenter?.invoke() }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    Car3D(driving, Modifier.fillMaxSize(), showFront = showFront)
                    if (!driving.driving) {
                        PillButton(
                            if (showFront) "Back" else "Front",
                            modifier = Modifier.align(Alignment.TopEnd).padding(Space.l),
                            icon = Icons.Filled.Refresh,
                        ) { showFront = !showFront }
                    }
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                if (showMap) "Car" else "Map",
                icon = if (showMap) CockpitIcons.Navigation else CockpitIcons.Map,
                primary = true,
            ) { showMap = !showMap }
        }
    }
}

@Composable
private fun AssistantCard(actions: DashboardActions, modifier: Modifier) {
    CockpitCard(modifier, onClick = actions.onOpenAssistant, padding = Space.m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Box(
                Modifier.size(Space.touch).clip(CircleShape).background(CockpitColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CockpitIcons.Mic, contentDescription = null, tint = CockpitColors.OnAccent, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Assistant", style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextPrimary)
                Text("Tap to talk", style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextSecondary)
            }
            Spacer(Modifier.width(Space.s))
        }
    }
}

