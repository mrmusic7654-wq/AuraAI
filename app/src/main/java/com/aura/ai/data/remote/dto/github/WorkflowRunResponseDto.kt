package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowRunResponseDto(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val created_at: String,
    val updated_at: String,
    val html_url: String
)

@Serializable
data class WorkflowRunsListDto(
    val total_count: Int,
    val workflow_runs: List<WorkflowRunResponseDto>
)
