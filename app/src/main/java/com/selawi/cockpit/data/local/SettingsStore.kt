package com.selawi.cockpit.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cockpit_settings")

/** Driver preferences, kept on the car. */
class SettingsStore(private val context: Context) {
    private val fullScreenKey = booleanPreferencesKey("full_screen")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")
    private val pinnedKey = stringPreferencesKey("pinned_apps")
    private val themeKey = stringPreferencesKey("theme")
    private val accentKey = androidx.datastore.preferences.core.intPreferencesKey("accent")
    private val backdropKey = stringPreferencesKey("backdrop")
    private val carPaintKey = stringPreferencesKey("car_paint")
    private val flightModeKey = stringPreferencesKey("flight_mode")
    private val planePaintKey = stringPreferencesKey("plane_paint")

    /** The jet's colour in flight mode, kept apart from the car's; pearl white by default. */
    val planePaint: Flow<String> = context.dataStore.data.map { it[planePaintKey] ?: PLANE_DEFAULT }

    suspend fun setPlanePaint(paint: String) = context.dataStore.edit { it[planePaintKey] = paint }

    /** Car or plane on the dashboard: Auto switches by speed. */
    val flightMode: Flow<FlightMode> = context.dataStore.data.map { prefs ->
        prefs[flightModeKey]?.let { runCatching { FlightMode.valueOf(it) }.getOrNull() } ?: FlightMode.Auto
    }

    suspend fun setFlightMode(mode: FlightMode) = context.dataStore.edit { it[flightModeKey] = mode.name }

    /** The 3D car's paint (see CarPaint); pearl white by default. */
    val carPaint: Flow<String> = context.dataStore.data.map { it[carPaintKey] ?: "Pearl" }

    suspend fun setCarPaint(paint: String) = context.dataStore.edit { it[carPaintKey] = paint }

    /** The home screen's background. */
    val backdrop: Flow<Backdrop> = context.dataStore.data.map { prefs ->
        prefs[backdropKey]?.let { runCatching { Backdrop.valueOf(it) }.getOrNull() } ?: Backdrop.CoverGlow
    }

    suspend fun setBackdrop(b: Backdrop) = context.dataStore.edit { it[backdropKey] = b.name }

    /** The driver's accent colour (ARGB), or null for the default blue. */
    val accent: Flow<Int?> = context.dataStore.data.map { it[accentKey] }

    suspend fun setAccent(argb: Int?) = context.dataStore.edit {
        if (argb == null) it.remove(accentKey) else it[accentKey] = argb
    }

    /** Auto follows sunrise and sunset, like Google Maps. */
    val theme: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Auto
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }

    val fullScreen: Flow<Boolean> = context.dataStore.data.map { it[fullScreenKey] ?: true }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[keepScreenOnKey] ?: false }

    /** Pinned app components in order, or null when the driver has not chosen any yet. */
    val pinnedApps: Flow<List<String>?> = context.dataStore.data.map { prefs ->
        prefs[pinnedKey]?.split(SEPARATOR)?.filter { it.isNotBlank() }
    }

    suspend fun setFullScreen(enabled: Boolean) = context.dataStore.edit { it[fullScreenKey] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = context.dataStore.edit { it[keepScreenOnKey] = enabled }

    suspend fun setPinned(components: List<String>) =
        context.dataStore.edit { it[pinnedKey] = components.joinToString(SEPARATOR) }

    private companion object {
        const val SEPARATOR = "|"
        const val PLANE_DEFAULT = "Pearl"
    }
}

enum class ThemeMode { Auto, Light, Dark }

/** Auto shows the plane once the speed is a plane's; Plane and Car fix the choice. */
enum class FlightMode { Auto, Plane, Car }

/** Home screen backgrounds: plain, the playing cover's colour, or a fixed soft gradient. */
enum class Backdrop(val label: String) {
    Plain("Plain"),
    CoverGlow("Cover glow"),
    Accent("Accent"),
    Dusk("Dusk"),
    Ocean("Ocean"),
    Aurora("Aurora"),
}
