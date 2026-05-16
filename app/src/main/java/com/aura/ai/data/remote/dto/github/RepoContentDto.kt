package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class RepoContentDto(
    val type: String,
    val name: String,
    val path: String,
    val sha: String,
    val size: Long?,
    val url: String,
    val download_url: String?,
    val content: String?,
    val encoding: String?
)
