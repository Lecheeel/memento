package com.lecheeel.memento.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lecheeel.memento.data.NotificationRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        NotificationRepository.initialize(context.applicationContext)
        val keepAliveEnabled = NotificationRepository.snapshot().keepAliveEnabled
        Log.d(TAG, "boot completed keepAliveEnabled=$keepAliveEnabled")
        if (keepAliveEnabled) {
            KeepAliveService.setEnabled(context, true)
        }
    }

    companion object {
        private const val TAG = "MementoBoot"
    }
}
