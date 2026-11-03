package com.selawi.cockpit

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.selawi.cockpit.core.design.CockpitTheme
import com.selawi.cockpit.features.apps.AppInfo
import com.selawi.cockpit.features.apps.AppsScreen
import com.selawi.cockpit.features.assistant.AssistantScreen
import com.selawi.cockpit.features.dashboard.DashboardScreen
import com.selawi.cockpit.features.dashboard.DashboardState
import com.selawi.cockpit.features.media.MediaControls
import com.selawi.cockpit.features.media.MediaSource
import com.selawi.cockpit.features.media.NowPlaying
import com.selawi.cockpit.features.settings.SettingsActions
import com.selawi.cockpit.features.settings.SettingsScreen
import com.selawi.cockpit.features.settings.SettingsState
import com.selawi.cockpit.data.local.ThemeMode
import com.selawi.cockpit.features.vehicle.DrivingState
import com.selawi.cockpit.features.weather.HourForecast
import com.selawi.cockpit.features.weather.Sky
import com.selawi.cockpit.features.weather.Weather
import com.selawi.cockpit.features.vehicle.Trip
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/** Renders each screen at the Seal 05's resolution (1920 x 1080, 240 dpi) with sample data. */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = carScreen(1920),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 100.0,
    )

    @Test
    fun dashboard() = shot(Destination.Home) { compact -> Dashboard(compact, parked) }

    @Test
    fun dashboardDriving() = shot(Destination.Home) { compact -> Dashboard(compact, moving) }

    @Test
    fun dashboardDay() = shot(Destination.Home, dark = false) { compact -> Dashboard(compact, moving) }

    @Test
    fun dashboardSplitScreen() {
        // Roughly half the screen, as in BYD's split screen.
        paparazzi.unsafeUpdateConfig(deviceConfig = carScreen(940))
        shot(Destination.Home) { compact -> Dashboard(compact, parked) }
    }

    @Test
    fun apps() = shot(Destination.Apps) {
        AppsScreen(apps = allApps, pinned = allApps.take(5), onOpen = {}, onTogglePin = {}, onCarHome = {})
    }

    @Test
    fun appsDay() = shot(Destination.Apps, dark = false) {
        AppsScreen(apps = allApps, pinned = allApps.take(5), onOpen = {}, onTogglePin = {}, onCarHome = {})
    }

    @Test
    fun assistant() = shot(Destination.Assistant) { AssistantScreen() }

    @Test
    fun settings() = shot(Destination.Settings) {
        SettingsScreen(SettingsState(fullScreen = true, keepOn = false, theme = ThemeMode.Auto, crash = null), SettingsActions())
    }

    private val parked = DrivingState(trip = Trip(distanceKm = 23.4, movingSeconds = 2460, maxKmh = 96))
    private val moving = DrivingState(speedKmh = 62, driving = true, headingDeg = 310f, trip = Trip(23.4, 2460, 96))

    @Composable
    private fun Dashboard(compact: Boolean, driving: DrivingState) {
        DashboardScreen(
            compact = compact,
            state = DashboardState(
                weather = Weather(
                    27, 31, 18, "Mostly clear", isDay = true, place = "Amman", sky = Sky.Clear,
                    hours = listOf(
                        HourForecast(LocalTime.of(14, 0), 28, Sky.Clear, true),
                        HourForecast(LocalTime.of(15, 0), 28, Sky.PartlyCloudy, true),
                        HourForecast(LocalTime.of(16, 0), 26, Sky.PartlyCloudy, true),
                        HourForecast(LocalTime.of(17, 0), 24, Sky.Cloudy, true),
                        HourForecast(LocalTime.of(18, 0), 21, Sky.Clear, false),
                        HourForecast(LocalTime.of(19, 0), 19, Sky.Rain, false),
                    ),
                    sunrise = LocalTime.of(6, 12),
                    sunset = LocalTime.of(16, 58),
                ),
                playing = playing,
                driving = driving,
                fixedTime = LocalDateTime.of(2026, 11, 3, 12, 51),
            ),
            controls = MediaControls.None,
        )
    }

    private fun shot(destination: Destination, dark: Boolean = true, content: @Composable (Boolean) -> Unit) {
        paparazzi.snapshot {
            // Marks this as a preview, so the 3D car draws its stand-in instead of the native engine.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CockpitTheme(dark) {
                    DashFrame(destination, onSelect = {}, pinned = allApps.take(4)) { compact -> content(compact) }
                }
            }
        }
    }

    private fun carScreen(widthPx: Int) = DeviceConfig.PIXEL_5.copy(
        screenWidth = widthPx,
        screenHeight = 1080,
        xdpi = 240,
        ydpi = 240,
        density = Density.HIGH,
        orientation = ScreenOrientation.LANDSCAPE,
        softButtons = false,
    )

    // Lazy: bitmaps can only be made once Paparazzi has started its renderer.
    private val playing by lazy {
        NowPlaying(
        title = "Song title",
        artist = "Artist name",
        packageName = "com.spotify.music",
        isPlaying = true,
        art = artwork(),
        source = MediaSource.Browser,
            durationMs = 200_000,
            positionMs = 74_000,
            positionAt = 1,
        )
    }

    private val allApps by lazy {
        listOf(
        "Spotify" to 0xFF1DB954, "SoundCloud" to 0xFFFF5500, "DiAuto" to 0xFF2D6BFF,
        "YouTube" to 0xFFFF0033, "Chrome" to 0xFF4285F4, "DiPlay" to 0xFF34C759,
        "Bluetooth Phone" to 0xFF19C3B4, "Album" to 0xFF3DD5C8, "DVR" to 0xFF30C6E8,
        "App Store" to 0xFFFF9F1C, "Bilibili" to 0xFFFB7299, "CarLife" to 0xFF3B6CFF,
        ).map { (name, color) -> AppInfo(name, ComponentName("demo.$name", "demo.Main"), icon(name, color.toInt())) }
    }

    private fun artwork() = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).also { b ->
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 600f, 600f, 0xFFE52D27.toInt(), 0xFF2B0A3D.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(b).drawRect(0f, 0f, 600f, 600f, paint)
    }.asImageBitmap()

    private fun icon(name: String, color: Int) = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        c.drawRoundRect(0f, 0f, 144f, 144f, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = 72f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        c.drawText(name.take(1), 72f, 98f, text)
    }.asImageBitmap()
}
