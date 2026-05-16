package com.aura.ai.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GeminiResponseDto(
    val candidates: List<Candidate>? = emptyList(),
    val promptFeedback: PromptFeedback? = null
) {
    val text: String?
        get() = candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
}

@Serializable
data class Candidate(
    val content: Content,
    val finishReason: String? = null,
    val index: Int = 0,
    val safetyRatings: List<SafetyRating>? = null
)

@Serializable
data class PromptFeedback(
    val safetyRatings: List<SafetyRating>? = null
)

@Serializable
data class SafetyRating(
    val category: String,
    val probability: String
)
