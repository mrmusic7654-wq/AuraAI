package com.aura.ai.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraPreferences @Inject constructor(
    private val context: Context
) {
    // Encrypted storage for sensitive keys
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "aura_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Regular preferences for non-sensitive data
    private val prefs: SharedPreferences = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    // ============================================
    // DEFAULT API KEYS (Replace with your actual keys)
    // ============================================
    companion object {
        const val DEFAULT_GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"
        const val DEFAULT_GITHUB_TOKEN = "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"
        const val DEFAULT_TELEGRAM_TOKEN = ""
    }

    // ============================================
    // GEMINI API KEY
    // ============================================
    fun getApiKey(): String? {
        // First check encrypted storage, then fall back to default
        val stored = securePrefs.getString("api_key", null)
        return if (stored.isNullOrBlank()) DEFAULT_GEMINI_API_KEY.takeIf { it != "YOUR_GEMINI_API_KEY_HERE" } else stored
    }

    fun saveApiKey(apiKey: String) {
        securePrefs.edit().putString("api_key", apiKey).apply()
    }

    fun hasApiKey(): Boolean {
        return getApiKey()?.isNotBlank() == true
    }

    // ============================================
    // GITHUB TOKEN
    // ============================================
    fun getGitHubToken(): String? {
        val stored = securePrefs.getString("github_token", null)
        return if (stored.isNullOrBlank()) DEFAULT_GITHUB_TOKEN.takeIf { it != "YOUR_GITHUB_TOKEN_HERE" } else stored
    }

    fun saveGitHubToken(token: String) {
        securePrefs.edit().putString("github_token", token).apply()
    }

    fun hasGitHubToken(): Boolean {
        return getGitHubToken()?.isNotBlank() == true
    }

    // ============================================
    // TELEGRAM BOT TOKEN
    // ============================================
    fun getTelegramToken(): String? {
        val stored = securePrefs.getString("telegram_token", null)
        return if (stored.isNullOrBlank()) DEFAULT_TELEGRAM_TOKEN.takeIf { it.isNotBlank() } else stored
    }

    fun saveTelegramToken(token: String) {
        securePrefs.edit().putString("telegram_token", token).apply()
    }

    fun hasTelegramToken(): Boolean {
        return getTelegramToken()?.isNotBlank() == true
    }

    // ============================================
    // THEME PREFERENCE
    // ============================================
    fun getThemeMode(): String {
        return prefs.getString("theme_mode", "Cyberpunk Dark") ?: "Cyberpunk Dark"
    }

    fun saveThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    // ============================================
    // APP USAGE STATS
    // ============================================
    fun getTotalApiCalls(): Int {
        return prefs.getInt("total_api_calls", 0)
    }

    fun incrementApiCalls() {
        prefs.edit().putInt("total_api_calls", getTotalApiCalls() + 1).apply()
    }

    fun resetApiCalls() {
        prefs.edit().putInt("total_api_calls", 0).apply()
    }

    // ============================================
    // USER PREFERENCES
    // ============================================
    fun getUserName(): String? {
        return prefs.getString("user_name", null)
    }

    fun saveUserName(name: String) {
        prefs.edit().putString("user_name", name).apply()
    }

    fun isFirstRun(): Boolean {
        val firstRun = prefs.getBoolean("first_run", true)
        if (firstRun) prefs.edit().putBoolean("first_run", false).apply()
        return firstRun
    }

    // ============================================
    // DATA MANAGEMENT
    // ============================================
    fun clearAllData() {
        securePrefs.edit().clear().apply()
        prefs.edit().clear().apply()
    }

    fun clearApiKeys() {
        securePrefs.edit().remove("api_key").remove("github_token").remove("telegram_token").apply()
    }
}
