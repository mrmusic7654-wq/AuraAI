package com.aura.ai.services.orchestrator

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TaskStep(
    val id: String,
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60000,
    val retryCount: Int = 3,
    val dependsOn: List<String> = emptyList(),
    val condition: String? = null, // "if_previous_success", "if_previous_failed"
    val fallbackStepId: String? = null
)

data class TaskPlan(
    val taskId: String,
    val taskName: String,
    val steps: List<TaskStep>,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

data class StepResult(
    val stepId: String,
    val status: String, // "PENDING", "RUNNING", "SUCCESS", "FAILED", "SKIPPED"
    val output: String = "",
    val error: String = "",
    val durationMs: Long = 0,
    val retriesUsed: Int = 0
)

data class TaskState(
    val taskPlan: TaskPlan,
    val currentStepIndex: Int = 0,
    val stepResults: Map<String, StepResult> = emptyMap(),
    val overallStatus: String = "PENDING",
    val startedAt: Long = 0,
    val pausedAt: Long = 0,
    val completedAt: Long = 0
)

class TaskOrchestrator(
    private val actionExecutor: ActionExecutor,
    private val stateManager: StateManager,
    private val errorRecovery: ErrorRecoveryEngine,
    private val metrics: AuraMetrics
) {
    private val _currentTask = MutableStateFlow<TaskState?>(null)
    val currentTask: StateFlow<TaskState?> = _currentTask.asStateFlow()
    
    private var taskJob: Job? = null
    
    suspend fun executePlan(plan: TaskPlan): TaskState {
        val taskState = stateManager.getLastState()?.takeIf { it.taskPlan.taskId == plan.taskId }
            ?: TaskState(taskPlan = plan)
        
        _currentTask.value = taskState
        stateManager.saveState(taskState)
        
        taskJob = CoroutineScope(Dispatchers.IO).launch {
            executeSteps(taskState)
        }
        
        return taskState
    }
    
    private suspend fun executeSteps(state: TaskState) {
        var updatedState = state.copy(startedAt = System.currentTimeMillis(), overallStatus = "RUNNING")
        
        for (i in state.currentStepIndex until state.taskPlan.steps.size) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                updatedState = updatedState.copy(overallStatus = "PAUSED", pausedAt = System.currentTimeMillis())
                stateManager.saveState(updatedState)
                return
            }
            
            val step = state.taskPlan.steps[i]
            if (!canExecuteStep(step, updatedState.stepResults)) {
                updatedState = updatedState.copy(
                    stepResults = updatedState.stepResults + (step.id to StepResult(step.id, "SKIPPED"))
                )
                continue
            }
            
            updatedState = updatedState.copy(currentStepIndex = i)
            metrics.logStepStart(step.id, step.action)
            
            val result = executeStepWithRetry(step)
            
            updatedState = updatedState.copy(
                stepResults = updatedState.stepResults + (step.id to result)
            )
            
            stateManager.saveState(updatedState)
            metrics.logStepComplete(step.id, result.status, result.durationMs)
            
            if (result.status == "FAILED") {
                val recovered = errorRecovery.handleStepFailure(step, result, updatedState)
                if (!recovered) {
                    updatedState = updatedState.copy(overallStatus = "FAILED", completedAt = System.currentTimeMillis())
                    stateManager.saveState(updatedState)
                    return
                }
            }
        }
        
        updatedState = updatedState.copy(overallStatus = "SUCCESS", completedAt = System.currentTimeMillis())
        stateManager.saveState(updatedState)
        stateManager.clearState()
    }
    
    private fun canExecuteStep(step: TaskStep, results: Map<String, StepResult>): Boolean {
        if (step.dependsOn.isEmpty()) return true
        return step.dependsOn.all { depId ->
            val depResult = results[depId]
            when (step.condition) {
                "if_previous_success" -> depResult?.status == "SUCCESS"
                "if_previous_failed" -> depResult?.status == "FAILED"
                else -> depResult?.status in listOf("SUCCESS", "SKIPPED")
            }
        }
    }
    
    private suspend fun executeStepWithRetry(step: TaskStep): StepResult {
        var lastError = ""
        for (attempt in 0..step.retryCount) {
            try {
                val startTime = System.currentTimeMillis()
                val output = withTimeout(step.timeoutMs) {
                    actionExecutor.executeAction(step.action, step.params)
                }
                val duration = System.currentTimeMillis() - startTime
                return StepResult(step.id, "SUCCESS", output, "", duration, attempt)
            } catch (e: TimeoutCancellationException) {
                lastError = "Timeout after ${step.timeoutMs}ms"
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
            }
        }
        return StepResult(step.id, "FAILED", "", lastError, step.timeoutMs, step.retryCount)
    }
    
    fun pause() { taskJob?.cancel() }
    fun resume() { _currentTask.value?.let { executePlan(it.taskPlan) } }
    fun cancel() { taskJob?.cancel(); stateManager.clearState(); _currentTask.value = null }
}
