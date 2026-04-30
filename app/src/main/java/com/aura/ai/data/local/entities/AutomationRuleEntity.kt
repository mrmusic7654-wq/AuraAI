package com.aura.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val triggerApp: String? = null,
    val triggerText: String? = null,
    val triggerTime: String? = null,
    val actions: String = "[]",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggered: Long? = null
)
