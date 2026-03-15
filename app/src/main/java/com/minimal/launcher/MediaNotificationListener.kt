package com.minimal.launcher

import android.service.notification.NotificationListenerService

/**
 * A minimal NotificationListenerService whose sole purpose is to allow
 * [MainActivity] to call [MediaSessionManager.getActiveSessions] with a
 * valid ComponentName. The actual media-controller interaction happens in
 * MainActivity — this service just needs to be running and enabled.
 *
 * The user must grant Notification access once via:
 *   Settings → Apps → Special app access → Notification access
 */
class MediaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        isConnected = true
    }

    override fun onListenerDisconnected() {
        isConnected = false
    }

    companion object {
        /** True once the system has bound and connected the listener. */
        var isConnected: Boolean = false
            private set
    }
}
