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
    private val dao: AutomationRuleDao
) : AutomationRepository {
    private val j = Json { ignoreUnknownKeys = true }
    
    override fun getAllRules(): Flow<List<AutomationRuleData>> = dao.getAllRules().map { it.mapNotNull { e -> e.toD() } }
    override fun getEnabledRules(): Flow<List<AutomationRuleData>> = dao.getEnabledRules().map { it.mapNotNull { e -> e.toD() } }
    override suspend fun getRuleById(id: String) = dao.getRuleById(id)?.toD()
    
    override suspend fun createRule(n: String, d: String?, a: String?, t: String?, tm: String?, ac: List<AgentAction>) = try {
        val e = AutomationRuleEntity(UUID.randomUUID().toString(), n, d, a, t, tm, j.encodeToString(ac))
        dao.insertRule(e); Result.success(e.id)
    } catch (ex: Exception) { Result.failure(ex) }
    
    override suspend fun updateRule(r: AutomationRuleData) = try {
        dao.updateRule(r.toE()); Result.success(Unit)
    } catch (ex: Exception) { Result.failure(ex) }
    
    override suspend fun deleteRule(id: String) { dao.deleteRule(id) }
    override suspend fun setRuleEnabled(id: String, en: Boolean) { dao.setRuleEnabled(id, en) }
    override suspend fun updateLastTriggered(id: String) {}
    
    private fun AutomationRuleEntity.toD() = try { AutomationRuleData(id, name, description, triggerApp, triggerText, triggerTime, j.decodeFromString(actions), isEnabled, createdAt, lastTriggered) } catch (e: Exception) { null }
    private fun AutomationRuleData.toE() = AutomationRuleEntity(id, name, description, triggerApp, triggerText, triggerTime, j.encodeToString(actions), isEnabled, createdAt, lastTriggered)
}
