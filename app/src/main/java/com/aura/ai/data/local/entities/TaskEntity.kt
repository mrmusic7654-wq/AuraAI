package com.aura.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.TaskStatus

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val description: String,
    val status: TaskStatus,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val actions: List<AgentAction>,
    val currentActionIndex: Int,
    val error: String?
)
