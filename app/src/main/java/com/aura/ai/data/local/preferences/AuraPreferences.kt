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
    // ═══════════════════════════════════════════
    // ENCRYPTED STORAGE FOR SENSITIVE KEYS
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
    private val prefs: SharedPreferences = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    // ═══════════════════════════════════════════
    // DEFAULT API KEYS (Replace with your actual keys)
    // ═══════════════════════════════════════════
    companion object {
        const val DEFAULT_GEMINI_API_KEY = "AIzaSyBEbUwT5_LDxENu7FNpD5oSTs3mvwJDDPc"
        const val DEFAULT_GITHUB_TOKEN = "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"
        const val DEFAULT_TELEGRAM_TOKEN = "8962193188:AAEmhBBttbsbF9nFSMG5Kv1SqCpHJh8MNaQ"
    }

    // ═══════════════════════════════════════════
    // SECTION 1: GEMINI API KEY
    // ═══════════════════════════════════════════
    fun getApiKey(): String? {
        val stored = securePrefs.getString("api_key", null)
        return if (stored.isNullOrBlank()) DEFAULT_GEMINI_API_KEY.takeIf { it != "YOUR_GEMINI_API_KEY_HERE" } else stored
    }

    fun saveApiKey(apiKey: String) {
        securePrefs.edit().putString("api_key", apiKey).apply()
    }

    fun hasApiKey(): Boolean {
        return getApiKey()?.isNotBlank() == true
    }

    // ═══════════════════════════════════════════
    // SECTION 2: GITHUB TOKEN
    // ═══════════════════════════════════════════
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

    // ═══════════════════════════════════════════
    // SECTION 3: TELEGRAM BOT TOKEN
    // ═══════════════════════════════════════════
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

    // ═══════════════════════════════════════════
    // SECTION 4: THEME
    // ═══════════════════════════════════════════
    fun getThemeMode(): String {
        return prefs.getString("theme_mode", "Cyberpunk Dark") ?: "Cyberpunk Dark"
    }

    fun saveThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    // ═══════════════════════════════════════════
    // SECTION 5: MODEL PREFERENCES
    // ═══════════════════════════════════════════
    fun getPreferredModel(): String? {
        return prefs.getString("preferred_model", null)
    }

    fun setPreferredModel(model: String) {
        prefs.edit().putString("preferred_model", model).apply()
    }

    // ═══════════════════════════════════════════
    // SECTION 6: SESSION PERSISTENCE
    // ═══════════════════════════════════════════
    fun getLastSessionId(): String? {
        return prefs.getString("last_session_id", null)
    }

    fun setLastSessionId(id: String) {
        prefs.edit().putString("last_session_id", id).apply()
    }

    // ═══════════════════════════════════════════
    // SECTION 7: API USAGE TRACKING
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
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastReset = getDailyResetDate()
        if (lastReset != today) {
            setTotalApiCalls(0)
            setDailyResetDate(today)
            return true
        }
        return false
    }

    // ═══════════════════════════════════════════
    // SECTION 8: USER PROFILE
    // ═══════════════════════════════════════════
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

    // ═══════════════════════════════════════════
    // SECTION 9: CODESPACES
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
    // SECTION 10: GENERIC HELPERS
    // ═══════════════════════════════════════════
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String?): String? {
        return prefs.getString(key, default)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, default: Int): Int {
        return prefs.getInt(key, default)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    // ═══════════════════════════════════════════
    // SECTION 11: DATA MANAGEMENT
    // ═══════════════════════════════════════════
    fun clearAllData() {
        securePrefs.edit().clear().apply()
        prefs.edit().clear().apply()
    }

    fun clearApiKeys() {
        securePrefs.edit().remove("api_key").remove("github_token").remove("telegram_token").apply()
    }
}
