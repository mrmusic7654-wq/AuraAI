package com.aura.ai.presentation.screens.history

import com.aura.ai.data.local.entities.TaskEntity

data class HistoryState(
    val tasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTask: TaskEntity? = null,
    val showDeleteConfirmation: Boolean = false
)
