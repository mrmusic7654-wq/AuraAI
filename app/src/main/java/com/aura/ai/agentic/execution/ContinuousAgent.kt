package com.aura.ai.agentic.execution

import com.aura.ai.agentic.planning.TaskPlanner
import com.aura.ai.agentic.core.Consciousness
import kotlinx.coroutines.*

class ContinuousAgent(
    private val taskPlanner: TaskPlanner,
    private val consciousness: Consciousness,
    private val onProgress: (String) -> Unit,
    private val onComplete: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    
    private var agentJob: Job? = null
    private val taskQueue = mutableListOf<String>()
    private var isRunning = false
    
    fun start() {
        if (isRunning) return
        isRunning = true
        
        agentJob = CoroutineScope(Dispatchers.IO).launch {
            consciousness.updateStatus("AGENTIC", "Continuous agent active")
            
            while (isActive && isRunning) {
                if (taskQueue.isNotEmpty()) {
                    val task = taskQueue.removeAt(0)
                    executeTask(task)
                }
                delay(2000) // Check for new tasks every 2 seconds
            }
        }
    }
    
    fun stop() {
        isRunning = false
        agentJob?.cancel()
        consciousness.updateStatus("IDLE", "Agent stopped")
    }
    
    fun addTask(task: String) {
        taskQueue.add(task)
        onProgress("📋 Task queued: ${task.take(50)}...")
    }
    
    fun addTasks(tasks: List<String>) {
        tasks.forEach { addTask(it) }
    }
    
    fun getQueueSize(): Int = taskQueue.size
    
    fun getQueueStatus(): String {
        return if (taskQueue.isEmpty()) "No tasks queued"
        else "📋 ${taskQueue.size} tasks queued:\n${taskQueue.take(5).joinToString("\n") { "  • ${it.take(80)}" }}"
    }
    
    private suspend fun executeTask(task: String) {
        consciousness.updateStatus("EXECUTING", task)
        onProgress("🔄 Executing: ${task.take(60)}...")
        
        try {
            val result = taskPlanner.planAndExecute(task)
            consciousness.recordTaskCompletion(true)
            onComplete("✅ Done: ${task.take(50)}\n$result")
        } catch (e: Exception) {
            consciousness.recordTaskCompletion(false)
            onError("❌ Failed: ${task.take(50)} - ${e.message}")
        }
    }
}
