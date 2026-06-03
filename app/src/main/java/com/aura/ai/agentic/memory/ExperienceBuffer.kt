package com.aura.ai.agentic.memory

data class Experience(
    val id: String = java.util.UUID.randomUUID().toString(),
    val action: String,
    val context: String,
    val result: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val apiCallsUsed: Int = 0
)

class ExperienceBuffer(private val maxSize: Int = 100) {
    
    private val experiences = mutableListOf<Experience>()
    
    fun recordExperience(action: String, context: String, result: String, success: Boolean, durationMs: Long = 0, apiCallsUsed: Int = 0) {
        val experience = Experience(
            action = action,
            context = context,
            result = result,
            success = success,
            durationMs = durationMs,
            apiCallsUsed = apiCallsUsed
        )
        
        experiences.add(experience)
        
        // Keep buffer within size limit
        if (experiences.size > maxSize) {
            experiences.removeAt(0)
        }
    }
    
    fun recordSuccess(action: String, result: String = "Success") {
        recordExperience(action, "", result, true)
    }
    
    fun recordFailure(action: String, error: String) {
        recordExperience(action, "", error, false)
    }
    
    fun getSimilarExperience(action: String): Experience? {
        val keywords = action.lowercase().split(" ").filter { it.length > 3 }
        
        return experiences
            .filter { exp ->
                keywords.any { exp.action.lowercase().contains(it) }
            }
            .maxByOrNull { it.timestamp }
    }
    
    fun getSuccessRate(action: String): Float {
        val similar = experiences.filter { it.action.contains(action, ignoreCase = true) }
        if (similar.isEmpty()) return 0.5f
        
        val successes = similar.count { it.success }
        return successes.toFloat() / similar.size
    }
    
    fun shouldAvoid(action: String): Boolean {
        val rate = getSuccessRate(action)
        val count = experiences.count { it.action.contains(action, ignoreCase = true) }
        return count >= 3 && rate < 0.3f
    }
    
    fun shouldAutoApprove(action: String): Boolean {
        val rate = getSuccessRate(action)
        val count = experiences.count { it.action.contains(action, ignoreCase = true) }
        return count >= 5 && rate > 0.8f
    }
    
    fun getBestTimeForAction(action: String): Int? {
        val successes = experiences.filter { it.action.contains(action, ignoreCase = true) && it.success }
        if (successes.isEmpty()) return null
        
        // Find which hour had most successes
        val hourCounts = successes.groupBy {
            java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
                .get(java.util.Calendar.HOUR_OF_DAY)
        }
        
        return hourCounts.maxByOrNull { it.value.size }?.key
    }
    
    fun getStats(): String {
        val total = experiences.size
        val successRate = if (total > 0) experiences.count { it.success }.toFloat() / total else 0f
        
        return """
📊 Experience Buffer:
Total experiences: $total
Success rate: ${(successRate * 100).toInt()}%
Recent: ${experiences.takeLast(3).joinToString(", ") { if (it.success) "✅ ${it.action.take(30)}" else "❌ ${it.action.take(30)}" }}
        """.trimIndent()
    }
}
