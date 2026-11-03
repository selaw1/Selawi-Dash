package com.selawi.cockpit.features.calls

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.Space
import kotlinx.coroutines.delay

private val CallGreen = Color(0xFF22C55E)

/** Shown while a call rings or runs: a pulsing phone, the call time, and a way to the car's call screen. */
@Composable
fun CallBanner(call: CallInfo, onOpenPhone: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(call) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (call.state == CallState.Ringing) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    CockpitCard(modifier, padding = Space.m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Box(
                Modifier.size(56.dp).scale(pulse).clip(CircleShape).background(CallGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CockpitIcons.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (call.state == CallState.Ringing) "Incoming call" else "On a call",
                    style = MaterialTheme.typography.titleMedium,
                    color = CockpitColors.TextPrimary,
                )
                Text(
                    if (call.state == CallState.Ringing) "Answer on the steering wheel or the car's call screen"
                    else elapsed(now - call.since),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CockpitColors.TextSecondary,
                )
            }
            PillButton("Open phone", icon = CockpitIcons.Phone, onClick = onOpenPhone)
        }
    }
}

private fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
