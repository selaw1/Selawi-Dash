package com.selawi.cockpit.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cockpit_settings")

/** Driver preferences, kept on the car. */
class SettingsStore(private val context: Context) {
    private val fullScreenKey = booleanPreferencesKey("full_screen")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")
    private val pinnedKey = stringPreferencesKey("pinned_apps")
    private val themeKey = stringPreferencesKey("theme")
    private val splitKey = booleanPreferencesKey("open_beside")
    private val pairingKey = stringPreferencesKey("pairing_code")

    /** Six-digit code the phone app must send before the cockpit accepts it. Made once, kept. */
    suspend fun pairingCode(): String {
        context.dataStore.data.first()[pairingKey]?.let { return it }
        val code = (100_000 + java.security.SecureRandom().nextInt(900_000)).toString()
        context.dataStore.edit { it[pairingKey] = code }
        return code
    }

    /** Auto follows sunrise and sunset, like Google Maps. */
    val theme: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Auto
    }

    /** Open apps in split screen beside the cockpit when the car allows it. */
    val openBeside: Flow<Boolean> = context.dataStore.data.map { it[splitKey] ?: true }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setOpenBeside(enabled: Boolean) = context.dataStore.edit { it[splitKey] = enabled }

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
    }
}

enum class ThemeMode { Auto, Light, Dark }
