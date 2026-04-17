package com.aura.ai.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ScreenContextDto(
    val packageName: String,
    val activityName: String,
    val elements: List<UIElementDto>,
    val timestamp: Long,
    val screenWidth: Int,
    val screenHeight: Int
)

@Serializable
data class UIElementDto(
    val id: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isVisible: Boolean,
    val bounds: BoundsDto
)

@Serializable
data class BoundsDto(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
