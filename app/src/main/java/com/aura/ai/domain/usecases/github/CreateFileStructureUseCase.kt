package com.aura.ai.domain.usecases.github

import com.aura.ai.data.models.github.BatchFileResult
import com.aura.ai.data.models.github.FileToCreate
import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class CreateFileStructureUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(
        owner: String,
        repo: String,
        files: List<FileToCreate>,
        branch: String = "main"
    ): Result<BatchFileResult> {
        return repository.createMultipleFiles(
            owner = owner,
            repo = repo,
            files = files,
            message = "Create project structure from Aura AI",
            branch = branch
        )
    }
}
