package com.aura.ai.domain.usecases.github

import com.aura.ai.domain.repository.GitHubRepository
import java.io.File
import javax.inject.Inject

class DownloadArtifactUseCase @Inject constructor(
    private val repository: GitHubRepository
) {
    
    suspend operator fun invoke(
        owner: String,
        repo: String,
        artifactId: Long,
        outputFile: File
    ): Result<File> {
        return repository.downloadArtifact(owner, repo, artifactId)
            .map { data ->
                outputFile.writeBytes(data)
                outputFile
            }
    }
}
