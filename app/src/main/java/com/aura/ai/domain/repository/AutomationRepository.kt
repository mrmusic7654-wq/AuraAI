package com.aura.ai.domain.repository

import com.aura.ai.domain.usecases.automation.AutomationRuleWithActions
import kotlinx.coroutines.flow.Flow

interface AutomationRepository {
    
    fun getAllRules(): Flow<List<AutomationRuleWithActions>>
    
    fun getEnabledRules(): Flow<List<AutomationRuleWithActions>>
    
    suspend fun getRuleById(ruleId: String): AutomationRuleWithActions?
    
    suspend fun createRule(
        name: String,
        description: String?,
        triggerApp: String?,
        triggerText: String?,
        triggerTime: String?,
        actions: List<com.aura.ai.data.models.AgentAction>
    ): Result<String>
    
    suspend fun updateRule(rule: AutomationRuleWithActions): Result<Unit>
    
    suspend fun deleteRule(ruleId: String)
    
    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean)
    
    suspend fun updateLastTriggered(ruleId: String)
}
