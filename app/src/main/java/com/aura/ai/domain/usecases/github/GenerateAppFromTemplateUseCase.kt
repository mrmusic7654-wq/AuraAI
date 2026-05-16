package com.aura.ai.domain.usecases.github

import com.aura.ai.data.models.github.FileToCreate
import com.aura.ai.data.models.github.ProjectTemplate
import com.aura.ai.domain.repository.GitHubRepository
import javax.inject.Inject

class GenerateAppFromTemplateUseCase @Inject constructor(
    private val repository: GitHubRepository,
    private val createRepository: CreateRepositoryUseCase
) {
    
    suspend operator fun invoke(
        template: ProjectTemplate,
        appName: String,
        description: String? = null
    ): Result<String> {
        return createRepository(appName, description, false).mapCatching { repo ->
            val files = template.files.map { file ->
                FileToCreate(
                    path = file.path,
                    content = file.content.replace("{{APP_NAME}}", appName)
                )
            }
            
            val result = repository.createMultipleFiles(
                owner = repo.owner.login,
                repo = repo.name,
                files = files,
                message = "Initial commit from Aura AI template: ${template.name}"
            )
            
            result.getOrThrow()
            repo.htmlUrl
        }
    }
}
