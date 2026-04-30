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

data class AgentAction(
    val type: ActionType = ActionType.HOME,
    val target: String? = null,
    val text: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val startX: Float? = null,
    val startY: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null,
    val duration: Long = 300,
    val packageName: String? = null
)

enum class ActionType { TAP, SWIPE, TYPE, BACK, HOME, RECENTS, WAIT, OPEN_APP, FIND_AND_TAP }

data class TaskResult(val success: Boolean = false, val message: String = "")
