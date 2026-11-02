package com.selawi.cockpit.probe

import android.service.notification.NotificationListenerService

/**
 * Empty listener. Android only lets an app read other apps' media sessions
 * (title, artwork, controls) while the user has granted it notification access.
 */
class MediaListenerService : NotificationListenerService()
