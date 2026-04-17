package com.aura.ai.data.models

data class ScreenContext(
    val packageName: String,
    val activityName: String,
    val elements: List<UIElement>,
    val timestamp: Long = System.currentTimeMillis(),
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
) {
    fun toTextRepresentation(): String {
        val builder = StringBuilder()
        builder.appendLine("Current Screen: $packageName")
        builder.appendLine("Activity: $activityName")
        builder.appendLine("Visible Elements:")
        
        elements.filter { it.isVisible && it.isClickable }.forEach { element ->
            builder.appendLine("- ${element.getDisplayText()} (${element.className})")
        }
        
        return builder.toString()
    }
}

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
) {
    fun getDisplayText(): String {
        return when {
            !text.isNullOrBlank() -> text
            !contentDescription.isNullOrBlank() -> contentDescription
            !id.isNullOrBlank() -> id.substringAfterLast("/")
            else -> className.substringAfterLast(".")
        }
    }
}

data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Float = (left + right) / 2f
    val centerY: Float = (top + bottom) / 2f
    val width: Int = right - left
    val height: Int = bottom - top
}
