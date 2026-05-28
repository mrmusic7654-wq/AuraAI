package com.aura.ai.services.intelligence

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScheduledTask(
    val id: String,
    val name: String,
    val priority: Int, // 0=low, 1=medium, 2=high, 3=critical
    val action: suspend () -> String,
    val status: String = "QUEUED"
)

class TaskScheduler {
    
    private val _queue = MutableStateFlow<List<ScheduledTask>>(emptyList())
    val queue: StateFlow<List<ScheduledTask>> = _queue.asStateFlow()
    
    private var currentTask: ScheduledTask? = null
    private var taskJob: Job? = null
    
    fun schedule(task: ScheduledTask): String {
        val newQueue = _queue.value.toMutableList()
        newQueue.add(task)
        newQueue.sortByDescending { it.priority }
        _queue.value = newQueue
        
        if (currentTask == null) {
            processNext()
        }
        
        return task.id
    }
    
    fun scheduleMultiple(tasks: List<ScheduledTask>) {
        tasks.forEach { schedule(it) }
    }
    
    fun cancelTask(taskId: String): Boolean {
        val newQueue = _queue.value.filter { it.id != taskId }
        if (currentTask?.id == taskId) {
            taskJob?.cancel()
            currentTask = null
            processNext()
        }
        _queue.value = newQueue
        return true
    }
    
    fun getCurrentTask(): ScheduledTask? = currentTask
    
    fun getQueueSize(): Int = _queue.value.size
    
    fun getQueueByPriority(priority: Int): List<ScheduledTask> {
        return _queue.value.filter { it.priority == priority }
    }
    
    private fun processNext() {
        if (_queue.value.isEmpty()) return
        val next = _queue.value.first()
        val remaining = _queue.value.drop(1)
        _queue.value = remaining
        
        currentTask = next
        taskJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = next.action()
                currentTask = null
                processNext()
            } catch (e: Exception) {
                currentTask = null
                processNext()
            }
        }
    }
    
    fun clearQueue() {
        taskJob?.cancel()
        currentTask = null
        _queue.value = emptyList()
    }
}
