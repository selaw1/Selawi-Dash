package com.selawi.cockpit.probe.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.probe.MainActivity

@Composable
fun ScreenTestScreen(activity: MainActivity) {
    var immersive by rememberSaveable { mutableStateOf(false) }
    var keepOn by rememberSaveable { mutableStateOf(false) }
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val bars = WindowInsets.systemBars

    Page("Screen and full screen") {
        Note("Full screen hides the car's status and navigation bars. Swipe from the edge to bring them back for a moment.")
        ButtonRow {
            BigButton(if (immersive) "Exit full screen" else "Go full screen") {
                immersive = !immersive
                activity.setFullScreen(immersive)
            }
            BigButton(if (keepOn) "Keep screen on: ON" else "Keep screen on: OFF") {
                keepOn = !keepOn
                activity.setKeepScreenOn(keepOn)
            }
        }
        InfoLine("Window size", "${config.screenWidthDp} x ${config.screenHeightDp} dp")
        InfoLine("Density", "${density.density}x")
        InfoLine(
            "System bars (px)",
            "left ${bars.getLeft(density, dir)}, top ${bars.getTop(density)}, " +
                "right ${bars.getRight(density, dir)}, bottom ${bars.getBottom(density)}",
        )
        InfoLine("Split screen", if (activity.isInMultiWindowMode) "yes" else "no")
        InfoLine("Picture-in-picture", if (activity.isInPictureInPictureMode) "yes" else "no")

        FrameMeter()
    }
}

/** Shows how smoothly Compose animates on this head unit. */
@Composable
private fun FrameMeter() {
    var fps by remember { mutableIntStateOf(0) }
    var worstMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var windowStart = 0L
        var last = 0L
        var worst = 0L
        while (true) {
            withFrameNanos { t ->
                if (windowStart == 0L) windowStart = t
                if (last != 0L) worst = maxOf(worst, t - last)
                last = t
                frames++
                if (t - windowStart >= 1_000_000_000L) {
                    fps = frames
                    worstMs = (worst / 1_000_000).toInt()
                    frames = 0
                    worst = 0
                    windowStart = t
                }
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "meter")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "x",
    )
    InfoLine("Animation", "$fps fps, slowest frame $worstMs ms", if (fps >= 50) Good else if (fps >= 30) Accent else Bad)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Background),
    ) {
        Box(
            Modifier
                .offset(x = (x * 600).dp)
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Accent),
        )
    }
}
