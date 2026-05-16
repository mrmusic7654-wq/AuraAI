package com.aura.ai.domain.repository

import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.ScreenContext
import com.aura.ai.data.models.Task
import com.aura.ai.services.AuraAccessibilityService
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    fun getActiveTask(): Flow<Task?>
    suspend fun executeTask(taskDescription: String): Flow<TaskExecutionUpdate>
    suspend fun planTask(taskDescription: String): List<AgentAction>
    suspend fun continueTask(taskId: String): Flow<TaskExecutionUpdate>
    suspend fun cancelTask(taskId: String)
    fun setAccessibilityService(service: AuraAccessibilityService?)
}

sealed class TaskExecutionUpdate {
    data class Starting(val description: String) : TaskExecutionUpdate()
    data class TaskCreated(val task: Task) : TaskExecutionUpdate()
    data class ScreenCaptured(val screenContext: ScreenContext?) : TaskExecutionUpdate()
    object Planning : TaskExecutionUpdate()
    data class ActionsPlanned(val actions: List<AgentAction>) : TaskExecutionUpdate()
    data class ExecutingAction(val index: Int, val action: AgentAction) : TaskExecutionUpdate()
    data class Paused(val task: Task) : TaskExecutionUpdate()
    data class Completed(val task: Task) : TaskExecutionUpdate()
    data class Error(val message: String) : TaskExecutionUpdate()
}
