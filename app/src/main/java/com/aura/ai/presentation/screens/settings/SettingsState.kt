package com.aura.ai.presentation.screens.settings

data class SettingsState(
    val hasApiKey: Boolean = false,
    val apiKey: String = "",
    val selectedModel: String = "gemini-2.0-flash-exp",
    val showApiKeyDialog: Boolean = false,
    val isExporting: Boolean = false,
    val darkMode: Boolean = true,
    val notificationsEnabled: Boolean = true
)
