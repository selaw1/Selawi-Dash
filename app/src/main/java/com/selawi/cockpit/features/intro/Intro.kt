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
import kotlinx.coroutines.delay
import com.selawi.cockpit.features.weather.Weather
import java.time.LocalTime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

/**
 * The opening moment, once each time the app starts: the logo glows up on black, the dashboard
 * fades in behind it, and the 3D car's headlights flick on.
 */
object Intro {
    /** When the headlights start their flick, or 0 when no intro ran (lights simply on). */
    @Volatile var lightsAt = 0L
    /** When the 3D car starts swinging round from the back to the front. */
    @Volatile var swingAt = 0L
    private var played = false

    /** True only the first time it is asked in this run of the app. */
    fun claim(): Boolean {
        if (played) return false
        played = true
        val now = SystemClock.elapsedRealtime()
        lightsAt = now + FADE_AT_MS + 500
        swingAt = now + FADE_AT_MS - 300
        return true
    }

    /** When the intro starts fading to the dashboard: logo, greeting, a moment to read. */
    const val FADE_AT_MS = 2_400L

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
fun StartupIntro(weather: Weather? = null) {
    var show by remember { mutableStateOf(Intro.claim()) }
    if (!show) return
    val logo = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val scale = remember { Animatable(0.84f) }
    val cover = remember { Animatable(1f) }
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val start = SystemClock.elapsedRealtime()
        launch { scale.animateTo(1f, tween(1_500, easing = FastOutSlowInEasing)) }
        logo.animateTo(1f, tween(450))
        launch { glow.animateTo(1f, tween(450)); glow.animateTo(0.55f, tween(500)) }
        words.animateTo(1f, tween(500))
        // Hold so the greeting can be read, then fade to the dashboard.
        delay((Intro.FADE_AT_MS - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0))
        cover.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
        show = false
    }
    val greeting = remember { greeting(LocalTime.now().hour) }
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
            // The logo image has empty space around the mark; pull the words up under it.
            Column(
                Modifier.offset(y = (-96).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "SELAWI DASH",
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 8.sp),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.graphicsLayer { alpha = logo.value },
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.graphicsLayer {
                        alpha = words.value
                        translationY = (1f - words.value) * 12.dp.toPx()
                    },
                )
                if (weather != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${weather.temperature}° · ${weather.condition}" + (if (weather.place.isNotBlank() && weather.place != "Here") " in ${weather.place}" else ""),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.graphicsLayer {
                            alpha = words.value
                            translationY = (1f - words.value) * 12.dp.toPx()
                        },
                    )
                }
            }
        }
    }
}

private fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
