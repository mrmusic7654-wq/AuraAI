package com.aura.ai.services.orchestrator

import android.content.Context
import android.content.Intent
import android.provider.Settings

enum class ErrorCategory {
    NETWORK, APP_NOT_FOUND, UI_ELEMENT_MISSING, TIMEOUT,
    PERMISSION_DENIED, CRASH, UNKNOWN, API_ERROR
}

data class ErrorRecord(
    val category: ErrorCategory,
    val message: String,
    val stepId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val resolved: Boolean = false,
    val resolution: String = ""
)

class ErrorRecoveryEngine(
    private val context: Context,
    private val stateManager: StateManager,
    private val metrics: AuraMetrics
) {
    
    private val errorHistory = mutableListOf<ErrorRecord>()
    
    suspend fun handleStepFailure(
        step: TaskStep,
        result: StepResult,
        taskState: TaskState
    ): Boolean {
        val category = categorizeError(result.error)
        val record = ErrorRecord(category, result.error, step.id)
        errorHistory.add(record)
        metrics.logError(category, result.error)
        
        return when (category) {
            ErrorCategory.NETWORK -> handleNetworkError()
            ErrorCategory.APP_NOT_FOUND -> handleAppNotFound()
            ErrorCategory.UI_ELEMENT_MISSING -> handleUIElementMissing(step, result)
            ErrorCategory.TIMEOUT -> handleTimeout(step)
            ErrorCategory.PERMISSION_DENIED -> handlePermissionDenied()
            ErrorCategory.CRASH -> handleCrash(taskState)
            ErrorCategory.API_ERROR -> handleAPIError()
            ErrorCategory.UNKNOWN -> handleUnknown(result)
        }
    }
    
    fun categorizeError(error: String): ErrorCategory {
        return when {
            error.contains("Network", ignoreCase = true) ||
            error.contains("timeout", ignoreCase = true) ||
            error.contains("unreachable", ignoreCase = true) -> ErrorCategory.NETWORK
            
            error.contains("not found", ignoreCase = true) ||
            error.contains("not installed", ignoreCase = true) ||
            error.contains("no package", ignoreCase = true) -> ErrorCategory.APP_NOT_FOUND
            
            error.contains("cannot find", ignoreCase = true) ||
            error.contains("element", ignoreCase = true) ||
            error.contains("node", ignoreCase = true) -> ErrorCategory.UI_ELEMENT_MISSING
            
            error.contains("timeout", ignoreCase = true) ||
            error.contains("timed out", ignoreCase = true) -> ErrorCategory.TIMEOUT
            
            error.contains("permission", ignoreCase = true) ||
            error.contains("denied", ignoreCase = true) -> ErrorCategory.PERMISSION_DENIED
            
            error.contains("crash", ignoreCase = true) ||
            error.contains("fatal", ignoreCase = true) -> ErrorCategory.CRASH
            
            error.contains("api", ignoreCase = true) ||
            error.contains("http", ignoreCase = true) ||
            error.contains("response code", ignoreCase = true) -> ErrorCategory.API_ERROR
            
            else -> ErrorCategory.UNKNOWN
        }
    }
    
    private suspend fun handleNetworkError(): Boolean {
        // Wait and retry - network may come back
        kotlinx.coroutines.delay(5000)
        return true // Retry the step
    }
    
    private suspend fun handleAppNotFound(): Boolean {
        // Can't recover - skip this step
        return false
    }
    
    private suspend fun handleUIElementMissing(step: TaskStep, result: StepResult): Boolean {
        // Check if we have fallback methods
        return step.fallbackStepId != null
    }
    
    private suspend fun handleTimeout(step: TaskStep): Boolean {
        // Double the timeout and retry once
        return step.retryCount > 0
    }
    
    private suspend fun handlePermissionDenied(): Boolean {
        // Try to open settings
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { }
        return false // User must grant permission
    }
    
    private suspend fun handleCrash(taskState: TaskState): Boolean {
        // Try to restore from last checkpoint
        val lastCheckpoint = stateManager.getCheckpointCount() - 1
        if (lastCheckpoint >= 0) {
            val restored = stateManager.restoreFromCheckpoint(lastCheckpoint)
            return restored != null
        }
        return false
    }
    
    private suspend fun handleAPIError(): Boolean {
        // Retry with backoff
        kotlinx.coroutines.delay(3000)
        return true
    }
    
    private suspend fun handleUnknown(result: StepResult): Boolean {
        // Last resort - take screenshot and ask user
        return false
    }
    
    fun getErrorStats(): Map<ErrorCategory, Int> {
        return errorHistory.groupBy { it.category }.mapValues { it.value.size }
    }
    
    fun getRecentErrors(limit: Int = 10): List<ErrorRecord> {
        return errorHistory.takeLast(limit)
    }
}
