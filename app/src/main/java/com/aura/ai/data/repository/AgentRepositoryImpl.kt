package com.aura.ai.data.repository

import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.models.*
import com.aura.ai.domain.repository.AgentRepository
import com.aura.ai.domain.repository.TaskExecutionUpdate
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

    private var service: AuraAccessibilityService? = null
    private val _task = MutableStateFlow<Task?>(null)

    override fun setAccessibilityService(s: AuraAccessibilityService?) { service = s }
    override fun getActiveTask(): Flow<Task?> = _task.asStateFlow()

    override suspend fun executeTask(desc: String): Flow<TaskExecutionUpdate> = flow {
        emit(TaskExecutionUpdate.Starting(desc))
        val t = Task(description = desc)
        _task.value = t
        emit(TaskExecutionUpdate.Completed(t))
    }
    override suspend fun planTask(d: String) = emptyList<AgentAction>()
    override suspend fun continueTask(id: String): Flow<TaskExecutionUpdate> = flow { emit(TaskExecutionUpdate.Error("N/I")) }
    override suspend fun cancelTask(id: String) { _task.value = null }
}
