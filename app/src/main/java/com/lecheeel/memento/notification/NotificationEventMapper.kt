package com.lecheeel.memento.notification

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.lecheeel.memento.data.AppSettings
import com.lecheeel.memento.data.FilterMode
import com.lecheeel.memento.data.NotificationEvent
import java.util.UUID

object NotificationEventMapper {
    fun from(context: Context, sbn: StatusBarNotification, eventType: String, settings: AppSettings): NotificationEvent? {
        val packageName = sbn.packageName.orEmpty()
        if (!passesFilters(packageName, settings)) return null

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val category = notification.category.orEmpty()
        val appLabel = resolveAppLabel(context, packageName)
        val sensitive = isSensitive(notification, title, text, subText, settings)

        return NotificationEvent(
            eventId = UUID.randomUUID().toString(),
            packageName = packageName,
            appLabel = appLabel,
            title = sanitize(title, settings, sensitive),
            text = sanitize(text, settings, sensitive),
            subText = sanitize(subText, settings, sensitive),
            category = category,
            postTime = sbn.postTime,
            eventType = eventType,
            clearable = sbn.isClearable,
            sensitive = sensitive,
        )
    }

    private fun passesFilters(packageName: String, settings: AppSettings): Boolean {
        val selected = settings.selectedPackagesCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        return when (settings.filterMode) {
            FilterMode.WHITELIST -> selected.contains(packageName)
            FilterMode.BLACKLIST -> !selected.contains(packageName)
        }
    }

    private fun sanitize(text: String, settings: AppSettings, sensitive: Boolean): String {
        if (!settings.redactSensitiveText || !sensitive) return text
        return when {
            text.isBlank() -> text
            text.length <= 4 -> "****"
            else -> text.take(2) + "****" + text.takeLast(2)
        }
    }

    private fun isSensitive(
        notification: Notification,
        title: String,
        text: String,
        subText: String,
        settings: AppSettings,
    ): Boolean {
        val combined = listOf(notification.category.orEmpty(), title, text, subText).joinToString(" ").lowercase()
        val keywordHits = settings.keywordFiltersCsv
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        return combined.contains("otp") ||
            combined.contains("验证码") ||
            combined.contains("verification") ||
            keywordHits.any { combined.contains(it) }
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            packageName
        }
    }
}

