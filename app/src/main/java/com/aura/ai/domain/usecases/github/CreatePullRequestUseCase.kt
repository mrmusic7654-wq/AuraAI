package com.aura.ai.domain.usecases.github

import com.aura.ai.data.models.github.GitHubPullRequest
import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class CreatePullRequestUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String = "main",
        body: String? = null
    ): Result<GitHubPullRequest> {
        return repository.createPullRequest(owner, repo, title, head, base, body)
    }
}
