package com.aura.ai.agentic.core

data class AuraState(
    val mode: String = "IDLE",           // IDLE, EXECUTING, THINKING, ERROR
    val currentTask: String = "None",
    val activeGoal: String = "",
    val uptimeMs: Long = 0,
    val tasksCompleted: Int = 0,
    val tasksFailed: Int = 0,
    val lastAction: String = "None",
    val lastActionTime: Long = 0,
    val batteryLevel: Int = 100,
    val apiCallsToday: Int = 0,
    val activeRepos: Int = 0,
    val failingBuilds: List<String> = emptyList(),
    val pendingApprovals: List<String> = emptyList()
)

class Consciousness {
    
    private var state = AuraState()
    private val startTime = System.currentTimeMillis()
    
    fun updateStatus(mode: String, task: String) {
        state = state.copy(
            mode = mode,
            currentTask = task,
            lastAction = task,
            lastActionTime = System.currentTimeMillis(),
            uptimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    fun recordTaskCompletion(success: Boolean) {
        state = state.copy(
            tasksCompleted = state.tasksCompleted + if (success) 1 else 0,
            tasksFailed = state.tasksFailed + if (!success) 1 else 0,
            mode = "IDLE"
        )
    }
    
    fun addPendingApproval(action: String) {
        state = state.copy(pendingApprovals = state.pendingApprovals + action)
    }
    
    fun removePendingApproval(action: String) {
        state = state.copy(pendingApprovals = state.pendingApprovals - action)
    }
    
    fun updateBatteryLevel(level: Int) {
        state = state.copy(batteryLevel = level)
    }
    
    fun updateApiCalls(count: Int) {
        state = state.copy(apiCallsToday = count)
    }
    
    fun updateActiveRepos(count: Int) {
        state = state.copy(activeRepos = count)
    }
    
    fun updateFailingBuilds(builds: List<String>) {
        state = state.copy(failingBuilds = builds)
    }
    
    fun getCurrentState(): AuraState = state
    
    fun getUptimeFormatted(): String {
        val seconds = state.uptimeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return "${hours}h ${minutes % 60}m ${seconds % 60}s"
    }
    
    fun getStatusReport(): String {
        return """
🧠 AURA STATUS REPORT
─────────────────────────
Mode: ${state.mode}
Active Goal: ${state.activeGoal.ifEmpty { "None" }}
Current Task: ${state.currentTask}
Uptime: ${getUptimeFormatted()}
Tasks: ${state.tasksCompleted} completed / ${state.tasksFailed} failed
Battery: ${state.batteryLevel}%
API Calls: ${state.apiCallsToday}
Active Repos: ${state.activeRepos}
Failing Builds: ${state.failingBuilds.size}
Pending Approvals: ${state.pendingApprovals.size}
        """.trimIndent()
    }
}
