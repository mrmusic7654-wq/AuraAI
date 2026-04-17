package com.aura.ai.domain.usecases.github

import com.aura.ai.data.models.github.GitHubRepo
import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class ListUserReposUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(): Result<List<GitHubRepo>> {
        return repository.listRepositories()
    }
}
