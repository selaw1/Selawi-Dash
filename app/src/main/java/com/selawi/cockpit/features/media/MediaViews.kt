package com.selawi.cockpit.features.media

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.RoundIconButton
import com.selawi.cockpit.core.design.SectionLabel
import com.selawi.cockpit.core.design.Space
import kotlinx.coroutines.delay

/** The three media actions, so screens do not depend on where playback is controlled. */
class MediaControls(
    val previous: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
) {
    companion object {
        fun of(repo: MediaRepository) = MediaControls(repo::previous, repo::playPause, repo::next)
        val None = MediaControls({}, {}, {})
    }
}

/**
 * Now playing: cover, track, a live progress bar and the controls. The card glows in the cover's
 * main colour, so the dashboard takes on the mood of the music.
 */
@Composable
fun MediaCard(
    playing: NowPlaying,
    controls: MediaControls,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val tint by animateColorAsState(
        remember(playing.art) { playing.art?.let(::dominantColor) } ?: CockpitColors.Accent,
        tween(800),
        label = "tint",
    )
    CockpitCard(
        modifier.drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(tint.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.2f, 0f),
                    radius = size.maxDimension,
                ),
            )
        },
    ) {
        SectionLabel(if (playing.hasTrack) appLabel(context, playing.packageName) ?: "Now playing" else "Music")
        Spacer(Modifier.height(Space.m))
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Artwork(playing.art, Modifier.size(88.dp))
                TrackText(playing, Modifier.weight(1f))
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Artwork(playing.art, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                TrackText(playing, Modifier.weight(1f).fillMaxHeight(), bottom = true)
            }
        }
        Spacer(Modifier.height(Space.m))
        ProgressBar(playing, tint)
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(CockpitIcons.SkipPrevious, "Previous track", onClick = controls.previous)
            RoundIconButton(
                if (playing.isPlaying) CockpitIcons.Pause else CockpitIcons.Play,
                if (playing.isPlaying) "Pause" else "Play",
                size = 76.dp,
                primary = true,
                onClick = controls.playPause,
            )
            RoundIconButton(CockpitIcons.SkipNext, "Next track", onClick = controls.next)
        }
    }
}

@Composable
private fun Artwork(art: ImageBitmap?, modifier: Modifier) {
    AnimatedContent(
        targetState = art,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "art",
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(CockpitColors.SurfaceHigh),
    ) { image ->
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(CockpitIcons.MusicNote, null, tint = CockpitColors.TextTertiary, modifier = Modifier.size(44.dp))
            }
        }
    }
}

@Composable
private fun TrackText(playing: NowPlaying, modifier: Modifier, bottom: Boolean = false) {
    Column(modifier, verticalArrangement = if (bottom) Arrangement.Bottom else Arrangement.Center) {
        Text(
            if (playing.hasTrack) playing.title else "Nothing playing",
            style = MaterialTheme.typography.titleLarge,
            color = CockpitColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (playing.hasTrack) playing.artist else "Play music from your phone or any app",
            style = MaterialTheme.typography.bodyLarge,
            color = CockpitColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Thin bar that moves smoothly between player updates. Hidden when the player gives no position. */
@Composable
private fun ProgressBar(playing: NowPlaying, tint: Color) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playing.isPlaying) {
        while (playing.isPlaying) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val progress = playing.progress(now)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(CockpitColors.SurfaceHigh),
    ) {
        if (progress != null) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(tint))
        }
    }
}

/** Average colour of the cover, made a little brighter and richer so it reads as a glow. */
private fun dominantColor(art: ImageBitmap): Color? = runCatching {
    val small = android.graphics.Bitmap.createScaledBitmap(art.asAndroidBitmap(), 1, 1, true)
    val pixel = small.getPixel(0, 0)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(pixel, hsv)
    hsv[1] = (hsv[1] * 1.4f).coerceIn(0.35f, 1f)
    hsv[2] = hsv[2].coerceIn(0.65f, 1f)
    Color(android.graphics.Color.HSVToColor(hsv))
}.getOrNull()

fun appLabel(context: Context, packageName: String?): String? = packageName?.let {
    runCatching {
        val pm = context.packageManager
        val label = pm.getApplicationInfo(it, 0).loadLabel(pm).toString()
        label.takeIf { name -> name != it }
    }.getOrNull()
}
