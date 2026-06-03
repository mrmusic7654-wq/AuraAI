 package com.aura.ai.agentic.core

import com.aura.ai.agentic.core.Consciousness
import com.aura.ai.agentic.memory.ExperienceBuffer
import com.aura.ai.agentic.memory.KnowledgeGraph
import com.aura.ai.agentic.memory.PatternRecognizer
import com.aura.ai.agentic.planning.PriorityEngine
import com.aura.ai.agentic.planning.ResourcePlanner
import com.aura.ai.agentic.planning.TaskDecomposer
import com.aura.ai.agentic.execution.AutonomousExecutor
import com.aura.ai.agentic.execution.TrustManager
import kotlinx.coroutines.*

data class BrainDecision(
    val shouldAct: Boolean,
    val reasoning: String,
    val suggestedAction: String,
    val confidence: Float,
    val requiresApproval: Boolean
)

class AuraBrain(
    private val consciousness: Consciousness,
    private val patternRecognizer: PatternRecognizer,
    private val priorityEngine: PriorityEngine,
    private val resourcePlanner: ResourcePlanner,
    private val goalEngine: GoalEngine,
    private val taskDecomposer: TaskDecomposer,
    private val autonomousExecutor: AutonomousExecutor,
    private val trustManager: TrustManager,
    private val experienceBuffer: ExperienceBuffer,
    private val knowledgeGraph: KnowledgeGraph
) {
    
    suspend fun think(): BrainDecision {
        // Step 1: What's happening right now?
        val currentState = consciousness.getCurrentState()
        
        // Step 2: What have I learned about the user?
        val userPatterns = patternRecognizer.getActivePatterns()
        
        // Step 3: What needs attention?
        val priorities = priorityEngine.getPriorities(currentState)
        
        // Step 4: Can I afford to act?
        val resources = resourcePlanner.canAct()
        
        // Step 5: Should I act now?
        val shouldAct = priorities.isNotEmpty() && resources.canProceed
        
        if (!shouldAct) {
            return BrainDecision(
                shouldAct = false,
                reasoning = if (priorities.isEmpty()) "No pending tasks" else resources.reason,
                suggestedAction = "IDLE",
                confidence = 0.9f,
                requiresApproval = false
            )
        }
        
        // Step 6: What's the best action?
        val topPriority = priorities.first()
        
        // Step 7: Is it something the user would want?
        val matchesPattern = patternRecognizer.matchesUserPattern(topPriority.action)
        
        // Step 8: Check experience - did this work before?
        val pastExperience = experienceBuffer.getSimilarExperience(topPriority.action)
        val pastSuccess = pastExperience?.success ?: true
        
        // Step 9: Trust level for this action
        val trustLevel = trustManager.getTrustLevel(topPriority.action)
        
        // Step 10: Build reasoning
        val reasoning = buildString {
            append("State: ${currentState.mode}. ")
            append("Priority: ${topPriority.action} (urgency: ${topPriority.urgency}). ")
            if (matchesPattern) append("Matches user pattern. ")
            if (!pastSuccess) append("⚠️ Failed before. ")
            append("Trust: $trustLevel.")
        }
        
        return BrainDecision(
            shouldAct = true,
            reasoning = reasoning,
            suggestedAction = topPriority.action,
            confidence = if (matchesPattern && pastSuccess) 0.9f else 0.6f,
            requiresApproval = trustLevel == TrustManager.TrustLevel.ASK_FIRST
        )
    }
    
    suspend fun executeDecision(decision: BrainDecision): String {
        if (!decision.shouldAct) return "IDLE"
        
        consciousness.updateStatus("EXECUTING", decision.suggestedAction)
        
        val result = try {
            if (decision.requiresApproval) {
                "PENDING_APPROVAL: ${decision.suggestedAction}"
            } else {
                autonomousExecutor.execute(decision.suggestedAction)
                consciousness.updateStatus("IDLE", "Task complete")
                "SUCCESS: ${decision.suggestedAction}"
            }
        } catch (e: Exception) {
            consciousness.updateStatus("ERROR", e.message ?: "Unknown error")
            experienceBuffer.recordFailure(decision.suggestedAction, e.message ?: "")
            "FAILED: ${e.message}"
        }
        
        return result
    }
}
