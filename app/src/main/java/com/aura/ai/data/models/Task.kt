package com.aura.ai.data.models

data class Task(
    val id: String = "",
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val pausedAt: Long? = null,
    val actions: List<AgentAction> = emptyList(),
    val currentActionIndex: Int = 0,
    val result: TaskResult? = null,
    val error: String? = null
)

enum class TaskStatus { PENDING, PLANNING, EXECUTING, PAUSED, RESUMING, COMPLETED, FAILED, CANCELLED }

data class TaskResult(val success: Boolean = false, val message: String = "")
