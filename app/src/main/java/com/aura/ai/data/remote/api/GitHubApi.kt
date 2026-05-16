package com.aura.ai.data.remote.api

import com.aura.ai.data.remote.dto.github.*
import retrofit2.http.*

interface GitHubApi {
    
    @GET("user")
    suspend fun getCurrentUser(): UserProfileDto
    
    @POST("user/repos")
    suspend fun createRepository(
        @Body request: CreateRepoRequestDto
    ): RepoResponseDto
    
    @GET("user/repos")
    suspend fun listRepositories(
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<RepoResponseDto>
    
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): RepoResponseDto
    
    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CreateFileRequestDto
    ): FileContentResponseDto
    
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): List<RepoContentDto>
    
    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateBranchRequestDto
    ): BranchResponseDto
    
    @POST("repos/{owner}/{repo}/pulls")
    suspend fun createPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreatePullRequestDto
    ): PullRequestResponseDto
    
    @POST("repos/{owner}/{repo}/actions/workflows/{workflowId}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflowId") workflowId: String,
        @Body request: WorkflowDispatchRequestDto
    )
    
    @GET("repos/{owner}/{repo}/actions/runs/{runId}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): WorkflowRunResponseDto
    
    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("workflow_id") workflowId: String? = null,
        @Query("status") status: String? = null
    ): WorkflowRunsListDto
    
    @GET("repos/{owner}/{repo}/actions/artifacts/{artifactId}/zip")
    @Streaming
    suspend fun downloadArtifact(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("artifactId") artifactId: Long
    ): okhttp3.ResponseBody
}
