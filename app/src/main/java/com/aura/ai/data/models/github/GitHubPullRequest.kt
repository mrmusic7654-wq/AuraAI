package com.aura.ai.data.models.github

data class GitHubPullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val createdAt: String
)
