package com.aura.ai.data.models.github

data class GitHubArtifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val url: String,
    val archiveDownloadUrl: String,
    val expired: Boolean,
    val createdAt: String,
    val updatedAt: String
)
