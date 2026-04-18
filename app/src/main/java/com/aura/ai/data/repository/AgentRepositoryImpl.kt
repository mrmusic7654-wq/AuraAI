package com.aura.ai.data.repository

import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.models.*
import com.aura.ai.data.remote.datasource.GeminiRemoteDataSource
import com.aura.ai.domain.repository.AgentRepository
import com.aura.ai.domain.repository.TaskRepository
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.ScreenStateManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepositoryImpl @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val taskRepository: TaskRepository,
    private val screenStateManager: ScreenStateManager,
    private val preferences: AuraPreferences,
    private val geminiDataSource: GeminiRemoteDataSource
) : AgentRepository {
    
    private var accessibilityService: AuraAccessibilityService? = null
    private val _activeTask = MutableStateFlow<Task?>(null)
    private var currentExecutionJob: kotlinx.coroutines.Job? = null
    
    override fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }
    
    override fun getActiveTask(): Flow<Task?> = _activeTask.asStateFlow()
    
    override suspend fun executeTask(taskDescription: String): Flow<TaskExecutionUpdate> = flow {
        emit(TaskExecutionUpdate.Starting(taskDescription))
        
        try {
            val task = Task(
                description = taskDescription,
                status = TaskStatus.PLANNING
            )
            taskRepository.saveTask(task)
            _activeTask.value = task
            emit(TaskExecutionUpdate.TaskCreated(task))
            
            val screenContext = accessibilityService?.captureCurrentScreen()
            emit(TaskExecutionUpdate.ScreenCaptured(screenContext))
            
            emit(TaskExecutionUpdate.Planning)
            val actions = planTask(taskDescription)
            
            val updatedTask = task.copy(
                status = TaskStatus.EXECUTING,
                startedAt = System.currentTimeMillis(),
                actions = actions
            )
            taskRepository.saveTask(updatedTask)
            _activeTask.value = updatedTask
            emit(TaskExecutionUpdate.ActionsPlanned(actions))
            
            for ((index, action) in actions.withIndex()) {
                emit(TaskExecutionUpdate.ExecutingAction(index, action))
                
                val success = executeAction(action)
                
                if (!success) {
                    throw Exception("Failed to execute action: ${action.type}")
                }
                
                taskRepository.updateTaskProgress(updatedTask.id, index + 1)
                delay(500)
            }
            
            val completedTask = updatedTask.copy(
                status = TaskStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                result = TaskResult(
                    success = true,
                    message = "Task completed successfully"
                )
            )
            taskRepository.saveTask(completedTask)
            _activeTask.value = null
            emit(TaskExecutionUpdate.Completed(completedTask))
            
        } catch (e: Exception) {
            Timber.e(e, "Task execution failed")
            _activeTask.value = null
            emit(TaskExecutionUpdate.Error(e.message ?: "Unknown error"))
        }
    }
    
    override suspend fun planTask(taskDescription: String): List<AgentAction> {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    You are Aura AI, a phone automation assistant.
                    Break down this request into specific phone actions:
                    "$taskDescription"
                    
                    Available actions: TAP, SWIPE, TYPE, BACK, HOME, OPEN_APP, WAIT
                    
                    Return a JSON array of actions.
                """.trimIndent()
                
                val response = geminiDataSource.generateResponse(prompt)
                response.getOrNull()?.let { parseActions(it) } ?: getFallbackActions()
            } catch (e: Exception) {
                getFallbackActions()
            }
        }
    }
    
    override suspend fun continueTask(taskId: String): Flow<TaskExecutionUpdate> = flow {
        val task = taskRepository.getTask(taskId)
        if (task == null) {
            emit(TaskExecutionUpdate.Error("Task not found"))
            return@flow
        }
        
        // Resume from current action index
        emit(TaskExecutionUpdate.Starting("Continuing: ${task.description}"))
        // Implementation similar to executeTask but starting from currentActionIndex
    }
    
    override suspend fun cancelTask(taskId: String) {
        currentExecutionJob?.cancel()
        _activeTask.value = null
        
        taskRepository.getTask(taskId)?.let { task ->
            val cancelledTask = task.copy(
                status = TaskStatus.CANCELLED,
                completedAt = System.currentTimeMillis()
            )
            taskRepository.saveTask(cancelledTask)
        }
    }
    
    private suspend fun executeAction(action: AgentAction): Boolean {
        return when (action.type) {
            ActionType.TAP -> {
                if (action.x != null && action.y != null) {
                    accessibilityService?.submitAction(
                        com.aura.ai.services.AccessibilityAction.Tap(action.x, action.y)
                    ) ?: false
                } else {
                    false
                }
            }
            ActionType.HOME -> {
                accessibilityService?.submitAction(
                    com.aura.ai.services.AccessibilityAction.GlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                    )
                ) ?: false
            }
            ActionType.BACK -> {
                accessibilityService?.submitAction(
                    com.aura.ai.services.AccessibilityAction.GlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                    )
                ) ?: false
            }
            ActionType.WAIT -> {
                delay(action.duration)
                true
            }
            else -> false
        }
    }
    
    private fun parseActions(response: String): List<AgentAction> {
        return listOf(AgentAction(type = ActionType.HOME))
    }
    
    private fun getFallbackActions(): List<AgentAction> {
        return listOf(AgentAction(type = ActionType.HOME))
    }
}
