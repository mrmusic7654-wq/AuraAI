package com.aura.ai.domain.usecases.automation

import com.aura.ai.data.local.dao.AutomationRuleDao
import com.aura.ai.data.local.entities.AutomationRuleEntity
import com.aura.ai.data.models.AgentAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class CreateRuleUseCase @Inject constructor(
    private val ruleDao: AutomationRuleDao
) {
    
    private val json = Json { encodeDefaults = true }
    
    suspend operator fun invoke(
        name: String,
        description: String?,
        triggerApp: String?,
        triggerText: String?,
        triggerTime: String?,
        actions: List<AgentAction>
    ): Result<String> {
        return try {
            val actionsJson = json.encodeToString(actions)
            
            val rule = AutomationRuleEntity(
                name = name,
                description = description,
                triggerApp = triggerApp,
                triggerText = triggerText,
                triggerTime = triggerTime,
                actions = actionsJson,
                isEnabled = true
            )
            
            ruleDao.insertRule(rule)
            Result.success(rule.id)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
