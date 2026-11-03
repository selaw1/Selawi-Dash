package com.selawi.cockpit.features.phone

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.Space

/** Next turn from Google Maps on the phone, laid over the cockpit's map. */
@Composable
fun NavCard(guidance: NavGuidance, modifier: Modifier = Modifier) {
    Row(
        modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CockpitColors.Surface.copy(alpha = 0.94f))
            .padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(CockpitColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            val icon = guidance.icon
            if (icon != null) {
                Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(CockpitColors.OnAccent), modifier = Modifier.size(52.dp))
            } else {
                Icon(CockpitIcons.Navigation, contentDescription = null, tint = CockpitColors.OnAccent, modifier = Modifier.size(40.dp))
            }
        }
        Column(Modifier.weight(1f, fill = false)) {
            // Google Maps puts the distance or the instruction first, depending on the step.
            Text(
                guidance.distance,
                style = if (guidance.distance.length <= 12) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = CockpitColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                guidance.instruction,
                style = MaterialTheme.typography.titleMedium,
                color = CockpitColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (guidance.eta.isNotBlank()) {
                Text(guidance.eta, style = MaterialTheme.typography.bodyMedium, color = CockpitColors.TextSecondary, maxLines = 1)
            }
        }
    }
}
