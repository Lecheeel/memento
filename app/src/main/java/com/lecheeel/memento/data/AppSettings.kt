package com.lecheeel.memento.data

data class AppSettings(
    val serverBaseUrl: String,
    val authToken: String,
    val encryptionSecret: String,
    val captureEnabled: Boolean,
    val keepAliveEnabled: Boolean,
    val filterMode: FilterMode,
    val selectedPackagesCsv: String,
    val showSystemApps: Boolean,
    val redactSensitiveText: Boolean,
    val keywordFiltersCsv: String,
)

