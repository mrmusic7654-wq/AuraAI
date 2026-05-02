package com.aura.ai.domain.usecases.automation

import com.aura.ai.data.models.AgentAction
import com.aura.ai.domain.repository.AutomationRepository
import javax.inject.Inject

class CreateRuleUseCase @Inject constructor(
    private val repository: AutomationRepository
) {
    suspend operator fun invoke(
        name: String, description: String?, triggerApp: String?, triggerText: String?,
        triggerTime: String?, actions: List<AgentAction>
    ): Result<String> = repository.createRule(name, description, triggerApp, triggerText, triggerTime, actions)
}
