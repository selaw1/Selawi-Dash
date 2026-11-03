package com.selawi.cockpit.features.navigation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.RoundIconButton
import com.selawi.cockpit.core.design.ScreenTitle
import com.selawi.cockpit.core.design.SectionLabel
import com.selawi.cockpit.core.design.Space

/** Full map screen with a recenter button and the phone navigation shortcut. */
@Composable
fun MapScreen(onDriveMode: () -> Unit, onOpenPhoneNav: () -> Unit) {
    val controller = remember { MapController() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(Space.cardRadius)),
        ) {
            CarMap(Modifier.fillMaxSize(), interactive = true, controller = controller)
            RoundIconButton(
                CockpitIcons.MyLocation,
                "Centre on the car",
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.m),
            ) { controller.recenter?.invoke() }
        }
        Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            ScreenTitle("Map")
            CockpitCard(Modifier.fillMaxWidth()) {
                SectionLabel("Navigate with your phone")
                Spacer(Modifier.height(Space.s))
                Text(
                    "Google Maps runs on your phone and shows here through DiAuto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CockpitColors.TextSecondary,
                )
                Spacer(Modifier.height(Space.m))
                PillButton("Drive mode", Modifier.fillMaxWidth(), icon = CockpitIcons.SplitScreen, primary = true, onClick = onDriveMode)
                Spacer(Modifier.height(Space.s))
                PillButton("Open DiAuto", Modifier.fillMaxWidth(), icon = CockpitIcons.Navigation, onClick = onOpenPhoneNav)
            }
        }
    }
}
