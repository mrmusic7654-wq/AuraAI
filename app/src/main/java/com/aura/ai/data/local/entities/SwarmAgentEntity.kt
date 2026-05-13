package com.aura.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "swarm_agents")
data class SwarmAgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val apiKey: String,
    val modelName: String = "gemini-2.5-flash",
    val status: String = "standby",
    val requestsUsed: Int = 0,
    val requestLimit: Int = 1500,
    val currentTask: String = "",
    val lastSync: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
