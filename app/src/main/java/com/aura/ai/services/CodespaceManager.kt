package com.aura.ai.services

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CodespacesManager(private val token: String) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class CodespaceInfo(
        val id: String,
        val name: String,
        val state: String,
        val webUrl: String
    )

    suspend fun createCodespace(owner: String, repo: String, branch: String = "main"): CodespaceInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val body = """{"repository":"$owner/$repo","ref":"$branch","machine":"basicLinux32gb"}"""
                val response = client.newCall(
                    Request.Builder()
                        .url("https://api.github.com/user/codespaces")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()
                
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    CodespaceInfo(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        state = json.getString("state"),
                        webUrl = json.getString("web_url")
                    )
                } else null
            } catch (e: Exception) { null }
        }
    }

    suspend fun waitForCodespaceReady(codespaceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            var attempts = 0
            while (attempts < 30) {
                try {
                    val response = client.newCall(
                        Request.Builder()
                            .url("https://api.github.com/user/codespaces/$codespaceId")
                            .header("Authorization", "Bearer $token")
                            .header("Accept", "application/vnd.github+json")
                            .build()
                    ).execute()
                    
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        if (json.getString("state") == "Available") return@withContext true
                    }
                } catch (e: Exception) { }
                delay(10000)
                attempts++
            }
            return@withContext false
        }
    }

    suspend fun executeScript(codespaceId: String, script: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val body = """{"command":"$script","workingDirectory":"/workspaces"}"""
                val response = client.newCall(
                    Request.Builder()
                        .url("https://api.github.com/user/codespaces/$codespaceId/exec")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()
                
                if (response.isSuccessful) response.body?.string() ?: "Execution submitted"
                else "Execution failed: ${response.code}"
            } catch (e: Exception) { "Error: ${e.message}" }
        }
    }

    suspend fun batchGenerateFiles(
        codespaceId: String,
        files: Map<String, String>,
        owner: String,
        repo: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val filesJson = JSONObject()
                files.forEach { (path, content) ->
                    filesJson.put(path, Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP))
                }
                
                val pythonScript = buildString {
                    append("import os, base64, json, subprocess\n\n")
                    append("files = ${filesJson.toString()}\n\n")
                    append("for path, content_b64 in files.items():\n")
                    append("    os.makedirs(os.path.dirname(path), exist_ok=True)\n")
                    append("    content = base64.b64decode(content_b64).decode('utf-8')\n")
                    append("    with open(path, 'w') as f:\n")
                    append("        f.write(content)\n")
                    append("subprocess.run(['git', 'add', '.'])\n")
                    append("subprocess.run(['git', 'commit', '-m', 'Batch generate ${files.size} files'])\n")
                    append("subprocess.run(['git', 'push'])\n")
                }

                val encodedScript = Base64.encodeToString(pythonScript.toByteArray(), Base64.NO_WRAP)
                val body = """{"command":"echo '$encodedScript' | base64 -d > /tmp/gen.py && python3 /tmp/gen.py","workingDirectory":"/workspaces/${repo}"}"""
                
                val response = client.newCall(
                    Request.Builder()
                        .url("https://api.github.com/user/codespaces/$codespaceId/exec")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()

                response.isSuccessful
            } catch (e: Exception) { false }
        }
    }

    suspend fun runBuildInCodespace(codespaceId: String, repo: String): String {
        return executeScript(codespaceId, "cd /workspaces/$repo && chmod +x gradlew && ./gradlew assembleDebug 2>&1 | tail -50")
    }

    suspend fun listCodespaces(): List<CodespaceInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url("https://api.github.com/user/codespaces?per_page=10")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .build()
                ).execute()

                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val codespaces = json.optJSONArray("codespaces") ?: return@withContext emptyList()
                    (0 until codespaces.length()).map {
                        val cs = codespaces.getJSONObject(it)
                        CodespaceInfo(cs.getString("id"), cs.getString("name"), cs.getString("state"), cs.getString("web_url"))
                    }
                } else emptyList()
            } catch (e: Exception) { emptyList() }
        }
    }

    suspend fun deleteCodespace(codespaceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url("https://api.github.com/user/codespaces/$codespaceId")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .delete()
                        .build()
                ).execute()
                response.isSuccessful
            } catch (e: Exception) { false }
        }
    }
}
