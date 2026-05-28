package com.aura.ai.services.intelligence

import android.content.Context
import org.json.JSONObject
import java.io.File

data class UserPreferences(
    val preferredModel: String = "gemini-2.5-flash",
    val preferredCodingStyle: String = "MVVM",
    val preferredTheme: String = "Dark",
    val verboseMode: Boolean = false,
    val autoCompress: Boolean = true,
    val screenshotInterval: Int = 30,
    val favoriteApps: List<String> = emptyList(),
    val workHours: Pair<Int, Int> = Pair(9, 18),
    val commonPhrases: Map<String, String> = emptyMap()
)

class UserPreferenceLearner(context: Context) {
    
    private val prefsDir = File(context.filesDir, "user_prefs")
    private val prefsFile = File(prefsDir, "learned_prefs.json")
    private var preferences = UserPreferences()
    
    init {
        prefsDir.mkdirs()
        loadPreferences()
    }
    
    fun learnFromCommand(command: String) {
        val updated = preferences.copy()
        
        // Learn preferred model from commands like "use gemini-2.5-flash"
        Regex("use (\\S+)").find(command)?.let {
            val model = it.groupValues[1]
            if (model.contains("gemini")) {
                preferences = updated.copy(preferredModel = model)
            }
        }
        
        // Learn work hours from usage patterns
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour < preferences.workHours.first) {
            preferences = updated.copy(workHours = Pair(hour, preferences.workHours.second))
        }
        if (hour > preferences.workHours.second) {
            preferences = updated.copy(workHours = Pair(preferences.workHours.first, hour))
        }
        
        savePreferences()
    }
    
    fun learnAppUsage(packageName: String) {
        val updatedFavorites = preferences.favoriteApps.toMutableList()
        if (!updatedFavorites.contains(packageName)) {
            updatedFavorites.add(packageName)
            if (updatedFavorites.size > 20) updatedFavorites.removeAt(0)
            preferences = preferences.copy(favoriteApps = updatedFavorites)
            savePreferences()
        }
    }
    
    fun getPreferences(): UserPreferences = preferences
    
    fun getPersonalizedGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        return "$greeting! I'm using ${preferences.preferredModel} in ${preferences.preferredTheme} mode."
    }
    
    fun shouldBeQuiet(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour < preferences.workHours.first || hour > preferences.workHours.second
    }
    
    private fun loadPreferences() {
        if (!prefsFile.exists()) return
        try {
            val json = JSONObject(prefsFile.readText())
            preferences = UserPreferences(
                preferredModel = json.optString("preferredModel", "gemini-2.5-flash"),
                preferredCodingStyle = json.optString("preferredCodingStyle", "MVVM"),
                preferredTheme = json.optString("preferredTheme", "Dark"),
                verboseMode = json.optBoolean("verboseMode", false),
                autoCompress = json.optBoolean("autoCompress", true),
                screenshotInterval = json.optInt("screenshotInterval", 30),
                workHours = Pair(json.optInt("workStart", 9), json.optInt("workEnd", 18))
            )
        } catch (e: Exception) { }
    }
    
    private fun savePreferences() {
        val json = JSONObject().apply {
            put("preferredModel", preferences.preferredModel)
            put("preferredCodingStyle", preferences.preferredCodingStyle)
            put("preferredTheme", preferences.preferredTheme)
            put("verboseMode", preferences.verboseMode)
            put("autoCompress", preferences.autoCompress)
            put("screenshotInterval", preferences.screenshotInterval)
            put("workStart", preferences.workHours.first)
            put("workEnd", preferences.workHours.second)
        }
        prefsFile.writeText(json.toString())
    }
}
