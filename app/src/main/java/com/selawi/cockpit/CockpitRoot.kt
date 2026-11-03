package com.selawi.cockpit

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.CockpitTheme
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.core.platform.hasLocationPermission
import com.selawi.cockpit.features.apps.AppsScreen
import com.selawi.cockpit.features.assistant.AssistantScreen
import com.selawi.cockpit.features.dashboard.DashboardActions
import com.selawi.cockpit.features.dashboard.DashboardScreen
import com.selawi.cockpit.features.media.MediaScreen
import com.selawi.cockpit.features.navigation.MapScreen
import com.selawi.cockpit.features.navigation.PhoneNavigation
import com.selawi.cockpit.features.settings.SettingsScreen

enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Map("Map", CockpitIcons.Map),
    Media("Media", CockpitIcons.MusicNote),
    Apps("Apps", CockpitIcons.Apps),
    Assistant("Assistant", CockpitIcons.Mic),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun CockpitRoot(activity: MainActivity, app: CockpitApplication) {
    CockpitTheme {
        var destination by rememberSaveable { mutableStateOf(Destination.Home) }

        // Location makes the map follow the car and the weather local. The cockpit works without it.
        val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            app.weather.refreshNow()
        }
        LaunchedEffect(Unit) {
            if (!activity.hasLocationPermission()) {
                locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(CockpitColors.Background)
                .safeDrawingPadding(),
        ) {
            // Beside DiAuto in split screen the window is roughly half the screen wide.
            val compact = maxWidth < 840.dp
            Row(Modifier.fillMaxSize()) {
                NavigationRail(destination, compact) { destination = it }
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "destination",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = Space.m, end = Space.m, bottom = Space.m),
                ) { target ->
                    when (target) {
                        Destination.Home -> DashboardScreen(
                            compact = compact,
                            apps = app.apps,
                            media = app.media,
                            weather = app.weather,
                            actions = DashboardActions(
                                onDriveMode = { PhoneNavigation.openBeside(activity) },
                                onOpenMap = { destination = Destination.Map },
                                onOpenApps = { destination = Destination.Apps },
                                onOpenAssistant = { destination = Destination.Assistant },
                            ),
                        )
                        Destination.Map -> MapScreen(
                            onDriveMode = { PhoneNavigation.openBeside(activity) },
                            onOpenPhoneNav = { PhoneNavigation.open(activity) },
                        )
                        Destination.Media -> MediaScreen(app.media)
                        Destination.Apps -> AppsScreen(app.apps)
                        Destination.Assistant -> AssistantScreen()
                        Destination.Settings -> SettingsScreen(app.settings, app.media)
                    }
                }
            }
        }
    }
}

/** Left-hand rail, on the driver's side. Icons only when the window is narrow. */
@Composable
private fun NavigationRail(selected: Destination, compact: Boolean, onSelect: (Destination) -> Unit) {
    Column(
        Modifier
            .width(if (compact) 88.dp else 112.dp)
            .fillMaxHeight()
            .padding(vertical = Space.m, horizontal = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Destination.entries.forEach { d ->
            if (d == Destination.Settings) Spacer(Modifier.weight(1f))
            val active = d == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 72.dp else 84.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) CockpitColors.SurfaceHigh else CockpitColors.Background)
                    .clickable { onSelect(d) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        d.icon,
                        contentDescription = d.label,
                        tint = if (active) CockpitColors.Accent else CockpitColors.TextSecondary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                if (!compact) {
                    Text(
                        d.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) CockpitColors.TextPrimary else CockpitColors.TextTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
