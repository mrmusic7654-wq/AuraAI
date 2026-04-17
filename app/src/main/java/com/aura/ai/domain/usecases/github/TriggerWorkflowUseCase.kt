package com.aura.ai.domain.usecases.github

import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class TriggerWorkflowUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(
        owner: String,
        repo: String,
        workflowId: String,
        ref: String = "main",
        inputs: Map<String, String> = emptyMap()
    ): Result<Unit> {
        return repository.triggerWorkflow(owner, repo, workflowId, ref, inputs)
    }
}
