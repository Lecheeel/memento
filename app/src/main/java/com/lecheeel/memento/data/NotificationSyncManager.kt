package com.lecheeel.memento.data

import android.content.Context
import android.util.Log
import com.lecheeel.memento.network.NotificationUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class NotificationSyncManager private constructor(context: Context) {
    companion object {
        private const val TAG = "MementoSync"

        @Volatile
        private var instance: NotificationSyncManager? = null

        fun get(context: Context): NotificationSyncManager =
            instance ?: synchronized(this) {
                instance ?: NotificationSyncManager(context).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val queueStore = EventQueueStore(appContext)
    private val uploader = NotificationUploader()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushing = AtomicBoolean(false)

    fun enqueue(event: NotificationEvent) {
        queueStore.enqueue(event)
        Log.d(TAG, "enqueue package=${event.packageName} queueSize=${queueStore.size()}")
        flushAsync()
    }

    fun flushAsync() {
        if (!flushing.compareAndSet(false, true)) {
            Log.d(TAG, "flush skipped because another flush is running")
            return
        }
        Log.d(TAG, "flush scheduled queueSize=${queueStore.size()}")
        scope.launch {
            try {
                val ok = flushOnce()
                Log.d(TAG, "flush finished ok=$ok queueSize=${queueStore.size()}")
            } finally {
                flushing.set(false)
            }
        }
    }

    suspend fun flushOnce(): Boolean = withContext(Dispatchers.IO) {
        val settings = NotificationRepository.snapshot()
        if (!settings.captureEnabled) {
            Log.d(TAG, "flush aborted because capture is disabled")
            return@withContext false
        }
        var completed = true
        while (true) {
            val batch = queueStore.peekBatch(10)
            if (batch.isEmpty()) {
                Log.d(TAG, "flush queue empty")
                break
            }
            Log.d(TAG, "upload batch size=${batch.size}")
            val result = uploader.upload(batch, settings)
            if (!result.success) {
                Log.w(TAG, "upload failed code=${result.responseCode} message=${result.message}")
                completed = false
                break
            }
            Log.d(TAG, "upload success code=${result.responseCode} message=${result.message}")
            queueStore.dropFirst(batch.size)
        }
        completed
    }

    fun queueSize(): Int = queueStore.size()
}

