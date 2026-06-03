package com.aura.ai.agentic.core

import com.aura.ai.agentic.memory.Consciousness
import com.aura.ai.agentic.memory.PatternRecognizer
import kotlinx.coroutines.*

class SelfPromptLoop(
    private val auraBrain: AuraBrain,
    private val consciousness: Consciousness,
    private val patternRecognizer: PatternRecognizer,
    private val onDecisionReady: (BrainDecision) -> Unit,
    private val onApprovalNeeded: (String) -> Unit
) {
    
    private var loopJob: Job? = null
    private var isActive = false
    private val decisionHistory = mutableListOf<BrainDecision>()
    
    // Adapts interval based on time of day and user activity
    fun getAdaptiveInterval(): Long {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val userPattern = patternRecognizer.getActiveHours()
        
        return when {
            hour in userPattern.activeStart..userPattern.activeEnd -> 120_000  // 2 min during active hours
            hour in 22..23 || hour in 0..6 -> 600_000  // 10 min during sleep
            else -> 300_000  // 5 min default
        }
    }
    
    fun start() {
        if (isActive) return
        isActive = true
        
        loopJob = CoroutineScope(Dispatchers.IO).launch {
            consciousness.updateStatus("AGENTIC", "Self-prompting loop active")
            
            while (isActive) {
                try {
                    // Think phase
                    val decision = auraBrain.think()
                    decisionHistory.add(decision)
                    
                    if (decision.shouldAct) {
                        if (decision.requiresApproval) {
                            onApprovalNeeded(decision.suggestedAction)
                        } else {
                            onDecisionReady(decision)
                            auraBrain.executeDecision(decision)
                        }
                    }
                    
                    // Adaptive wait
                    val interval = getAdaptiveInterval()
                    delay(interval)
                    
                } catch (e: Exception) {
                    consciousness.updateStatus("ERROR", "Loop error: ${e.message}")
                    delay(300_000) // Wait 5 min on error
                }
            }
        }
    }
    
    fun stop() {
        isActive = false
        loopJob?.cancel()
        consciousness.updateStatus("IDLE", "Self-prompting stopped")
    }
    
    fun forceThink(): BrainDecision {
        return runBlocking { auraBrain.think() }
    }
    
    fun getDecisionHistory(limit: Int = 10): List<BrainDecision> {
        return decisionHistory.takeLast(limit)
    }
    
    fun getStatus(): String {
        return if (isActive) "Agentic mode: ACTIVE (${getAdaptiveInterval()/1000}s interval)"
        else "Agentic mode: INACTIVE"
    }
}
