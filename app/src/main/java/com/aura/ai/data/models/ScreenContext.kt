package com.aura.ai.data.models

data class ScreenContext(
    val packageName: String,
    val activityName: String,
    val elements: List<UIElement>,
    val timestamp: Long = System.currentTimeMillis()
)

data class UIElement(
    val id: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isVisible: Boolean,
    val bounds: Bounds,
    val childCount: Int = 0
)

data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Float = (left + right) / 2f
    val centerY: Float = (top + bottom) / 2f
}
