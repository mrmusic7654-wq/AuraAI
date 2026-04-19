package com.aura.ai.domain.repository

import com.aura.ai.data.models.github.*
import kotlinx.coroutines.flow.Flow

interface GitHubRepository {
    
    suspend fun authenticate(token: String): Result<GitHubUser>
    
    suspend fun validateToken(): Boolean
    
    suspend fun createRepository(
        name: String,
        description: String? = null,
        isPrivate: Boolean = false
    ): Result<GitHubRepo>
    
    suspend fun listRepositories(): Result<List<GitHubRepo>>
    
    suspend fun getRepository(owner: String, repo: String): Result<GitHubRepo>
    
    suspend fun createOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String = "main"
    ): Result<GitHubFile>
    
    suspend fun createMultipleFiles(
        owner: String,
        repo: String,
        files: List<FileToCreate>,
        message: String,
        branch: String = "main"
    ): Result<BatchFileResult>
    
    suspend fun createBranch(
        owner: String,
        repo: String,
        branchName: String,
        sourceBranch: String = "main"
    ): Result<GitHubBranch>
    
    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String = "main",
        body: String? = null
    ): Result<GitHubPullRequest>
    
    suspend fun triggerWorkflow(
        owner: String,
        repo: String,
        workflowId: String,
        ref: String = "main",
        inputs: Map<String, String> = emptyMap()
    ): Result<Unit>
    
    suspend fun getWorkflowRun(
        owner: String,
        repo: String,
        runId: Long
    ): Result<GitHubWorkflowRun>
    
    suspend fun listWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: String? = null,
        status: String? = null
    ): Result<List<GitHubWorkflowRun>>
    
    suspend fun downloadArtifact(
        owner: String,
        repo: String,
        artifactId: Long
    ): Result<ByteArray>
    
    suspend fun getRepositoryContents(
        owner: String,
        repo: String,
        path: String = "",
        ref: String = "main"
    ): Result<List<GitHubContent>>
    
    fun observeRepository(owner: String, repo: String): Flow<GitHubRepo?>
    
    suspend fun refreshRepository(owner: String, repo: String)
}
