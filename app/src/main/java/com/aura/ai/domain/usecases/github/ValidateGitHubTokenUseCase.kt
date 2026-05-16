package com.aura.ai.domain.usecases.github

import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class ValidateGitHubTokenUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(): Boolean {
        return repository.validateToken()
    }
}
