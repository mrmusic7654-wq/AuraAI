package com.aura.ai.domain.repository

import com.aura.ai.data.models.AgentAction
import kotlinx.coroutines.flow.Flow

data class AutomationRuleData(
    val id: String, val name: String, val description: String?, val triggerApp: String?,
    val triggerText: String?, val triggerTime: String?, val actions: List<AgentAction>,
    val isEnabled: Boolean, val createdAt: Long, val lastTriggered: Long?
)

interface AutomationRepository {
    fun getAllRules(): Flow<List<AutomationRuleData>>
    fun getEnabledRules(): Flow<List<AutomationRuleData>>
    suspend fun getRuleById(ruleId: String): AutomationRuleData?
    suspend fun createRule(name: String, description: String?, triggerApp: String?, triggerText: String?, triggerTime: String?, actions: List<AgentAction>): Result<String>
    suspend fun updateRule(rule: AutomationRuleData): Result<Unit>
    suspend fun deleteRule(ruleId: String)
    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean)
    suspend fun updateLastTriggered(ruleId: String)
}
