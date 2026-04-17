package com.aura.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screen_elements_cache")
data class ScreenElementEntity(
    @PrimaryKey
    val elementSignature: String,
    val packageName: String,
    val elementText: String?,
    val elementId: String?,
    val className: String,
    val bounds: String,
    val lastSeen: Long = System.currentTimeMillis()
)
