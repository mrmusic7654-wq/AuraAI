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
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val actions: List<AgentAction> = emptyList(),
    val currentActionIndex: Int = 0,
    val error: String? = null
)
