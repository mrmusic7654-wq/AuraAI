package com.aura.ai.data.models.github

data class GitHubFile(
    val name: String,
    val path: String,
    val sha: String,
    val content: String,
    val url: String
)

data class FileToCreate(
    val path: String,
    val content: String
)

data class BatchFileResult(
    val success: Boolean,
    val createdFiles: List<String>,
    val errors: List<String>
)
