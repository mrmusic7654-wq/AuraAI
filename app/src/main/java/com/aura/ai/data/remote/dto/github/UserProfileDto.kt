package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val login: String,
    val id: Long,
    val name: String?,
    val email: String?,
    val avatar_url: String,
    val html_url: String
)
