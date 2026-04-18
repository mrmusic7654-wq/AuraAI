package com.aura.ai.domain.usecases.github

import com.aura.ai.data.models.github.GitHubRepo
import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class CreateRepositoryUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(
        name: String,
        description: String? = null,
        isPrivate: Boolean = false
    ): Result<GitHubRepo> {
        return repository.createRepository(name, description, isPrivate)
    }
}
