package com.aura.ai.data.local.dao

import androidx.room.*
import com.aura.ai.data.local.entities.SwarmAgentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SwarmAgentDao {

    @Query("SELECT * FROM swarm_agents ORDER BY createdAt DESC")
    fun getAllAgents(): Flow<List<SwarmAgentEntity>>

    @Query("SELECT * FROM swarm_agents WHERE status = 'active'")
    fun getActiveAgents(): Flow<List<SwarmAgentEntity>>

    @Query("SELECT * FROM swarm_agents WHERE id = :agentId")
    suspend fun getAgentById(agentId: String): SwarmAgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: SwarmAgentEntity)

    @Update
    suspend fun updateAgent(agent: SwarmAgentEntity)

    @Query("DELETE FROM swarm_agents WHERE id = :agentId")
    suspend fun deleteAgent(agentId: String)

    @Query("DELETE FROM swarm_agents")
    suspend fun clearAll()
}
