package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowDispatchRequestDto(
    val ref: String,
    val inputs: Map<String, String> = emptyMap()
)
