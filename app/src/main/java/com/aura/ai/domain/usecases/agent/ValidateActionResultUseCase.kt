package com.aura.ai.domain.usecases.agent

import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.ScreenContext
import javax.inject.Inject

class ValidateActionResultUseCase @Inject constructor() {
    
    operator fun invoke(
        action: AgentAction,
        beforeScreen: ScreenContext?,
        afterScreen: ScreenContext?
    ): ValidationResult {
        if (beforeScreen == null || afterScreen == null) {
            return ValidationResult.Unknown
        }
        
        return when (action.type) {
            com.aura.ai.data.models.ActionType.TAP,
            com.aura.ai.data.models.ActionType.FIND_AND_TAP -> {
                if (beforeScreen.packageName != afterScreen.packageName ||
                    beforeScreen.activityName != afterScreen.activityName) {
                    ValidationResult.Success
                } else if (beforeScreen.elements.size != afterScreen.elements.size) {
                    ValidationResult.Success
                } else {
                    ValidationResult.Unknown
                }
            }
            
            com.aura.ai.data.models.ActionType.OPEN_APP -> {
                if (action.packageName == afterScreen.packageName) {
                    ValidationResult.Success
                } else {
                    ValidationResult.Failure("Failed to open ${action.packageName}")
                }
            }
            
            com.aura.ai.data.models.ActionType.TYPE -> {
                // Can't easily validate typing without checking specific field
                ValidationResult.Success
            }
            
            com.aura.ai.data.models.ActionType.WAIT -> ValidationResult.Success
            
            else -> ValidationResult.Unknown
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    object Unknown : ValidationResult()
    data class Failure(val reason: String) : ValidationResult()
}
