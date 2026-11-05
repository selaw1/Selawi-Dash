package com.selawi.cockpit.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.BuildConfig
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.ScreenTitle
import com.selawi.cockpit.core.design.SectionLabel
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.data.local.Backdrop
import com.selawi.cockpit.data.local.ThemeMode
import com.selawi.cockpit.features.dashboard.drawBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind

class SettingsState(
    val fullScreen: Boolean,
    val keepOn: Boolean,
    val theme: ThemeMode,
    val crash: String?,
    val accent: androidx.compose.ui.graphics.Color? = null,
    val coverStatus: String = "",
    val motionSource: String = "",
    val backdrop: Backdrop = Backdrop.CoverGlow,
)

class SettingsActions(
    val onFullScreen: (Boolean) -> Unit = {},
    val onKeepOn: (Boolean) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onCarHome: () -> Unit = {},
    val onChooseCar: () -> Unit = {},
    val onDefaultCar: () -> Unit = {},
    val onShareCrash: () -> Unit = {},
    val onClearCrash: () -> Unit = {},
    val onAccent: (androidx.compose.ui.graphics.Color?, Boolean) -> Unit = { _, _ -> },
    val onBackdrop: (Backdrop) -> Unit = {},
)

@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenTitle("Settings")
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Appearance")
            Spacer(Modifier.height(Space.s))
            Text(
                "Auto switches to the light theme by day and the dark theme at night, like Google Maps.",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextSecondary,
            )
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ThemeMode.entries.forEach { mode ->
                    PillButton(mode.name, primary = mode == state.theme) { actions.onTheme(mode) }
                }
            }
            ToggleRow(
                "Full screen",
                "Hide the car's status and climate bars. Swipe from the bottom edge to show them for a moment.",
                state.fullScreen,
                actions.onFullScreen,
            )
            ToggleRow("Keep screen on", "Stop the screen from dimming while the cockpit is open.", state.keepOn, actions.onKeepOn)
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Accent colour")
            Spacer(Modifier.height(Space.s))
            Text(
                "Pick any colour on the wheel; the whole dashboard follows. Brightness is tuned for day and night so it stays readable.",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextSecondary,
            )
            Spacer(Modifier.height(Space.m))
            AccentPicker(state.accent, actions.onAccent)
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Background")
            Spacer(Modifier.height(Space.s))
            Text(
                "Behind the home screen. Cover glow follows the colours of the song that is playing.",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextSecondary,
            )
            Spacer(Modifier.height(Space.m))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Backdrop.entries.forEach { b ->
                    BackdropSwatch(b, selected = b == state.backdrop) { actions.onBackdrop(b) }
                }
            }
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Car")
            Spacer(Modifier.height(Space.s))
            Text(
                "3D car on the home screen: choose any .glb model you are allowed to use, from the car's storage or a " +
                    "USB stick. It appears the next time you open Home. Drag the car to turn it; double-tap to reset.",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextSecondary,
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PillButton("Choose car model", onClick = actions.onChooseCar)
                PillButton("Use default car", onClick = actions.onDefaultCar)
                PillButton("Open BYD home", onClick = actions.onCarHome)
            }
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Problem report")
            Spacer(Modifier.height(Space.s))
            Text(
                "Last cover: ${state.coverStatus}",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextSecondary,
            )
            if (state.motionSource.isNotEmpty()) {
                Text(
                    "Braking detection: ${state.motionSource}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CockpitColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(Space.s))
            if (state.crash == null) {
                Text("No crashes recorded.", style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextSecondary)
            } else {
                Text(
                    state.crash.lineSequence().take(6).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CockpitColors.Warning,
                )
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    PillButton("Share report", primary = true, onClick = actions.onShareCrash)
                    PillButton("Clear", onClick = actions.onClearCrash)
                }
            }
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Credits")
            Spacer(Modifier.height(Space.s))
            Text(
                "Default 3D car: \"Car Concept\" from the Khronos glTF Sample Assets, © 2024 Darmstadt Graphics Group GmbH, " +
                    "model and textures by Eric Chadwick, licensed CC BY 4.0. Logos removed. " +
                    "Weather by Open-Meteo. Fonts: Space Grotesk and Inter, SIL Open Font License 1.1.",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextSecondary,
            )
        }
        Text(
            "Selawi Dash ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = CockpitColors.TextTertiary,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(88.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = CockpitColors.Accent),
        )
    }
}

/** A small preview of a background, outlined in the accent when chosen. */
@Composable
private fun BackdropSwatch(backdrop: Backdrop, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val accent = CockpitColors.Accent
    val strength = if (CockpitColors.isDark) 0.55f else 0.25f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(width = 112.dp, height = 64.dp)
                .clip(shape)
                .background(CockpitColors.Background)
                .drawBehind { drawBackdrop(backdrop, SampleCoverTint, accent, strength) }
                .border(if (selected) 3.dp else 1.dp, if (selected) accent else CockpitColors.Stroke, shape)
                .clickable(onClick = onClick),
        )
        Text(
            backdrop.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) CockpitColors.TextPrimary else CockpitColors.TextSecondary,
        )
    }
}

/** Stands in for a song's cover colour in the Cover glow preview. */
private val SampleCoverTint = androidx.compose.ui.graphics.Color(0xFFE0457B)
