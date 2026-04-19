package com.aura.ai.domain.repository

import com.aura.ai.data.models.Task
import com.aura.ai.data.repository.TaskExecutionUpdate
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    
    suspend fun executeTask(taskDescription: String): Flow<TaskExecutionUpdate>
    
    suspend fun planTask(taskDescription: String): List<com.aura.ai.data.models.AgentAction>
    
    suspend fun continueTask(taskId: String): Flow<TaskExecutionUpdate>
    
    suspend fun cancelTask(taskId: String)
    
    fun getActiveTask(): Flow<Task?>
    
    fun setAccessibilityService(service: com.aura.ai.services.AuraAccessibilityService?)
}
