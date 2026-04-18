package com.aura.ai.data.models.github

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: TemplateCategory,
    val files: List<TemplateFile>,
    val workflowFile: String? = null
)

data class TemplateFile(
    val path: String,
    val content: String,
    val isDirectory: Boolean = false
)

enum class TemplateCategory {
    ANDROID_APP,
    COMPOSE_UI,
    API_INTEGRATION,
    DATABASE,
    GITHUB_ACTIONS
}
