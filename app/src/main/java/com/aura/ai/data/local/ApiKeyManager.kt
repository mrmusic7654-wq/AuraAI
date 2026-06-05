package com.aura.ai.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "aura_api_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // ═══════════════════════════════════════════
    // API KEY TYPES
    // ═══════════════════════════════════════════
    
    enum class ApiType(val key: String, val displayName: String, val description: String, val url: String) {
        GEMINI_1("gemini_key_1", "Gemini API Key 1", "Primary Gemini API key", "https://aistudio.google.com/apikey"),
        GEMINI_2("gemini_key_2", "Gemini API Key 2", "Secondary Gemini API key (fallback)", "https://aistudio.google.com/apikey"),
        GEMINI_3("gemini_key_3", "Gemini API Key 3", "Third Gemini API key (backup)", "https://aistudio.google.com/apikey"),
        GITHUB("github_token", "GitHub Token", "GitHub personal access token", "https://github.com/settings/tokens"),
        TELEGRAM("telegram_token", "Telegram Bot Token", "Telegram bot API token", "https://t.me/BotFather"),
        DEEPSEEK("deepseek_key", "DeepSeek API Key", "DeepSeek API key", "https://platform.deepseek.com"),
        OPENAI("openai_key", "OpenAI API Key", "OpenAI/ChatGPT API key", "https://platform.openai.com"),
        CLAUDE("claude_key", "Claude API Key", "Anthropic Claude API key", "https://console.anthropic.com"),
        ELEVENLABS("elevenlabs_key", "ElevenLabs API Key", "Voice cloning & TTS API key", "https://elevenlabs.io"),
        SUNO("suno_key", "Suno API Key", "AI music generation API key", "https://suno.ai"),
        HUGGINGFACE("hf_token", "Hugging Face Token", "Hugging Face API token", "https://huggingface.co/settings/tokens"),
        CUSTOM_1("custom_key_1", "Custom API Key 1", "Custom API key slot", ""),
        CUSTOM_2("custom_key_2", "Custom API Key 2", "Custom API key slot", ""),
        CUSTOM_3("custom_key_3", "Custom API Key 3", "Custom API key slot", "")
    }
    
    // ═══════════════════════════════════════════
    // CRUD OPERATIONS
    // ═══════════════════════════════════════════
    
    fun saveKey(apiType: ApiType, key: String) {
        securePrefs.edit().putString(apiType.key, key).apply()
    }
    
    fun getKey(apiType: ApiType): String? {
        return securePrefs.getString(apiType.key, null)
    }
    
    fun deleteKey(apiType: ApiType) {
        securePrefs.edit().remove(apiType.key).apply()
    }
    
    fun hasKey(apiType: ApiType): Boolean {
        return !getKey(apiType).isNullOrBlank()
    }
    
    fun getAllKeys(): Map<ApiType, String?> {
        return ApiType.values().associateWith { getKey(it) }
    }
    
    fun getActiveGeminiKeys(): List<String> {
        return listOfNotNull(getKey(ApiType.GEMINI_1), getKey(ApiType.GEMINI_2), getKey(ApiType.GEMINI_3))
    }
    
    fun getActiveKeysCount(): Int {
        return ApiType.values().count { hasKey(it) }
    }
    
    fun getConfiguredKeys(): List<ApiType> {
        return ApiType.values().filter { hasKey(it) }
    }
    
    fun getUnconfiguredKeys(): List<ApiType> {
        return ApiType.values().filter { !hasKey(it) }
    }
    
    fun clearAllKeys() {
        securePrefs.edit().clear().apply()
    }
    
    // ═══════════════════════════════════════════
    // KEY STATUS
    // ═══════════════════════════════════════════
    
    fun getKeyStatus(apiType: ApiType): KeyStatus {
        val key = getKey(apiType)
        return when {
            key == null -> KeyStatus.NOT_SET
            key.isBlank() -> KeyStatus.EMPTY
            key.length < 10 -> KeyStatus.INVALID
            else -> KeyStatus.ACTIVE
        }
    }
    
    enum class KeyStatus {
        NOT_SET, EMPTY, INVALID, ACTIVE
    }
    
    fun getStatusReport(): String {
        val sb = StringBuilder("🔑 API KEY STATUS:\n\n")
        ApiType.values().groupBy { getKeyStatus(it) }.forEach { (status, keys) ->
            val icon = when (status) {
                KeyStatus.ACTIVE -> "✅"
                KeyStatus.NOT_SET -> "⬜"
                KeyStatus.EMPTY -> "⚠️"
                KeyStatus.INVALID -> "❌"
            }
            sb.append("$icon ${keys.size} keys ${status.name.lowercase()}\n")
        }
        sb.append("\nTotal: ${getActiveKeysCount()}/${ApiType.values().size} configured")
        return sb.toString()
    }
}
