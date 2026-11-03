package com.selawi.cockpit.features.media

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.RoundIconButton
import com.selawi.cockpit.core.design.ScreenTitle
import com.selawi.cockpit.core.design.SectionLabel
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.core.platform.tryStart

/** Dashboard media panel: artwork, track and the three main controls. */
@Composable
fun MediaCard(repo: MediaRepository, modifier: Modifier = Modifier, compact: Boolean = false) {
    val playing by repo.nowPlaying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CockpitCard(modifier) {
        SectionLabel(if (playing.hasTrack) appLabel(context, playing.packageName) ?: "Now playing" else "Media")
        Spacer(Modifier.height(Space.m))
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Artwork(playing, Modifier.size(96.dp))
                TrackText(playing, Modifier.weight(1f))
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Artwork(playing, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
            }
            Spacer(Modifier.height(Space.m))
            TrackText(playing, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Space.m))
        Controls(repo, playing.isPlaying, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Artwork(playing: NowPlaying, modifier: Modifier) {
    AnimatedContent(
        targetState = playing.art,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "art",
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(CockpitColors.SurfaceHigh),
    ) { art ->
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(CockpitIcons.MusicNote, null, tint = CockpitColors.TextTertiary, modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Composable
private fun TrackText(playing: NowPlaying, modifier: Modifier) {
    Column(modifier) {
        Text(
            if (playing.hasTrack) playing.title else "Nothing playing",
            style = MaterialTheme.typography.titleLarge,
            color = CockpitColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (playing.hasTrack) playing.artist else "Start music in any app; the buttons below control it.",
            style = MaterialTheme.typography.bodyLarge,
            color = CockpitColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Controls(repo: MediaRepository, isPlaying: Boolean, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(CockpitIcons.SkipPrevious, "Previous track") { repo.previous() }
        RoundIconButton(
            if (isPlaying) CockpitIcons.Pause else CockpitIcons.Play,
            if (isPlaying) "Pause" else "Play",
            size = 80.dp,
            primary = true,
        ) { repo.playPause() }
        RoundIconButton(CockpitIcons.SkipNext, "Next track") { repo.next() }
    }
}

/** Full media screen: large player plus how to get track details on this car. */
@Composable
fun MediaScreen(repo: MediaRepository) {
    val context = LocalContext.current
    val access by repo.sessionAccess.collectAsStateWithLifecycle()
    val playing by repo.nowPlaying.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        MediaCard(repo, Modifier.weight(1f).fillMaxHeight())
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            ScreenTitle("Media")
            CockpitCard {
                SectionLabel("Track details")
                Spacer(Modifier.height(Space.s))
                val status = when {
                    access -> "Reading every music app directly."
                    playing.source == MediaSource.Broadcast -> "Reading the track from the app's broadcast."
                    else -> "Controls work. Song name and artwork need one of the options below."
                }
                Text(status, style = MaterialTheme.typography.bodyLarge, color = CockpitColors.TextPrimary)
                if (!access) {
                    Spacer(Modifier.height(Space.m))
                    PillButton("Allow access to music apps") { NotificationAccess.open(context) }
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "Spotify: turn on Settings → Device Broadcast Status. The cockpit then shows the " +
                            "song and cover without any permission.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CockpitColors.TextSecondary,
                    )
                }
            }
            CockpitCard {
                SectionLabel("Open a music app")
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    MUSIC_APPS.forEach { (label, pkg) ->
                        val launch = remember(pkg) { context.packageManager.getLaunchIntentForPackage(pkg) }
                        if (launch != null) {
                            PillButton(label, Modifier.widthIn(min = 120.dp)) {
                                context.tryStart(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val MUSIC_APPS = listOf(
    "Spotify" to "com.spotify.music",
    "SoundCloud" to "com.soundcloud.android",
    "YouTube" to "app.revanced.android.youtube",
)

fun appLabel(context: Context, packageName: String?): String? = packageName?.let {
    runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(it, 0).loadLabel(pm).toString()
    }.getOrNull()
}
