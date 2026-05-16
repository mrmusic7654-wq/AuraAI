package com.aura.ai.domain.repository

import com.aura.ai.data.local.entities.TaskEntity
import com.aura.ai.data.models.Task
import com.aura.ai.data.models.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    
    suspend fun saveTask(task: Task)
    
    suspend fun updateTask(task: Task)
    
    suspend fun updateTaskProgress(taskId: String, currentActionIndex: Int)
    
    suspend fun getTask(taskId: String): Task?
    
    fun getAllTasks(): Flow<List<TaskEntity>>
    
    fun getTasksByStatus(status: TaskStatus): Flow<List<TaskEntity>>
    
    suspend fun deleteTask(taskId: String)
    
    suspend fun clearAllTasks()
}
