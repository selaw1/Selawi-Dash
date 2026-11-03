package com.selawi.cockpit.features.dashboard

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.SectionLabel
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.core.platform.tryStart
import com.selawi.cockpit.features.apps.AppTile
import com.selawi.cockpit.features.apps.AppsRepository
import com.selawi.cockpit.features.media.MediaCard
import com.selawi.cockpit.features.media.MediaRepository
import com.selawi.cockpit.features.navigation.FreeMap
import com.selawi.cockpit.features.weather.Weather
import com.selawi.cockpit.features.weather.WeatherRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardActions(
    val onDriveMode: () -> Unit,
    val onOpenMap: () -> Unit,
    val onOpenApps: () -> Unit,
    val onOpenAssistant: () -> Unit,
)

@Composable
fun DashboardScreen(
    compact: Boolean,
    apps: AppsRepository,
    media: MediaRepository,
    weather: WeatherRepository,
    actions: DashboardActions,
) {
    if (compact) CompactDashboard(apps, media, weather, actions) else WideDashboard(apps, media, weather, actions)
}

/** Full screen: time and weather, navigation, media, shortcuts. */
@Composable
private fun WideDashboard(apps: AppsRepository, media: MediaRepository, weather: WeatherRepository, actions: DashboardActions) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            ClockWeatherCard(weather, Modifier.fillMaxWidth().weight(1f))
            NavigationCard(actions, Modifier.fillMaxWidth().weight(1.15f))
        }
        MediaCard(media, Modifier.weight(1.15f).fillMaxHeight())
        Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            ShortcutsCard(apps, actions, Modifier.fillMaxWidth().weight(1f))
            AssistantCard(actions, Modifier.fillMaxWidth())
        }
    }
}

/** Beside DiAuto in split screen: one column with the essentials. */
@Composable
private fun CompactDashboard(apps: AppsRepository, media: MediaRepository, weather: WeatherRepository, actions: DashboardActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ClockWeatherCard(weather, Modifier.fillMaxWidth(), compact = true)
        MediaCard(media, Modifier.fillMaxWidth(), compact = true)
        ShortcutsRow(apps, actions)
        AssistantCard(actions, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ClockWeatherCard(repo: WeatherRepository, modifier: Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val weather by repo.weather.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            // Wake at the start of the next minute instead of polling every second.
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val is24 = DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    val amPm = if (is24) "" else now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

    CockpitCard(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                time,
                style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                color = CockpitColors.TextPrimary,
            )
            if (amPm.isNotEmpty()) {
                Text(
                    amPm,
                    style = MaterialTheme.typography.titleLarge,
                    color = CockpitColors.TextSecondary,
                    modifier = Modifier.padding(start = Space.s, bottom = if (compact) 10.dp else 18.dp),
                )
            }
        }
        Text(date, style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextSecondary)
        if (!compact) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(Space.m))
        WeatherLine(weather)
    }
}

@Composable
private fun WeatherLine(weather: Weather?) {
    if (weather == null) {
        Text("Weather loading…", style = MaterialTheme.typography.bodyLarge, color = CockpitColors.TextTertiary)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Text("${weather.temperature}°", style = MaterialTheme.typography.displayMedium, color = CockpitColors.Accent)
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

@Composable
private fun NavigationCard(actions: DashboardActions, modifier: Modifier) {
    CockpitCard(modifier, padding = 0.dp) {
        Box(Modifier.fillMaxSize()) {
            // Preview only; tap to open the full map.
            FreeMap(Modifier.fillMaxSize(), interactive = false)
            Box(Modifier.fillMaxSize().clickable(onClick = actions.onOpenMap))
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(CockpitColors.Background.copy(alpha = 0.82f))
                    .padding(Space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Navigation")
                    Text(
                        "Google Maps from your phone",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CockpitColors.TextPrimary,
                    )
                }
                PillButton("Drive mode", icon = CockpitIcons.SplitScreen, primary = true, onClick = actions.onDriveMode)
            }
        }
    }
}

@Composable
private fun ShortcutsCard(repo: AppsRepository, actions: DashboardActions, modifier: Modifier) {
    val context = LocalContext.current
    val pinned by repo.pinned.collectAsStateWithLifecycle()
    CockpitCard(modifier) {
        SectionLabel("Shortcuts")
        Spacer(Modifier.height(Space.m))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            modifier = Modifier.weight(1f),
        ) {
            items(pinned, key = { it.key }) { app ->
                AppTile(app, iconSize = 52.dp, onOpen = { context.tryStart(repo.launch(app)) })
            }
            item(key = "add") { AddTile(actions.onOpenApps) }
        }
    }
}

@Composable
private fun ShortcutsRow(repo: AppsRepository, actions: DashboardActions) {
    val context = LocalContext.current
    val pinned by repo.pinned.collectAsStateWithLifecycle()
    CockpitCard(Modifier.fillMaxWidth()) {
        SectionLabel("Shortcuts")
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            pinned.take(3).forEach { app ->
                Box(Modifier.weight(1f)) { AppTile(app, iconSize = 48.dp, onOpen = { context.tryStart(repo.launch(app)) }) }
            }
            Box(Modifier.weight(1f)) { AddTile(actions.onOpenApps) }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CockpitColors.SurfaceHigh)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(CockpitIcons.Apps, contentDescription = null, tint = CockpitColors.TextSecondary, modifier = Modifier.size(36.dp))
        Text("All apps", style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextSecondary)
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
                Icon(CockpitIcons.Mic, contentDescription = null, tint = CockpitColors.Background, modifier = Modifier.size(30.dp))
            }
            Column {
                Text("Assistant", style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextPrimary)
                Text("Tap to talk", style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextSecondary)
            }
        }
    }
}
