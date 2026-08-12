package com.lecheeel.memento.data

import android.content.Context
import android.content.SharedPreferences

object NotificationRepository {
    private const val DEFAULT_SERVER_URL = "http://YOUR_SERVER_IP:8080"
    private const val DEFAULT_AUTH_TOKEN = ""
    private const val DEFAULT_ENCRYPTION_SECRET = ""

    private const val PREFS_NAME = "cyber_brain_settings"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_ENCRYPTION_SECRET = "encryption_secret"
    private const val KEY_CAPTURE_ENABLED = "capture_enabled"
    private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
    private const val KEY_FILTER_MODE = "filter_mode"
    private const val KEY_SELECTED_PACKAGES = "selected_packages"
    private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
    private const val KEY_REDACT_SENSITIVE = "redact_sensitive"
    private const val KEY_KEYWORDS = "keyword_filters"
    private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
    private const val LEGACY_ALLOWED_PACKAGES = "allowed_packages"
    private const val LEGACY_BLOCKED_PACKAGES = "blocked_packages"

    @Volatile
    private var appContext: Context? = null
    private val lock = Any()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        settings(context).apply {
            if (!contains(KEY_SERVER_URL)) edit().putString(KEY_SERVER_URL, DEFAULT_SERVER_URL).apply()
            if (!contains(KEY_AUTH_TOKEN)) edit().putString(KEY_AUTH_TOKEN, DEFAULT_AUTH_TOKEN).apply()
            if (!contains(KEY_ENCRYPTION_SECRET)) edit().putString(KEY_ENCRYPTION_SECRET, DEFAULT_ENCRYPTION_SECRET).apply()
            if (!contains(KEY_CAPTURE_ENABLED)) edit().putBoolean(KEY_CAPTURE_ENABLED, false).apply()
            if (!contains(KEY_KEEP_ALIVE_ENABLED)) edit().putBoolean(KEY_KEEP_ALIVE_ENABLED, false).apply()
            if (!contains(KEY_FILTER_MODE)) edit().putString(KEY_FILTER_MODE, migrateFilterMode(this).name).apply()
            if (!contains(KEY_SELECTED_PACKAGES)) {
                val migrated = migrateSelectedPackages(this)
                edit().putString(KEY_SELECTED_PACKAGES, migrated.ifBlank { "com.tencent.mm" }).apply()
            }
            if (!contains(KEY_SHOW_SYSTEM_APPS)) edit().putBoolean(KEY_SHOW_SYSTEM_APPS, false).apply()
            if (!contains(KEY_REDACT_SENSITIVE)) edit().putBoolean(KEY_REDACT_SENSITIVE, true).apply()
            if (!contains(KEY_KEYWORDS)) edit().putString(KEY_KEYWORDS, "").apply()
            if (!contains(KEY_USE_DYNAMIC_COLOR)) edit().putBoolean(KEY_USE_DYNAMIC_COLOR, true).apply()
        }
    }

    fun snapshot(): AppSettings {
        val prefs = prefs()
        return AppSettings(
            serverBaseUrl = prefs.getString(KEY_SERVER_URL, "") ?: "",
            authToken = prefs.getString(KEY_AUTH_TOKEN, "") ?: "",
            encryptionSecret = prefs.getString(KEY_ENCRYPTION_SECRET, "") ?: "",
            captureEnabled = prefs.getBoolean(KEY_CAPTURE_ENABLED, false),
            keepAliveEnabled = prefs.getBoolean(KEY_KEEP_ALIVE_ENABLED, false),
            filterMode = runCatching { FilterMode.valueOf(prefs.getString(KEY_FILTER_MODE, FilterMode.WHITELIST.name) ?: FilterMode.WHITELIST.name) }
                .getOrDefault(FilterMode.WHITELIST),
            selectedPackagesCsv = prefs.getString(KEY_SELECTED_PACKAGES, "") ?: "",
            showSystemApps = prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false),
            redactSensitiveText = prefs.getBoolean(KEY_REDACT_SENSITIVE, true),
            keywordFiltersCsv = prefs.getString(KEY_KEYWORDS, "") ?: "",
            useDynamicColor = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true),
        )
    }

    fun update(block: (AppSettings) -> AppSettings) {
        synchronized(lock) {
            val current = snapshot()
            val updated = block(current)
            prefs().edit()
                .putString(KEY_SERVER_URL, updated.serverBaseUrl.trim())
                .putString(KEY_AUTH_TOKEN, updated.authToken.trim())
                .putString(KEY_ENCRYPTION_SECRET, updated.encryptionSecret.trim())
                .putBoolean(KEY_CAPTURE_ENABLED, updated.captureEnabled)
                .putBoolean(KEY_KEEP_ALIVE_ENABLED, updated.keepAliveEnabled)
                .putString(KEY_FILTER_MODE, updated.filterMode.name)
                .putString(KEY_SELECTED_PACKAGES, updated.selectedPackagesCsv.trim())
                .putBoolean(KEY_SHOW_SYSTEM_APPS, updated.showSystemApps)
                .putBoolean(KEY_REDACT_SENSITIVE, updated.redactSensitiveText)
                .putString(KEY_KEYWORDS, updated.keywordFiltersCsv.trim())
                .putBoolean(KEY_USE_DYNAMIC_COLOR, updated.useDynamicColor)
                .apply()
        }
    }

    private fun prefs(): SharedPreferences {
        val context = appContext ?: error("NotificationRepository not initialized")
        return settings(context)
    }

    private fun settings(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun migrateSelectedPackages(prefs: SharedPreferences): String {
        val allowed = prefs.getString(LEGACY_ALLOWED_PACKAGES, "")?.trim().orEmpty()
        val blocked = prefs.getString(LEGACY_BLOCKED_PACKAGES, "")?.trim().orEmpty()
        return when {
            allowed.isNotBlank() -> allowed
            blocked.isNotBlank() -> blocked
            else -> "com.tencent.mm"
        }
    }

    private fun migrateFilterMode(prefs: SharedPreferences): FilterMode {
        val allowed = prefs.getString(LEGACY_ALLOWED_PACKAGES, "")?.trim().orEmpty()
        val blocked = prefs.getString(LEGACY_BLOCKED_PACKAGES, "")?.trim().orEmpty()
        return when {
            allowed.isNotBlank() -> FilterMode.WHITELIST
            blocked.isNotBlank() -> FilterMode.BLACKLIST
            else -> FilterMode.WHITELIST
        }
    }
}

