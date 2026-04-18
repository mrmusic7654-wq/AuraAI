package com.aura.ai.presentation.screens.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.data.models.github.*
import com.aura.ai.domain.repository.GitHubRepository
import com.aura.ai.domain.usecases.github.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GitHubViewModel @Inject constructor(
    private val gitHubRepository: GitHubRepository,
    private val authenticateUseCase: AuthenticateGitHubUseCase,
    private val createRepositoryUseCase: CreateRepositoryUseCase,
    private val validateTokenUseCase: ValidateGitHubTokenUseCase,
    private val generateAppFromTemplateUseCase: GenerateAppFromTemplateUseCase
) : ViewModel() {
    
    private val _state = MutableStateFlow(GitHubState())
    val state: StateFlow<GitHubState> = _state.asStateFlow()
    
    init {
        checkAuthStatus()
    }
    
    private fun checkAuthStatus() {
        viewModelScope.launch {
            val isValid = validateTokenUseCase()
            _state.update { it.copy(isAuthenticated = isValid) }
            if (isValid) {
                loadRepositories()
            }
        }
    }
    
    fun authenticate(token: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            authenticateUseCase(token)
                .onSuccess { user ->
                    _state.update {
                        it.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            currentUser = user
                        )
                    }
                    loadRepositories()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }
    
    fun loadRepositories() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            gitHubRepository.listRepositories()
                .onSuccess { repos ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            repositories = repos
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }
    
    fun createRepository(name: String, description: String? = null, isPrivate: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true) }
            
            createRepositoryUseCase(name, description, isPrivate)
                .onSuccess { repo ->
                    _state.update {
                        it.copy(
                            isCreating = false,
                            showCreateDialog = false,
                            repositories = it.repositories + repo
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isCreating = false,
                            error = error.message
                        )
                    }
                }
        }
    }
    
    fun generateAppFromTemplate(template: ProjectTemplate, appName: String) {
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true) }
            
            generateAppFromTemplateUseCase(template, appName)
                .onSuccess { repoUrl ->
                    _state.update {
                        it.copy(
                            isGenerating = false,
                            generatedRepoUrl = repoUrl
                        )
                    }
                    loadRepositories()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isGenerating = false,
                            error = error.message
                        )
                    }
                }
        }
    }
    
    fun selectRepository(repo: GitHubRepo?) {
        _state.update { it.copy(selectedRepository = repo) }
    }
    
    fun showCreateDialog() {
        _state.update { it.copy(showCreateDialog = true) }
    }
    
    fun hideCreateDialog() {
        _state.update { it.copy(showCreateDialog = false) }
    }
    
    fun showAuthDialog() {
        _state.update { it.copy(showAuthDialog = true) }
    }
    
    fun hideAuthDialog() {
        _state.update { it.copy(showAuthDialog = false) }
    }
    
    fun logout() {
        // Clear token and reset state
        _state.update { GitHubState() }
    }
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
