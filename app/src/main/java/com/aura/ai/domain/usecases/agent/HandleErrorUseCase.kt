package com.aura.ai.domain.usecases.agent

import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.ActionType
import javax.inject.Inject

class HandleErrorUseCase @Inject constructor() {
    
    operator fun invoke(
        failedAction: AgentAction,
        errorMessage: String,
        attemptCount: Int
    ): ErrorRecoveryPlan {
        
        // Max retries exceeded
        if (attemptCount >= 3) {
            return ErrorRecoveryPlan.Abort("Max retry attempts exceeded: $errorMessage")
        }
        
        return when (failedAction.type) {
            ActionType.TAP,
            ActionType.FIND_AND_TAP -> {
                // Try swiping down to reveal hidden elements
                ErrorRecoveryPlan.RetryWithAlternate(
                    alternateAction = AgentAction(
                        type = ActionType.SWIPE,
                        startX = 540f,
                        startY = 1000f,
                        endX = 540f,
                        endY = 500f,
                        duration = 300
                    ),
                    reason = "Element not found, scrolling down"
                )
            }
            
            ActionType.OPEN_APP -> {
                // Try going home and opening again
                ErrorRecoveryPlan.RetryWithPreActions(
                    preActions = listOf(
                        AgentAction(type = ActionType.HOME),
                        AgentAction(type = ActionType.WAIT, duration = 500)
                    ),
                    reason = "App launch failed, retrying from home"
                )
            }
            
            else -> {
                // Wait and retry same action
                ErrorRecoveryPlan.RetrySame(
                    delayMs = 1000,
                    reason = "Action failed, retrying after delay"
                )
            }
        }
    }
}

sealed class ErrorRecoveryPlan {
    object Abort : ErrorRecoveryPlan()
    data class Abort(val reason: String) : ErrorRecoveryPlan()
    data class RetrySame(val delayMs: Long, val reason: String) : ErrorRecoveryPlan()
    data class RetryWithAlternate(val alternateAction: AgentAction, val reason: String) : ErrorRecoveryPlan()
    data class RetryWithPreActions(val preActions: List<AgentAction>, val reason: String) : ErrorRecoveryPlan()
}
