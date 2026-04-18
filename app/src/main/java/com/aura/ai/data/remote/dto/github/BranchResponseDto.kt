package com.aura.ai.data.remote.dto.github

import kotlinx.serialization.Serializable

@Serializable
data class BranchResponseDto(
    val ref: String,
    val url: String
)

@Serializable
data class CreateBranchRequestDto(
    val ref: String,
    val sha: String
)
