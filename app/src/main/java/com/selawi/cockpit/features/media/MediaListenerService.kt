package com.selawi.cockpit.features.media

import android.service.notification.NotificationListenerService

/**
 * Empty listener. Android only shares other apps' media sessions (title, artwork, controls)
 * with an app the driver has granted notification access.
 */
class MediaListenerService : NotificationListenerService()
