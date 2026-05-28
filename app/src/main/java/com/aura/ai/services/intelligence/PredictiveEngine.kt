package com.aura.ai.services.intelligence

class PredictiveEngine(
    private val experienceDB: ExperienceDB,
    private val appProfileManager: com.aura.ai.services.orchestrator.AppProfileManager
) {
    
    data class Prediction(
        val willSucceed: Boolean,
        val confidence: Float,
        val estimatedDurationMs: Long,
        val likelyFailurePoint: String?,
        val recommendations: List<String>
    )
    
    fun predictTask(taskName: String, steps: List<String>): Prediction {
        val stepPredictions = steps.map { predictStep(it, "") }
        val overallConfidence = if (stepPredictions.isNotEmpty()) {
            stepPredictions.map { it.confidence }.average().toFloat()
        } else 0.5f
        
        val estimatedDuration = stepPredictions.sumOf { it.estimatedDurationMs }
        val failurePoints = stepPredictions.filter { it.confidence < 0.5f }.map { it.likelyFailurePoint ?: "Unknown" }
        val recommendations = stepPredictions.flatMap { it.recommendations }
        
        return Prediction(
            willSucceed = overallConfidence > 0.6f,
            confidence = overallConfidence,
            estimatedDurationMs = estimatedDuration,
            likelyFailurePoint = failurePoints.firstOrNull(),
            recommendations = recommendations.distinct()
        )
    }
    
    private fun predictStep(action: String, appPackage: String): Prediction {
        val successRate = experienceDB.getSuccessRate(action, appPackage)
        val shouldAvoid = experienceDB.shouldAvoid(action, appPackage)
        val bestMethod = experienceDB.getBestMethod(action, appPackage)
        
        val recommendations = mutableListOf<String>()
        if (shouldAvoid) recommendations.add("Avoid $action on $appPackage - low success rate")
        if (bestMethod != null) recommendations.add("Use proven method: ${bestMethod.take(100)}")
        
        return Prediction(
            willSucceed = !shouldAvoid,
            confidence = successRate,
            estimatedDurationMs = appProfileManager.getProfile(appPackage)?.avgResponseTime ?: 5000,
            likelyFailurePoint = if (shouldAvoid) "$action on $appPackage" else null,
            recommendations = recommendations
        )
    }
    
    fun preCheckEnvironment(): List<String> {
        val issues = mutableListOf<String>()
        val untrusted = appProfileManager.getUntrustedApps()
        if (untrusted.isNotEmpty()) {
            issues.add("⚠️ ${untrusted.size} apps have low reliability scores")
        }
        return issues
    }
    
    fun getTaskPreview(taskName: String, steps: List<String>): String {
        val prediction = predictTask(taskName, steps)
        val confidencePct = (prediction.confidence * 100).toInt()
        
        return """
🔮 TASK PREVIEW: $taskName
Confidence: $confidencePct%
Est. Duration: ${prediction.estimatedDurationMs / 1000}s
Likely Success: ${if (prediction.willSucceed) "✅ Yes" else "⚠️ Risky"}
Failure Risk: ${prediction.likelyFailurePoint ?: "None identified"}
${if (prediction.recommendations.isNotEmpty()) "\n💡 Recommendations:\n${prediction.recommendations.joinToString("\n") { "  • $it" }}" else ""}
        """.trimIndent()
    }
}
