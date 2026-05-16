package com.aura.ai.data.models.github

data class GitHubContent(
    val type: ContentType,
    val name: String,
    val path: String,
    val sha: String,
    val size: Long?,
    val content: String?,
    val downloadUrl: String?
)

enum class ContentType {
    FILE, DIR, SYMLINK
}
