package com.aura.ai.services.intelligence

class TaskSimulator(
    private val predictiveEngine: PredictiveEngine,
    private val resourceManager: com.aura.ai.services.orchestrator.ResourceManager,
    private val appProfileManager: com.aura.ai.services.orchestrator.AppProfileManager
) {
    
    data class SimulationResult(
        val taskName: String,
        val totalSteps: Int,
        val estimatedDurationMs: Long,
        val estimatedApiCalls: Int,
        val estimatedBatteryDrain: Int,
        val confidence: Float,
        val warnings: List<String>,
        val recommendations: List<String>,
        val canProceed: Boolean
    )
    
    suspend fun simulateTask(taskName: String, steps: List<String>): SimulationResult {
        val prediction = predictiveEngine.predictTask(taskName, steps)
        val resourceState = resourceManager.getCurrentState()
        
        val estimatedApiCalls = steps.size // Rough estimate
        val estimatedBatteryDrain = (steps.size * 0.5).toInt() // ~0.5% per step
        
        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Resource warnings
        if (resourceState.batteryLevel < estimatedBatteryDrain + 10 && !resourceState.batteryCharging) {
            warnings.add("⚠️ Battery may not last the full task (${resourceState.batteryLevel}% remaining)")
            recommendations.add("Plug in charger before starting")
        }
        
        if (resourceState.storageFreeMB < 500) {
            warnings.add("⚠️ Low storage (${resourceState.storageFreeMB}MB free)")
            recommendations.add("Clean old files before starting")
        }
        
        if (resourceState.isNetworkMetered) {
            warnings.add("⚠️ On metered network - data charges may apply")
        }
        
        // Confidence warnings
        if (prediction.confidence < 0.5f) {
            warnings.add("⚠️ Low confidence prediction (${(prediction.confidence * 100).toInt()}%)")
            recommendations.add("Consider reviewing step: ${prediction.likelyFailurePoint}")
        }
        
        if (prediction.likelyFailurePoint != null) {
            recommendations.add("Pre-check: ${prediction.likelyFailurePoint}")
        }
        
        // App availability check
        val appSteps = steps.filter { it.contains("open", ignoreCase = true) }
        for (step in appSteps) {
            val appName = step.substringAfter("open").trim()
            val profile = appProfileManager.getProfile(appName)
            if (profile == null) {
                warnings.add("⚠️ No profile for '$appName' - may be unreliable")
            } else if (profile.successRate < 0.5f) {
                warnings.add("⚠️ '$appName' has low reliability (${(profile.successRate * 100).toInt()}%)")
            }
        }
        
        return SimulationResult(
            taskName = taskName,
            totalSteps = steps.size,
            estimatedDurationMs = prediction.estimatedDurationMs,
            estimatedApiCalls = estimatedApiCalls,
            estimatedBatteryDrain = estimatedBatteryDrain,
            confidence = prediction.confidence,
            warnings = warnings,
            recommendations = recommendations,
            canProceed = warnings.isEmpty() || prediction.confidence > 0.4f
        )
    }
    
    fun getSimulationReport(result: SimulationResult): String {
        return """
🔮 SIMULATION: ${result.taskName}
─────────────────────────────
📊 Steps: ${result.totalSteps}
⏱️ Est. Duration: ${result.estimatedDurationMs / 1000}s
📡 Est. API Calls: ${result.estimatedApiCalls}
🔋 Est. Battery: -${result.estimatedBatteryDrain}%
📈 Confidence: ${(result.confidence * 100).toInt()}%
${if (result.warnings.isNotEmpty()) "\n⚠️ WARNINGS:\n${result.warnings.joinToString("\n") { "  $it" }}" else ""}
${if (result.recommendations.isNotEmpty()) "\n💡 RECOMMENDATIONS:\n${result.recommendations.joinToString("\n") { "  $it" }}" else ""}
─────────────────────────────
${if (result.canProceed) "✅ Ready to proceed" else "❌ Not recommended - fix issues first"}
        """.trimIndent()
    }
}
