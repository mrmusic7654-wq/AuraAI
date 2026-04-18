package com.aura.ai.data.repository

import com.aura.ai.data.local.dao.AutomationRuleDao
import com.aura.ai.data.local.entities.AutomationRuleEntity
import com.aura.ai.data.models.AgentAction
import com.aura.ai.domain.repository.AutomationRepository
import com.aura.ai.domain.usecases.automation.AutomationRuleWithActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRepositoryImpl @Inject constructor(
    private val ruleDao: AutomationRuleDao
) : AutomationRepository {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override fun getAllRules(): Flow<List<AutomationRuleWithActions>> {
        return ruleDao.getAllRules().map { entities ->
            entities.mapNotNull { it.toDomainModel() }
        }
    }
    
    override fun getEnabledRules(): Flow<List<AutomationRuleWithActions>> {
        return ruleDao.getEnabledRules().map { entities ->
            entities.mapNotNull { it.toDomainModel() }
        }
    }
    
    override suspend fun getRuleById(ruleId: String): AutomationRuleWithActions? {
        return ruleDao.getRuleById(ruleId)?.toDomainModel()
    }
    
    override suspend fun createRule(
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
    
    override suspend fun updateRule(rule: AutomationRuleWithActions): Result<Unit> {
        return try {
            val entity = rule.toEntity()
            ruleDao.updateRule(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteRule(ruleId: String) {
        ruleDao.deleteRule(ruleId)
    }
    
    override suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        ruleDao.setRuleEnabled(ruleId, enabled)
    }
    
    override suspend fun updateLastTriggered(ruleId: String) {
        val rule = ruleDao.getRuleById(ruleId) ?: return
        val updatedRule = rule.copy(lastTriggered = System.currentTimeMillis())
        ruleDao.updateRule(updatedRule)
    }
    
    private fun AutomationRuleEntity.toDomainModel(): AutomationRuleWithActions? {
        return try {
            val actions = json.decodeFromString<List<AgentAction>>(actions)
            AutomationRuleWithActions(
                id = id,
                name = name,
                description = description,
                triggerApp = triggerApp,
                triggerText = triggerText,
                triggerTime = triggerTime,
                actions = actions,
                isEnabled = isEnabled,
                createdAt = createdAt,
                lastTriggered = lastTriggered
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun AutomationRuleWithActions.toEntity(): AutomationRuleEntity {
        return AutomationRuleEntity(
            id = id,
            name = name,
            description = description,
            triggerApp = triggerApp,
            triggerText = triggerText,
            triggerTime = triggerTime,
            actions = json.encodeToString(actions),
            isEnabled = isEnabled,
            createdAt = createdAt,
            lastTriggered = lastTriggered
        )
    }
}
