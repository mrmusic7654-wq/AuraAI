package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class RepoResponseDto(
    val id: Long,
    val name: String,
    val full_name: String,
    val description: String?,
    val private: Boolean,
    val html_url: String,
    val clone_url: String,
    val default_branch: String,
    val created_at: String,
    val updated_at: String,
    val owner: OwnerDto
)

@Serializable
data class OwnerDto(
    val login: String,
    val id: Long,
    val avatar_url: String
)
