package com.selawi.cockpit.features.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.features.settings.AccentPicker
import com.selawi.cockpit.features.settings.CarPaintPicker
import kotlin.math.PI
import kotlin.math.sin

/** Which colour picker is on the back of the music card, if any. */
enum class ColourPanel { None, Car, Accent }

/**
 * The music card, turning over like a real card to show a colour picker on its back. The card
 * lifts a little as it turns (it shrinks slightly at the halfway point) and settles on the other
 * side; Done turns it back. Switching between the car and accent pickers while it is open
 * cross-fades the back instead of flipping again.
 */
@Composable
fun FlipCard(
    panel: ColourPanel,
    carPaint: String,
    accent: Color?,
    actions: QuickActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        if (panel == ColourPanel.None) 0f else 180f,
        tween(720, easing = FastOutSlowInEasing),
        label = "flip",
    )
    // Keep showing the last picker while the card turns back to the music.
    var last by remember { mutableStateOf(ColourPanel.Car) }
    LaunchedEffect(panel) { if (panel != ColourPanel.None) last = panel }
    val back = if (panel != ColourPanel.None) panel else last

    Box(
        modifier.graphicsLayer {
            rotationY = rotation
            // A long lens keeps the turn looking flat and natural rather than stretched.
            cameraDistance = 16f * density
            val lift = sin(rotation / 180f * PI.toFloat())
            scaleX = 1f - 0.07f * lift
            scaleY = 1f - 0.07f * lift
        },
    ) {
        if (rotation <= 90f) {
            front()
        } else {
            // Turned round once more, so the back reads the right way and not mirrored.
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                ColourCard(back, carPaint, accent, actions, onClose)
            }
        }
    }
}

@Composable
private fun ColourCard(panel: ColourPanel, carPaint: String, accent: Color?, actions: QuickActions, onClose: () -> Unit) {
    CockpitCard(Modifier.fillMaxSize(), padding = Space.l) {
        AnimatedContent(
            targetState = panel,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "picker",
            modifier = Modifier.fillMaxSize(),
        ) { shown ->
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (shown == ColourPanel.Accent) "Accent colour" else "Car colour",
                        style = MaterialTheme.typography.titleLarge,
                        color = CockpitColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton("Done", primary = true, onClick = onClose)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (shown == ColourPanel.Accent) {
                        AccentPicker(accent, actions.onAccent)
                    } else {
                        CarPaintPicker(carPaint, actions.onCarPaint, preview = false)
                    }
                }
            }
        }
    }
}
