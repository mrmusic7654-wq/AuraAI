package com.aura.ai.data.repository

import com.aura.ai.data.local.dao.GitHubRepoDao
import com.aura.ai.data.local.entities.GitHubRepoEntity
import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.data.models.github.*
import com.aura.ai.data.remote.datasource.GitHubRemoteDataSource
import com.aura.ai.data.remote.dto.github.RepoResponseDto
import com.aura.ai.domain.repository.GitHubRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val remoteDataSource: GitHubRemoteDataSource,
    private val repoDao: GitHubRepoDao,
    private val preferences: AuraPreferences
) : GitHubRepository {
    
    override suspend fun authenticate(token: String): Result<GitHubUser> {
        preferences.saveGitHubToken(token)
        
        return remoteDataSource.getCurrentUser().map { dto ->
            GitHubUser(
                login = dto.login,
                id = dto.id,
                name = dto.name,
                email = dto.email,
                avatarUrl = dto.avatar_url
            )
        }
    }
    
    override suspend fun validateToken(): Boolean {
        return remoteDataSource.getCurrentUser().isSuccess
    }
    
    override suspend fun createRepository(
        name: String,
        description: String?,
        isPrivate: Boolean
    ): Result<GitHubRepo> {
        return remoteDataSource.createRepository(name, description, isPrivate)
            .map { it.toDomainModel() }
            .onSuccess { repo ->
                repoDao.insertRepo(repo.toEntity())
            }
    }
    
    override suspend fun listRepositories(): Result<List<GitHubRepo>> {
        return remoteDataSource.listRepositories()
            .map { list -> list.map { it.toDomainModel() } }
            .onSuccess { repos ->
                repoDao.insertAll(repos.map { it.toEntity() })
            }
    }
    
    override suspend fun getRepository(owner: String, repo: String): Result<GitHubRepo> {
        val cached = repoDao.getRepoByFullName("$owner/$repo")
        if (cached != null) {
            return Result.success(cached.toDomainModel())
        }
        
        return remoteDataSource.getCurrentUser().map { dto ->
            GitHubRepo(
                id = 0,
                name = repo,
                fullName = "$owner/$repo",
                description = null,
                isPrivate = false,
                htmlUrl = "",
                cloneUrl = "",
                defaultBranch = "main",
                createdAt = "",
                updatedAt = "",
                owner = GitHubUser(
                    login = owner,
                    id = dto.id,
                    name = dto.name,
                    email = dto.email,
                    avatarUrl = dto.avatar_url
                )
            )
        }
    }
    
    override suspend fun createOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String
    ): Result<GitHubFile> {
        return remoteDataSource.createFile(owner, repo, path, content, message, branch)
            .map { dto ->
                GitHubFile(
                    name = dto.content?.name ?: path.substringAfterLast("/"),
                    path = path,
                    sha = dto.content?.sha ?: "",
                    content = content,
                    url = dto.content?.html_url ?: ""
                )
            }
    }
    
    override suspend fun createMultipleFiles(
        owner: String,
        repo: String,
        files: List<FileToCreate>,
        message: String,
        branch: String
    ): Result<BatchFileResult> {
        val created = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        files.forEach { file ->
            createOrUpdateFile(owner, repo, file.path, file.content, message, branch)
                .onSuccess { created.add(file.path) }
                .onFailure { errors.add("${file.path}: ${it.message}") }
        }
        
        return Result.success(
            BatchFileResult(
                success = errors.isEmpty(),
                createdFiles = created,
                errors = errors
            )
        )
    }
    
    override suspend fun createBranch(
        owner: String,
        repo: String,
        branchName: String,
        sourceBranch: String
    ): Result<GitHubBranch> {
        // Need to get SHA first, then create branch
        return Result.failure(NotImplementedError())
    }
    
    override suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String?
    ): Result<GitHubPullRequest> {
        return Result.failure(NotImplementedError())
    }
    
    override suspend fun triggerWorkflow(
        owner: String,
        repo: String,
        workflowId: String,
        ref: String,
        inputs: Map<String, String>
    ): Result<Unit> {
        return remoteDataSource.dispatchWorkflow(owner, repo, workflowId, ref, inputs)
    }
    
    override suspend fun getWorkflowRun(
        owner: String,
        repo: String,
        runId: Long
    ): Result<GitHubWorkflowRun> {
        return remoteDataSource.getWorkflowRun(owner, repo, runId)
            .map { dto ->
                GitHubWorkflowRun(
                    id = dto.id,
                    name = dto.name,
                    status = WorkflowStatus.valueOf(dto.status.uppercase()),
                    conclusion = dto.conclusion?.let { 
                        WorkflowConclusion.valueOf(it.uppercase()) 
                    },
                    createdAt = dto.created_at,
                    updatedAt = dto.updated_at,
                    htmlUrl = dto.html_url
                )
            }
    }
    
    override suspend fun listWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: String?,
        status: String?
    ): Result<List<GitHubWorkflowRun>> {
        return Result.failure(NotImplementedError())
    }
    
    override suspend fun downloadArtifact(
        owner: String,
        repo: String,
        artifactId: Long
    ): Result<ByteArray> {
        return Result.failure(NotImplementedError())
    }
    
    override suspend fun getRepositoryContents(
        owner: String,
        repo: String,
        path: String,
        ref: String
    ): Result<List<GitHubContent>> {
        return remoteDataSource.getContents(owner, repo, path, ref)
            .map { list ->
                list.map { dto ->
                    GitHubContent(
                        type = if (dto.type == "file") ContentType.FILE else ContentType.DIR,
                        name = dto.name,
                        path = dto.path,
                        sha = dto.sha,
                        size = dto.size,
                        content = dto.content,
                        downloadUrl = dto.download_url
                    )
                }
            }
    }
    
    override fun observeRepository(owner: String, repo: String): Flow<GitHubRepo?> {
        return repoDao.observeRepoByFullName("$owner/$repo")
            .map { it?.toDomainModel() }
    }
    
    override suspend fun refreshRepository(owner: String, repo: String) {
        getRepository(owner, repo)
    }
    
    private fun RepoResponseDto.toDomainModel(): GitHubRepo {
        return GitHubRepo(
            id = id,
            name = name,
            fullName = full_name,
            description = description,
            isPrivate = private,
            htmlUrl = html_url,
            cloneUrl = clone_url,
            defaultBranch = default_branch,
            createdAt = created_at,
            updatedAt = updated_at,
            owner = GitHubUser(
                login = owner.login,
                id = owner.id,
                name = null,
                email = null,
                avatarUrl = owner.avatar_url
            )
        )
    }
    
    private fun GitHubRepo.toEntity(): GitHubRepoEntity {
        return GitHubRepoEntity(
            id = id,
            name = name,
            fullName = fullName,
            description = description,
            isPrivate = isPrivate,
            htmlUrl = htmlUrl,
            cloneUrl = cloneUrl,
            defaultBranch = defaultBranch,
            ownerLogin = owner.login,
            ownerAvatarUrl = owner.avatarUrl,
            lastSynced = System.currentTimeMillis()
        )
    }
    
    private fun GitHubRepoEntity.toDomainModel(): GitHubRepo {
        return GitHubRepo(
            id = id,
            name = name,
            fullName = fullName,
            description = description,
            isPrivate = isPrivate,
            htmlUrl = htmlUrl,
            cloneUrl = cloneUrl,
            defaultBranch = defaultBranch,
            createdAt = "",
            updatedAt = "",
            owner = GitHubUser(
                login = ownerLogin,
                id = 0,
                name = null,
                email = null,
                avatarUrl = ownerAvatarUrl
            )
        )
    }
}
