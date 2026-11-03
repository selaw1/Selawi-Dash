package com.selawi.cockpit.features.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.selawi.cockpit.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppInfo(
    val label: String,
    val component: ComponentName,
    val icon: ImageBitmap?,
) {
    val key: String get() = component.flattenToString()
    val packageName: String get() = component.packageName
}

/** Installed apps and the driver's pinned shortcuts. */
class AppsRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    /** Pinned apps; until the driver pins something, a few likely favourites are shown. */
    val pinned: StateFlow<List<AppInfo>> = combine(_apps, settings.pinnedApps) { all, keys ->
        if (keys == null) {
            DEFAULT_PACKAGES.mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg } }
        } else {
            keys.mapNotNull { key -> all.firstOrNull { it.key == key } }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
        // Keep the list current when apps are installed or removed.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) = refresh()
            },
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun refresh() {
        scope.launch { _apps.value = withContext(Dispatchers.IO) { load() } }
    }

    fun isPinned(app: AppInfo): Boolean = pinned.value.any { it.key == app.key }

    fun togglePinned(app: AppInfo) {
        scope.launch {
            val current = settings.pinnedApps.first() ?: pinned.value.map { it.key }
            val next = if (app.key in current) current - app.key else current + app.key
            settings.setPinned(next)
        }
    }

    fun launch(app: AppInfo): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun load(): List<AppInfo> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                AppInfo(
                    label = info.loadLabel(pm).toString(),
                    component = ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                    icon = runCatching { info.loadIcon(pm).toBitmap(144, 144).asImageBitmap() }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        val DEFAULT_PACKAGES = listOf(
            "com.spotify.music",
            "com.soundcloud.android",
            "app.revanced.android.youtube",
            "com.andrerinas.headunitrevived",
            "com.android.chrome",
        )
    }
}
