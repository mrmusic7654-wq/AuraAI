package com.aura.ai.presentation.screens.github

import com.aura.ai.data.models.github.GitHubRepo
import com.aura.ai.data.models.github.ProjectTemplate

sealed class GitHubEvent {
    data class Authenticate(val token: String) : GitHubEvent()
    object LoadRepositories : GitHubEvent()
    data class CreateRepository(val name: String, val description: String?, val isPrivate: Boolean) : GitHubEvent()
    data class SelectRepository(val repo: GitHubRepo?) : GitHubEvent()
    data class GenerateApp(val template: ProjectTemplate, val appName: String) : GitHubEvent()
    object Logout : GitHubEvent()
    object ShowCreateDialog : GitHubEvent()
    object HideCreateDialog : GitHubEvent()
    object ShowAuthDialog : GitHubEvent()
    object HideAuthDialog : GitHubEvent()
    object ClearError : GitHubEvent()
}
