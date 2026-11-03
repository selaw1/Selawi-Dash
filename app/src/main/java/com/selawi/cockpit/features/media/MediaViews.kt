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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
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

/** The cover's main colour, for the card and the background wash; the accent when there is no cover. */
@Composable
fun rememberCoverTint(playing: NowPlaying): Color {
    val tint by animateColorAsState(
        remember(playing.art) { playing.art?.let(::dominantColor) } ?: CockpitColors.Accent,
        tween(900),
        label = "tint",
    )
    return tint
}

/**
 * Now playing, Tesla-style: a large cover beside the source, song, artist, a live progress bar
 * with times, and big controls.
 */
@Composable
fun MediaCard(
    playing: NowPlaying,
    controls: MediaControls,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val tint = rememberCoverTint(playing)
    CockpitCard(modifier, padding = Space.l) {
        Row(Modifier.fillMaxWidth().weight(1f, fill = !compact), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            val coverModifier = if (compact) Modifier.size(120.dp) else
                Modifier.fillMaxHeight().widthIn(max = 280.dp).aspectRatio(1f, matchHeightConstraintsFirst = true)
            Artwork(playing.art, coverModifier.align(Alignment.CenterVertically), tint)
            Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                Text(
                    (appLabel(context, playing.packageName) ?: if (playing.hasTrack) "Now playing" else "Music").uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = CockpitColors.Accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (playing.hasTrack) playing.title else "Nothing playing",
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = CockpitColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (playing.hasTrack) playing.artist else "Play music on your phone or any app",
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    color = CockpitColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
                ProgressBar(playing)
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp)) {
                    RoundIconButton(CockpitIcons.SkipPrevious, "Previous track", size = 56.dp, onClick = controls.previous)
                    RoundIconButton(
                        if (playing.isPlaying) CockpitIcons.Pause else CockpitIcons.Play,
                        if (playing.isPlaying) "Pause" else "Play",
                        size = 72.dp,
                        primary = true,
                        onClick = controls.playPause,
                    )
                    RoundIconButton(CockpitIcons.SkipNext, "Next track", size = 56.dp, onClick = controls.next)
                }
            }
        }
    }
}

@Composable
private fun Artwork(art: ImageBitmap?, modifier: Modifier, tint: Color) {
    val shape = RoundedCornerShape(14.dp)
    AnimatedContent(
        targetState = art,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "art",
        modifier = modifier
            .shadow(if (art != null) 24.dp else 0.dp, shape, ambientColor = tint, spotColor = tint)
            .clip(shape)
            .background(CockpitColors.SurfaceHigh),
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

/** Thin bar with elapsed and total time, moving smoothly between player updates. */
@Composable
private fun ProgressBar(playing: NowPlaying) {
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
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(CockpitColors.TextPrimary))
        }
    }
    if (progress != null) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(clock((progress * playing.durationMs).toLong()), style = MaterialTheme.typography.labelSmall, color = CockpitColors.TextSecondary, modifier = Modifier.weight(1f))
            Text(clock(playing.durationMs), style = MaterialTheme.typography.labelSmall, color = CockpitColors.TextSecondary)
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
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
