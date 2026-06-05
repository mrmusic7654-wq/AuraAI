package com.aura.ai.services

import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudConnector(
    private val spaceUrl: String,
    private val hfToken: String
) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    data class CloudResult(
        val success: Boolean,
        val data: String,
        val code: Int
    )
    
    // ═══════════════════════════════════════════
    // TASK DELEGATION
    // ═══════════════════════════════════════════
    
    suspend fun checkHealth(): CloudResult {
        return callApi("/api/health", emptyMap())
    }
    
    suspend fun delegateCodeGeneration(appName: String, description: String): CloudResult {
        return callApi("/api/generate", mapOf(
            "app_name" to appName,
            "description" to description
        ))
    }
    
    suspend fun delegateBuildMonitoring(owner: String, repo: String): CloudResult {
        return callApi("/api/monitor", mapOf(
            "owner" to owner,
            "repo" to repo
        ))
    }
    
    suspend fun delegateScreenshotAnalysis(imageBase64: String, prompt: String): CloudResult {
        return callApi("/api/analyze", mapOf(
            "image" to imageBase64,
            "prompt" to prompt
        ))
    }
    
    suspend fun delegateContextCompression(conversation: String): CloudResult {
        return callApi("/api/compress", mapOf(
            "conversation" to conversation
        ))
    }
    
    suspend fun delegateZipProcessing(zipBase64: String): CloudResult {
        return callApi("/api/process-zip", mapOf(
            "zip_content" to zipBase64
        ))
    }
    
    suspend fun delegateFixErrors(owner: String, repo: String, errors: String): CloudResult {
        return callApi("/api/fix", mapOf(
            "owner" to owner,
            "repo" to repo,
            "errors" to errors
        ))
    }
    
    suspend fun getUsage(): CloudResult {
        return callApi("/api/usage", emptyMap())
    }
    
    // ═══════════════════════════════════════════
    // API CALL
    // ═══════════════════════════════════════════
    
    private suspend fun callApi(endpoint: String, params: Map<String, String>): CloudResult {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                params.forEach { (key, value) -> json.put(key, value) }
                
                val request = Request.Builder()
                    .url("$spaceUrl$endpoint")
                    .addHeader("Authorization", "Bearer $hfToken")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                
                CloudResult(
                    success = response.isSuccessful,
                    data = body,
                    code = response.code
                )
            } catch (e: Exception) {
                CloudResult(
                    success = false,
                    data = e.message ?: "Unknown error",
                    code = 0
                )
            }
        }
    }
    
    // ═══════════════════════════════════════════
    // QUICK TASKS (Fire and forget with callback)
    // ═══════════════════════════════════════════
    
    fun startBuildMonitoring(owner: String, repo: String, onUpdate: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = delegateBuildMonitoring(owner, repo)
            withContext(Dispatchers.Main) {
                onUpdate(if (result.success) result.data else "Build monitoring failed: ${result.data}")
            }
        }
    }
    
    fun startCodeGeneration(appName: String, description: String, onUpdate: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = delegateCodeGeneration(appName, description)
            withContext(Dispatchers.Main) {
                onUpdate(if (result.success) result.data else "Generation failed: ${result.data}")
            }
        }
    }
    
    fun startScreenshotAnalysis(imageBase64: String, prompt: String, onUpdate: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = delegateScreenshotAnalysis(imageBase64, prompt)
            withContext(Dispatchers.Main) {
                onUpdate(if (result.success) result.data else "Analysis failed: ${result.data}")
            }
        }
    }
    
    fun startContextCompression(conversation: String, onUpdate: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = delegateContextCompression(conversation)
            withContext(Dispatchers.Main) {
                onUpdate(if (result.success) result.data else "Compression failed: ${result.data}")
            }
        }
    }
    
    // ═══════════════════════════════════════════
    // STATUS
    // ═══════════════════════════════════════════
    
    fun isConfigured(): Boolean {
        return spaceUrl.isNotBlank() && hfToken.isNotBlank()
    }
    
    fun getSpaceUrl(): String = spaceUrl
}
