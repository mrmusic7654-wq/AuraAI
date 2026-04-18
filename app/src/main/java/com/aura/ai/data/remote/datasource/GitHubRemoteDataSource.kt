package com.aura.ai.data.remote.datasource

import android.util.Base64
import com.aura.ai.data.remote.api.GitHubApi
import com.aura.ai.data.remote.dto.github.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRemoteDataSource @Inject constructor(
    private val gitHubApi: GitHubApi
) {
    
    suspend fun getCurrentUser(): Result<UserProfileDto> {
        return try {
            Result.success(gitHubApi.getCurrentUser())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user")
            Result.failure(e)
        }
    }
    
    suspend fun createRepository(
        name: String,
        description: String?,
        isPrivate: Boolean
    ): Result<RepoResponseDto> {
        return try {
            val request = CreateRepoRequestDto(
                name = name,
                description = description,
                private = isPrivate,
                auto_init = true
            )
            Result.success(gitHubApi.createRepository(request))
        } catch (e: Exception) {
            Timber.e(e, "Failed to create repository")
            Result.failure(e)
        }
    }
    
    suspend fun listRepositories(): Result<List<RepoResponseDto>> {
        return try {
            Result.success(gitHubApi.listRepositories())
        } catch (e: Exception) {
            Timber.e(e, "Failed to list repositories")
            Result.failure(e)
        }
    }
    
    suspend fun createFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String
    ): Result<FileContentResponseDto> {
        return try {
            val encodedContent = Base64.encodeToString(
                content.toByteArray(),
                Base64.NO_WRAP
            )
            
            val request = CreateFileRequestDto(
                message = message,
                content = encodedContent,
                branch = branch
            )
            
            Result.success(
                gitHubApi.createOrUpdateFile(owner, repo, path, request)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create file")
            Result.failure(e)
        }
    }
    
    suspend fun getContents(
        owner: String,
        repo: String,
        path: String,
        ref: String?
    ): Result<List<RepoContentDto>> {
        return try {
            Result.success(gitHubApi.getContents(owner, repo, path, ref))
        } catch (e: Exception) {
            Timber.e(e, "Failed to get contents")
            Result.failure(e)
        }
    }
    
    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: String,
        ref: String,
        inputs: Map<String, String>
    ): Result<Unit> {
        return try {
            val request = WorkflowDispatchRequestDto(ref, inputs)
            gitHubApi.dispatchWorkflow(owner, repo, workflowId, request)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to dispatch workflow")
            Result.failure(e)
        }
    }
    
    suspend fun getWorkflowRun(
        owner: String,
        repo: String,
        runId: Long
    ): Result<WorkflowRunResponseDto> {
        return try {
            Result.success(gitHubApi.getWorkflowRun(owner, repo, runId))
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workflow run")
            Result.failure(e)
        }
    }
}
