package com.selawi.cockpit

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.CockpitTheme
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.core.platform.CrashLog
import com.selawi.cockpit.core.platform.hasLocationPermission
import com.selawi.cockpit.core.platform.openCarHome
import com.selawi.cockpit.core.platform.tryStart
import com.selawi.cockpit.data.local.ThemeMode
import com.selawi.cockpit.features.apps.AppInfo
import com.selawi.cockpit.features.apps.AppsScreen
import com.selawi.cockpit.features.assistant.AssistantScreen
import com.selawi.cockpit.features.dashboard.DashboardScreen
import com.selawi.cockpit.features.dashboard.DashboardState
import com.selawi.cockpit.features.media.MediaControls
import com.selawi.cockpit.features.settings.SettingsActions
import com.selawi.cockpit.features.settings.SettingsScreen
import com.selawi.cockpit.features.settings.SettingsState
import com.selawi.cockpit.features.vehicle.CarModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Apps("Apps", CockpitIcons.Apps),
    Assistant("Assistant", CockpitIcons.Mic),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun CockpitRoot(activity: MainActivity, app: CockpitApplication) {
    val weather by app.weather.weather.collectAsStateWithLifecycle()
    val themeMode by app.settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.Auto)
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = LocalTime.now().hour
        }
    }
    // Auto: the weather service knows sunrise and sunset for the car's position; the clock is the fallback.
    val dark = when (themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.Auto -> weather?.isDay?.not() ?: (hour < 6 || hour >= 18)
    }

    CockpitTheme(dark) {
        var destination by rememberSaveable { mutableStateOf(Destination.Home) }
        val scope = rememberCoroutineScope()
        val playing by app.media.nowPlaying.collectAsStateWithLifecycle()
        val pinned by app.apps.pinned.collectAsStateWithLifecycle()
        val allApps by app.apps.apps.collectAsStateWithLifecycle()
        val driving by app.driving.state.collectAsStateWithLifecycle()
        val fullScreen by app.settings.fullScreen.collectAsStateWithLifecycle(initialValue = true)
        val keepOn by app.settings.keepScreenOn.collectAsStateWithLifecycle(initialValue = false)
        var crash by remember { mutableStateOf(CrashLog.read(activity)) }
        val controls = remember { MediaControls.of(app.media) }

        // Location gives the speed, trip, heading and local weather. The dashboard works without it.
        val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            app.weather.refreshNow()
            app.driving.start()
        }
        LaunchedEffect(Unit) {
            if (!activity.hasLocationPermission()) {
                locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        // The driver's own car model (.glb). Copied into the app so it survives the USB stick being removed.
        val carPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        activity.contentResolver.openInputStream(uri)!!.use { input ->
                            CarModel.customFile(activity).outputStream().use { input.copyTo(it) }
                        }
                    }
                }
            }
        }
        val openApp: (AppInfo) -> Unit = { activity.tryStart(app.apps.launch(it), "${it.label} could not be opened") }

        DashFrame(destination, onSelect = { destination = it }) { compact ->
            AnimatedContent(
                targetState = destination,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "destination",
                modifier = Modifier.fillMaxSize(),
            ) { target ->
                when (target) {
                    Destination.Home -> DashboardScreen(
                        compact = compact,
                        state = DashboardState(weather, playing, driving),
                        controls = controls,
                    )
                    Destination.Apps -> {
                        LaunchedEffect(Unit) { app.apps.refresh() }
                        AppsScreen(
                            apps = allApps,
                            pinned = pinned,
                            onOpen = openApp,
                            onTogglePin = app.apps::togglePinned,
                            onCarHome = { activity.openCarHome() },
                        )
                    }
                    Destination.Assistant -> AssistantScreen()
                    Destination.Settings -> SettingsScreen(
                        state = SettingsState(fullScreen, keepOn, themeMode, crash),
                        actions = SettingsActions(
                            onFullScreen = { scope.launch { app.settings.setFullScreen(it) } },
                            onKeepOn = { scope.launch { app.settings.setKeepScreenOn(it) } },
                            onTheme = { scope.launch { app.settings.setTheme(it) } },
                            onCarHome = { activity.openCarHome() },
                            onChooseCar = { carPicker.launch(arrayOf("*/*")) },
                            onDefaultCar = { CarModel.customFile(activity).delete() },
                            onShareCrash = {
                                crash?.let { text ->
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                    activity.tryStart(Intent.createChooser(send, "Share problem report"), "No app can share text")
                                }
                            },
                            onClearCrash = {
                                CrashLog.clear(activity)
                                crash = null
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The dashboard frame: a soft glow on the background, the content, and the dock along the bottom.
 * Content learns whether the window is narrow (for example in the car's split screen).
 */
@Composable
fun DashFrame(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    content: @Composable (compact: Boolean) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(CockpitColors.Background)
            .background(
                Brush.radialGradient(
                    listOf(CockpitColors.BackgroundGlow, Color.Transparent),
                    center = Offset(900f, 700f),
                    radius = 1500f,
                ),
            )
            .safeDrawingPadding(),
    ) {
        val compact = maxWidth < 840.dp
        Column(Modifier.fillMaxSize().padding(start = Space.l, end = Space.l, top = Space.m)) {
            Box(Modifier.fillMaxWidth().weight(1f)) { content(compact) }
            Dock(selected, compact, onSelect, Modifier.align(Alignment.CenterHorizontally).padding(vertical = Space.m))
        }
    }
}

/** Floating dock with the four places; the assistant sits in the middle as the main action. */
@Composable
private fun Dock(selected: Destination, compact: Boolean, onSelect: (Destination) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(40.dp)
    Row(
        modifier
            .clip(shape)
            .background(CockpitColors.Surface)
            .border(1.dp, CockpitColors.Stroke, shape)
            .padding(horizontal = Space.s, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { d ->
            val active = d == selected
            if (d == Destination.Assistant) {
                Box(
                    Modifier
                        .padding(horizontal = Space.s)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (active) CockpitColors.Accent else CockpitColors.Accent.copy(alpha = 0.85f))
                        .clickable { onSelect(d) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(d.icon, contentDescription = d.label, tint = CockpitColors.OnAccent, modifier = Modifier.size(30.dp))
                }
            } else {
                Row(
                    Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (active) CockpitColors.SurfaceHigh else Color.Transparent)
                        .clickable { onSelect(d) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Icon(
                        d.icon,
                        contentDescription = d.label,
                        tint = if (active) CockpitColors.Accent else CockpitColors.TextSecondary,
                        modifier = Modifier.size(26.dp),
                    )
                    if (!compact) {
                        Text(
                            d.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) CockpitColors.TextPrimary else CockpitColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

