package com.aura.ai.services.intelligence
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ParallelTask(
    val id: String,
    val name: String,
    val action: suspend () -> String,
    val priority: Int = 0
)

data class ParallelResult(
    val taskId: String,
    val result: String,
    val success: Boolean,
    val durationMs: Long
)

class ParallelTaskExecutor {
    
    private val _activeTasks = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeTasks: StateFlow<Map<String, String>> = _activeTasks.asStateFlow()
    
    suspend fun executeParallel(tasks: List<ParallelTask>): Map<String, ParallelResult> {
        val results = mutableMapOf<String, ParallelResult>()
        val sortedTasks = tasks.sortedByDescending { it.priority }
        val deferreds = mutableListOf<Deferred<ParallelResult>>()
        
        coroutineScope {
            for (task in sortedTasks) {
                _activeTasks.value = _activeTasks.value + (task.id to "RUNNING")
                deferreds.add(async {
                    val startTime = System.currentTimeMillis()
                    try {
                        val result = task.action()
                        val duration = System.currentTimeMillis() - startTime
                        _activeTasks.value = _activeTasks.value + (task.id to "COMPLETED")
                        ParallelResult(task.id, result, true, duration)
                    } catch (e: Exception) {
                        _activeTasks.value = _activeTasks.value + (task.id to "FAILED")
                        ParallelResult(task.id, e.message ?: "Failed", false, System.currentTimeMillis() - startTime)
                    }
                })
            }
            
            deferreds.awaitAll().forEach { result ->
                results[result.taskId] = result
            }
        }
        
        _activeTasks.value = emptyMap()
        return results
    }
    
    suspend fun executeWithMaxConcurrency(tasks: List<ParallelTask>, maxConcurrent: Int = 3): Map<String, ParallelResult> {
        val results = mutableMapOf<String, ParallelResult>()
        val chunks = tasks.chunked(maxConcurrent)
        
        for (chunk in chunks) {
            val chunkResults = executeParallel(chunk)
            results.putAll(chunkResults)
        }
        
        return results
    }
    
    fun canParallelize(taskIds: List<String>): Boolean {
        return taskIds.size > 1
    }
    
    fun getActiveTaskCount(): Int = _activeTasks.value.count { it.value == "RUNNING" }
    fun getCompletedTaskCount(): Int = _activeTasks.value.count { it.value == "COMPLETED" }
    fun getFailedTaskCount(): Int = _activeTasks.value.count { it.value == "FAILED" }
}
