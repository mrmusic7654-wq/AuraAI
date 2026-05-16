package com.aura.ai.domain.usecases.github

import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class CreateWorkflowFileUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(
        owner: String,
        repo: String,
        workflowContent: String,
        workflowName: String = "build",
        branch: String = "main"
    ): Result<String> {
        return repository.createOrUpdateFile(
            owner = owner,
            repo = repo,
            path = ".github/workflows/${workflowName}.yml",
            content = workflowContent,
            message = "Add GitHub Actions workflow from Aura AI",
            branch = branch
        ).map { it.sha }
    }
}
