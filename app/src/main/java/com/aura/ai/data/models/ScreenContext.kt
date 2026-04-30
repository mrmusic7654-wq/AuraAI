package com.aura.ai.data.models

data class ScreenContext(
    val packageName: String = "",
    val activityName: String = "",
    val elements: List<UIElement> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
) {
    fun toTextRepresentation(): String = "Package: $packageName, Activity: $activityName, Elements: ${elements.size}"
}

data class UIElement(
    val id: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String = "",
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isVisible: Boolean = true,
    val bounds: Bounds = Bounds(),
    val childCount: Int = 0
) {
    fun getDisplayText(): String = text ?: contentDescription ?: className
}

data class Bounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val centerX: Float = (left + right) / 2f
    val centerY: Float = (top + bottom) / 2f
}
