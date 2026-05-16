package com.aura.ai.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TaskResultDto(
    val taskId: String,
    val success: Boolean,
    val message: String,
    val timestamp: Long,
    val data: Map<String, String> = emptyMap()
)
