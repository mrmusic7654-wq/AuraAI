package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class CreateFileRequestDto(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String? = null
)
