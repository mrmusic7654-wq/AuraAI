package com.aura.ai.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraPreferences @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    // Gemini API Key
    fun getApiKey(): String? = prefs.getString("api_key", null)
    fun saveApiKey(apiKey: String) { prefs.edit().putString("api_key", apiKey).apply() }

    // GitHub Token
    fun getGitHubToken(): String? = prefs.getString("github_token", null)
    fun saveGitHubToken(token: String) { prefs.edit().putString("github_token", token).apply() }

    // Telegram Bot Token
    fun getTelegramToken(): String? = prefs.getString("telegram_token", null)
    fun saveTelegramToken(token: String) { prefs.edit().putString("telegram_token", token).apply() }

    // Theme Preference
    fun getThemeMode(): String = prefs.getString("theme_mode", "Cyberpunk Dark") ?: "Cyberpunk Dark"
    fun saveThemeMode(mode: String) { prefs.edit().putString("theme_mode", mode).apply() }
}
