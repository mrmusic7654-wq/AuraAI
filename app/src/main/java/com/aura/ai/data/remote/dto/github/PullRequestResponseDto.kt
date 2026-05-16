package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class PullRequestResponseDto(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val html_url: String,
    val created_at: String
)

@Serializable
data class CreatePullRequestDto(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null
)
