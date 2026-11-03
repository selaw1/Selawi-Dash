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
