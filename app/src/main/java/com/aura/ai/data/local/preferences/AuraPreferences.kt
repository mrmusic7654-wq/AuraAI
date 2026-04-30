package com.aura.ai.data.local.preferences

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraPreferences @Inject constructor(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    fun getApiKey(): String? = prefs.getString("api_key", null)
    fun saveApiKey(apiKey: String) { prefs.edit().putString("api_key", apiKey).apply() }
    fun getGitHubToken(): String? = prefs.getString("github_token", null)
    fun saveGitHubToken(token: String) { prefs.edit().putString("github_token", token).apply() }
}
