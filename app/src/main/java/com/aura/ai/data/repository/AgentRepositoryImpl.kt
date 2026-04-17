package com.aura.ai.data.repository

import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.models.*
import com.aura.ai.domain.repository.AgentRepository
import com.aura.ai.domain.repository.TaskRepository
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.ScreenStateManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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
    
    fun setAccessibilityService(service: AuraAccessibilityService?) {
        this.accessibilityService = service
    }
    
    override suspend fun executeTask(taskDescription: String): Flow<TaskExecutionUpdate> = flow {
        emit(TaskExecutionUpdate.Starting(taskDescription))
        
        try {
            // Create task
            val task = Task(
                description = taskDescription,
                status = TaskStatus.PLANNING
            )
            taskRepository.saveTask(task)
            emit(TaskExecutionUpdate.TaskCreated(task))
            
            // Get current screen context
            val screenContext = accessibilityService?.captureCurrentScreen()
            emit(TaskExecutionUpdate.ScreenCaptured(screenContext))
            
            // Plan actions using Gemini
            emit(TaskExecutionUpdate.Planning)
            val actions = planActions(taskDescription, screenContext)
            
            // Update task with planned actions
            val updatedTask = task.copy(
                status = TaskStatus.EXECUTING,
                startedAt = System.currentTimeMillis(),
                actions = actions
            )
            taskRepository.saveTask(updatedTask)
            emit(TaskExecutionUpdate.ActionsPlanned(actions))
            
            // Execute actions
            for ((index, action) in actions.withIndex()) {
                emit(TaskExecutionUpdate.ExecutingAction(index, action))
                
                val success = executeAction(action)
                
                if (!success) {
                    throw Exception("Failed to execute action: $action")
                }
                
                // Update task progress
                taskRepository.updateTaskProgress(updatedTask.id, index + 1)
                
                // Small delay between actions
                delay(500)
            }
            
            // Mark task as completed
            val completedTask = updatedTask.copy(
                status = TaskStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                result = TaskResult(
                    success = true,
                    message = "Task completed successfully"
                )
            )
            taskRepository.saveTask(completedTask)
            emit(TaskExecutionUpdate.Completed(completedTask))
            
        } catch (e: Exception) {
            Timber.e(e, "Task execution failed")
            emit(TaskExecutionUpdate.Error(e.message ?: "Unknown error"))
        }
    }
    
    private suspend fun planActions(
        taskDescription: String, 
        screenContext: ScreenContext?
    ): List<AgentAction> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
                You are Aura AI, an intelligent phone automation assistant.
                Your task is to break down user requests into specific phone actions.
                
                Available actions:
                - TAP: Tap at specific coordinates or on UI elements
                - SWIPE: Swipe from one point to another
                - TYPE: Type text into input fields
                - BACK: Press back button
                - HOME: Go to home screen
                - OPEN_APP: Open a specific app
                - WAIT: Wait for specified milliseconds
                
                Current screen context:
                ${screenContext?.toTextRepresentation() ?: "Screen context not available"}
                
                Respond with a JSON array of actions.
            """.trimIndent()
            
            val response = generativeModel.generateContent(
                content {
                    text(systemPrompt)
                    text("User request: $taskDescription")
                }
            )
            
            val responseText = response.text ?: throw Exception("No response from Gemini")
            
            // Parse response into actions
            parseActionsFromResponse(responseText)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to plan actions")
            // Return fallback actions
            listOf(
                AgentAction(type = ActionType.HOME),
                AgentAction(type = ActionType.WAIT, duration = 1000)
            )
        }
    }
    
    private fun parseActionsFromResponse(response: String): List<AgentAction> {
        // Simple parsing - in production, use proper JSON parsing
        val actions = mutableListOf<AgentAction>()
        
        // Extract actions from response
        when {
            response.contains("home", ignoreCase = true) -> {
                actions.add(AgentAction(type = ActionType.HOME))
            }
            response.contains("back", ignoreCase = true) -> {
                actions.add(AgentAction(type = ActionType.BACK))
            }
            response.contains("open", ignoreCase = true) -> {
                // Extract app name
                val appName = extractAppName(response)
                if (appName != null) {
                    val packageName = getPackageNameForApp(appName)
                    actions.add(AgentAction(type = ActionType.OPEN_APP, packageName = packageName))
                }
            }
        }
        
        if (actions.isEmpty()) {
            actions.add(AgentAction(type = ActionType.HOME))
        }
        
        return actions
    }
    
    private suspend fun executeAction(action: AgentAction): Boolean {
        return when (action.type) {
            ActionType.TAP -> {
                if (action.x != null && action.y != null) {
                    accessibilityService?.submitAction(
                        AccessibilityAction.Tap(action.x, action.y)
                    ) ?: false
                } else if (action.target != null) {
                    accessibilityService?.submitAction(
                        AccessibilityAction.FindAndTap(action.target)
                    ) ?: false
                } else {
                    false
                }
            }
            ActionType.SWIPE -> {
                if (action.startX != null && action.startY != null && 
                    action.endX != null && action.endY != null) {
                    accessibilityService?.submitAction(
                        AccessibilityAction.Swipe(
                            action.startX, action.startY,
                            action.endX, action.endY,
                            action.duration
                        )
                    ) ?: false
                } else {
                    false
                }
            }
            ActionType.TYPE -> {
                if (action.text != null) {
                    accessibilityService?.submitAction(
                        AccessibilityAction.Type(action.text)
                    ) ?: false
                } else {
                    false
                }
            }
            ActionType.BACK -> {
                accessibilityService?.submitAction(
                    AccessibilityAction.GlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                ) ?: false
            }
            ActionType.HOME -> {
                accessibilityService?.submitAction(
                    AccessibilityAction.GlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                ) ?: false
            }
            ActionType.RECENTS -> {
                accessibilityService?.submitAction(
                    AccessibilityAction.GlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                ) ?: false
            }
            ActionType.OPEN_APP -> {
                if (action.packageName != null) {
                    accessibilityService?.submitAction(
                        AccessibilityAction.OpenApp(action.packageName)
                    ) ?: false
                } else {
                    false
                }
            }
            ActionType.WAIT -> {
                delay(action.duration)
                true
            }
            else -> false
        }
    }
    
    private fun extractAppName(response: String): String? {
        // Simple extraction - in production, use NLP
        val patterns = listOf(
            "open (\\w+)" to RegexOptions.IGNORE_CASE,
            "launch (\\w+)" to RegexOptions.IGNORE_CASE,
            "start (\\w+)" to RegexOptions.IGNORE_CASE
        )
        
        for ((pattern, options) in patterns) {
            val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE))
            val match = regex.find(response)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    private fun getPackageNameForApp(appName: String): String {
        return when (appName.lowercase()) {
            "chrome" -> "com.android.chrome"
            "youtube" -> "com.google.android.youtube"
            "gmail" -> "com.google.android.gm"
            "maps" -> "com.google.android.apps.maps"
            "play store" -> "com.android.vending"
            "settings" -> "com.android.settings"
            "camera" -> "com.android.camera"
            "gallery", "photos" -> "com.google.android.apps.photos"
            "whatsapp" -> "com.whatsapp"
            "instagram" -> "com.instagram.android"
            "facebook" -> "com.facebook.katana"
            "twitter", "x" -> "com.twitter.android"
            "spotify" -> "com.spotify.music"
            "netflix" -> "com.netflix.mediaclient"
            else -> appName.lowercase()
        }
    }
}

sealed class TaskExecutionUpdate {
    data class Starting(val description: String) : TaskExecutionUpdate()
    data class TaskCreated(val task: Task) : TaskExecutionUpdate()
    data class ScreenCaptured(val screenContext: ScreenContext?) : TaskExecutionUpdate()
    object Planning : TaskExecutionUpdate()
    data class ActionsPlanned(val actions: List<AgentAction>) : TaskExecutionUpdate()
    data class ExecutingAction(val index: Int, val action: AgentAction) : TaskExecutionUpdate()
    data class Completed(val task: Task) : TaskExecutionUpdate()
    data class Error(val message: String) : TaskExecutionUpdate()
}
