package com.aura.ai.data.models

import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val actions: List<AgentAction> = emptyList(),
    val currentActionIndex: Int = 0,
    val error: String? = null
)

enum class TaskStatus {
    PENDING,
    PLANNING,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AgentAction(
    val type: ActionType,
    val target: String? = null,
    val text: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val duration: Long? = null
)

enum class ActionType {
    TAP,
    SWIPE,
    TYPE,
    BACK,
    HOME,
    RECENTS,
    WAIT,
    SCREENSHOT,
    OPEN_APP
}
