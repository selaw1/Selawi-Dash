package com.selawi.cockpit.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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

@Composable
fun SettingsScreen(
    fullScreen: Boolean,
    keepOn: Boolean,
    access: Boolean,
    onFullScreen: (Boolean) -> Unit,
    onKeepOn: (Boolean) -> Unit,
    onAllowAccess: () -> Unit,
    onCarHome: () -> Unit,
) {

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ScreenTitle("Settings")
        CockpitCard(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
            SectionLabel("Display")
            ToggleRow(
                "Full screen",
                "Hide the car's status and climate bars. Swipe from the bottom edge to show them for a moment.",
                fullScreen,
                onFullScreen,
            )
            ToggleRow("Keep screen on", "Stop the screen from dimming while the cockpit is open.", keepOn, onKeepOn)
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Music")
            Spacer(Modifier.height(Space.s))
            Text(
                if (access) "Access to music apps: allowed" else "Access to music apps: not allowed",
                style = MaterialTheme.typography.titleMedium,
                color = if (access) CockpitColors.Accent else CockpitColors.Warning,
            )
            Spacer(Modifier.height(Space.s))
            PillButton("Allow access", onClick = onAllowAccess)
        }
        CockpitCard(Modifier.fillMaxWidth()) {
            SectionLabel("Car")
            Spacer(Modifier.height(Space.s))
            PillButton("Open BYD home", onClick = onCarHome)
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
