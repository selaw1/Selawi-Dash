package com.selawi.cockpit.features.media

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.service.media.MediaBrowserService
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where the track information came from. */
enum class MediaSource { None, Session, Browser, Broadcast }

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val packageName: String? = null,
    val isPlaying: Boolean = false,
    val art: ImageBitmap? = null,
    val source: MediaSource = MediaSource.None,
    /** Song length and position, when the player reports them. */
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    /** SystemClock.elapsedRealtime() when [positionMs] was measured, so the bar can move between updates. */
    val positionAt: Long = 0,
) {
    val hasTrack: Boolean get() = title.isNotBlank()

    /** Where the song is now, from 0 to 1, or null when the player gives no position. */
    fun progress(nowElapsed: Long): Float? {
        if (durationMs <= 0 || positionAt == 0L) return null
        val position = positionMs + if (isPlaying) nowElapsed - positionAt else 0
        return (position.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}

/**
 * Tracks what is playing and controls it.
 *
 * Track details come from Android media sessions when the driver has granted notification access
 * (BYD blocks the normal screen for it), otherwise from the "now playing" broadcasts that Spotify
 * (with Device Broadcast Status on) and some other players send. Controls always work through
 * media keys, which need no permission.
 */
class MediaRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying

    private val _sessionAccess = MutableStateFlow(false)
    val sessionAccess: StateFlow<Boolean> = _sessionAccess

    private val _browserApps = MutableStateFlow<List<String>>(emptyList())
    /** Apps that let the cockpit read their player directly (no permission needed). */
    val browserApps: StateFlow<List<String>> = _browserApps

    private var controller: MediaController? = null
    private val browsers = mutableMapOf<String, MediaBrowser>()
    private val browserControllers = mutableMapOf<String, MediaController>()
    private var broadcastTrack = NowPlaying()
    private var lastArtKey: String? = null
    private var lastArt: ImageBitmap? = null
    private var lastPlayingPackage: String? = null
    private val spotifyArt = mutableMapOf<String, ImageBitmap?>()

    init {
        registerBroadcasts()
        connectBrowsers()
        scope.launch {
            while (true) {
                pollSessions()
                delay(1000)
            }
        }
    }

    fun playPause() {
        val c = controller
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        } else {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            // Broadcasts may lag; flip the button at once so the driver sees the tap land.
            broadcastTrack = broadcastTrack.copy(isPlaying = !broadcastTrack.isPlaying)
            if (controller == null) _nowPlaying.value = broadcastTrack
        }
    }

    fun next() = controller?.transportControls?.skipToNext() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous() = controller?.transportControls?.skipToPrevious() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun mediaKey(code: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun pollSessions() {
        val allowed = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        _sessionAccess.value = allowed
        val controllers = if (allowed) {
            runCatching {
                val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                msm.getActiveSessions(ComponentName(context, MediaListenerService::class.java))
            }.getOrDefault(emptyList())
        } else {
            browserControllers.values.toList()
        }
        // Prefer the player that is actually playing, then the last one that was. An idle app
        // (the radio, say) must not take over the buttons just because it is connected.
        val playingNow = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (playingNow != null) lastPlayingPackage = playingNow.packageName
        val active = playingNow
            ?: controllers.firstOrNull { it.packageName == lastPlayingPackage }
            ?: if (allowed) controllers.firstOrNull() else null
        // Without a playing player, keep the last one so its buttons still work.
        controller = active
        if (active == null || (active.playbackState?.state != PlaybackState.STATE_PLAYING && active.metadata == null)) {
            if (_nowPlaying.value.source != MediaSource.Broadcast) _nowPlaying.value = broadcastTrack
            return
        }
        val meta = active.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val artKey = "${active.packageName}/$title/$artist"
        if (artKey != lastArtKey) {
            lastArtKey = artKey
            val bitmap: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            lastArt = bitmap?.asImageBitmap()
        }
        _nowPlaying.value = NowPlaying(
            title = title,
            artist = artist,
            packageName = active.packageName,
            isPlaying = active.playbackState?.state == PlaybackState.STATE_PLAYING,
            art = lastArt,
            source = if (allowed) MediaSource.Session else MediaSource.Browser,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            positionMs = active.playbackState?.position ?: 0,
            positionAt = active.playbackState?.lastPositionUpdateTime ?: 0,
        )
    }

    /**
     * Connects to every app that offers a media browser service: the documented way car screens
     * (Android Auto, Android Automotive) read players. It needs no permission, but each app decides
     * whether to accept; refusals are simply skipped. This also covers the car's Bluetooth music,
     * which carries the track playing on a connected phone.
     */
    fun connectBrowsers() {
        val pm = context.packageManager
        val services = runCatching {
            pm.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0)
        }.getOrDefault(emptyList())
        for (info in services) {
            val service = info.serviceInfo ?: continue
            val pkg = service.packageName
            if (pkg == context.packageName || pkg in browsers || !service.exported) continue
            val component = ComponentName(pkg, service.name)
            lateinit var browser: MediaBrowser
            browser = MediaBrowser(context, component, object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    runCatching {
                        browserControllers[pkg] = MediaController(context, browser.sessionToken)
                        _browserApps.value = browserControllers.keys.toList()
                    }
                }

                override fun onConnectionSuspended() {
                    browserControllers.remove(pkg)
                    browsers.remove(pkg)
                    _browserApps.value = browserControllers.keys.toList()
                }

                override fun onConnectionFailed() {
                    browserControllers.remove(pkg)
                }
            }, null)
            browsers[pkg] = browser
            runCatching { browser.connect() }
        }
    }

    private fun registerBroadcasts() {
        val filter = IntentFilter().apply {
            addAction(SPOTIFY_METADATA)
            addAction(SPOTIFY_PLAYBACK)
            GENERIC_ACTIONS.forEach { addAction(it) }
        }
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) = onBroadcast(intent)
            },
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun onBroadcast(intent: Intent) {
        val next = when (intent.action) {
            SPOTIFY_METADATA -> {
                val id = intent.getStringExtra("id")
                broadcastTrack.copy(
                    title = intent.getStringExtra("track").orEmpty(),
                    artist = intent.getStringExtra("artist").orEmpty(),
                    packageName = SPOTIFY,
                    art = id?.let { spotifyArt[it] },
                    source = MediaSource.Broadcast,
                ).also { if (id != null && id !in spotifyArt) fetchSpotifyArt(id) }
            }
            SPOTIFY_PLAYBACK -> broadcastTrack.copy(
                isPlaying = intent.getBooleanExtra("playing", broadcastTrack.isPlaying),
                packageName = SPOTIFY,
                source = MediaSource.Broadcast,
            )
            else -> {
                val track = intent.getStringExtra("track") ?: return
                broadcastTrack.copy(
                    title = track,
                    artist = intent.getStringExtra("artist").orEmpty(),
                    packageName = null,
                    isPlaying = intent.getBooleanExtra("playing", true),
                    art = null,
                    source = MediaSource.Broadcast,
                )
            }
        }
        broadcastTrack = next
        if (_nowPlaying.value.source == MediaSource.None || _nowPlaying.value.source == MediaSource.Broadcast || controller == null) _nowPlaying.value = next
    }

    /** Spotify's broadcast has no artwork; its public oEmbed endpoint gives the cover for a track id. */
    private fun fetchSpotifyArt(id: String) {
        spotifyArt[id] = null
        scope.launch {
            val art = withContext(Dispatchers.IO) {
                runCatching {
                    val url = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(id, "UTF-8")
                    val json = JSONObject(httpGet(url).decodeToString())
                    val bytes = httpGet(json.getString("thumbnail_url"))
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
            spotifyArt[id] = art
            if (art != null && broadcastTrack.packageName == SPOTIFY) {
                broadcastTrack = broadcastTrack.copy(art = art)
                if (_nowPlaying.value.source == MediaSource.Broadcast) _nowPlaying.value = broadcastTrack
            }
        }
    }

    private fun httpGet(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val SPOTIFY = "com.spotify.music"
        const val SPOTIFY_METADATA = "com.spotify.music.metadatachanged"
        const val SPOTIFY_PLAYBACK = "com.spotify.music.playbackstatechanged"
        val GENERIC_ACTIONS = listOf(
            "com.android.music.metachanged",
            "com.android.music.playstatechanged",
        )
    }
}
