package com.lecheeel.memento.data

import org.json.JSONObject

data class NotificationEvent(
    val eventId: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val subText: String,
    val category: String,
    val postTime: Long,
    val eventType: String,
    val clearable: Boolean,
    val sensitive: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("eventId", eventId)
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("title", title)
        put("text", text)
        put("subText", subText)
        put("category", category)
        put("postTime", postTime)
        put("eventType", eventType)
        put("clearable", clearable)
        put("sensitive", sensitive)
    }

    companion object {
        fun fromJson(json: JSONObject): NotificationEvent = NotificationEvent(
            eventId = json.optString("eventId"),
            packageName = json.optString("packageName"),
            appLabel = json.optString("appLabel"),
            title = json.optString("title"),
            text = json.optString("text"),
            subText = json.optString("subText"),
            category = json.optString("category"),
            postTime = json.optLong("postTime"),
            eventType = json.optString("eventType"),
            clearable = json.optBoolean("clearable"),
            sensitive = json.optBoolean("sensitive"),
        )
    }
}

