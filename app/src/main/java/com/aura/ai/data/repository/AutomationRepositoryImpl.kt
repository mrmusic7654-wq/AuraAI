package com.aura.ai.data.repository

import com.aura.ai.data.local.dao.AutomationRuleDao
import com.aura.ai.data.local.entities.AutomationRuleEntity
import com.aura.ai.data.models.AgentAction
import com.aura.ai.domain.repository.AutomationRepository
import com.aura.ai.domain.repository.AutomationRuleData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRepositoryImpl @Inject constructor(
    private val ruleDao: AutomationRuleDao
) : AutomationRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun getAllRules(): Flow<List<AutomationRuleData>> = ruleDao.getAllRules().map { list -> list.mapNotNull { it.toDomain() } }
    override fun getEnabledRules(): Flow<List<AutomationRuleData>> = ruleDao.getEnabledRules().map { list -> list.mapNotNull { it.toDomain() } }
    override suspend fun getRuleById(ruleId: String): AutomationRuleData? = ruleDao.getRuleById(ruleId)?.toDomain()

    override suspend fun createRule(name: String, description: String?, triggerApp: String?, triggerText: String?, triggerTime: String?, actions: List<AgentAction>): Result<String> {
        return try {
            val entity = AutomationRuleEntity(
                id = UUID.randomUUID().toString(), name = name, description = description,
                triggerApp = triggerApp, triggerText = triggerText, triggerTime = triggerTime,
                actions = json.encodeToString(actions)
            )
            ruleDao.insertRule(entity)
            Result.success(entity.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun updateRule(rule: AutomationRuleData): Result<Unit> = try {
        ruleDao.updateRule(AutomationRuleEntity(rule.id, rule.name, rule.description, rule.triggerApp, rule.triggerText, rule.triggerTime, json.encodeToString(rule.actions), rule.isEnabled, rule.createdAt, rule.lastTriggered))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun deleteRule(ruleId: String) { ruleDao.deleteRule(ruleId) }
    override suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) { ruleDao.setRuleEnabled(ruleId, enabled) }
    override suspend fun updateLastTriggered(ruleId: String) {}

    private fun AutomationRuleEntity.toDomain(): AutomationRuleData? = try {
        AutomationRuleData(id, name, description, triggerApp, triggerText, triggerTime, json.decodeFromString(actions), isEnabled, createdAt, lastTriggered)
    } catch (e: Exception) { null }
}
