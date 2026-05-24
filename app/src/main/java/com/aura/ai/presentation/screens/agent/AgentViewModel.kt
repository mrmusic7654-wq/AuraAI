package com.aura.ai.presentation.screens.agent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.data.local.database.SessionDatabase
import com.aura.ai.data.local.database.SessionEntity
import com.aura.ai.data.local.database.MessageEntity
import com.aura.ai.data.local.database.ModelUsageEntity
import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.CodespacesManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ═══════════════════════════════════════════
// SECTION 1: DATA CLASSES
// ═══════════════════════════════════════════

data class ChatMessage(val text: String, val isUser: Boolean)
data class ModelInfo(val name: String, val displayName: String, val strength: String, val dailyRequests: Int, val dailyLimit: Int, val isInCooldown: Boolean, val isSelected: Boolean)
data class BuildLoopState(val attemptNumber: Int = 0, val maxAttempts: Int = 20, val buildStatus: BuildStatus = BuildStatus.IDLE, val workflowRunId: Long? = null, val errorSummary: String = "", val lastFixDescription: String = "", val buildUrl: String = "", val totalFixesApplied: Int = 0, val artifactUrl: String? = null)
enum class BuildStatus { IDLE, BUILDING, WAITING_FOR_BUILD, BUILD_SUCCESS, ANALYZING_ERROR, FIXING, RETRYING, FAILED, DOWNLOADING_ARTIFACT }
data class AgentUiState(val messages: List<ChatMessage> = listOf(ChatMessage("AURA AI - READY", false)), val input: String = "", val loading: Boolean = false, val isExecuting: Boolean = false, val currentTask: String = "", val executionMode: ExecutionMode = ExecutionMode.IDLE, val activeModel: String = "gemini-2.5-flash", val showDrawer: Boolean = false, val showModelDashboard: Boolean = false, val manualModelSelected: Boolean = false, val currentSessionId: String? = null, val buildLoop: BuildLoopState? = null, val isGeneratingApp: Boolean = false, val generationProgress: String = "", val codespaceMode: Boolean = false, val activeCodespaceId: String? = null)
enum class ExecutionMode { IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION, FILE_OPERATION, REPO_ANALYSIS, FEATURE_TRANSFER }

private sealed class WorkflowResult { data object Success : WorkflowResult(); data class Failure(val error: String, val logs: String) : WorkflowResult(); data object Timeout : WorkflowResult() }

// ═══════════════════════════════════════════
// SECTION 2: VIEWMODEL
// ═══════════════════════════════════════════

