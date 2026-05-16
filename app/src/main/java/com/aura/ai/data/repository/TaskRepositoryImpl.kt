package com.aura.ai.data.repository

import com.aura.ai.data.local.dao.TaskDao
import com.aura.ai.data.local.entities.TaskEntity
import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.models.Task
import com.aura.ai.data.models.TaskStatus
import com.aura.ai.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val preferences: AuraPreferences
) : TaskRepository {
    
    override suspend fun saveTask(task: Task) {
        val entity = TaskEntity(
            id = task.id,
            description = task.description,
            status = task.status,
            createdAt = task.createdAt,
            startedAt = task.startedAt,
            completedAt = task.completedAt,
            actions = task.actions,
            currentActionIndex = task.currentActionIndex,
            error = task.error
        )
        taskDao.insertTask(entity)
    }
    
    override suspend fun updateTask(task: Task) {
        saveTask(task)
    }
    
    override suspend fun updateTaskProgress(taskId: String, currentActionIndex: Int) {
        val task = taskDao.getTaskById(taskId) ?: return
        val updatedTask = task.copy(currentActionIndex = currentActionIndex)
        taskDao.updateTask(updatedTask)
    }
    
    override suspend fun getTask(taskId: String): Task? {
        return taskDao.getTaskById(taskId)?.toDomainModel()
    }
    
    override fun getAllTasks(): Flow<List<TaskEntity>> {
        return taskDao.getAllTasks()
    }
    
    override fun getTasksByStatus(status: TaskStatus): Flow<List<TaskEntity>> {
        return taskDao.getTasksByStatus(status)
    }
    
    override suspend fun deleteTask(taskId: String) {
        taskDao.deleteTask(taskId)
    }
    
    override suspend fun clearAllTasks() {
        taskDao.clearAllTasks()
    }
    
    private fun TaskEntity.toDomainModel(): Task {
        return Task(
            id = id,
            description = description,
            status = status,
            createdAt = createdAt,
            startedAt = startedAt,
            completedAt = completedAt,
            actions = actions,
            currentActionIndex = currentActionIndex,
            error = error
        )
    }
}
