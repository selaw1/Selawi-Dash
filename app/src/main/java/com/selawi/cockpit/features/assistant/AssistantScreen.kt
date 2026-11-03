package com.selawi.cockpit.features.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.Space

/**
 * Placeholder for the voice assistant (Phase 4). The car has no speech recognition or usable
 * text-to-speech, so the assistant will record audio itself and use cloud speech services
 * through a backend that holds the AI keys.
 */
@Composable
fun AssistantScreen() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(160.dp).clip(CircleShape).background(CockpitColors.AccentDim),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(CockpitColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CockpitIcons.Mic, contentDescription = null, tint = CockpitColors.Background, modifier = Modifier.size(52.dp))
            }
        }
        Text("Assistant", style = MaterialTheme.typography.headlineMedium, color = CockpitColors.TextPrimary)
        Text(
            "Voice conversations, opening apps and controlling music are coming in the next phase.",
            style = MaterialTheme.typography.bodyLarge,
            color = CockpitColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}
