package com.aura.ai.presentation.screens.history

data class HistoryState(
    val tasks: List<TaskData> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTask: TaskData? = null
)

data class TaskData(
    val id: String = "",
    val description: String = "",
    val status: String = "PENDING",
    val createdAt: String = "Just now"
)
