package com.aura.ai.data.repository

import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.models.*
import com.aura.ai.domain.repository.AgentRepository
import com.aura.ai.domain.repository.TaskRepository
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.ScreenStateManager
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepositoryImpl @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val taskRepository: TaskRepository,
    private val screenStateManager: ScreenStateManager,
    private val preferences: AuraPreferences
) : AgentRepository {

    private var accessibilityService: AuraAccessibilityService? = null
    private val _activeTask = MutableStateFlow<Task?>(null)

    override fun setAccessibilityService(service: AuraAccessibilityService?) { this.accessibilityService = service }
    override fun getActiveTask(): Flow<Task?> = _activeTask.asStateFlow()

    override suspend fun executeTask(taskDescription: String): Flow<TaskExecutionUpdate> = flow {
        emit(TaskExecutionUpdate.Starting(taskDescription))
        val task = Task(description = taskDescription, status = TaskStatus.EXECUTING)
        _activeTask.value = task
        emit(TaskExecutionUpdate.Completed(task))
    }

    override suspend fun planTask(taskDescription: String): List<AgentAction> = emptyList()
    override suspend fun continueTask(taskId: String): Flow<TaskExecutionUpdate> = flow { emit(TaskExecutionUpdate.Error("Not implemented")) }
    override suspend fun cancelTask(taskId: String) { _activeTask.value = null }
}
