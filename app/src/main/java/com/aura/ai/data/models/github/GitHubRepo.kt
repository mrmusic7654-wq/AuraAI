package com.aura.ai.data.models.github

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val isPrivate: Boolean,
    val htmlUrl: String,
    val cloneUrl: String,
    val defaultBranch: String,
    val createdAt: String,
    val updatedAt: String,
    val owner: GitHubUser
)

data class GitHubUser(
    val login: String,
    val id: Long,
    val name: String?,
    val email: String?,
    val avatarUrl: String
)
