package com.selawi.cockpit.probe.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.selawi.cockpit.probe.MediaListenerService
import com.selawi.cockpit.probe.tryStart
import kotlinx.coroutines.delay

private class Playing(
    val controller: MediaController,
    val app: String,
    val title: String,
    val artist: String,
    val state: String,
    val art: ImageBitmap?,
)

@Composable
fun MediaScreen() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf<List<Playing>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            allowed = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            if (allowed) {
                runCatching { readSessions(context) }
                    .onSuccess { sessions = it; error = null }
                    .onFailure { error = "${it.javaClass.simpleName}: ${it.message}" }
            }
            delay(1000)
        }
    }

    Page("Media") {
        Text("Media keys (no permission needed)", color = Accent, fontSize = 18.sp)
        Note("Start Spotify or another player, come back here and try these.")
        ButtonRow {
            BigButton("Previous") { mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            BigButton("Play / pause") { mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            BigButton("Next") { mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT) }
        }

        Text("Now playing (needs notification access)", color = Accent, fontSize = 18.sp)
        InfoLine("Notification access", if (allowed) "allowed" else "not allowed", if (allowed) Good else Bad)
        if (!allowed) {
            ButtonRow {
                BigButton("Allow access") {
                    context.tryStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "This car has no notification access screen")
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    BigButton("Allow access (direct)") {
                        context.tryStart(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                ComponentName(context, MediaListenerService::class.java).flattenToString(),
                            ),
                            "This car has no notification access screen",
                        )
                    }
                }
            }
            Note(
                "If the car shows \"not supported\" or nothing happens, note it in the report. " +
                    "It can also be allowed once over ADB: adb shell cmd notification allow_listener " +
                    "com.selawi.cockpit.probe/com.selawi.cockpit.probe.MediaListenerService",
            )
        }
        error?.let { Note("Error: $it") }
        if (allowed && sessions.isEmpty()) Note("No app is playing right now.")
        sessions.forEach { SessionCard(it) }
    }
}

@Composable
private fun SessionCard(p: Playing) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelHigh)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(Background)) {
            p.art?.let { Image(it, contentDescription = null, modifier = Modifier.size(120.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(p.artist, color = Muted, fontSize = 18.sp)
            Text("${p.app} · ${p.state}", color = Muted, fontSize = 14.sp)
            ButtonRow {
                BigButton("Prev") { p.controller.transportControls.skipToPrevious() }
                BigButton("Play") { p.controller.transportControls.play() }
                BigButton("Pause") { p.controller.transportControls.pause() }
                BigButton("Next") { p.controller.transportControls.skipToNext() }
            }
        }
    }
}

private fun readSessions(context: Context): List<Playing> {
    val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val controllers = msm.getActiveSessions(ComponentName(context, MediaListenerService::class.java))
    return controllers.map { c ->
        val meta = c.metadata
        val art: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        Playing(
            controller = c,
            app = c.packageName,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "(no title)",
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            state = stateName(c.playbackState?.state),
            art = art?.asImageBitmap(),
        )
    }
}

private fun stateName(state: Int?) = when (state) {
    PlaybackState.STATE_PLAYING -> "playing"
    PlaybackState.STATE_PAUSED -> "paused"
    PlaybackState.STATE_STOPPED -> "stopped"
    PlaybackState.STATE_BUFFERING -> "buffering"
    PlaybackState.STATE_NONE, null -> "idle"
    else -> "state $state"
}

private fun mediaKey(context: Context, code: Int) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val now = SystemClock.uptimeMillis()
    am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
    am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
}
