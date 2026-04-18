package com.aura.ai.data.models.github

data class GitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

data class GitHubWorkflowRun(
    val id: Long,
    val name: String,
    val status: WorkflowStatus,
    val conclusion: WorkflowConclusion?,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String
)

enum class WorkflowStatus {
    QUEUED, IN_PROGRESS, COMPLETED, WAITING, PENDING
}

enum class WorkflowConclusion {
    SUCCESS, FAILURE, CANCELLED, SKIPPED, TIMED_OUT
}
