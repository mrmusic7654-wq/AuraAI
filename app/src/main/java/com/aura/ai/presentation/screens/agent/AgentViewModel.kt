package com.aura.ai.presentation.screens.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.data.models.Task
import com.aura.ai.data.models.TaskStatus
import com.aura.ai.data.repository.AgentRepositoryImpl
import com.aura.ai.data.repository.TaskExecutionUpdate
import com.aura.ai.domain.usecases.agent.*
import com.aura.ai.domain.usecases.accessibility.*
import com.aura.ai.services.AuraAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentRepository: AgentRepositoryImpl,
    private val parseUserIntentUseCase: ParseUserIntentUseCase,
    private val planActionsUseCase: PlanActionsUseCase,
    private val executeActionUseCase: ExecuteActionUseCase,
    private val validateActionResultUseCase: ValidateActionResultUseCase,
    private val handleErrorUseCase: HandleErrorUseCase,
    private val captureScreenStateUseCase: CaptureScreenStateUseCase,
    private val performBackUseCase: PerformBackUseCase,
    private val performHomeUseCase: PerformHomeUseCase
) : ViewModel() {
    
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()
    
    private var executionJob: kotlinx.coroutines.Job? = null
    
    fun setAccessibilityService(service: AuraAccessibilityService?) {
        agentRepository.setAccessibilityService(service)
        executeActionUseCase.setAccessibilityService(service)
        captureScreenStateUseCase.setAccessibilityService(service)
        performBackUseCase.setAccessibilityService(service)
        performHomeUseCase.setAccessibilityService(service)
        
        _state.update { it.copy(hasAccessibilityPermission = service != null) }
    }
    
    fun checkAccessibilityStatus() {
        // This will be called from Activity/Fragment
    }
    
    fun updateInput(input: String) {
        _state.update { it.copy(currentInput = input) }
    }
    
    fun sendMessage() {
        val input = _state.value.currentInput
        if (input.isBlank()) return
        
        _state.update { state ->
            state.copy(
                messages = state.messages + ChatMessage.User(input),
                currentInput = "",
                isExecuting = true
            )
        }
        
        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            executeTask(input)
        }
    }
    
    private suspend fun executeTask(userInput: String) {
        try {
            // Parse intent
            val parsedIntent = parseUserIntentUseCase(userInput)
            
            // Capture current screen
            val screenContext = captureScreenStateUseCase()
            
            // Plan actions
            val actions = planActionsUseCase(userInput, screenContext, parsedIntent)
            
            if (actions.isEmpty()) {
                _state.update {
                    it.copy(
                        isExecuting = false,
                        messages = it.messages + ChatMessage.Agent("I couldn't determine how to do that. Can you be more specific?")
                    )
                }
                return
            }
            
            // Create task state
            val taskState = TaskState(
                id = java.util.UUID.randomUUID().toString(),
                description = userInput,
                status = TaskStatus.EXECUTING,
                actions = actions
            )
            
            _state.update { it.copy(currentTask = taskState) }
            
            // Execute actions
            var success = true
            var failureReason: String? = null
            
            for ((index, action) in actions.withIndex()) {
                _state.update {
                    it.copy(currentTask = it.currentTask?.copy(currentActionIndex = index))
                }
                
                val beforeScreen = captureScreenStateUseCase()
                val actionSuccess = executeActionUseCase(action)
                kotlinx.coroutines.delay(500)
                val afterScreen = captureScreenStateUseCase()
                
                if (!actionSuccess) {
                    val validation = validateActionResultUseCase(action, beforeScreen, afterScreen)
                    if (validation !is ValidationResult.Success) {
                        // Try recovery
                        val recoveryPlan = handleErrorUseCase(action, "Action failed", 1)
                        success = handleRecovery(recoveryPlan)
                        if (!success) {
                            failureReason = "Failed to execute: $action"
                            break
                        }
                    }
                }
            }
            
            // Update final state
            _state.update {
                it.copy(
                    isExecuting = false,
                    currentTask = it.currentTask?.copy(
                        status = if (success) TaskStatus.COMPLETED else TaskStatus.FAILED,
                        completedAt = System.currentTimeMillis()
                    ),
                    messages = it.messages + ChatMessage.Agent(
                        if (success) "Task completed successfully!"
                        else "Task failed: ${failureReason ?: "Unknown error"}"
                    )
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Task execution failed")
            _state.update {
                it.copy(
                    isExecuting = false,
                    messages = it.messages + ChatMessage.Agent("Sorry, something went wrong: ${e.message}")
                )
            }
        }
    }
    
    private suspend fun handleRecovery(plan: ErrorRecoveryPlan): Boolean {
        return when (plan) {
            is ErrorRecoveryPlan.RetrySame -> {
                kotlinx.coroutines.delay(plan.delayMs)
                true
            }
            is ErrorRecoveryPlan.RetryWithAlternate -> {
                executeActionUseCase(plan.alternateAction)
            }
            is ErrorRecoveryPlan.RetryWithPreActions -> {
                plan.preActions.forEach { action ->
                    executeActionUseCase(action)
                    kotlinx.coroutines.delay(300)
                }
                true
            }
            is ErrorRecoveryPlan.Abort -> false
            else -> false
        }
    }
    
    fun stopExecution() {
        executionJob?.cancel()
        _state.update {
            it.copy(
                isExecuting = false,
                currentTask = it.currentTask?.copy(status = TaskStatus.CANCELLED),
                messages = it.messages + ChatMessage.Agent("Task cancelled.")
            )
        }
    }
    
    fun clearConversation() {
        _state.update { AgentState() }
    }
}
