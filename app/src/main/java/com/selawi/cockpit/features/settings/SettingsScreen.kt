package com.selawi.cockpit.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import com.selawi.cockpit.data.local.ThemeMode

class SettingsState(
    val fullScreen: Boolean,
    val keepOn: Boolean,
    val theme: ThemeMode,
    val openBeside: Boolean,
    val musicAccess: Boolean,
    val crash: String?,
    val phoneStatus: String = "",
    val phoneCode: String = "",
    val phoneAddress: String = "",
)

class SettingsActions(
    val onFullScreen: (Boolean) -> Unit = {},
    val onKeepOn: (Boolean) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onOpenBeside: (Boolean) -> Unit = {},
    val onAllowAccess: () -> Unit = {},
    val onCarHome: () -> Unit = {},
    val onChooseCar: () -> Unit = {},
    val onDefaultCar: () -> Unit = {},
    val onShareCrash: () -> Unit = {},
    val onClearCrash: () -> Unit = {},
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
            SectionLabel("Apps")
            ToggleRow(
                "Open apps beside the cockpit",
                "Music apps, shortcuts and Drive mode open in split screen next to the cockpit when the car allows it.",
                state.openBeside,
                actions.onOpenBeside,
            )
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Phone")
            Spacer(Modifier.height(Space.s))
            Text(state.phoneStatus, style = MaterialTheme.typography.titleMedium, color = CockpitColors.TextPrimary)
            Spacer(Modifier.height(Space.s))
            Text(
                "Install Cockpit Link on your phone, join the same Wi-Fi as the car (the car's hotspot or your " +
                    "phone's), and enter this code once. Google Maps directions from the phone then show on the cockpit's map.",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextSecondary,
            )
            Spacer(Modifier.height(Space.s))
            Text(
                "Pairing code  ${state.phoneCode}",
                style = MaterialTheme.typography.headlineMedium,
                color = CockpitColors.Accent,
            )
            if (state.phoneAddress.isNotBlank()) {
                Text(
                    "Car address (if the phone does not find the car by itself): ${state.phoneAddress}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CockpitColors.TextSecondary,
                )
            }
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Music")
            Spacer(Modifier.height(Space.s))
            Text(
                if (state.musicAccess) "Access to music apps: allowed" else "Access to music apps: not allowed",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.musicAccess) CockpitColors.Accent else CockpitColors.Warning,
            )
            Spacer(Modifier.height(Space.s))
            PillButton("Allow access", onClick = actions.onAllowAccess)
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Car")
            Spacer(Modifier.height(Space.s))
            Text(
                "3D car on the home screen: choose any .glb model you are allowed to use, from the car's storage or a " +
                    "USB stick. It appears the next time you open Home.",
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
                    "Map data © OpenStreetMap contributors, tiles by OpenFreeMap. Weather by Open-Meteo.",
                style = MaterialTheme.typography.bodyMedium,
                color = CockpitColors.TextSecondary,
            )
        }
        Text(
            "Selawi Cockpit ${BuildConfig.VERSION_NAME}",
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
