package com.lecheeel.memento.notification

import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lecheeel.memento.data.NotificationRepository
import com.lecheeel.memento.data.NotificationSyncManager

class NotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "MementoListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "listener connected")
        NotificationSyncManager.get(this).flushAsync()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) {
            Log.d(TAG, "posted notification is null")
            return
        }
        val settings = NotificationRepository.snapshot()
        if (!settings.captureEnabled) {
            Log.d(TAG, "capture disabled, ignore posted from ${sbn.packageName}")
            return
        }
        val event = NotificationEventMapper.from(this, sbn, "posted", settings)
        if (event == null) {
            Log.d(TAG, "filtered posted from ${sbn.packageName}")
            return
        }
        Log.d(TAG, "captured posted from ${event.packageName}, queueing")
        NotificationSyncManager.get(this).enqueue(event)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) {
            Log.d(TAG, "removed notification is null")
            return
        }
        val settings = NotificationRepository.snapshot()
        if (!settings.captureEnabled) {
            Log.d(TAG, "capture disabled, ignore removed from ${sbn.packageName}")
            return
        }
        val event = NotificationEventMapper.from(this, sbn, "removed", settings)
        if (event == null) {
            Log.d(TAG, "filtered removed from ${sbn.packageName}")
            return
        }
        Log.d(TAG, "captured removed from ${event.packageName}, queueing")
        NotificationSyncManager.get(this).enqueue(event)
    }
}

