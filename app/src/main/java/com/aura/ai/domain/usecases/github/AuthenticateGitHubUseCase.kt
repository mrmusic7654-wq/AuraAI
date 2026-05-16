package com.aura.ai.domain.usecases.github

import com.aura.ai.data.models.github.GitHubUser
import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class AuthenticateGitHubUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(token: String): Result<GitHubUser> {
        return repository.authenticate(token)
    }
}
