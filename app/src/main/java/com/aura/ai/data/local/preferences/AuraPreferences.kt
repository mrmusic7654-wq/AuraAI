package com.aura.ai.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    // ═══════════════════════════════════════════
    // ENCRYPTED STORAGE FOR SENSITIVE DATA
    // ═══════════════════════════════════════════
    
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

    // ═══════════════════════════════════════════
    // REGULAR STORAGE FOR NON-SENSITIVE DATA
    // ═══════════════════════════════════════════
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "aura_prefs",
        Context.MODE_PRIVATE
    )

    // ═══════════════════════════════════════════
    // DEFAULT API KEYS (Replace with your keys)
    // ═══════════════════════════════════════════
    
    companion object {
        const val DEFAULT_GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
        const val DEFAULT_GITHUB_TOKEN = "YOUR_GITHUB_TOKEN"
    }

    // ═══════════════════════════════════════════
    // GEMINI API KEY
    // ═══════════════════════════════════════════
    
    fun getApiKey(): String? {
        val stored = securePrefs.getString("api_key", null)
        return if (stored.isNullOrBlank()) DEFAULT_GEMINI_API_KEY else stored
    }

    fun saveApiKey(apiKey: String) {
        securePrefs.edit().putString("api_key", apiKey).apply()
    }

    fun hasApiKey(): Boolean = getApiKey()?.isNotBlank() == true

    // ═══════════════════════════════════════════
    // GITHUB TOKEN
    // ═══════════════════════════════════════════
    
    fun getGitHubToken(): String? {
        val stored = securePrefs.getString("github_token", null)
        return if (stored.isNullOrBlank()) DEFAULT_GITHUB_TOKEN else stored
    }

    fun saveGitHubToken(token: String) {
        securePrefs.edit().putString("github_token", token).apply()
    }

    fun hasGitHubToken(): Boolean = getGitHubToken()?.isNotBlank() == true

    // ═══════════════════════════════════════════
    // TELEGRAM BOT TOKEN
    // ═══════════════════════════════════════════
    
    fun getTelegramToken(): String? {
        return securePrefs.getString("telegram_token", null)
    }

    fun saveTelegramToken(token: String) {
        securePrefs.edit().putString("telegram_token", token).apply()
    }

    fun hasTelegramToken(): Boolean = getTelegramToken()?.isNotBlank() == true

    // ═══════════════════════════════════════════
    // MODEL PREFERENCES
    // ═══════════════════════════════════════════
    
    fun getPreferredModel(): String? {
        return prefs.getString("preferred_model", null)
    }

    fun setPreferredModel(model: String) {
        prefs.edit().putString("preferred_model", model).apply()
    }

    // ═══════════════════════════════════════════
    // SESSION PERSISTENCE
    // ═══════════════════════════════════════════
    
    fun getLastSessionId(): String? {
        return prefs.getString("last_session_id", null)
    }

    fun setLastSessionId(id: String) {
        prefs.edit().putString("last_session_id", id).apply()
    }

    // ═══════════════════════════════════════════
    // API USAGE TRACKING
    // ═══════════════════════════════════════════
    
    fun getTotalApiCalls(): Int {
        return prefs.getInt("total_api_calls", 0)
    }

    fun setTotalApiCalls(count: Int) {
        prefs.edit().putInt("total_api_calls", count).apply()
    }

    fun incrementApiCalls(): Int {
        val newCount = getTotalApiCalls() + 1
        setTotalApiCalls(newCount)
        return newCount
    }

    fun resetApiCalls() {
        prefs.edit().putInt("total_api_calls", 0).apply()
    }

    fun getDailyResetDate(): String {
        return prefs.getString("daily_reset_date", "") ?: ""
    }

    fun setDailyResetDate(date: String) {
        prefs.edit().putString("daily_reset_date", date).apply()
    }

    fun resetDailyCountersIfNeeded(): Boolean {
        val today = java.text.SimpleDateFormat(
            "yyyy-MM-dd",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        val lastReset = getDailyResetDate()
        if (lastReset != today) {
            setTotalApiCalls(0)
            setDailyResetDate(today)
            return true
        }
        return false
    }

    // ═══════════════════════════════════════════
    // HUGGING FACE SPACE
    // ═══════════════════════════════════════════
    
    fun getHfSpaceUrl(): String {
        return prefs.getString("hf_space_url", "https://aura-orchestrator.hf.space")
            ?: "https://aura-orchestrator.hf.space"
    }

    fun setHfSpaceUrl(url: String) {
        prefs.edit().putString("hf_space_url", url).apply()
    }

    // ═══════════════════════════════════════════
    // CODESPACES
    // ═══════════════════════════════════════════
    
    fun getActiveCodespaceId(): String? {
        return prefs.getString("active_codespace_id", null)
    }

    fun setActiveCodespaceId(id: String) {
        prefs.edit().putString("active_codespace_id", id).apply()
    }

    fun clearCodespaceId() {
        prefs.edit().remove("active_codespace_id").apply()
    }

    // ═══════════════════════════════════════════
    // APP STATE
    // ═══════════════════════════════════════════
    
    fun isFirstLaunch(): Boolean {
        val firstRun = prefs.getBoolean("first_run", true)
        if (firstRun) prefs.edit().putBoolean("first_run", false).apply()
        return firstRun
    }

    fun getThemeMode(): String {
        return prefs.getString("theme_mode", "Dark") ?: "Dark"
    }

    fun saveThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    // ═══════════════════════════════════════════
    // USER PROFILE
    // ═══════════════════════════════════════════
    
    fun getUserName(): String? {
        return prefs.getString("user_name", null)
    }

    fun saveUserName(name: String) {
        prefs.edit().putString("user_name", name).apply()
    }

    // ═══════════════════════════════════════════
    // GENERIC HELPERS
    // ═══════════════════════════════════════════
    
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return prefs.getBoolean(key, default)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    // ═══════════════════════════════════════════
    // DATA MANAGEMENT
    // ═══════════════════════════════════════════
    
    fun clearAllData() {
        securePrefs.edit().clear().apply()
        prefs.edit().clear().apply()
    }

    fun clearApiKeys() {
        securePrefs.edit()
            .remove("api_key")
            .remove("github_token")
            .remove("telegram_token")
            .apply()
    }
}
