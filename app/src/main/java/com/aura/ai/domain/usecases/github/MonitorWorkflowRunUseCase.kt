package com.aura.ai.domain.usecases.github

import com.aura.ai.data.models.github.GitHubWorkflowRun
import com.aura.ai.domain.repository.GitHubRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

class MonitorWorkflowRunUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(
        owner: String,
        repo: String,
        runId: Long,
        pollingIntervalMs: Long = 5000,
        onUpdate: (GitHubWorkflowRun) -> Unit
    ): Result<GitHubWorkflowRun> {
        var currentRun: GitHubWorkflowRun
        
        do {
            val result = repository.getWorkflowRun(owner, repo, runId)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
            
            currentRun = result.getOrThrow()
            onUpdate(currentRun)
            
            if (currentRun.status != com.aura.ai.data.models.github.WorkflowStatus.COMPLETED) {
                delay(pollingIntervalMs)
            }
        } while (currentRun.status != com.aura.ai.data.models.github.WorkflowStatus.COMPLETED)
        
        return Result.success(currentRun)
    }
}
