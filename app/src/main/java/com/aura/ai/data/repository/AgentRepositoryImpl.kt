package com.aura.ai.data.repository

import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.models.*
import com.aura.ai.domain.repository.AgentRepository
import com.aura.ai.domain.repository.TaskRepository
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.AccessibilityAction
import com.aura.ai.services.ScreenStateManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.*
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
    private var currentExecutionJob: Job? = null
    private val isPaused = MutableStateFlow(false)

    override fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }

    override fun getActiveTask(): Flow<Task?> = _activeTask.asStateFlow()

    override suspend fun executeTask(taskDescription: String): Flow<TaskExecutionUpdate> = flow {
        emit(TaskExecutionUpdate.Starting(taskDescription))

        try {
            val task = Task(description = taskDescription, status = TaskStatus.PLANNING)
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
                if (isPaused.value) {
                    emit(TaskExecutionUpdate.Paused(updatedTask))
                    return@flow
                }

                emit(TaskExecutionUpdate.ExecutingAction(index, action))
                val success = executeAction(action)
                if (!success) throw Exception("Failed to execute action: ${action.type}")
                taskRepository.updateTaskProgress(updatedTask.id, index + 1)
                delay(500)
            }

            val completedTask = updatedTask.copy(
                status = TaskStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                result = TaskResult(success = true, message = "Task completed successfully")
            )
            taskRepository.saveTask(completedTask)
            _activeTask.value = null
            emit(TaskExecutionUpdate.Completed(completedTask))

        } catch (e: Exception) {
            Timber.e(e, "Task execution failed")
            _activeTask.value = null
            isPaused.value = false
            emit(TaskExecutionUpdate.Error(e.message ?: "Unknown error"))
        }
    }

    override suspend fun planTask(taskDescription: String): List<AgentAction> {
        return try {
            val screenContext = accessibilityService?.captureCurrentScreen()
            val prompt = """
                You are a phone automation agent. Based on the user's request and current screen state,
                plan the exact sequence of actions needed.

                User Request: $taskDescription
                Current Screen: ${screenContext?.toTextRepresentation() ?: "Not available"}

                Available Actions: TAP, SWIPE, TYPE, BACK, HOME, OPEN_APP, WAIT, FIND_AND_TAP

                Return a JSON array of actions.
            """.trimIndent()

            val response = generativeModel.generateContent(content { text(prompt) })
            val responseText = response.text ?: throw Exception("No response from Gemini")
            parseActionsFromResponse(responseText)
        } catch (e: Exception) {
            Timber.e(e, "Failed to plan actions")
            listOf(AgentAction(type = ActionType.HOME))
        }
    }

    override suspend fun continueTask(taskId: String): Flow<TaskExecutionUpdate> = flow {
        emit(TaskExecutionUpdate.Error("Continue task not implemented"))
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
                    accessibilityService?.submitAction(AccessibilityAction.Tap(action.x, action.y)) ?: false
                } else if (action.target != null) {
                    accessibilityService?.submitAction(AccessibilityAction.FindAndTap(action.target)) ?: false
                } else false
            }
            ActionType.SWIPE -> {
                if (action.startX != null && action.startY != null && action.endX != null && action.endY != null) {
                    accessibilityService?.submitAction(
                        AccessibilityAction.Swipe(action.startX, action.startY, action.endX, action.endY, action.duration)
                    ) ?: false
                } else false
            }
            ActionType.TYPE -> {
                if (action.text != null) {
                    accessibilityService?.submitAction(AccessibilityAction.Type(action.text)) ?: false
                } else false
            }
            ActionType.BACK -> accessibilityService?.submitAction(
                AccessibilityAction.GlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            ) ?: false
            ActionType.HOME -> accessibilityService?.submitAction(
                AccessibilityAction.GlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            ) ?: false
            ActionType.OPEN_APP -> {
                if (action.packageName != null) {
                    accessibilityService?.submitAction(AccessibilityAction.OpenApp(action.packageName)) ?: false
                } else false
            }
            ActionType.WAIT -> {
                delay(action.duration)
                true
            }
            ActionType.FIND_AND_TAP -> {
                if (action.target != null) {
                    accessibilityService?.submitAction(AccessibilityAction.FindAndTap(action.target)) ?: false
                } else false
            }
            ActionType.RECENTS -> accessibilityService?.submitAction(
                AccessibilityAction.GlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            ) ?: false
            else -> false
        }
    }

    private fun parseActionsFromResponse(response: String): List<AgentAction> {
        val actions = mutableListOf<AgentAction>()
        when {
            response.contains("home", ignoreCase = true) -> actions.add(AgentAction(type = ActionType.HOME))
            response.contains("back", ignoreCase = true) -> actions.add(AgentAction(type = ActionType.BACK))
            response.contains("open", ignoreCase = true) -> {
                val packageName = when {
                    response.contains("chrome", ignoreCase = true) -> "com.android.chrome"
                    response.contains("youtube", ignoreCase = true) -> "com.google.android.youtube"
                    response.contains("gmail", ignoreCase = true) -> "com.google.android.gm"
                    response.contains("settings", ignoreCase = true) -> "com.android.settings"
                    response.contains("whatsapp", ignoreCase = true) -> "com.whatsapp"
                    else -> null
                }
                if (packageName != null) {
                    actions.add(AgentAction(type = ActionType.OPEN_APP, packageName = packageName))
                }
            }
        }
        if (actions.isEmpty()) {
            actions.add(AgentAction(type = ActionType.HOME))
        }
        return actions
    }
}
