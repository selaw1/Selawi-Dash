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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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



    private var controller: MediaController? = null
    private val browsers = mutableMapOf<String, MediaBrowser>()
    private val browserControllers = mutableMapOf<String, MediaController>()
    private var broadcastTrack = NowPlaying()
    private var lastArtKey: String? = null
    private var lastArt: ImageBitmap? = null
    private var lastPlayingPackage: String? = null
    private val artCache = mutableMapOf<String, ImageBitmap>()
    private val attempts = mutableMapOf<String, Attempt>()
    private val loading = mutableSetOf<String>()
    private var keySince = 0L
    private val lookupLock = Mutex()
    private var lastLookupAt = 0L
    private var lookupPausedUntil = 0L

    private val _coverStatus = MutableStateFlow("No song yet")
    /** Where the last cover came from, or why there was none; shown in Settings. */
    val coverStatus: StateFlow<String> = _coverStatus

    // Shown at once when the driver taps play/pause, and kept briefly while the phone catches up,
    // so a slow Bluetooth round trip never makes the button flick back.
    private var optimisticPlaying: Boolean? = null
    private var optimisticUntil = 0L

    // Live updates from the active player, so changes show at once instead of on the next poll.
    private var watched: MediaController? = null
    private val watcher = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pollSessions()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pollSessions()
    }
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
        val now = SystemClock.elapsedRealtime()
        val current = _nowPlaying.value
        val target = !current.isPlaying
        // Answer the tap immediately; the player confirms over Bluetooth a moment later.
        optimisticPlaying = target
        optimisticUntil = now + 1500
        val position = current.progress(now)?.let { (it * current.durationMs).toLong() } ?: current.positionMs
        _nowPlaying.value = current.copy(isPlaying = target, positionMs = position, positionAt = now)
        val c = controller
        if (c != null) {
            if (target) c.transportControls.play() else c.transportControls.pause()
        } else {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            broadcastTrack = broadcastTrack.copy(isPlaying = target)
        }
    }

    fun next() {
        restartProgress()
        controller?.transportControls?.skipToNext() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        restartProgress()
        controller?.transportControls?.skipToPrevious() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    /** A skip shows at once: the bar jumps back to the start until the new song arrives. */
    private fun restartProgress() {
        _nowPlaying.value = _nowPlaying.value.copy(positionMs = 0, positionAt = SystemClock.elapsedRealtime())
    }

    private fun mediaKey(code: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun pollSessions() {
        val allowed = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
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
        if (watched?.sessionToken != active?.sessionToken) {
            runCatching { watched?.unregisterCallback(watcher) }
            watched = active
            runCatching { active?.registerCallback(watcher, android.os.Handler(android.os.Looper.getMainLooper())) }
        }
        if (active == null || (active.playbackState?.state != PlaybackState.STATE_PLAYING && active.metadata == null)) {
            if (_nowPlaying.value.source != MediaSource.Broadcast) _nowPlaying.value = broadcastTrack
            return
        }
        val meta = active.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val artKey = songKey(title, artist)
        val now = SystemClock.elapsedRealtime()
        if (artKey != lastArtKey) {
            lastArtKey = artKey
            keySince = now
            lastArt = null
        }
        if (lastArt == null) {
            // Checked on every update: Bluetooth often sends the cover link a moment after the title.
            val bitmap: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            lastArt = bitmap?.asImageBitmap() ?: artCache[artKey]
            if (bitmap != null) _coverStatus.value = "From the player"
            // Wait until the song's details have settled, so one song makes one lookup.
            if (lastArt == null && title.isNotBlank() && now - keySince > 1200) {
                val uris = listOfNotNull(
                    meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                    meta?.getString(MediaMetadata.METADATA_KEY_ART_URI),
                    meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
                )
                loadArt(artKey, uris, title, artist)
            }
        }
        val actuallyPlaying = active.playbackState?.state == PlaybackState.STATE_PLAYING
        val shownPlaying = optimisticPlaying?.takeIf { SystemClock.elapsedRealtime() < optimisticUntil } ?: actuallyPlaying
        if (optimisticPlaying == actuallyPlaying || SystemClock.elapsedRealtime() >= optimisticUntil) optimisticPlaying = null
        _nowPlaying.value = NowPlaying(
            title = title,
            artist = artist,
            packageName = active.packageName,
            isPlaying = shownPlaying,
            art = lastArt,
            source = if (allowed) MediaSource.Session else MediaSource.Browser,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            positionMs = active.playbackState?.position ?: 0,
            positionAt = active.playbackState?.lastPositionUpdateTime ?: 0,
        )
    }

    /**
     * Cover for a song that arrived without one. Bluetooth players usually hand the cover over as
     * a link to the car's Bluetooth service rather than as an image, so that is tried first; if
     * there is still nothing, the cover is looked up by title and artist in Apple's free iTunes
     * search. Found covers are kept per song; failures are retried later, up to four times.
     */
    private fun loadArt(key: String, uris: List<String>, title: String, artist: String) {
        if (key in loading) return
        val attempt = attempts[key]
        val now = SystemClock.elapsedRealtime()
        if (attempt != null && (attempt.count >= 4 || now < attempt.retryAt)) return
        loading += key
        scope.launch {
            val art = withContext(Dispatchers.IO) {
                uris.firstNotNullOfOrNull { uri ->
                    runCatching {
                        context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                            BitmapFactory.decodeStream(it)?.asImageBitmap()
                        }
                    }.getOrNull()
                }?.also { _coverStatus.value = "From Bluetooth" } ?: lookupLock.withLock { lookUpCover(title, artist) }
            }
            loading -= key
            if (art != null) {
                artCache[key] = art
                attempts.remove(key)
                if (lastArtKey == key) {
                    lastArt = art
                    _nowPlaying.value = _nowPlaying.value.copy(art = art)
                }
            } else {
                val count = (attempts[key]?.count ?: 0) + 1
                attempts[key] = Attempt(count, SystemClock.elapsedRealtime() + 20_000L * count)
            }
        }
    }

    /** Runs one lookup at a time, spaced out, and pauses for a minute when Apple asks it to slow down. */
    private suspend fun lookUpCover(title: String, artist: String): ImageBitmap? {
        val now = SystemClock.elapsedRealtime()
        if (now < lookupPausedUntil) {
            _coverStatus.value = "Cover search paused (too many requests); retrying soon"
            return null
        }
        val wait = lastLookupAt + 2500 - now
        if (wait > 0) delay(wait)
        lastLookupAt = SystemClock.elapsedRealtime()
        val cleanTitle = cleanTitle(title)
        val mainArtist = mainArtist(artist)
        return try {
            val best = search("$mainArtist $cleanTitle".trim(), mainArtist)
                ?: search(cleanTitle, mainArtist)
            if (best == null) {
                _coverStatus.value = "No cover found for \"$cleanTitle\""
                null
            } else {
                val bytes = httpGet(best.replace("100x100bb", "600x600bb"))
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    ?.also { _coverStatus.value = "From iTunes search" }
            }
        } catch (e: HttpStatusException) {
            if (e.code == 403 || e.code == 429) {
                lookupPausedUntil = SystemClock.elapsedRealtime() + 60_000
                _coverStatus.value = "Cover search busy (HTTP ${e.code}); retrying in a minute"
            } else {
                _coverStatus.value = "Cover search failed (HTTP ${e.code})"
            }
            null
        } catch (e: Exception) {
            _coverStatus.value = "Cover search failed: ${e.javaClass.simpleName} (no internet?)"
            null
        }
    }

    /** Artwork link of the best match, preferring a result by the same artist. */
    private fun search(term: String, artist: String): String? {
        if (term.isBlank()) return null
        val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&media=music&entity=song&limit=5"
        val results = JSONObject(httpGet(url).decodeToString()).getJSONArray("results")
        if (results.length() == 0) return null
        val all = (0 until results.length()).map { results.getJSONObject(it) }
        val match = all.firstOrNull { artist.isNotBlank() && it.optString("artistName").contains(artist, ignoreCase = true) }
            ?: all.first()
        return match.optString("artworkUrl100").takeIf { it.isNotEmpty() }
    }

    private class Attempt(val count: Int, val retryAt: Long)

    private class HttpStatusException(val code: Int) : Exception("HTTP $code")

    private fun songKey(title: String, artist: String) = cleanTitle(title).lowercase() + "|" + mainArtist(artist).lowercase()

    /** "Song (feat. X) - Remastered 2011" → "Song". */
    private fun cleanTitle(title: String) = title
        .replace(Regex("\\s*[(\\[].*?[)\\]]"), "")
        .replace(Regex("\\s+-\\s+.*$"), "")
        .trim()

    /** "Artist A, Artist B & C" → "Artist A". */
    private fun mainArtist(artist: String) = artist
        .split(Regex(",|&| feat\\.? | ft\\.? | x ", RegexOption.IGNORE_CASE))
        .first()
        .trim()

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
                    }
                }

                override fun onConnectionSuspended() {
                    browserControllers.remove(pkg)
                    browsers.remove(pkg)
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
        c.setRequestProperty("User-Agent", "SelawiDash/1.0 (Android)")
        return try {
            val code = c.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
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
