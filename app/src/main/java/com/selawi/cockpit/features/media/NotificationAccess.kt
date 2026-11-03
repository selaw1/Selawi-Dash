package com.selawi.cockpit.features.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * Opens Android's notification access screen.
 *
 * BYD answers the standard settings action with a "not supported" page, so the real screens are
 * tried first by their exact component name, which bypasses that interception.
 */
object NotificationAccess {
    private val candidates = listOf(
        "com.android.settings" to "com.android.settings.Settings\$NotificationAccessSettingsActivity",
        "com.android.settings" to "com.android.settings.Settings\$NotificationAccessDetailsActivity",
        "com.android.settings" to "com.android.settings.Settings\$NotificationListenerSettingsActivity",
    )

    fun open(context: Context) {
        val listener = ComponentName(context, MediaListenerService::class.java).flattenToString()
        for ((pkg, cls) in candidates) {
            val intent = Intent()
                .setComponent(ComponentName(pkg, cls))
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, 0) == null) continue
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        // Last resort: the standard action, which BYD may answer with its "not supported" page.
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(fallback) }.isFailure) {
            Toast.makeText(context, "This car has no notification access screen", Toast.LENGTH_LONG).show()
        }
    }
}
