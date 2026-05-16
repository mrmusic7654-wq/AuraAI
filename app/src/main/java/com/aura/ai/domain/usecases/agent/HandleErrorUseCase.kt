package com.aura.ai.domain.usecases.agent

import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.ActionType
import javax.inject.Inject

class HandleErrorUseCase @Inject constructor() {
    operator fun invoke(failedAction: AgentAction, errorMessage: String, attemptCount: Int): ErrorRecoveryPlan =
        if (attemptCount >= 3) ErrorRecoveryPlan.Abort else ErrorRecoveryPlan.Retry
}

sealed class ErrorRecoveryPlan {
    object Abort : ErrorRecoveryPlan()
    object Retry : ErrorRecoveryPlan()
}