@HiltViewModel
class AgentViewModel @Inject constructor(private val preferences: AuraPreferences) : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()
    private var taskJob: Job? = null
    private var activeRepo = ""; private var activeOwner = ""
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    private val sessionDb by lazy { SessionDatabase.getInstance(com.aura.ai.AuraApplication.instance) }
    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    private val _modelUsage = MutableStateFlow<List<ModelUsageEntity>>(emptyList())

    private val modelRegistry = linkedMapOf("gemini-2.5-flash" to Triple(250, 10, "Balanced"), "gemini-3.1-flash-lite" to Triple(1000, 15, "Fast"), "gemini-2.0-flash-lite" to Triple(1500, 15, "Legacy"))
    private val modelCooldowns = mutableMapOf<String, Long>()
    private var consecutiveFailures = 0

    init {
        loadSessions()
        loadModelUsage()
        viewModelScope.launch {
            resetDailyCountersIfNeeded()
            val lastId = preferences.getLastSessionId()
            if (lastId != null) switchSession(lastId)
            else if (_sessions.value.isEmpty()) createNewSession()
        }
    }

    private fun selectModel(): String { if (_state.value.manualModelSelected) return _state.value.activeModel; if (consecutiveFailures >= 3) return "gemini-2.0-flash-lite"; return "gemini-2.5-flash" }
    private fun isModelInCooldown(m: String) = modelCooldowns[m]?.let { System.currentTimeMillis() < it } ?: false
    private fun recordModelUsage(m: String) { viewModelScope.launch { val e = sessionDb.modelUsageDao().getModelUsage(m); sessionDb.modelUsageDao().insertOrUpdateModelUsage(ModelUsageEntity(m, (e?.dailyRequests ?: 0) + 1, 1500, "", 0, "")) } }
    private fun applyModelCooldown(m: String) { consecutiveFailures++; modelCooldowns[m] = System.currentTimeMillis() + 60000 }

    // ═══════════════════════════════════════════
    // SECTION 2.1: PUBLIC INTERFACE
    // ═══════════════════════════════════════════

    fun updateInput(text: String) { _state.value = _state.value.copy(input = text) }
    fun toggleDrawer() { _state.value = _state.value.copy(showDrawer = !_state.value.showDrawer) }
    fun toggleModelDashboard() { _state.value = _state.value.copy(showModelDashboard = !_state.value.showModelDashboard) }
    
    fun selectModel(modelName: String) { viewModelScope.launch { _state.value = _state.value.copy(activeModel = modelName, manualModelSelected = true, showModelDashboard = false); _currentSessionId.value?.let { sessionDb.sessionDao().updateSelectedModel(it, modelName) }; preferences.setPreferredModel(modelName) } }
    fun getModelInfoList(): List<ModelInfo> = modelRegistry.map { (n, s) -> val u = _modelUsage.value.find { it.modelName == n }; ModelInfo(n, n.replace("gemini-", "").uppercase(), s.third, u?.dailyRequests ?: 0, s.first, isModelInCooldown(n), n == _state.value.activeModel) }
    
    fun createNewSession() { viewModelScope.launch { val s = SessionEntity(id = UUID.randomUUID().toString(), title = "New Session", selectedModel = _state.value.activeModel); sessionDb.sessionDao().insertSession(s); switchSession(s.id) } }
    fun switchSession(sessionId: String) { viewModelScope.launch { val s = sessionDb.sessionDao().getSession(sessionId) ?: return@launch; _currentSessionId.value = sessionId; preferences.setLastSessionId(sessionId); val msgs = sessionDb.messageDao().getMessagesForSessionOnce(sessionId); _state.value = _state.value.copy(messages = if (msgs.isEmpty()) _state.value.messages else msgs.map { ChatMessage(it.text, it.isUser) }, currentSessionId = sessionId, activeModel = s.selectedModel) } }
    fun deleteSession(sessionId: String) { viewModelScope.launch { sessionDb.sessionDao().deleteSession(sessionId); if (_currentSessionId.value == sessionId) { _currentSessionId.value = null; val r = _sessions.value.filter { it.id != sessionId }; if (r.isNotEmpty()) switchSession(r.first().id) else createNewSession() } } }

    fun send() { val msg = _state.value.input.trim(); if (msg.isBlank()) return; _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(msg, true), input = "", loading = true); if (handleControl(msg)) return; taskJob = viewModelScope.launch { _state.value = _state.value.copy(isExecuting = true); saveMsg(msg, true); val r = execute(msg); _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(r, false), loading = false, isExecuting = false, executionMode = ExecutionMode.IDLE); saveMsg(r, false) } }

    private fun handleControl(input: String): Boolean = when (input.lowercase().trim()) { "stop", "cancel" -> { taskJob?.cancel(); _state.value = _state.value.copy(loading = false, isExecuting = false, isGeneratingApp = false, buildLoop = null); true } else -> false }

    // ═══════════════════════════════════════════
    // SECTION 2.2: COMMAND ROUTER
    // ═══════════════════════════════════════════

    private suspend fun execute(input: String): String {
        val lower = input.lowercase().trim()
        if (lower == "device info") return "📱 ${Build.MODEL}\n🤖 ${Build.VERSION.RELEASE}"
        if (lower == "time") return "🕐 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
        if (lower.startsWith("open ")) { val app = lower.removePrefix("open ").trim(); val pkg = resolveApp(app) ?: return "❌ Unknown app"; return try { val i = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(pkg); i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); com.aura.ai.AuraApplication.instance.startActivity(i); "✅ Opened $app" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower == "home") { val s = AuraAccessibilityService.instance; if (s != null) { s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "🏠 Home" } }
        if (lower == "back") { val s = AuraAccessibilityService.instance; if (s != null) { s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "⬅️ Back" } }
        if (lower == "screenshot") { val s = AuraAccessibilityService.instance; if (s != null) { s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "📸 Screenshot" } }
        if (lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) { if (lower.contains("repo")) return githubCommand(input); _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP); return createApp(input) }
        if (lower.startsWith("continue ")) { _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP); return continueApp(input) }
        if (lower.startsWith("codespace ")) { return codespaceCommand(lower) }
        return githubCommand(input) ?: chatWithGemini(input)
    }

    // ═══════════════════════════════════════════
    // SECTION 2.3: CODESPACE COMMANDS
    // ═══════════════════════════════════════════

    private suspend fun codespaceCommand(lower: String): String {
        val token = preferences.getGitHubToken() ?: return "❌ No GitHub token."
        val manager = CodespacesManager(token)
        if (lower.startsWith("codespace create")) { val parts = lower.removePrefix("codespace create").trim().split("/"); val o = if (parts.size == 2) parts[0] else activeOwner; val r = if (parts.size == 2) parts[1] else activeRepo; if (o.isBlank() || r.isBlank()) return "❌ Specify owner/repo."; val cs = manager.createCodespace(o, r) ?: return "❌ Failed."; _state.value = _state.value.copy(activeCodespaceId = cs.id, codespaceMode = true); return "🖥️ Codespace: ${cs.name}\n🔗 ${cs.webUrl}" }
        if (lower == "codespace list") { val list = manager.listCodespaces(); return if (list.isEmpty()) "📁 None." else list.joinToString("\n") { "• ${it.name}" } }
        return "❌ Unknown codespace command."
    }

    // ═══════════════════════════════════════════
    // SECTION 2.4: GITHUB COMMANDS
    // ═══════════════════════════════════════════

    private suspend fun githubCommand(input: String): String? {
        val token = preferences.getGitHubToken() ?: return null; val lower = input.lowercase().trim()
        if (lower.contains("create") && lower.contains("repo")) { val n = input.replace(Regex("(?i)(create|a|repo|repository|github)"), "").trim().sanitize().take(50); return apiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$n","private":false,"auto_init":true}""") }
        if (lower.contains("list") && lower.contains("repo")) return apiCall("GET", "https://api.github.com/user/repos?per_page=10", token, null)
        if (lower.startsWith("compile ")) { val repo = lower.removePrefix("compile ").trim(); val p = repo.split("/"); if (p.size != 2) return "❌ Format: compile owner/repo"; return triggerBuild(token, p[0], p[1]) }
        if (lower.startsWith("browse repo ")) { val repo = lower.removePrefix("browse repo ").trim(); val p = repo.split("/"); if (p.size != 2) return "❌ Format: browse owner/repo"; return browseRepo(token, p[0], p[1]) }
        if (lower.startsWith("set repo ")) { val repo = lower.removePrefix("set repo ").trim(); val p = repo.split("/"); if (p.size != 2) return "❌ Format: set repo owner/repo"; activeOwner = p[0]; activeRepo = p[1]; return "✅ Active: $activeOwner/$activeRepo" }
        if (lower.startsWith("read repo file ")) { val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" "); if (parts.size < 2) return "❌ Format: read repo file owner/repo path"; val rp = parts[0].split("/"); if (rp.size != 2) return "❌ Format."; return readFile(token, rp[0], rp[1], parts.drop(1).joinToString(" ")) }
        return null
    }

        // ═══════════════════════════════════════════
    // SECTION 2.5: APP GENERATION
    // ═══════════════════════════════════════════

    private suspend fun createApp(input: String): String {
        val token = preferences.getGitHubToken() ?: return "❌ No GitHub token."
        val key = preferences.getApiKey() ?: return "❌ No Gemini API key."
        _state.value = _state.value.copy(isGeneratingApp = true)
        val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
        val appName = appDesc.split(" ").firstOrNull()?.sanitize()?.take(50) ?: "MyApp"
        val description = appDesc.split(" ").drop(1).joinToString(" ").trim().ifBlank { "A simple Android app" }
        return generateApp(token, key, appName, description, null)
    }

    private suspend fun continueApp(input: String): String {
        val token = preferences.getGitHubToken() ?: return "❌ No GitHub token."
        val key = preferences.getApiKey() ?: return "❌ No Gemini API key."
        if (activeRepo.isBlank()) return "❌ No active repo. Use 'set repo owner/repo' first."
        _state.value = _state.value.copy(isGeneratingApp = true)
        val instruction = input.removePrefix("continue ").trim()
        val existingFiles = getFileTree(token, activeOwner, activeRepo)
        addMsg("📁 Reading ${existingFiles.size} existing files...")
        val existingContext = buildExistingContext(token, activeOwner, activeRepo, existingFiles)
        return generateApp(token, key, activeRepo, instruction, existingContext)
    }

    private suspend fun generateApp(token: String, key: String, appName: String, description: String, existingContext: String?): String {
        try {
            addMsg("🧠 Planning $appName...")
            val model = GenerativeModel(selectModel(), key, generationConfig { temperature = 0.2f; maxOutputTokens = 60000 })
            
            val planPrompt = if (existingContext != null) """
You are continuing an existing Android app. The user wants: "$description"

EXISTING PROJECT FILES:
$existingContext

Your task: Generate new/modified files to add this feature to the existing app.
Keep all existing code intact. Only add or modify what's needed.

FORMAT YOUR RESPONSE EXACTLY LIKE THIS:
===FILE:path/to/file.kt===
[complete file content - either new or modified]
===END===
            """.trimIndent() else """
You are an AI agent that creates complete Android apps. The user wants: "$appName - $description"

Generate ALL files for a complete, compilable Android app.

FORMAT YOUR RESPONSE EXACTLY LIKE THIS:
===FILE:build.gradle.kts===
[complete content]
===END===
===FILE:app/build.gradle.kts===
[complete content]
===END===
===FILE:settings.gradle.kts===
[complete content]
===END===
===FILE:gradle.properties===
[complete content]
===END===
===FILE:gradle/wrapper/gradle-wrapper.properties===
[complete content]
===END===
===FILE:app/src/main/AndroidManifest.xml===
[complete content]
===END===
... (all files needed)

RULES:
- Every file must have COMPLETE, working code - no placeholders
- Package: com.example.${appName.sanitize()}
- Use Jetpack Compose with Material3
- Min SDK 26, Target SDK 34, Compose compiler 1.5.10
            """.trimIndent()
            
            addMsg("📝 Generating files...")
            val response = model.generateContent(content { text(planPrompt) }).text ?: return "❌ No response."
            recordModelUsage(selectModel())
            
            val files = parseFileResponse(response)
            if (files.isEmpty()) return "❌ Could not parse files."
            addMsg("✅ Generated ${files.size} files")
            
            // Create repo if new app
            if (existingContext == null) {
                addMsg("📁 Creating GitHub repository...")
                val createResult = apiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$appName","private":false,"auto_init":false}""")
                if (createResult.startsWith("❌")) return "❌ $createResult"
                val userResult = apiCall("GET", "https://api.github.com/user", token, null)
                val owner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(userResult)?.groupValues?.get(1) ?: return "❌ No username."
                activeOwner = owner; activeRepo = appName
            }
            
            addMsg("📤 Pushing ${files.size} files...")
            var pushed = 0
            for ((path, content) in files) {
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val sha = if (existingContext != null) getFileSha(token, activeOwner, activeRepo, path) else null
                val body = if (sha != null) """{"message":"Update $path","content":"$encoded","sha":"$sha"}""" else """{"message":"Add $path","content":"$encoded"}"""
                if (!apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$path", token, body).startsWith("❌")) pushed++
            }
            
            if (existingContext == null) addWorkflow(token, activeOwner, activeRepo, appName)
            addMsg("✅ Pushed $pushed/${files.size} files")
            
            addMsg("🔨 Triggering build...")
            val runId = triggerWorkflow(token, activeOwner, activeRepo)
            if (runId != null) {
                addMsg("🔗 Build: https://github.com/$activeOwner/$activeRepo/actions/runs/$runId")
                val buildResult = monitorBuild(token, activeOwner, activeRepo, runId, key)
                _state.value = _state.value.copy(isGeneratingApp = false)
                return buildResult
            }
            
            _state.value = _state.value.copy(isGeneratingApp = false)
            return "✅ App generated!\n📁 github.com/$activeOwner/$activeRepo\n📄 ${files.size} files"
        } catch (e: Exception) {
            _state.value = _state.value.copy(isGeneratingApp = false)
            return "❌ ${e.message}"
        }
    }

    private fun parseFileResponse(response: String): Map<String, String> {
        val files = mutableMapOf<String, String>()
        Regex("===FILE:(.+?)===\\s*([\\s\\S]*?)\\s*===END===").findAll(response).forEach { m ->
            val path = m.groupValues[1].trim(); val content = m.groupValues[2].trim()
            if (path.isNotEmpty() && content.isNotEmpty() && content.length > 20) files[path] = content
        }
        return files
    }

    private suspend fun buildExistingContext(token: String, owner: String, repo: String, fileTree: List<String>): String {
        val sb = StringBuilder()
        for (path in fileTree.take(30)) {
            try {
                val content = readFileContent(token, owner, repo, path)
                if (content != null) sb.append("===FILE:$path===\n${content.take(2000)}\n===END===\n")
            } catch (e: Exception) { }
        }
        return sb.toString()
    }
            

    // ═══════════════════════════════════════════
    // SECTION 2.6: BUILD MONITORING
    // ═══════════════════════════════════════════

    private suspend fun monitorBuild(token: String, owner: String, repo: String, runId: Long, key: String): String {
        var delay = 5000L; var attempt = 0
        repeat(60) {
            delay(delay); delay = minOf(delay * 2, 30000L); attempt++
            val status = withContext(Dispatchers.IO) {
                try { val b = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId").header("Authorization", "Bearer $token").build()).execute().body?.string(); Pair(Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(b ?: "")?.groupValues?.get(1), Regex("\"conclusion\"\\s*:\\s*\"([^\"]+)\"").find(b ?: "")?.groupValues?.get(1)) } catch (e: Exception) { null }
            }
            if (status?.first == "completed") {
                return if (status.second == "success") {
                    val artifact = getArtifact(token, owner, repo, runId)
                    "🎉 BUILD SUCCESS!\n📱 $repo\n📥 ${artifact ?: "APK in Actions"}"
                } else {
                    val logs = fetchLogs(token, owner, repo, runId)
                    val errors = extractErrors(logs)
                    if (attempt < 3) {
                        addMsg("🔧 Auto-fixing...")
                        val fixed = fixErrors(key, token, owner, repo, errors, logs)
                        if (fixed) {
                            val newRunId = triggerWorkflow(token, owner, repo)
                            if (newRunId != null) return monitorBuild(token, owner, repo, newRunId, key)
                        }
                    }
                    "❌ Build failed after $attempt attempts.\n🔗 https://github.com/$owner/$repo/actions/runs/$runId"
                }
            }
        }
        return "⏰ Build timed out."
    }

    private suspend fun fixErrors(key: String, token: String, owner: String, repo: String, errors: String, logs: String): Boolean {
        val model = GenerativeModel(selectModel(), key, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
        return try {
            val response = model.generateContent(content { text("Fix these build errors:\n$errors\n\nReturn fixed files:\n===FILE:path===\ncontent\n===END===") }).text
            val text = response ?: return false
            recordModelUsage(selectModel())
            val files = parseFileResponse(text)
            if (files.isEmpty()) return false
            var applied = 0
            for ((path, content) in files) {
                val sha = getFileSha(token, owner, repo, path)
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val r = if (sha != null) apiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/$path", token, """{"message":"Fix","content":"$encoded","sha":"$sha"}""") else apiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/$path", token, """{"message":"Add","content":"$encoded"}""")
                if (!r.startsWith("❌")) applied++
            }
            applied > 0
        } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // SECTION 2.7: GITHUB API HELPERS
    // ═══════════════════════════════════════════

    private suspend fun apiCall(method: String, url: String, token: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").header("Content-Type", "application/json").apply { when (method) { "POST" -> post((body ?: "{}").toRequestBody("application/json".toMediaType())); "PUT" -> put((body ?: "{}").toRequestBody("application/json".toMediaType())); "PATCH" -> patch((body ?: "{}").toRequestBody("application/json".toMediaType())) } }.build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) { val rb = res.body?.string() ?: "OK"; if (method == "POST" && url.contains("/user/repos")) "✅ ${Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(rb)?.groupValues?.get(1) ?: "done"}" else if (method == "GET" && url.contains("/user/repos") && !url.contains("/contents")) { val arr = JSONArray(rb); if (arr.length() == 0) "📁 None" else "📁:\n" + (0 until minOf(arr.length(), 10)).joinToString("\n") { "• ${arr.getJSONObject(it).getString("full_name")}" } } else rb } else "❌ ${res.code}"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun triggerBuild(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) { try { val lb = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string(); val wid = Regex("\"id\"\\s*:\\s*(\\d+)").find(lb ?: "")?.groupValues?.get(1) ?: return@withContext "❌"; if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "🚀 Triggered!" else "⚠️ Failed" } catch (e: Exception) { "❌ ${e.message}" } }

    private suspend fun triggerWorkflow(token: String, owner: String, repo: String): Long? = withContext(Dispatchers.IO) {
        try { val lb = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string(); val wid = Regex("\"id\"\\s*:\\s*(\\d+)").find(lb ?: "")?.groupValues?.get(1) ?: return@withContext null; client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute(); delay(5000); val rb = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1").header("Authorization", "Bearer $token").build()).execute().body?.string(); Regex("\"id\"\\s*:\\s*(\\d+)").find(rb ?: "")?.groupValues?.get(1)?.toLong() } catch (e: Exception) { null }
    }

    private suspend fun browseRepo(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) { try { val r = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute(); if (!r.isSuccessful) return@withContext "❌"; val t = JSONObject(r.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext "📁"; "📁 $owner/$repo:\n" + (0 until minOf(t.length(), 30)).joinToString("\n") { "  📄 ${t.getJSONObject(it).getString("path")}" } } catch (e: Exception) { "❌ ${e.message}" } }

    private suspend fun fetchLogs(token: String, owner: String, repo: String, runId: Long): String = withContext(Dispatchers.IO) { try { client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/logs").header("Authorization", "Bearer $token").build()).execute().body?.string()?.take(10000) ?: "" } catch (e: Exception) { "" } }

    private fun extractErrors(logs: String): String { val p = listOf(Regex("(?i)error:.*"), Regex("(?i)FAILURE:.*"), Regex("(?i)Unresolved reference.*")); val e = p.flatMap { it.findAll(logs).map { m -> m.value }.toList() }; return if (e.isEmpty()) logs.take(3000) else e.take(20).joinToString("\n") }

    private suspend fun getArtifact(token: String, owner: String, repo: String, runId: Long): String? = withContext(Dispatchers.IO) { try { Regex("\"archive_download_url\"\\s*:\\s*\"([^\"]+)\"").find(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "")?.groupValues?.get(1) } catch (e: Exception) { null } }

    private suspend fun getFileSha(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) { try { JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}").optString("sha", null) } catch (e: Exception) { null } }

    private suspend fun getFileTree(token: String, owner: String, repo: String): List<String> = withContext(Dispatchers.IO) {
        try { var r = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute(); if (!r.isSuccessful) r = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/master?recursive=1").header("Authorization", "Bearer $token").build()).execute(); if (r.isSuccessful) { val t = JSONObject(r.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext emptyList(); (0 until t.length()).map { t.getJSONObject(it).getString("path") } } else emptyList() } catch (e: Exception) { emptyList() }
    }

    private suspend fun readFileContent(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) { try { val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}"); val c = j.optString("content", ""); if (c.isNotBlank()) String(android.util.Base64.decode(c, android.util.Base64.DEFAULT)) else null } catch (e: Exception) { null } }

    private suspend fun readFile(token: String, owner: String, repo: String, path: String): String = withContext(Dispatchers.IO) { try { val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}"); val c = j.optString("content", ""); if (c.isBlank()) return@withContext "📄 Empty"; val d = String(android.util.Base64.decode(c, android.util.Base64.DEFAULT)); if (d.length > 3000) "📄 $path:\n${d.take(3000)}..." else "📄 $path:\n$d" } catch (e: Exception) { "❌ ${e.message}" } }

    private suspend fun addWorkflow(token: String, owner: String, repo: String, name: String) {
        val yaml = "name: Build $name\non: [push, workflow_dispatch]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    timeout-minutes: 30\n    steps:\n      - uses: actions/checkout@v4\n      - name: Setup Gradle\n        run: |\n          if [ ! -f \"gradlew\" ]; then gradle wrapper --gradle-version 8.4; fi\n          chmod +x gradlew\n      - uses: actions/setup-java@v4\n        with: {java-version: '17', distribution: 'temurin'}\n      - uses: gradle/actions/setup-gradle@v3\n      - run: ./gradlew assembleDebug --no-daemon\n        env:\n          GRADLE_OPTS: \"-Dorg.gradle.jvmargs=-Xmx4g\"\n      - uses: actions/upload-artifact@v4\n        with: {name: ${name}-debug, path: app/build/outputs/apk/debug/app-debug.apk}"
        apiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/.github/workflows/build.yml", token, """{"message":"Add CI","content":"${android.util.Base64.encodeToString(yaml.toByteArray(), android.util.Base64.NO_WRAP)}"}""")
    }

    // ═══════════════════════════════════════════
    // SECTION 2.8: GEMINI CHAT
    // ═══════════════════════════════════════════

    private suspend fun chatWithGemini(input: String): String {
        val key = preferences.getApiKey() ?: return "❌ No API key."
        val model = GenerativeModel(selectModel(), key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 })
        return try { val r = model.generateContent(content { text(input) }).text ?: "No response"; recordModelUsage(selectModel()); r } catch (e: Exception) { "❌ ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // SECTION 2.9: UTILITY FUNCTIONS
    // ═══════════════════════════════════════════

    private fun addMsg(text: String) { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(text, false), generationProgress = text) }
    private suspend fun saveMsg(text: String, isUser: Boolean) { _currentSessionId.value?.let { sessionDb.messageDao().insertMessage(MessageEntity(UUID.randomUUID().toString(), it, text, isUser)) } }
    private fun loadSessions() { viewModelScope.launch { sessionDb.sessionDao().getAllSessions().collect { _sessions.value = it; if (_currentSessionId.value == null && it.isNotEmpty()) switchSession(it.first().id) } } }
    private fun loadModelUsage() { viewModelScope.launch { sessionDb.modelUsageDao().getAllModelUsage().collect { _modelUsage.value = it } } }
    private suspend fun resetDailyCountersIfNeeded() { sessionDb.modelUsageDao().resetDailyCounters(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    private fun String.sanitize() = this.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun resolveApp(name: String): String? = when (name.lowercase()) { "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; "camera" -> "com.android.camera"; "gmail" -> "com.google.android.gm"; "maps" -> "com.google.android.apps.maps"; else -> null }
}
