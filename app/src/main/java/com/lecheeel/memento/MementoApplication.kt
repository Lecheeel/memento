package com.lecheeel.memento

import android.app.Application
import com.lecheeel.memento.data.NotificationRepository
import com.lecheeel.memento.notification.KeepAliveService

class Memento : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationRepository.initialize(this)
        KeepAliveService.setEnabled(this, NotificationRepository.snapshot().keepAliveEnabled)
    }
}

