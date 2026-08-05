package com.lecheeel.memento.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class EventQueueStore(context: Context) {
    private val lock = ReentrantLock()
    private val queueFile = File(context.filesDir, "notification_queue.json")

    fun enqueue(event: NotificationEvent) {
        lock.withLock {
            val queue = readUnsafe().toMutableList()
            queue.add(event)
            writeUnsafe(queue)
        }
    }

    fun peekBatch(limit: Int): List<NotificationEvent> = lock.withLock {
        readUnsafe().take(limit)
    }

    fun dropFirst(count: Int) {
        lock.withLock {
            val queue = readUnsafe().drop(count)
            writeUnsafe(queue)
        }
    }

    fun size(): Int = lock.withLock { readUnsafe().size }

    private fun readUnsafe(): List<NotificationEvent> {
        if (!queueFile.exists()) return emptyList()
        val raw = queueFile.readText()
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                add(NotificationEvent.fromJson(array.getJSONObject(index)))
            }
        }
    }

    private fun writeUnsafe(events: List<NotificationEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        queueFile.writeText(array.toString())
    }
}

