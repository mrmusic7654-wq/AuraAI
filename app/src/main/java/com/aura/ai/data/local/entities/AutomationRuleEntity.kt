package com.aura.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aura.ai.data.models.ActionType
import java.util.UUID

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String?,
    val triggerApp: String?,
    val triggerText: String?,
    val triggerTime: String?,
    val actions: String, // JSON string of actions
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggered: Long? = null
)
