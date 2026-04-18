package com.aura.ai.presentation.screens.github

import com.aura.ai.data.models.github.GitHubRepo
import com.aura.ai.data.models.github.GitHubUser
import com.aura.ai.data.models.github.GitHubWorkflowRun

data class GitHubState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val isGenerating: Boolean = false,
    val currentUser: GitHubUser? = null,
    val repositories: List<GitHubRepo> = emptyList(),
    val selectedRepository: GitHubRepo? = null,
    val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
    val showCreateDialog: Boolean = false,
    val showAuthDialog: Boolean = false,
    val generatedRepoUrl: String? = null,
    val error: String? = null
)
