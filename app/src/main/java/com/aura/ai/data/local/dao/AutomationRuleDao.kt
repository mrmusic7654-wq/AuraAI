package com.aura.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aura.ai.data.local.entities.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {
    
    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<AutomationRuleEntity>>
    
    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getEnabledRules(): Flow<List<AutomationRuleEntity>>
    
    @Query("SELECT * FROM automation_rules WHERE id = :ruleId")
    suspend fun getRuleById(ruleId: String): AutomationRuleEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRuleEntity)
    
    @Update
    suspend fun updateRule(rule: AutomationRuleEntity)
    
    @Query("UPDATE automation_rules SET isEnabled = :enabled WHERE id = :ruleId")
    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean)
    
    @Query("DELETE FROM automation_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)
}
