package com.aura.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val trigger: String,
    val action: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecuted: Long? = null
)
