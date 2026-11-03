package com.selawi.cockpit.link

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Watches Google Maps' turn-by-turn notification and forwards each step to the cockpit.
 * Only Google Maps' navigation notification is read; nothing else leaves the phone.
 */
class NavListenerService : NotificationListenerService() {
    private var lastIconHash = 0

    override fun onListenerConnected() {
        CockpitClient.start(this)
        runCatching { activeNotifications?.forEach { onNotificationPosted(it) } }
    }

    override fun onListenerDisconnected() {
        CockpitClient.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isNavigation(sbn)) return
        val n = sbn.notification
        val extras = n.extras
        val json = JSONObject()
            .put("type", "nav")
            .put("active", true)
            .put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            .put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            .put("sub", extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty())
        // The turn arrow. Sent only when it changes, to keep each update small.
        val icon = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
        if (icon != null) {
            val png = ByteArrayOutputStream().also { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            val hash = png.contentHashCode()
            if (hash != lastIconHash) {
                lastIconHash = hash
                json.put("icon", Base64.encodeToString(png, Base64.NO_WRAP))
            }
        }
        CockpitClient.send(json.toString())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isNavigation(sbn)) return
        lastIconHash = 0
        CockpitClient.send("""{"type":"nav","active":false}""")
    }

    private fun isNavigation(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != GOOGLE_MAPS) return false
        val n = sbn.notification
        return n.category == Notification.CATEGORY_NAVIGATION || (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
    }

    private companion object {
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
    }
}
