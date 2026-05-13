package com.aura.ai.presentation.screens.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.data.local.preferences.AuraPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class GitHubRepo(
    val id: Long, val name: String, val fullName: String, val description: String,
    val isPrivate: Boolean, val htmlUrl: String, val defaultBranch: String, val updatedAt: String
)

data class GitHubWorkflowRun(
    val id: Long, val name: String, val status: String, val conclusion: String?,
    val createdAt: String, val htmlUrl: String
)

data class GitHubState(
    val repos: List<GitHubRepo> = emptyList(),
    val selectedRepo: GitHubRepo? = null,
    val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val result: String = "",
    val showCreateDialog: Boolean = false,
    val showRepoDetail: Boolean = false,
    val fileContent: String = "",
    val filePath: String = "",
    val showFileViewer: Boolean = false,
    val isEditingFile: Boolean = false,
    val editFilePath: String = "",
    val editFileContent: String = ""
)

@HiltViewModel
class GitHubViewModel @Inject constructor(
    private val preferences: AuraPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(GitHubState())
    val state: StateFlow<GitHubState> = _state.asStateFlow()
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private fun getToken(): String? {
        val token = preferences.getGitHubToken()
        if (token.isNullOrBlank()) { _state.value = _state.value.copy(result = "❌ No GitHub token set."); return null }
        return token
    }

    fun loadRepositories() {
        val token = getToken() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url("https://api.github.com/user/repos?per_page=30&sort=updated").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string() ?: "[]")
                        val repos = (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            GitHubRepo(obj.getLong("id"), obj.getString("name"), obj.getString("full_name"), obj.optString("description", ""), obj.getBoolean("private"), obj.getString("html_url"), obj.getString("default_branch"), obj.getString("updated_at"))
                        }
                        _state.value = _state.value.copy(repos = repos, isLoading = false, result = "Loaded ${repos.size} repos")
                    } else _state.value = _state.value.copy(isLoading = false, result = "❌ ${resp.message}")
                } catch (e: Exception) { _state.value = _state.value.copy(isLoading = false, result = "❌ ${e.message}") }
            }
        }
    }

    fun createRepository(name: String, description: String, isPrivate: Boolean) {
        val token = getToken() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isCreating = true)
            withContext(Dispatchers.IO) {
                try {
                    val body = """{"name":"$name","description":"$description","private":$isPrivate,"auto_init":true}"""
                    val req = Request.Builder().url("https://api.github.com/user/repos").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").post(body.toRequestBody("application/json".toMediaType())).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val obj = JSONObject(resp.body?.string() ?: "{}")
                        val repo = GitHubRepo(obj.getLong("id"), obj.getString("name"), obj.getString("full_name"), obj.optString("description", ""), obj.getBoolean("private"), obj.getString("html_url"), obj.getString("default_branch"), obj.getString("updated_at"))
                        _state.value = _state.value.copy(repos = listOf(repo) + _state.value.repos, isCreating = false, showCreateDialog = false, result = "✅ Created: ${repo.name}")
                    } else _state.value = _state.value.copy(isCreating = false, result = "❌ ${resp.message}")
                } catch (e: Exception) { _state.value = _state.value.copy(isCreating = false, result = "❌ ${e.message}") }
            }
        }
    }

    fun selectRepo(repo: GitHubRepo) {
        _state.value = _state.value.copy(selectedRepo = repo, showRepoDetail = true)
        loadWorkflowRuns(repo.fullName)
    }

    fun loadWorkflowRuns(fullName: String) {
        val token = getToken() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url("https://api.github.com/repos/$fullName/actions/runs?per_page=20").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val obj = JSONObject(resp.body?.string() ?: "{}")
                        val arr = obj.optJSONArray("workflow_runs") ?: JSONArray()
                        val runs = (0 until arr.length()).map { i ->
                            val r = arr.getJSONObject(i)
                            GitHubWorkflowRun(r.getLong("id"), r.getString("name"), r.getString("status"), r.optString("conclusion", null), r.getString("created_at"), r.getString("html_url"))
                        }
                        _state.value = _state.value.copy(workflowRuns = runs, isLoading = false)
                    } else _state.value = _state.value.copy(isLoading = false, result = "❌ ${resp.message}")
                } catch (e: Exception) { _state.value = _state.value.copy(isLoading = false, result = "❌ ${e.message}") }
            }
        }
    }

    fun loadFileContent(fullName: String, path: String) {
        val token = getToken() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url("https://api.github.com/repos/$fullName/contents/$path").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val obj = JSONObject(resp.body?.string() ?: "{}")
                        val content = obj.optString("content", "")
                        val decoded = if (content.isNotBlank()) String(android.util.Base64.decode(content, android.util.Base64.DEFAULT)) else "Empty file"
                        _state.value = _state.value.copy(fileContent = decoded, filePath = path, showFileViewer = true, isLoading = false)
                    } else _state.value = _state.value.copy(isLoading = false, result = "❌ ${resp.message}")
                } catch (e: Exception) { _state.value = _state.value.copy(isLoading = false, result = "❌ ${e.message}") }
            }
        }
    }

    fun showCreateDialog() { _state.value = _state.value.copy(showCreateDialog = true) }
    fun hideCreateDialog() { _state.value = _state.value.copy(showCreateDialog = false) }
    fun hideRepoDetail() { _state.value = _state.value.copy(showRepoDetail = false, workflowRuns = emptyList()) }
    fun hideFileViewer() { _state.value = _state.value.copy(showFileViewer = false) }
    fun clearResult() { _state.value = _state.value.copy(result = "") }
}
