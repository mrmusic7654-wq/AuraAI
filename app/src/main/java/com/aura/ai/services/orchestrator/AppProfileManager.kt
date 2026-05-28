package com.aura.ai.services.orchestrator

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppProfile(
    val packageName: String,
    val appName: String,
    val inputFieldIds: List<String> = emptyList(),
    val sendButtonIds: List<String> = emptyList(),
    val avgResponseTime: Long = 5000,
    val knownIssues: List<String> = emptyList(),
    val bestTapMethod: String = "text",
    val screenshotRegions: Map<String, Rect> = emptyMap(),
    val lastUsed: Long = System.currentTimeMillis(),
    val successRate: Float = 0f,
    val totalInteractions: Int = 0,
    val successfulInteractions: Int = 0
)

class AppProfileManager(context: Context) {
    
    private val profilesDir = File(context.filesDir, "app_profiles")
    private val profilesFile = File(profilesDir, "profiles.json")
    private val profiles = mutableMapOf<String, AppProfile>()
    
    init {
        profilesDir.mkdirs()
        loadProfiles()
        addBuiltInProfiles()
    }
    
    fun getProfile(packageName: String): AppProfile? {
        return profiles[packageName] ?: createEmptyProfile(packageName)
    }
    
    fun updateProfile(packageName: String, update: (AppProfile) -> AppProfile) {
        val current = profiles[packageName] ?: createEmptyProfile(packageName)
        profiles[packageName] = update(current)
        saveProfiles()
    }
    
    fun recordSuccess(packageName: String) {
        updateProfile(packageName) { profile ->
            profile.copy(
                totalInteractions = profile.totalInteractions + 1,
                successfulInteractions = profile.successfulInteractions + 1,
                successRate = (profile.successfulInteractions + 1).toFloat() / (profile.totalInteractions + 1),
                lastUsed = System.currentTimeMillis()
            )
        }
    }
    
    fun recordFailure(packageName: String, issue: String) {
        updateProfile(packageName) { profile ->
            profile.copy(
                totalInteractions = profile.totalInteractions + 1,
                successRate = profile.successfulInteractions.toFloat() / (profile.totalInteractions + 1),
                knownIssues = (profile.knownIssues + issue).distinct().takeLast(10),
                lastUsed = System.currentTimeMillis()
            )
        }
    }
    
    fun getReliableApps(threshold: Float = 0.8f): List<AppProfile> {
        return profiles.values.filter { it.successRate >= threshold && it.totalInteractions >= 5 }
    }
    
    fun getUntrustedApps(): List<AppProfile> {
        return profiles.values.filter { it.successRate < 0.5f && it.totalInteractions >= 3 }
    }
    
    fun getMostUsedApps(limit: Int = 10): List<AppProfile> {
        return profiles.values.sortedByDescending { it.totalInteractions }.take(limit)
    }
    
    private fun createEmptyProfile(packageName: String): AppProfile {
        return AppProfile(
            packageName = packageName,
            appName = packageName.substringAfterLast(".")
        )
    }
    
    private fun addBuiltInProfiles() {
        val builtIn = mapOf(
            "com.deepseek.chat" to AppProfile(
                packageName = "com.deepseek.chat",
                appName = "DeepSeek",
                inputFieldIds = listOf("message_input", "chat_input"),
                sendButtonIds = listOf("send_button", "send_btn"),
                avgResponseTime = 15000,
                bestTapMethod = "text",
                knownIssues = listOf("Package name may change with updates")
            ),
            "com.whatsapp" to AppProfile(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                inputFieldIds = listOf("entry", "composebox"),
                sendButtonIds = listOf("send"),
                avgResponseTime = 3000,
                bestTapMethod = "id"
            ),
            "com.google.android.apps.bard" to AppProfile(
                packageName = "com.google.android.apps.bard",
                appName = "Gemini",
                inputFieldIds = listOf("input_area", "text_input"),
                sendButtonIds = listOf("send_button"),
                avgResponseTime = 10000,
                bestTapMethod = "text"
            )
        )
        
        for ((pkg, profile) in builtIn) {
            if (!profiles.containsKey(pkg)) {
                profiles[pkg] = profile
            }
        }
        saveProfiles()
    }
    
    private fun loadProfiles() {
        if (!profilesFile.exists()) return
        try {
            val json = JSONObject(profilesFile.readText())
            json.keys().forEach { pkg ->
                val pJson = json.getJSONObject(pkg)
                profiles[pkg] = AppProfile(
                    packageName = pkg,
                    appName = pJson.optString("appName", pkg),
                    avgResponseTime = pJson.optLong("avgResponseTime", 5000),
                    bestTapMethod = pJson.optString("bestTapMethod", "text"),
                    knownIssues = pJson.optJSONArray("knownIssues")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    lastUsed = pJson.optLong("lastUsed", 0),
                    successRate = pJson.optDouble("successRate", 0.0).toFloat(),
                    totalInteractions = pJson.optInt("totalInteractions", 0),
                    successfulInteractions = pJson.optInt("successfulInteractions", 0)
                )
            }
        } catch (e: Exception) { }
    }
    
    private fun saveProfiles() {
        val json = JSONObject()
        profiles.forEach { (pkg, profile) ->
            json.put(pkg, JSONObject().apply {
                put("appName", profile.appName)
                put("avgResponseTime", profile.avgResponseTime)
                put("bestTapMethod", profile.bestTapMethod)
                put("knownIssues", JSONArray(profile.knownIssues))
                put("lastUsed", profile.lastUsed)
                put("successRate", profile.successRate.toDouble())
                put("totalInteractions", profile.totalInteractions)
                put("successfulInteractions", profile.successfulInteractions)
            })
        }
        profilesFile.writeText(json.toString())
    }
}
