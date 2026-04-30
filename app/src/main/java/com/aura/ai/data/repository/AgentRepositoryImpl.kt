package com.aura.ai.data.repository

import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.models.*
import com.aura.ai.domain.repository.AgentRepository
import com.aura.ai.domain.repository.TaskRepository
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.ScreenStateManager
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import timber.log.Timber
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
    private val isPaused = MutableStateFlow(false)

    override fun setAccessibilityService(service: AuraAccessibilityService?) { this.accessibilityService = service }
    override fun getActiveTask(): Flow<Task?> = _activeTask.asStateFlow()

    override suspend fun executeTask(taskDescription: String): Flow<TaskExecutionUpdate> = flow {
        emit(TaskExecutionUpdate.Starting(taskDescription))
        try {
            val task = Task(description = taskDescription, status = TaskStatus.PLANNING)
            taskRepository.saveTask(task)
            _activeTask.value = task
            emit(TaskExecutionUpdate.TaskCreated(task))
            val actions = planTask(taskDescription)
            val updatedTask = task.copy(status = TaskStatus.EXECUTING, startedAt = System.currentTimeMillis(), actions = actions)
            taskRepository.saveTask(updatedTask)
            _activeTask.value = updatedTask
            emit(TaskExecutionUpdate.ActionsPlanned(actions))
            for ((index, action) in actions.withIndex()) {
                if (isPaused.value) { emit(TaskExecutionUpdate.Paused(updatedTask)); return@flow }
                emit(TaskExecutionUpdate.ExecutingAction(index, action))
                executeAction(action)
                taskRepository.updateTaskProgress(updatedTask.id, index + 1)
                delay(500)
            }
            val completedTask = updatedTask.copy(status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis(), result = TaskResult(success = true, message = "Done"))
            taskRepository.saveTask(completedTask)
            _activeTask.value = null
            emit(TaskExecutionUpdate.Completed(completedTask))
        } catch (e: Exception) {
            Timber.e(e, "Task failed")
            _activeTask.value = null
            emit(TaskExecutionUpdate.Error(e.message ?: "Unknown error"))
        }
    }

    override suspend fun planTask(taskDescription: String): List<AgentAction> = emptyList()
    override suspend fun continueTask(taskId: String): Flow<TaskExecutionUpdate> = flow { emit(TaskExecutionUpdate.Error("Not implemented")) }
    override suspend fun cancelTask(taskId: String) { _activeTask.value = null }

    private suspend fun executeAction(action: AgentAction): Boolean {
        delay(action.duration)
        return true
    }
}
