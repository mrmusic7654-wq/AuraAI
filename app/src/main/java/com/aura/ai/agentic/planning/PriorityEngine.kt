package com.aura.ai.agentic.planning

data class PrioritizedAction(
    val action: String,
    val urgency: Int,       // 1-10, higher = more urgent
    val value: Int,         // 1-10, higher = more valuable
    val effort: Int,        // 1-10, higher = more effort needed
    val deadline: Long? = null,
    val dependencies: List<String> = emptyList(),
    val score: Float = 0f  // Calculated priority score
)

class PriorityEngine {
    
    fun getPriorities(state: com.aura.ai.agentic.core.AuraState): List<PrioritizedAction> {
        val actions = mutableListOf<PrioritizedAction>()
        
        // Check failing builds (high urgency)
        for (build in state.failingBuilds) {
            actions.add(PrioritizedAction(
                action = "Fix failing build: $build",
                urgency = 9,
                value = 8,
                effort = 4
            ))
        }
        
        // Check pending approvals (medium urgency)
        for (approval in state.pendingApprovals) {
            actions.add(PrioritizedAction(
                action = approval,
                urgency = 7,
                value = 6,
                effort = 2
            ))
        }
        
        // Check if idle (low urgency maintenance)
        if (state.mode == "IDLE") {
            actions.add(PrioritizedAction(
                action = "Scan repos for issues",
                urgency = 3,
                value = 5,
                effort = 3
            ))
            actions.add(PrioritizedAction(
                action = "Update dependencies",
                urgency = 2,
                value = 4,
                effort = 3
            ))
            actions.add(PrioritizedAction(
                action = "Optimize repository structure",
                urgency = 1,
                value = 3,
                effort = 5
            ))
        }
        
        // Calculate scores and sort
        return actions.map { action ->
            val score = calculateScore(action)
            action.copy(score = score)
        }.sortedByDescending { it.score }
    }
    
    private fun calculateScore(action: PrioritizedAction): Float {
        // Simple priority formula: (urgency * 0.5 + value * 0.3) / (effort * 0.2 + 1)
        val numerator = (action.urgency * 0.5f + action.value * 0.3f)
        val denominator = (action.effort * 0.2f + 1)
        var score = numerator / denominator
        
        // Boost score if deadline is approaching
        action.deadline?.let { deadline ->
            val timeLeft = deadline - System.currentTimeMillis()
            if (timeLeft < 3600000) { // Less than 1 hour
                score *= 2f
            } else if (timeLeft < 86400000) { // Less than 1 day
                score *= 1.5f
            }
        }
        
        return score.coerceIn(0f, 10f)
    }
    
    fun suggestBestTime(action: String, experienceBuffer: com.aura.ai.agentic.memory.ExperienceBuffer): String {
        val bestHour = experienceBuffer.getBestTimeForAction(action)
        return if (bestHour != null) {
            "Best time for this: ${bestHour}:00 based on past success"
        } else {
            "No timing preference detected"
        }
    }
}
