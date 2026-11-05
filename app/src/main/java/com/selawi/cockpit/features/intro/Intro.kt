package com.selawi.cockpit.features.intro

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selawi.cockpit.R
import com.selawi.cockpit.core.design.CockpitColors
import kotlinx.coroutines.launch

/**
 * The opening moment, once each time the app starts: the logo glows up on black, the dashboard
 * fades in behind it, and the 3D car's headlights flick on.
 */
object Intro {
    /** When the headlights start their flick, or 0 when no intro ran (lights simply on). */
    @Volatile var lightsAt = 0L
    private var played = false

    /** True only the first time it is asked in this run of the app. */
    fun claim(): Boolean {
        if (played) return false
        played = true
        lightsAt = SystemClock.elapsedRealtime() + 1_300
        return true
    }

    /** 0 … 1: headlight brightness now: off, two quick flickers, then a smooth rise to full. */
    fun headlights(now: Long = SystemClock.elapsedRealtime()): Float {
        if (lightsAt == 0L) return 1f
        val t = (now - lightsAt) / 1000f
        return when {
            t < 0f -> 0f
            t < 0.08f -> 0.9f
            t < 0.2f -> 0.08f
            t < 0.3f -> 1f
            t < 0.42f -> 0.2f
            t < 0.9f -> 0.2f + (t - 0.42f) / 0.48f * 0.8f
            else -> 1f
        }
    }
}

@Composable
fun StartupIntro() {
    var show by remember { mutableStateOf(Intro.claim()) }
    if (!show) return
    val logo = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val scale = remember { Animatable(0.84f) }
    val cover = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(1_300, easing = FastOutSlowInEasing)) }
        logo.animateTo(1f, tween(450))
        glow.animateTo(1f, tween(450))
        glow.animateTo(0.55f, tween(350))
        cover.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        show = false
    }
    val accent = CockpitColors.Accent
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = cover.value }
            .background(Color(0xFF0B0B0D))
            // Swallows touches while it shows.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(360.dp)
                        .graphicsLayer { alpha = glow.value }
                        .drawBehind {
                            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.12f), Color.Transparent)))
                        },
                )
                Image(
                    painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(260.dp)
                        .graphicsLayer {
                            alpha = logo.value
                            scaleX = scale.value
                            scaleY = scale.value
                        },
                )
            }
            Text(
                "SELAWI DASH",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 8.sp),
                color = Color.White.copy(alpha = 0.85f),
                // The logo image has empty space around the mark; pull the name up under it.
                modifier = Modifier.offset(y = (-96).dp).graphicsLayer { alpha = glow.value },
            )
        }
    }
}
