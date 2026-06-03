package com.aura.ai.agentic.memory

data class UserPattern(
    val id: String = java.util.UUID.randomUUID().toString(),
    val patternType: String,  // "CODING_STYLE", "WORK_HOURS", "TOOL_PREFERENCE", "ERROR_PATTERN", "TASK_PATTERN"
    val description: String,
    val confidence: Float,
    val occurrences: Int,
    val lastObserved: Long = System.currentTimeMillis()
)

data class ActiveHours(
    val activeStart: Int = 8,  // 8 AM
    val activeEnd: Int = 22     // 10 PM
)

class PatternRecognizer {
    
    private val patterns = mutableListOf<UserPattern>()
    private val actionHistory = mutableListOf<Pair<String, Long>>()
    private var activeHours = ActiveHours()
    
    fun observeAction(action: String) {
        actionHistory.add(Pair(action, System.currentTimeMillis()))
        if (actionHistory.size > 200) actionHistory.removeAt(0)
        
        // Update active hours
        updateActiveHours()
        
        // Check for new patterns
        detectPatterns()
    }
    
    fun getActivePatterns(): List<UserPattern> {
        return patterns.filter { it.confidence > 0.5f }.sortedByDescending { it.confidence }
    }
    
    fun matchesUserPattern(action: String): Boolean {
        val relevantPatterns = patterns.filter { it.patternType == "TASK_PATTERN" || it.patternType == "TOOL_PREFERENCE" }
        return relevantPatterns.any { pattern ->
            val keywords = pattern.description.lowercase().split(" ")
            keywords.any { action.lowercase().contains(it) }
        }
    }
    
    fun getActiveHours(): ActiveHours = activeHours
    
    private fun updateActiveHours() {
        if (actionHistory.size < 10) return
        
        val hours = actionHistory.map {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.second
            cal.get(java.util.Calendar.HOUR_OF_DAY)
        }
        
        val sorted = hours.sorted()
        val q1 = sorted[sorted.size / 4]
        val q3 = sorted[sorted.size * 3 / 4]
        
        activeHours = ActiveHours(activeStart = q1, activeEnd = q3.coerceAtMost(23))
    }
    
    private fun detectPatterns() {
        // Detect coding style patterns
        detectCodingPatterns()
        
        // Detect tool preferences
        detectToolPreferences()
        
        // Detect task patterns
        detectTaskPatterns()
        
        // Detect error patterns
        detectErrorPatterns()
    }
    
    private fun detectCodingPatterns() {
        val recentActions = actionHistory.takeLast(50).map { it.first }
        
        // Check for Compose usage
        val composeCount = recentActions.count { it.contains("compose", ignoreCase = true) || it.contains("Composable", ignoreCase = true) }
        if (composeCount >= 5) {
            updateOrAddPattern("CODING_STYLE", "Prefers Jetpack Compose", 0.8f)
        }
        
        // Check for MVVM pattern
        val mvvmCount = recentActions.count { it.contains("ViewModel", ignoreCase = true) || it.contains("mvvm", ignoreCase = true) }
        if (mvvmCount >= 5) {
            updateOrAddPattern("CODING_STYLE", "Uses MVVM architecture", 0.8f)
        }
        
        // Check for Room database
        val roomCount = recentActions.count { it.contains("Room", ignoreCase = true) || it.contains("database", ignoreCase = true) }
        if (roomCount >= 5) {
            updateOrAddPattern("CODING_STYLE", "Always adds Room database", 0.9f)
        }
    }
    
    private fun detectToolPreferences() {
        val recentActions = actionHistory.takeLast(30).map { it.first }
        
        // Check for DeepSeek preference
        val deepseekCount = recentActions.count { it.contains("deepseek", ignoreCase = true) }
        if (deepseekCount >= 5) {
            updateOrAddPattern("TOOL_PREFERENCE", "Prefers DeepSeek for code generation", 0.85f)
        }
        
        // Check for GitHub usage
        val githubCount = recentActions.count { it.contains("github", ignoreCase = true) || it.contains("repo", ignoreCase = true) }
        if (githubCount >= 5) {
            updateOrAddPattern("TOOL_PREFERENCE", "Heavy GitHub user", 0.9f)
        }
    }
    
    private fun detectTaskPatterns() {
        val hourlyActions = actionHistory.groupBy {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.second
            cal.get(java.util.Calendar.HOUR_OF_DAY)
        }
        
        // Find peak activity hours
        val peakHour = hourlyActions.maxByOrNull { it.value.size }
        if (peakHour != null && peakHour.value.size >= 5) {
            updateOrAddPattern("TASK_PATTERN", "Most active at ${peakHour.key}:00", 0.7f)
        }
        
        // Check for common task types
        val taskTypes = actionHistory.takeLast(50).map { it.first }.groupBy { action ->
            when {
                action.contains("create app", ignoreCase = true) || action.contains("generate", ignoreCase = true) -> "CREATING"
                action.contains("fix", ignoreCase = true) || action.contains("debug", ignoreCase = true) -> "FIXING"
                action.contains("build", ignoreCase = true) || action.contains("compile", ignoreCase = true) -> "BUILDING"
                action.contains("deploy", ignoreCase = true) || action.contains("push", ignoreCase = true) -> "DEPLOYING"
                else -> "OTHER"
            }
        }
        
        val mostCommon = taskTypes.maxByOrNull { it.value.size }
        if (mostCommon != null && mostCommon.value.size >= 5) {
            updateOrAddPattern("TASK_PATTERN", "Most common task: ${mostCommon.key}", 0.7f)
        }
    }
    
    private fun detectErrorPatterns() {
        val recentErrors = actionHistory.takeLast(30).map { it.first }.filter { it.contains("error", ignoreCase = true) || it.contains("fail", ignoreCase = true) || it.contains("❌") }
        
        if (recentErrors.size >= 5) {
            val errorTypes = recentErrors.groupBy { error ->
                when {
                    error.contains("import", ignoreCase = true) -> "Import errors"
                    error.contains("dependency", ignoreCase = true) -> "Dependency issues"
                    error.contains("build", ignoreCase = true) -> "Build failures"
                    error.contains("permission", ignoreCase = true) -> "Permission errors"
                    else -> "Other errors"
                }
            }
            
            val mostCommonError = errorTypes.maxByOrNull { it.value.size }
            if (mostCommonError != null && mostCommonError.value.size >= 3) {
                updateOrAddPattern("ERROR_PATTERN", "Common error: ${mostCommonError.key}", 0.6f)
            }
        }
    }
    
    private fun updateOrAddPattern(type: String, description: String, confidence: Float) {
        val existing = patterns.find { it.patternType == type && it.description == description }
        if (existing != null) {
            val index = patterns.indexOf(existing)
            patterns[index] = existing.copy(
                confidence = (existing.confidence + confidence) / 2,
                occurrences = existing.occurrences + 1,
                lastObserved = System.currentTimeMillis()
            )
        } else {
            patterns.add(UserPattern(patternType = type, description = description, confidence = confidence, occurrences = 1))
        }
    }
    
    fun getPatternReport(): String {
        val active = getActivePatterns()
        if (active.isEmpty()) return "No strong patterns detected yet. Learning..."
        
        return buildString {
            append("🧠 LEARNED PATTERNS:\n")
            active.forEach { pattern ->
                append("• [${pattern.patternType}] ${pattern.description} (${(pattern.confidence * 100).toInt()}% confidence, ${pattern.occurrences}x)\n")
            }
        }
    }
}
