package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class FileContentResponseDto(
    val content: FileContentDto?,
    val commit: CommitDto
)

@Serializable
data class FileContentDto(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val url: String,
    val html_url: String,
    val download_url: String,
    val content: String?,
    val encoding: String?
)

@Serializable
data class CommitDto(
    val sha: String,
    val url: String,
    val message: String
)
