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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    // Accent: the saved colour, or the live one while the driver drags on the colour wheel.
    val savedAccent by app.settings.accent.collectAsStateWithLifecycle(initialValue = null)
    var previewing by remember { mutableStateOf(false) }
    var previewAccent by remember { mutableStateOf<Color?>(null) }
    val accent = if (previewing) previewAccent else savedAccent?.let { Color(it) }

    CockpitTheme(dark, accent) {
        var destination by rememberSaveable { mutableStateOf(Destination.Home) }
        val scope = rememberCoroutineScope()
        val playing by app.media.nowPlaying.collectAsStateWithLifecycle()
        val pinned by app.apps.pinned.collectAsStateWithLifecycle()
        val allApps by app.apps.apps.collectAsStateWithLifecycle()
        val driving by app.driving.state.collectAsStateWithLifecycle()
        val call by app.calls.call.collectAsStateWithLifecycle()
        val coverStatus by app.media.coverStatus.collectAsStateWithLifecycle()
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

        DashFrame(destination, onSelect = { destination = it }, pinned = pinned, onOpenApp = openApp) { compact ->
            AnimatedContent(
                targetState = destination,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "destination",
                modifier = Modifier.fillMaxSize(),
            ) { target ->
                when (target) {
                    Destination.Home -> DashboardScreen(
                        compact = compact,
                        state = DashboardState(weather, playing, driving, call),
                        controls = controls,
                        onOpenPhone = app.calls::openPhone,
                    )
                    Destination.Apps -> Box(Modifier.padding(Space.l)) {
                        LaunchedEffect(Unit) { app.apps.refresh() }
                        AppsScreen(
                            apps = allApps,
                            pinned = pinned,
                            onOpen = openApp,
                            onTogglePin = app.apps::togglePinned,
                            onCarHome = { activity.openCarHome() },
                        )
                    }
                    Destination.Assistant -> Box(Modifier.padding(Space.l)) { AssistantScreen() }
                    Destination.Settings -> Box(Modifier.padding(Space.l)) { SettingsScreen(
                        state = SettingsState(fullScreen, keepOn, themeMode, crash, accent, coverStatus),
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
                            onAccent = { color, final ->
                                previewing = true
                                previewAccent = color
                                if (final) scope.launch { app.settings.setAccent(color?.toArgb()) }
                            },
                        ),
                    ) }
                }
            }
        }
    }
}

/**
 * The dashboard frame: the content above a full-width dock, Tesla-style. Content learns whether the
 * window is narrow (for example in the car's split screen).
 */
@Composable
fun DashFrame(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    pinned: List<AppInfo> = emptyList(),
    onOpenApp: (AppInfo) -> Unit = {},
    content: @Composable (compact: Boolean) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(CockpitColors.Background)
            .safeDrawingPadding(),
    ) {
        val compact = maxWidth < 840.dp
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) { content(compact) }
            Dock(selected, compact, onSelect, pinned, onOpenApp)
        }
    }
}

/** Home on the left; pinned apps, the assistant and all apps in the middle; settings on the right. */
@Composable
private fun Dock(
    selected: Destination,
    compact: Boolean,
    onSelect: (Destination) -> Unit,
    pinned: List<AppInfo>,
    onOpenApp: (AppInfo) -> Unit,
) {
    val bar = if (CockpitColors.isDark) Color(0xFF111316) else Color.White
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(CockpitColors.Stroke))
        Row(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(bar)
                .padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockButton(Destination.Home.icon, "Home", selected == Destination.Home) { onSelect(Destination.Home) }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pinned.take(if (compact) 2 else 6).forEach { appInfo ->
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenApp(appInfo) },
                        contentAlignment = Alignment.Center,
                    ) {
                        appInfo.icon?.let { Image(it, contentDescription = appInfo.label, modifier = Modifier.size(52.dp)) }
                    }
                }
                Box(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .size(width = 64.dp, height = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(CockpitColors.Accent)
                        .clickable { onSelect(Destination.Assistant) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CockpitIcons.Mic, contentDescription = "Assistant", tint = CockpitColors.OnAccent, modifier = Modifier.size(26.dp))
                }
                DockButton(CockpitIcons.Apps, "All apps", selected == Destination.Apps) { onSelect(Destination.Apps) }
            }
            DockButton(Destination.Settings.icon, "Settings", selected == Destination.Settings) { onSelect(Destination.Settings) }
        }
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) CockpitColors.AccentDim else CockpitColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) CockpitColors.Accent else CockpitColors.TextPrimary, modifier = Modifier.size(26.dp))
    }
}
