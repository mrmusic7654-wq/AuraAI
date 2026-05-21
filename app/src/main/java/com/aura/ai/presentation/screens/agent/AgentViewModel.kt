package com.aura.ai.presentation.screens.agent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
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
import com.google.ai.client.generativeai.type.asimage
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
// SECTION 1: PUBLIC DATA CLASSES
// ═══════════════════════════════════════════

data class ChatMessage(val text: String, val isUser: Boolean, val isStreaming: Boolean = false)

data class ModelInfo(
    val name: String, val displayName: String, val strength: String,
    val dailyRequests: Int, val dailyLimit: Int, val isInCooldown: Boolean, val isSelected: Boolean
)

data class BuildLoopState(
    val attemptNumber: Int = 0, val maxAttempts: Int = 20,
    val buildStatus: BuildStatus = BuildStatus.IDLE, val workflowRunId: Long? = null,
    val errorSummary: String = "", val lastFixDescription: String = "",
    val buildUrl: String = "", val totalFixesApplied: Int = 0,
    val artifactUrl: String? = null
)

enum class BuildStatus {
    IDLE, BUILDING, WAITING_FOR_BUILD, BUILD_SUCCESS, ANALYZING_ERROR,
    FIXING, RETRYING, FAILED, DOWNLOADING_ARTIFACT, VALIDATING
}

data class AgentUiState(
    val messages: List<ChatMessage> = listOf(ChatMessage("AURA AI - NEURAL CORE ACTIVE", false)),
    val input: String = "", val loading: Boolean = false, val isExecuting: Boolean = false,
    val currentTask: String = "", val executionMode: ExecutionMode = ExecutionMode.IDLE,
    val activeModel: String = "gemini-3.1-flash-lite", val showDrawer: Boolean = false,
    val showModelDashboard: Boolean = false, val manualModelSelected: Boolean = false,
    val currentSessionId: String? = null, val buildLoop: BuildLoopState? = null,
    val isGeneratingApp: Boolean = false, val generationProgress: String = "",
    val codespaceMode: Boolean = false, val activeCodespaceId: String? = null,
    val codespaceStatus: String = "", val pendingBatchFiles: Map<String, String> = emptyMap(),
    val isOnline: Boolean = true, val totalApiCalls: Int = 0,
    val attachedFileUri: Uri? = null, val attachedFileName: String = ""
)

enum class ExecutionMode {
    IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION,
    FILE_OPERATION, REPO_ANALYSIS, FEATURE_TRANSFER, CODESPACE_GENERATION,
    BATCH_FILE_PUSH, IMAGE_ANALYSIS, FILE_UPLOAD, STREAMING_CHAT
}

// ═══════════════════════════════════════════
// SECTION 2: INTERNAL DATA CLASSES
// ═══════════════════════════════════════════

private data class AppArchitecture(
    val files: List<String>, val techStack: String,
    val dependencies: List<String>, val structure: String,
    val componentTree: Map<String, List<String>> = emptyMap(),
    val packageStructure: Map<String, List<String>> = emptyMap()
)

private data class FixPlan(val summary: String, val fileFixes: List<Pair<String, String>>)
private data class RepoInfo(val description: String, val stars: Int, val forks: Int, val language: String)
private data class RepoAnalysis(val architecture: String, val keyFeatures: List<String>, val fileStructure: Map<String, String>, val dependencies: List<String>, val coreLogic: Map<String, String>)
private data class FeatureTransferRequest(val sourceOwner: String, val sourceRepo: String, val targetFeatures: List<String>, val additionalContext: String)
private data class PreValidationResult(val isValid: Boolean, val issues: List<String>)
private data class ProjectContext(val packageName: String, val classes: MutableMap<String, String> = mutableMapOf(), val composables: MutableSet<String> = mutableSetOf(), val imports: MutableMap<String, List<String>> = mutableMapOf())
private data class QueuedCommand(val id: String, val command: String, val timestamp: Long)

private sealed class WorkflowResult {
    data object Success : WorkflowResult()
    data class Failure(val error: String, val logs: String) : WorkflowResult()
    data object Timeout : WorkflowResult()
}

// ═══════════════════════════════════════════
// SECTION 3: BUILD TEMPLATES (Hardcoded - Never AI-Generated)
// ═══════════════════════════════════════════

private val ROOT_BUILD_TEMPLATE = """
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
""".trimIndent()

private val SETTINGS_TEMPLATE = """
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "{{NAME}}"
include(":app")
""".trimIndent()

private val GRADLE_PROPERTIES_TEMPLATE = """
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""".trimIndent()

private val WRAPPER_PROPERTIES_TEMPLATE = """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""".trimIndent()

// ═══════════════════════════════════════════
// SECTION 4: VIEWMODEL CLASS
// ═══════════════════════════════════════════

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val preferences: AuraPreferences
) : ViewModel() {

    // ═══════════════════════════════════════════
    // SECTION 4.1: STATE MANAGEMENT
    // ═══════════════════════════════════════════

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()
    private var taskJob: Job? = null
    private var isPaused = false
    private var activeRepo = ""
    private var activeOwner = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private var pendingGenerationFiles: Map<String, String> = emptyMap()
    private var projectContext = ProjectContext(packageName = "")
    private val commandQueue = mutableListOf<QueuedCommand>()

    // ═══════════════════════════════════════════
    // SECTION 4.2: SESSION MANAGEMENT
    // ═══════════════════════════════════════════

    private val sessionDb by lazy { SessionDatabase.getInstance(com.aura.ai.AuraApplication.instance) }
    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    private val _modelUsage = MutableStateFlow<List<ModelUsageEntity>>(emptyList())
    private var sessionLoadingJob: Job? = null

    init {
        loadSessions()
        loadModelUsage()
        loadPreferredModel()
        viewModelScope.launch {
            resetDailyCountersIfNeeded()
            val lastSessionId = preferences.getLastSessionId()
            if (lastSessionId != null) {
                switchSession(lastSessionId)
            }
        }
        _state.value = _state.value.copy(isOnline = isNetworkAvailable())
        _state.value = _state.value.copy(totalApiCalls = preferences.getTotalApiCalls())
    }

    // ═══════════════════════════════════════════
    // SECTION 4.3: MODEL REGISTRY & SELECTION
    // ═══════════════════════════════════════════

    private data class ModelSpec(val rpd: Int, val rpm: Int, val description: String)

    private val modelRegistry = linkedMapOf(
        "gemini-3.1-flash-lite" to ModelSpec(1000, 15, "Fastest - Simple tasks & coding"),
        "gemini-2.5-flash-lite" to ModelSpec(1000, 15, "Reliable - High volume fallback"),
        "gemini-3-flash-preview" to ModelSpec(250, 10, "Quality - Complex reasoning"),
        "gemini-2.5-flash" to ModelSpec(250, 10, "Balanced - Debugging & code"),
        "gemini-3.1-flash-live" to ModelSpec(100, 5, "Real-time - Streaming chat"),
        "gemini-2.0-flash-lite" to ModelSpec(1500, 15, "Legacy - Highest volume"),
        "gemini-2.0-flash" to ModelSpec(500, 15, "Legacy - Deprecated soon")
    )

    private val taskModelPriority = mapOf(
        "chat" to listOf("gemini-3.1-flash-live", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite"),
        "code_gen" to listOf("gemini-3.1-flash-lite", "gemini-2.5-flash", "gemini-3-flash-preview"),
        "debug" to listOf("gemini-2.5-flash", "gemini-3-flash-preview", "gemini-3.1-flash-lite"),
        "complex" to listOf("gemini-3-flash-preview", "gemini-2.5-flash", "gemini-3.1-flash-lite"),
        "high_volume" to listOf("gemini-2.0-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite"),
        "general" to listOf("gemini-3.1-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash")
    )

    private val modelCooldowns = mutableMapOf<String, Long>()
    private val modelDailyUsage = mutableMapOf<String, Int>()
    private var consecutiveFailures = 0

    private fun selectOptimalModel(taskType: String): String {
        if (_state.value.manualModelSelected) return _state.value.activeModel
        if (consecutiveFailures >= 3) return taskModelPriority["high_volume"]!!.first { !isModelInCooldown(it) }
        val candidates = taskModelPriority[taskType] ?: taskModelPriority["general"]!!
        for (model in candidates) { if (!isModelInCooldown(model)) return model }
        return "gemini-2.0-flash-lite"
    }

    private fun isModelInCooldown(model: String) = modelCooldowns[model]?.let { System.currentTimeMillis() < it } ?: false
    private fun recordModelUsage(model: String) {
        modelDailyUsage[model] = (modelDailyUsage[model] ?: 0) + 1
        val newTotal = preferences.getTotalApiCalls() + 1
        preferences.setTotalApiCalls(newTotal)
        _state.value = _state.value.copy(totalApiCalls = newTotal)
        viewModelScope.launch {
            val existing = sessionDb.modelUsageDao().getModelUsage(model)
            sessionDb.modelUsageDao().insertOrUpdateModelUsage(ModelUsageEntity(model, (existing?.dailyRequests ?: 0) + 1, modelRegistry[model]?.rpd ?: 1500, "", 0, modelRegistry[model]?.description ?: ""))
        }
    }
    private fun applyModelCooldown(model: String) { consecutiveFailures++; modelCooldowns[model] = System.currentTimeMillis() + 60000 }
    private fun resetFailureState() { consecutiveFailures = 0 }

    // ═══════════════════════════════════════════
    // SECTION 4.4: NETWORK & QUEUE
    // ═══════════════════════════════════════════

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = com.aura.ai.AuraApplication.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetworkInfo?.isConnected == true
        } catch (e: Exception) { true }
    }

    private fun processQueue() {
        if (commandQueue.isNotEmpty() && isNetworkAvailable()) {
            val next = commandQueue.removeAt(0)
            _state.value = _state.value.copy(input = next.command)
            send()
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.5: PUBLIC INTERFACE
    // ═══════════════════════════════════════════

    fun updateInput(text: String) { _state.value = _state.value.copy(input = text) }
    fun toggleDrawer() { _state.value = _state.value.copy(showDrawer = !_state.value.showDrawer) }
    fun toggleModelDashboard() { _state.value = _state.value.copy(showModelDashboard = !_state.value.showModelDashboard) }

    fun attachFile(uri: Uri, fileName: String) {
        _state.value = _state.value.copy(attachedFileUri = uri, attachedFileName = fileName)
    }

    fun clearAttachment() {
        _state.value = _state.value.copy(attachedFileUri = null, attachedFileName = "")
    }

    fun selectModel(modelName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(activeModel = modelName, manualModelSelected = true, showModelDashboard = false)
            _currentSessionId.value?.let { sessionDb.sessionDao().updateSelectedModel(it, modelName) }
            preferences.setPreferredModel(modelName)
        }
    }

    fun getModelInfoList(): List<ModelInfo> {
        return modelRegistry.map { (name, spec) ->
            val usage = _modelUsage.value.find { it.modelName == name }
            ModelInfo(name, name.replace("gemini-", "").replace("-", " ").uppercase(), spec.description, usage?.dailyRequests ?: 0, spec.rpd, isModelInCooldown(name), name == _state.value.activeModel)
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val session = SessionEntity(id = UUID.randomUUID().toString(), title = "New Session", selectedModel = _state.value.activeModel)
            sessionDb.sessionDao().insertSession(session)
            switchSession(session.id)
        }
    }

    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            sessionLoadingJob?.cancel()
            sessionLoadingJob = viewModelScope.launch {
                val session = sessionDb.sessionDao().getSession(sessionId) ?: return@launch
                _currentSessionId.value = sessionId
                preferences.setLastSessionId(sessionId)
                val messages = sessionDb.messageDao().getMessagesForSessionOnce(sessionId)
                val chatMessages = messages.map { ChatMessage(text = it.text, isUser = it.isUser) }
                _state.value = _state.value.copy(messages = if (chatMessages.isEmpty()) _state.value.messages else chatMessages, currentSessionId = sessionId, manualModelSelected = true, activeModel = session.selectedModel, buildLoop = null, isGeneratingApp = false)
                sessionDb.sessionDao().updateSession(sessionId, System.currentTimeMillis(), session.title)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionDb.sessionDao().deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
                val remaining = _sessions.value.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) switchSession(remaining.first().id) else createNewSession()
            }
        }
    }

    fun send() {
        val msg = _state.value.input.trim()
        if (msg.isBlank()) return
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(msg, true), input = "", loading = true)
        if (handleControlCommand(msg)) return

        // Check offline
        if (!isNetworkAvailable()) {
            commandQueue.add(QueuedCommand(UUID.randomUUID().toString(), msg, System.currentTimeMillis()))
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage("📶 Offline - Queued. Will execute when connected.", false),
                loading = false
            )
            return
        }

        // Check file attachment
        val attachedUri = _state.value.attachedFileUri
        if (attachedUri != null) {
            taskJob = viewModelScope.launch {
                _state.value = _state.value.copy(isExecuting = true, currentTask = msg, executionMode = ExecutionMode.FILE_UPLOAD)
                saveMessage(msg, isUser = true)
                val result = executeFileUploadCommand(msg, attachedUri)
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatMessage(result, false),
                    loading = false, isExecuting = false, currentTask = "",
                    executionMode = ExecutionMode.IDLE, attachedFileUri = null, attachedFileName = ""
                )
                saveMessage(result, isUser = false, modelUsed = _state.value.activeModel)
            }
            return
        }

        taskJob = viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, currentTask = msg)
            saveMessage(msg, isUser = true)
            val result = executeCommandPipeline(msg)
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage(result, false),
                loading = false, isExecuting = false, currentTask = "", executionMode = ExecutionMode.IDLE
            )
            saveMessage(result, isUser = false, modelUsed = _state.value.activeModel)
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.6: COMMAND PIPELINE
    // ═══════════════════════════════════════════

    private suspend fun executeCommandPipeline(input: String): String {
        return executeCodespaceCommand(input) ?: executePhoneCommand(input) ?: executeGitHubCommand(input) ?: executeFileCommand(input) ?: executeSystemCommand(input) ?: executeStreamingChat(input)
    }

    private fun handleControlCommand(input: String): Boolean {
        return when (input.lowercase().trim()) {
            "pause" -> { if (_state.value.isExecuting) { isPaused = true; taskJob?.cancel(); _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⏸️ Paused.", false), loading = false) } else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("ℹ️ No active task.", false), loading = false); true }
            "stop", "cancel" -> { taskJob?.cancel(); isPaused = false; _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⏹️ Stopped.", false), loading = false, isExecuting = false, executionMode = ExecutionMode.IDLE, isGeneratingApp = false, buildLoop = null); true }
            "queue" -> { processQueue(); _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("📋 Processed queue. ${commandQueue.size} remaining.", false)); true }
            else -> false
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.7: CODESPACES COMMANDS
    // ═══════════════════════════════════════════

    private suspend fun executeCodespaceCommand(input: String): String? {
        val token = preferences.getGitHubToken() ?: return null
        val lower = input.lowercase().trim()
        val manager = CodespacesManager(token)
        if (lower.startsWith("codespace create")) { val parts = lower.removePrefix("codespace create").trim().split("/"); val owner = if (parts.size == 2) parts[0] else activeOwner; val repo = if (parts.size == 2) parts[1] else activeRepo; if (owner.isBlank() || repo.isBlank()) return "❌ Specify owner/repo."; _state.value = _state.value.copy(codespaceStatus = "Creating..."); val cs = manager.createCodespace(owner, repo) ?: return "❌ Failed."; _state.value = _state.value.copy(activeCodespaceId = cs.id, codespaceMode = true); return "🖥️ Codespace: ${cs.name}\n🔗 ${cs.webUrl}" }
        if (lower == "codespace list") { val list = manager.listCodespaces(); return if (list.isEmpty()) "📁 None." else "🖥️:\n" + list.joinToString("\n") { "• ${it.name}" } }
        return null
    }

    // ═══════════════════════════════════════════
    // SECTION 4.8: PHONE CONTROL
    // ═══════════════════════════════════════════

    private suspend fun executePhoneCommand(input: String): String? {
        val lower = input.lowercase().trim()
        val service = AuraAccessibilityService.instance ?: return null
        _state.value = _state.value.copy(executionMode = ExecutionMode.PHONE_CONTROL)
        if (lower.startsWith("open ")) { val appName = lower.removePrefix("open ").trim(); val pkg = resolveAppPackage(appName) ?: return "❌ Unknown app."; return try { val intent = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(pkg); if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); com.aura.ai.AuraApplication.instance.startActivity(intent); "✅ Opened $appName" } else "❌ Failed" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower == "home") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "🏠 Home" }
        if (lower == "back") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "⬅️ Back" }
        if (lower == "screenshot") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "📸 Screenshot" }
        return null
    }

    // ═══════════════════════════════════════════
    // SECTION 4.9: GITHUB COMMANDS (if/return - NO when BLOCK)
    // ═══════════════════════════════════════════

    private suspend fun executeGitHubCommand(input: String): String? {
        val token = preferences.getGitHubToken() ?: return null
        val apiKey = preferences.getApiKey(); val key = apiKey ?: ""
        val lower = input.lowercase().trim()
        _state.value = _state.value.copy(executionMode = ExecutionMode.GITHUB_OPERATION)

        if (lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) {
            if (!lower.contains("repo")) {
                val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
                val appName = appDesc.split(" ").firstOrNull()?.sanitize()?.take(50) ?: "MyApp"
                val description = appDesc.split(" ").drop(1).joinToString(" ").trim().ifBlank { "A simple application" }
                _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP)
                return createFullApplication(token, key, appName, description)
            }
        }
        if (lower.contains("create") && lower.contains("repo")) { val name = input.replace(Regex("(?i)(create|a|repo|repository|github)"), "").trim().sanitize().take(50); return if (name.isBlank()) "❌ Specify name." else githubApiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$name","private":false,"auto_init":true}""") }
        if (lower.contains("list") && lower.contains("repo")) return githubApiCall("GET", "https://api.github.com/user/repos?per_page=10&sort=updated", token, null)
        if (lower.startsWith("compile ")) { val repo = lower.removePrefix("compile ").trim(); val parts = repo.split("/"); return if (parts.size != 2) "❌ Format: compile owner/repo" else triggerWorkflowDispatch(token, parts[0], parts[1]) }
        if (lower.startsWith("browse repo ")) { val repo = lower.removePrefix("browse repo ").trim(); val parts = repo.split("/"); return if (parts.size != 2) "❌ Format: browse owner/repo" else browseRepositoryContents(token, parts[0], parts[1]) }
        if (lower.startsWith("read repo file ")) { val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" "); if (parts.size < 2) return "❌ Format: read repo file owner/repo path"; val repoParts = parts[0].split("/"); if (repoParts.size != 2) return "❌ Format."; return readRepoFileContents(token, repoParts[0], repoParts[1], parts.drop(1).joinToString(" ")) }
        if (lower.startsWith("fix ") || lower.startsWith("edit ")) { val remaining = input.replace(Regex("(?i)(fix|edit|update) "), ""); val filePath = remaining.substringBefore(":").trim(); val instruction = remaining.substringAfter(":").trim(); if (filePath.isBlank() || instruction.isBlank()) return "❌ Usage: fix path/file.kt: instruction"; if (activeRepo.isBlank()) return "❌ No active repo."; return repairFileInRepo(token, key, activeOwner, activeRepo, filePath, instruction) }
        if (lower.startsWith("add file ")) { val remaining = input.replace(Regex("(?i)(add|create) file "), ""); val filePath = remaining.substringBefore(":").trim(); val description = remaining.substringAfter(":").trim(); if (filePath.isBlank()) return "❌ Usage: add file path/file.kt: description"; if (activeRepo.isBlank()) return "❌ No active repo."; return createFileInRepo(token, key, activeOwner, activeRepo, filePath, description) }
        if (lower.startsWith("set repo ")) { val repo = lower.removePrefix("set repo ").trim(); val parts = repo.split("/"); if (parts.size != 2) return "❌ Format: set repo owner/repo"; activeOwner = parts[0]; activeRepo = parts[1]; return "✅ Active: $activeOwner/$activeRepo" }
        if (lower.startsWith("analyze repo ")) { val repo = input.replace(Regex("(?i)(analyze|study) repo "), "").trim(); val parts = repo.split("/"); if (parts.size != 2) return "❌ Format."; return analyzePublicRepo(token, key, parts[0], parts[1]) }
        if (lower.startsWith("transfer ")) { val instruction = input.replace(Regex("(?i)(transfer|port|add feature) "), ""); if (activeRepo.isBlank()) return "❌ No active repo."; return transferFeaturesFromRepo(token, key, instruction) }
        if (lower.startsWith("merge repo ")) { val sourceRepo = input.replace(Regex("(?i)(merge repo|clone features from) "), "").trim(); val parts = sourceRepo.split("/"); if (parts.size != 2) return "❌ Format."; if (activeRepo.isBlank()) return "❌ No active repo."; return mergeRepositoryFeatures(token, key, parts[0], parts[1]) }
        return null
    }

    // ═══════════════════════════════════════════
    // SECTION 4.10: FILE COMMANDS
    // ═══════════════════════════════════════════

    private fun executeFileCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("list files")) { val path = input.replace(Regex("(?i)list files"), "").trim().ifBlank { Environment.getExternalStorageDirectory().absolutePath }; return try { val files = File(path).listFiles()?.take(40); if (files.isNullOrEmpty()) "📁 Empty." else "📁:\n" + files.joinToString("\n") { it.name } } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower.startsWith("read file")) { val path = input.replace(Regex("(?i)read file"), "").trim(); return try { val content = File(path).readText(); if (content.length > 2000) "📄 ${content.take(2000)}..." else "📄 $content" } catch (e: Exception) { "❌ ${e.message}" } }
        return null
    }

    // ═══════════════════════════════════════════
    // SECTION 4.11: SYSTEM COMMANDS
    // ═══════════════════════════════════════════

    private fun executeSystemCommand(input: String): String? {
        if (input.lowercase() == "device info") return "📱 ${Build.MODEL}\n🤖 ${Build.VERSION.RELEASE}\n💾 ${getRamUsage()}\n📀 ${getStorageInfo()}\n🔋 ${getBatteryLevel()}"
        if (input.lowercase() == "time") return "🕐 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
        if (input.lowercase() == "usage") return "📊 API Calls: ${_state.value.totalApiCalls}\n📶 Online: ${_state.value.isOnline}"
        return null
    }

    // ═══════════════════════════════════════════
    // SECTION 4.12: STREAMING CHAT
    // ═══════════════════════════════════════════

    private suspend fun executeStreamingChat(input: String): String {
        val key = preferences.getApiKey() ?: return "❌ No API key."
        _state.value = _state.value.copy(executionMode = ExecutionMode.STREAMING_CHAT)
        val modelName = if (_state.value.manualModelSelected) _state.value.activeModel else selectOptimalModel("general")
        _state.value = _state.value.copy(activeModel = modelName)
        val model = GenerativeModel(modelName, key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 })

        var fullResponse = ""
        val streamingMsg = ChatMessage("", false, isStreaming = true)
        _state.value = _state.value.copy(messages = _state.value.messages + streamingMsg)

        return try {
            model.generateContentStream(content { text(input) }).collect { chunk ->
                val text = chunk.text ?: ""
                fullResponse += text
                val updatedMessages = _state.value.messages.toMutableList()
                updatedMessages[updatedMessages.lastIndex] = ChatMessage(fullResponse, false, isStreaming = true)
                _state.value = _state.value.copy(messages = updatedMessages)
            }
            recordModelUsage(modelName); resetFailureState()
            val finalMessages = _state.value.messages.toMutableList()
            finalMessages[finalMessages.lastIndex] = ChatMessage(fullResponse, false, isStreaming = false)
            _state.value = _state.value.copy(messages = finalMessages)
            fullResponse
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("429")) { applyModelCooldown(modelName); "⚠️ Rate limited." } else "❌ $errorMsg"
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.13: FILE UPLOAD & IMAGE ANALYSIS
    // ═══════════════════════════════════════════

    private suspend fun executeFileUploadCommand(prompt: String, uri: Uri): String {
        val context = com.aura.ai.AuraApplication.instance
        val mimeType = context.contentResolver.getType(uri) ?: return "❌ Unknown file type"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return "❌ Cannot read file"
        val bytes = inputStream.readBytes(); inputStream.close()
        val key = preferences.getApiKey() ?: return "❌ No API key."

        if (mimeType.startsWith("image/")) {
    _state.value = _state.value.copy(executionMode = ExecutionMode.IMAGE_ANALYSIS)
    val model = GenerativeModel("gemini-2.5-flash", key)
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return try {
        val response = model.generateContent(content { 
            image(bitmap)
            text(prompt) 
        }).text ?: "No response"
        recordModelUsage("gemini-2.5-flash"); response
    } catch (e: Exception) { "❌ ${e.message}" }
        }

        if (mimeType.startsWith("audio/")) {
            val model = GenerativeModel("gemini-2.5-flash", key)
            return try {
                val response = model.generateContent(content { text("Audio file uploaded. $prompt") }).text ?: "No response"
                recordModelUsage("gemini-2.5-flash"); response
            } catch (e: Exception) { "❌ ${e.message}" }
        }

        val textContent = String(bytes)
        return executeStreamingChat("$prompt\n\nFile content:\n${textContent.take(10000)}")
    }

    // ═══════════════════════════════════════════
    // SECTION 4.14: PARALLEL MODEL EXECUTION
    // ═══════════════════════════════════════════

    private suspend fun executeWithMultipleModels(input: String): String {
        val key = preferences.getApiKey() ?: return "❌ No API key."
        val models = listOf("gemini-2.5-flash", "gemini-3.1-flash-lite", "gemini-2.0-flash-lite")
        val results = mutableListOf<String>()

        coroutineScope {
            models.map { modelName ->
                async {
                    try {
                        val model = GenerativeModel(modelName, key, generationConfig { maxOutputTokens = 60000 })
                        model.generateContent(content { text(input) }).text
                    } catch (e: Exception) { null }
                }
            }.awaitAll().forEach { response -> if (response != null) results.add(response) }
        }

        return results.maxByOrNull { it.length } ?: "All models failed"
    }

    // ═══════════════════════════════════════════
// SECTION 4.15: AUTONOMOUS APP GENERATION
// ═══════════════════════════════════════════

private suspend fun createFullApplication(token: String, key: String, appName: String, description: String): String {
    _state.value = _state.value.copy(isGeneratingApp = true, generationProgress = "🚀 Starting")
    pendingGenerationFiles = emptyMap()
    projectContext = ProjectContext(packageName = "com.example.${appName.sanitize()}")
    val allFiles = mutableMapOf<String, String>()

    try {
        addProgressMessage("🧠 Phase 1/6: Deep architecture analysis...")
        val architecture = planArchitectureDeep(key, appName, description)
        if (architecture.files.isEmpty()) { _state.value = _state.value.copy(isGeneratingApp = false); return "❌ Planning failed." }
        addProgressMessage("📋 ${architecture.files.size} files | ${architecture.techStack}")

        addProgressMessage("📁 Phase 2/6: Creating repository...")
        val createResult = githubApiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$appName","private":false,"auto_init":false}""")
        if (createResult.startsWith("❌")) { _state.value = _state.value.copy(isGeneratingApp = false); return "❌ $createResult" }
        val userResult = githubApiCall("GET", "https://api.github.com/user", token, null)
        val owner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(userResult)?.groupValues?.get(1) ?: return "❌ No username."
        activeOwner = owner; activeRepo = appName

        addProgressMessage("⚙️ Phase 3/6: Generating build system from templates...")
        val buildFiles = generateBuildSystem(appName, architecture)
        allFiles.putAll(buildFiles)

        addProgressMessage("📝 Phase 4/6: Generating source files...")
        val sourceFiles = architecture.files.filter { it.startsWith("app/src/") }
        val generated = generateAllFilesOneShot(key, appName, description, architecture, sourceFiles, allFiles)
        allFiles.putAll(generated)
        addProgressMessage("✅ ${allFiles.size} files generated")

        addProgressMessage("🔍 Phase 5/6: Pre-validation...")
        val validation = preValidateProject(allFiles)
        if (!validation.isValid) {
            addProgressMessage("⚠️ ${validation.issues.size} issues found. Auto-fixing...")
            val fixed = autoFixValidationIssues(key, allFiles, validation.issues)
            allFiles.clear()
            allFiles.putAll(fixed)
            addProgressMessage("✅ Fixed. Re-validating...")
            val revalidation = preValidateProject(allFiles)
            if (!revalidation.isValid) {
                addProgressMessage("⚠️ ${revalidation.issues.size} issues remain. Will fix during build.")
            }
        }

        addProgressMessage("📤 Phase 6/6: Pushing & building...")
        val pushed = batchPushViaGitData(token, owner, appName, allFiles)
        if (pushed) {
            addWorkflowFile(token, owner, appName, appName)
            addProgressMessage("✅ Pushed ${allFiles.size} files in one commit")
            addProgressMessage("🔄 Starting build verification...")
            val buildResult = executeBuildLoop(token, key, owner, appName)
            _state.value = _state.value.copy(isGeneratingApp = false, executionMode = ExecutionMode.IDLE)
            return buildResult
        } else {
            addProgressMessage("⚠️ Batch push failed. Using per-file push...")
            var pushedCount = 0
            for ((path, content) in allFiles) {
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                if (!githubApiCall("PUT", "https://api.github.com/repos/$owner/$appName/contents/$path", token, """{"message":"Add $path","content":"$encoded"}""").startsWith("❌")) pushedCount++
            }
            addWorkflowFile(token, owner, appName, appName)
            addProgressMessage("✅ Pushed $pushedCount/${allFiles.size} files")
            val buildResult = executeBuildLoop(token, key, owner, appName)
            _state.value = _state.value.copy(isGeneratingApp = false, executionMode = ExecutionMode.IDLE)
            return buildResult
        }
    } catch (e: Exception) {
        _state.value = _state.value.copy(isGeneratingApp = false)
        return "❌ ${e.message}"
    }
}
    // ═══════════════════════════════════════════
    // SECTION 4.15.1: DEEP ARCHITECTURE PLANNING
    // ═══════════════════════════════════════════

    private suspend fun planArchitectureDeep(key: String, appName: String, description: String): AppArchitecture {
        val model = GenerativeModel(selectOptimalModel("code_gen"), key, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 })
        return try {
            val response = model.generateContent(content { text("Deep analyze: $appName - $description. Return JSON: {\"files\":[],\"techStack\":\"\",\"dependencies\":[],\"structure\":\"\",\"componentTree\":{},\"packageStructure\":{}}") }).text
            val text = response ?: return AppArchitecture(emptyList(), "", emptyList(), "")
            recordModelUsage(selectOptimalModel("code_gen"))
            val json = text.substringAfter("{").substringBeforeLast("}"); val obj = JSONObject("{${json}}")
            AppArchitecture((0 until obj.getJSONArray("files").length()).map { obj.getJSONArray("files").getString(it) }, obj.optString("techStack"), (0 until obj.getJSONArray("dependencies").length()).map { obj.getJSONArray("dependencies").getString(it) }, obj.optString("structure"))
        } catch (e: Exception) { AppArchitecture(emptyList(), "Standard", emptyList(), "Basic") }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.15.2: BUILD SYSTEM TEMPLATES
    // ═══════════════════════════════════════════

    private fun generateBuildSystem(appName: String, architecture: AppArchitecture): Map<String, String> {
        val deps = architecture.dependencies.joinToString("\n    implementation(\"") { it }
        val sanitized = appName.sanitize()
        return mapOf(
            "build.gradle.kts" to ROOT_BUILD_TEMPLATE,
            "app/build.gradle.kts" to """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.example.$sanitized"
    compileSdk = 34
    defaultConfig { applicationId = "com.example.$sanitized"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    $deps
}
            """.trimIndent(),
            "settings.gradle.kts" to SETTINGS_TEMPLATE.replace("{{NAME}}", appName),
            "gradle.properties" to GRADLE_PROPERTIES_TEMPLATE,
            "gradle/wrapper/gradle-wrapper.properties" to WRAPPER_PROPERTIES_TEMPLATE
        )
    }

    // ═══════════════════════════════════════════
    // SECTION 4.15.3: ONE-SHOT FILE GENERATION
    // ═══════════════════════════════════════════

    private suspend fun generateAllFilesOneShot(key: String, appName: String, description: String, architecture: AppArchitecture, fileList: List<String>, existingFiles: Map<String, String>): Map<String, String> {
        val model = GenerativeModel(selectOptimalModel("code_gen"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
        val contextSummary = existingFiles.keys.take(20).joinToString("\n") { "  ✅ $it" }
        val sanitized = appName.sanitize()

        return try {
            val response = model.generateContent(content { text("Generate ALL files.\nApp: $appName | $description\nStack: ${architecture.techStack}\nDeps: ${architecture.dependencies.joinToString()}\nPackage: com.example.$sanitized\nExisting: $contextSummary\nFiles (${fileList.size}): ${fileList.joinToString("\n")}\n\nFormat:\n===FILE:path===\ncontent\n===END===\n\nRULES: Complete code. No placeholders. All imports. Full implementations.") }).text
            val text = response ?: return emptyMap()
            recordModelUsage(selectOptimalModel("code_gen"))
            parseFileResponse(text)
        } catch (e: Exception) { emptyMap() }
    }

    private fun parseFileResponse(response: String): Map<String, String> {
        val files = mutableMapOf<String, String>()
        Regex("===FILE:(.+?)===\\s*([\\s\\S]*?)\\s*===END===").findAll(response).forEach { match ->
            val path = match.groupValues[1].trim(); val content = match.groupValues[2].trim()
            if (path.isNotEmpty() && content.isNotEmpty()) files[path] = content
        }
        return files
    }

    // ═══════════════════════════════════════════
    // SECTION 4.15.4: PRE-VALIDATION
    // ═══════════════════════════════════════════

    private fun preValidateProject(files: Map<String, String>): PreValidationResult {
        val issues = mutableListOf<String>()
        val manifest = files.entries.find { it.key.endsWith("AndroidManifest.xml") }?.value
        if (manifest != null) {
            if (!manifest.contains("<application")) issues.add("Manifest missing <application>")
            if (!manifest.contains("MainActivity")) issues.add("Manifest missing MainActivity")
        }
        val buildFile = files["app/build.gradle.kts"]
        if (buildFile != null && !buildFile.contains("compose")) issues.add("Build may be missing Compose")
        val packages = files.values.mapNotNull { Regex("package\\s+([\\w.]+)").find(it)?.groupValues?.get(1) }.toSet()
        if (packages.size > 1) issues.add("Inconsistent packages: $packages")
        return PreValidationResult(issues.isEmpty(), issues)
    }

    private suspend fun autoFixValidationIssues(key: String, files: Map<String, String>, issues: List<String>): Map<String, String> {
        val model = GenerativeModel(selectOptimalModel("debug"), key, generationConfig { temperature = 0.05f; maxOutputTokens = 60000 })
        return try {
            val response = model.generateContent(content { text("Fix these issues:\n${issues.joinToString("\n")}\n\nFiles:\n${files.entries.take(10).joinToString("\n") { "FILE:${it.key}\n${it.value.take(2000)}" }}") }).text
            val text = response ?: return files
            val fixed = parseFileResponse(text)
            if (fixed.isEmpty()) files else files.toMutableMap().apply { putAll(fixed) }
        } catch (e: Exception) { files }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.16: BUILD LOOP WITH ONE-SHOT ERROR SOLVING
    // ═══════════════════════════════════════════

    private suspend fun executeBuildLoop(token: String, key: String, owner: String, repo: String): String {
        var attempt = 0; val maxAttempts = 20; var totalFixes = 0
        _state.value = _state.value.copy(buildLoop = BuildLoopState(maxAttempts = maxAttempts))
        addProgressMessage("🔨 Triggering build...")
        var runId: Long? = triggerWorkflowAndGetRunId(token, owner, repo)
        if (runId == null) return "⚠️ Files pushed. Use 'compile repo $owner/$repo'."

        while (attempt < maxAttempts) {
            attempt++
            _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(attemptNumber = attempt, buildStatus = BuildStatus.WAITING_FOR_BUILD, workflowRunId = runId))
            addProgressMessage("🔨 Build $attempt/$maxAttempts...")
            val result = monitorWorkflowWithBackoff(token, owner, repo, runId!!)

            when (result) {
                is WorkflowResult.Success -> {
                    val artifactUrl = getArtifactDownloadUrl(token, owner, repo, runId)
                    _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(buildStatus = BuildStatus.BUILD_SUCCESS, artifactUrl = artifactUrl))
                    return "🎉 BUILD SUCCESS!\n📱 $repo\n🔄 Attempts: $attempt\n🔧 Fixes: $totalFixes\n${if (artifactUrl != null) "📥 APK: $artifactUrl" else "📥 APK in Actions"}"
                }
                is WorkflowResult.Failure -> {
                    addProgressMessage("❌ Failed. One-shot analysis...")
                    val fixed = solveBuildErrorsOneShot(token, key, owner, repo, result.logs)
                    if (fixed) { totalFixes++; addProgressMessage("✅ Fixes applied.") }
                    runId = retriggerBuild(token, owner, repo) ?: break
                }
                is WorkflowResult.Timeout -> { runId = retriggerBuild(token, owner, repo) ?: break }
            }
        }
        return "⚠️ Loop exhausted. 📱 $repo | 🔧 Fixes: $totalFixes"
    }

    // ═══════════════════════════════════════════
    // SECTION 4.16.1: WORKFLOW MONITORING (Exponential Backoff)
    // ═══════════════════════════════════════════

    private suspend fun monitorWorkflowWithBackoff(token: String, owner: String, repo: String, runId: Long): WorkflowResult {
        var delayMs = 5000L
        repeat(60) {
            delay(delayMs); delayMs = minOf(delayMs * 2, 30000L)
            val status = withContext(Dispatchers.IO) {
                try {
                    val body = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId").header("Authorization", "Bearer $token").build()).execute().body?.string()
                    Pair(Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(body ?: "")?.groupValues?.get(1), Regex("\"conclusion\"\\s*:\\s*\"([^\"]+)\"").find(body ?: "")?.groupValues?.get(1))
                } catch (e: Exception) { null }
            }
            if (status?.first == "completed") return if (status.second == "success") WorkflowResult.Success else WorkflowResult.Failure(extractKeyErrors(fetchWorkflowLogs(token, owner, repo, runId)), fetchWorkflowLogs(token, owner, repo, runId))
        }
        return WorkflowResult.Timeout
    }

    // ═══════════════════════════════════════════
    // SECTION 4.16.2: ONE-SHOT ERROR SOLVING
    // ═══════════════════════════════════════════

    private suspend fun solveBuildErrorsOneShot(token: String, key: String, owner: String, repo: String, errorLogs: String): Boolean {
        addProgressMessage("🧠 Reading entire project for context...")
        val fileTree = getFileTree(token, owner, repo)
        val allFiles = mutableMapOf<String, String>()
        for (path in fileTree.take(100)) { try { readFileContent(token, owner, repo, path)?.let { allFiles[path] = it } } catch (e: Exception) {} }

        val model = GenerativeModel(selectOptimalModel("debug"), key, generationConfig { temperature = 0.05f; maxOutputTokens = 60000 })
        return try {
            val filesContext = allFiles.entries.joinToString("\n") { "===FILE:${it.key}===\n${it.value.take(3000)}\n===END===" }
            val response = model.generateContent(content { text("Fix ALL errors.\nERRORS:\n$errorLogs\n\nPROJECT:\n$filesContext\n\nReturn fixed files: ===FILE:path=== content ===END===") }).text
            val text = response ?: return false
            recordModelUsage(selectOptimalModel("debug"))
            val fixedFiles = parseFileResponse(text)
            if (fixedFiles.isEmpty()) return false
            var applied = 0
            for ((path, content) in fixedFiles) {
                val sha = getFileSha(token, owner, repo, path)
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val result = if (sha != null) githubApiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/$path", token, """{"message":"Fix","content":"$encoded","sha":"$sha"}""") else githubApiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/$path", token, """{"message":"Add","content":"$encoded"}""")
                if (!result.startsWith("❌")) applied++
            }
            applied > 0
        } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.17: GIT DATA API BATCH PUSH
    // ═══════════════════════════════════════════

    private suspend fun batchPushViaGitData(token: String, owner: String, repo: String, files: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val refResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/refs/heads/main").header("Authorization", "Bearer $token").build()).execute()
            val refJson = JSONObject(refResp.body?.string() ?: "{}"); val baseSha = refJson.getJSONObject("object").getString("sha")
            val blobShas = files.map { (path, content) ->
                val blobResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/blobs").header("Authorization", "Bearer $token").post("""{"content":"${android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)}","encoding":"base64"}""".toRequestBody("application/json".toMediaType())).build()).execute()
                path to JSONObject(blobResp.body?.string() ?: "{}").getString("sha")
            }
            val treeItems = blobShas.joinToString(",") { (path, sha) -> """{"path":"$path","mode":"100644","type":"blob","sha":"$sha"}""" }
            val treeResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees").header("Authorization", "Bearer $token").post("""{"base_tree":"$baseSha","tree":[$treeItems]}""".toRequestBody("application/json".toMediaType())).build()).execute()
            val treeSha = JSONObject(treeResp.body?.string() ?: "{}").getString("sha")
            val commitResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/commits").header("Authorization", "Bearer $token").post("""{"message":"Generate ${files.size} files","tree":"$treeSha","parents":["$baseSha"]}""".toRequestBody("application/json".toMediaType())).build()).execute()
            val commitSha = JSONObject(commitResp.body?.string() ?: "{}").getString("sha")
            client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/refs/heads/main").header("Authorization", "Bearer $token").patch("""{"sha":"$commitSha"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            true
        } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.18: GITHUB API HELPERS
    // ═══════════════════════════════════════════

    private suspend fun triggerWorkflowAndGetRunId(token: String, owner: String, repo: String): Long? = withContext(Dispatchers.IO) {
        try {
            val listBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string()
            val workflowId = Regex("\"id\"\\s*:\\s*(\\d+)").find(listBody ?: "")?.groupValues?.get(1) ?: return@withContext null
            client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            delay(5000)
            val runsBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1").header("Authorization", "Bearer $token").build()).execute().body?.string()
            Regex("\"id\"\\s*:\\s*(\\d+)").find(runsBody ?: "")?.groupValues?.get(1)?.toLong()
        } catch (e: Exception) { null }
    }

    private suspend fun fetchWorkflowLogs(token: String, owner: String, repo: String, runId: Long): String = withContext(Dispatchers.IO) {
        try { client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/logs").header("Authorization", "Bearer $token").build()).execute().body?.string()?.take(15000) ?: "" } catch (e: Exception) { "" }
    }

    private fun extractKeyErrors(logs: String): String {
        val patterns = listOf(Regex("(?i)error:.*"), Regex("(?i)FAILURE:.*"), Regex("(?i)Unresolved reference.*"), Regex("(?i)BUILD FAILED.*"))
        val errors = patterns.flatMap { it.findAll(logs).map { m -> m.value }.toList() }
        return if (errors.isEmpty()) logs.take(3000) else errors.take(25).joinToString("\n")
    }

    private suspend fun retriggerBuild(token: String, owner: String, repo: String) = triggerWorkflowAndGetRunId(token, owner, repo)

    private suspend fun getArtifactDownloadUrl(token: String, owner: String, repo: String, runId: Long): String? = withContext(Dispatchers.IO) {
        try { Regex("\"archive_download_url\"\\s*:\\s*\"([^\"]+)\"").find(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "")?.groupValues?.get(1) } catch (e: Exception) { null }
    }

    private suspend fun addWorkflowFile(token: String, owner: String, repo: String, appName: String) {
        val yaml = "name: Build $appName\non: [push, workflow_dispatch]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    timeout-minutes: 30\n    steps:\n      - uses: actions/checkout@v4\n      - name: Setup Gradle Wrapper\n        run: |\n          if [ ! -f \"gradlew\" ]; then gradle wrapper --gradle-version 8.4; fi\n          chmod +x gradlew\n      - uses: actions/setup-java@v4\n        with: {java-version: '17', distribution: 'temurin'}\n      - uses: gradle/actions/setup-gradle@v3\n      - run: ./gradlew assembleDebug --no-daemon\n        env:\n          GRADLE_OPTS: \"-Dorg.gradle.jvmargs=-Xmx4g\"\n      - uses: actions/upload-artifact@v4\n        with: {name: ${appName}-debug, path: app/build/outputs/apk/debug/app-debug.apk}"
        githubApiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/.github/workflows/build.yml", token, """{"message":"Add CI","content":"${android.util.Base64.encodeToString(yaml.toByteArray(), android.util.Base64.NO_WRAP)}"}""")
    }

    // ═══════════════════════════════════════════
    // SECTION 4.19: CROSS-REPO FEATURE TRANSFER
    // ═══════════════════════════════════════════

    private suspend fun analyzePublicRepo(token: String, key: String, owner: String, repo: String): String {
        addProgressMessage("🔍 Analyzing $owner/$repo...")
        return withContext(Dispatchers.IO) {
            try { val info = getRepoInfo(token, owner, repo); "📊 $owner/$repo\n⭐ ${info.stars}\n💻 ${info.language}" } catch (e: Exception) { "❌ ${e.message}" }
        }
    }

    private suspend fun transferFeaturesFromRepo(token: String, key: String, instruction: String): String {
        if (activeOwner.isBlank()) return "❌ No active repo."
        addProgressMessage("🧠 Transferring...")
        return withContext(Dispatchers.IO) {
            try {
                val analysis = parseFeatureTransferRequest(key, instruction) ?: return@withContext "❌ Could not understand."
                val sourceFiles = getFileTree(token, analysis.sourceOwner, analysis.sourceRepo)
                val relevant = sourceFiles.filter { p -> analysis.targetFeatures.any { p.contains(it, true) } }.take(20)
                var created = 0; val currentFiles = getFileTree(token, activeOwner, activeRepo)
                for (sourcePath in relevant) {
                    try {
                        val sourceContent = readFileContent(token, analysis.sourceOwner, analysis.sourceRepo, sourcePath) ?: continue
                        val adapted = adaptFileForTargetRepo(key, sourcePath, sourceContent, analysis.sourceOwner, analysis.sourceRepo, activeOwner, activeRepo, instruction, currentFiles) ?: continue
                        val targetPath = determineTargetPath(sourcePath, activeRepo)
                        val encoded = android.util.Base64.encodeToString(adapted.toByteArray(), android.util.Base64.NO_WRAP)
                        if (!githubApiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$targetPath", token, """{"message":"Transfer","content":"$encoded"}""").startsWith("❌")) created++
                    } catch (e: Exception) { }
                }
                "✅ Transferred $created files"
            } catch (e: Exception) { "❌ ${e.message}" }
        }
    }

    private suspend fun mergeRepositoryFeatures(token: String, key: String, sourceOwner: String, sourceRepo: String): String {
        addProgressMessage("🔄 Merging...")
        return transferFeaturesFromRepo(token, key, "transfer all from $sourceOwner/$sourceRepo")
    }

    // ═══════════════════════════════════════════
    // SECTION 4.20: GITHUB HELPERS (compact)
    // ═══════════════════════════════════════════

    private suspend fun getRepoInfo(token: String, owner: String, repo: String): RepoInfo = withContext(Dispatchers.IO) {
        try { val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}"); RepoInfo(j.optString("description"), j.optInt("stargazers_count"), j.optInt("forks_count"), j.optString("language")) } catch (e: Exception) { RepoInfo("", 0, 0, "") }
    }

    private suspend fun getFileTree(token: String, owner: String, repo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            var r = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (!r.isSuccessful) r = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/master?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (r.isSuccessful) { val t = JSONObject(r.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext emptyList(); (0 until t.length()).map { t.getJSONObject(it).getString("path") }.filter { it !in listOf(".gitignore", "README.md", "LICENSE") } } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun readFileContent(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try { val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}"); val c = j.optString("content", ""); if (c.isNotBlank()) String(android.util.Base64.decode(c, android.util.Base64.DEFAULT)) else null } catch (e: Exception) { null }
    }

    private suspend fun getFileSha(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try { JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}").optString("sha", null) } catch (e: Exception) { null }
    }

    private suspend fun parseFeatureTransferRequest(key: String, instruction: String): FeatureTransferRequest? {
        val model = GenerativeModel(selectOptimalModel("complex"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
        return try { val r = model.generateContent(content { text("Parse: \"$instruction\". Return JSON: {\"sourceOwner\":\"\",\"sourceRepo\":\"\",\"targetFeatures\":[]}") }).text; val t = r ?: return null; val o = JSONObject(t.substringAfter("{").substringBeforeLast("}").let { "{$it}" }); FeatureTransferRequest(o.optString("sourceOwner"), o.optString("sourceRepo"), (0 until o.getJSONArray("targetFeatures").length()).map { o.getJSONArray("targetFeatures").getString(it) }, "") } catch (e: Exception) { null }
    }

    private suspend fun adaptFileForTargetRepo(key: String, sourcePath: String, sourceContent: String, sourceOwner: String, sourceRepo: String, targetOwner: String, targetRepo: String, instruction: String, currentTargetFiles: List<String>): String? {
        val model = GenerativeModel(selectOptimalModel("code_gen"), key, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 })
        return try { model.generateContent(content { text("Adapt:\n$sourceContent\n\nTo: $targetOwner/$targetRepo\nPath: $sourcePath\nInstruction: $instruction\nReturn ONLY adapted code.") }).text } catch (e: Exception) { null }
    }

    private fun determineTargetPath(sourcePath: String, targetRepo: String): String {
        if (sourcePath.contains("src/main/java/")) { val i = sourcePath.indexOf("src/main/java/") + 14; return "app/src/main/java/" + sourcePath.substring(i) }
        if (sourcePath.contains("src/main/res/")) return "app/" + sourcePath
        if (sourcePath.startsWith("app/")) return sourcePath
        return "app/src/main/java/com/example/${targetRepo.sanitize()}/${sourcePath.substringAfterLast("/")}"
    }

    // ═══════════════════════════════════════════
    // SECTION 4.21: GITHUB API OPERATIONS
    // ═══════════════════════════════════════════

    private suspend fun githubApiCall(method: String, url: String, token: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").header("Content-Type", "application/json").apply { when (method) { "POST" -> post((body ?: "{}").toRequestBody("application/json".toMediaType())); "PUT" -> put((body ?: "{}").toRequestBody("application/json".toMediaType())); "PATCH" -> patch((body ?: "{}").toRequestBody("application/json".toMediaType())) } }.build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val rb = res.body?.string() ?: "OK"
                if (method == "POST" && url.contains("/user/repos")) "✅ ${Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(rb)?.groupValues?.get(1) ?: "done"}"
                else if (method == "GET" && url.contains("/user/repos") && !url.contains("/contents")) { val repos = JSONArray(rb); if (repos.length() == 0) "📁 None" else "📁:\n" + (0 until minOf(repos.length(), 10)).joinToString("\n") { "• ${repos.getJSONObject(it).getString("full_name")}" } }
                else rb
            } else "❌ ${res.code}"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun triggerWorkflowDispatch(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try { val lb = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: ""; val wid = Regex("\"id\"\\s*:\\s*(\\d+)").find(lb)?.groupValues?.get(1) ?: return@withContext "❌"; if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "🚀 Triggered!" else "⚠️ Failed" } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun browseRepositoryContents(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try { val r = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute(); if (!r.isSuccessful) return@withContext "❌"; val t = JSONObject(r.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext "📁"; "📁 $owner/$repo (${t.length()}):\n" + (0 until minOf(t.length(), 50)).joinToString("\n") { "  📄 ${t.getJSONObject(it).getString("path")}" } } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun readRepoFileContents(token: String, owner: String, repo: String, path: String): String = withContext(Dispatchers.IO) {
        try { val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}"); val c = j.optString("content", ""); if (c.isBlank()) return@withContext "📄"; val d = String(android.util.Base64.decode(c, android.util.Base64.DEFAULT)); if (d.length > 3000) "📄 $path:\n${d.take(3000)}..." else "📄 $path:\n$d" } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun repairFileInRepo(token: String, key: String, owner: String, repo: String, path: String, instruction: String): String = withContext(Dispatchers.IO) {
        try {
            val rj = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            val cc = String(android.util.Base64.decode(rj.getString("content"), android.util.Base64.DEFAULT)); val sha = rj.getString("sha")
            val m = GenerativeModel(selectOptimalModel("debug"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
            val nc = m.generateContent(content { text("Fix:\n$cc\n\nInstruction: $instruction\nReturn ONLY fixed code.") }).text ?: return@withContext "❌"; recordModelUsage(selectOptimalModel("debug"))
            val enc = android.util.Base64.encodeToString(nc.toByteArray(), android.util.Base64.NO_WRAP)
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").put("""{"message":"Fix","content":"$enc","sha":"$sha"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "✅ Fixed" else "❌"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun createFileInRepo(token: String, key: String, owner: String, repo: String, path: String, description: String): String = withContext(Dispatchers.IO) {
        try { val m = GenerativeModel(selectOptimalModel("code_gen"), key, generationConfig { temperature = 0.2f; maxOutputTokens = 60000 }); val c = m.generateContent(content { text("Create: $path - $description. Return ONLY code.") }).text ?: return@withContext "❌"; recordModelUsage(selectOptimalModel("code_gen")); val enc = android.util.Base64.encodeToString(c.toByteArray(), android.util.Base64.NO_WRAP); if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").put("""{"message":"Add $path","content":"$enc"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "✅ Created" else "❌" } catch (e: Exception) { "❌ ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // SECTION 4.22: ACCESSIBILITY HELPERS
    // ═══════════════════════════════════════════

    private fun performTapOnText(service: AuraAccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false; val node = findAccessibilityNode(root, text) ?: return false.also { root.recycle() }
        val rect = android.graphics.Rect(); node.getBoundsInScreen(rect); root.recycle(); node.recycle()
        service.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(android.graphics.Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }, 0, 100)).build(), null, null); return true
    }

    private fun performTypeText(service: AuraAccessibilityService, text: String): Boolean {
        val focused = service.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val result = focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args); focused.recycle(); return result
    }

    private fun performScroll(service: AuraAccessibilityService, up: Boolean) {
        val d = service.resources.displayMetrics
        val path = if (up) android.graphics.Path().apply { moveTo(d.widthPixels/2f, d.heightPixels*0.3f); lineTo(d.widthPixels/2f, d.heightPixels*0.8f) } else android.graphics.Path().apply { moveTo(d.widthPixels/2f, d.heightPixels*0.8f); lineTo(d.widthPixels/2f, d.heightPixels*0.3f) }
        service.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)).build(), null, null)
    }

    private fun findAccessibilityNode(node: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.text?.contains(text, true) == true || node.contentDescription?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) { node.getChild(i)?.let { findAccessibilityNode(it, text)?.let { return it } } }; return null
    }

    // ═══════════════════════════════════════════
    // SECTION 4.23: UTILITY FUNCTIONS
    // ═══════════════════════════════════════════

    private fun addProgressMessage(text: String) { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(text, false), generationProgress = text) }
    private suspend fun saveMessage(text: String, isUser: Boolean, modelUsed: String? = null) { _currentSessionId.value?.let { sessionDb.messageDao().insertMessage(MessageEntity(UUID.randomUUID().toString(), it, text, isUser, modelUsed)) } }

    private fun loadSessions() {
        viewModelScope.launch { sessionDb.sessionDao().getAllSessions().collect { sl -> _sessions.value = sl; if (_currentSessionId.value == null && sl.isNotEmpty()) switchSession(sl.first().id) } }
    }
    private fun loadModelUsage() { viewModelScope.launch { sessionDb.modelUsageDao().getAllModelUsage().collect { _modelUsage.value = it } } }
    private suspend fun resetDailyCountersIfNeeded() { sessionDb.modelUsageDao().resetDailyCounters(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    private fun loadPreferredModel() { preferences.getPreferredModel()?.let { _state.value = _state.value.copy(activeModel = it, manualModelSelected = true) } }

    private fun String.sanitize() = this.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun resolveAppPackage(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; "camera" -> "com.android.camera"; "gmail" -> "com.google.android.gm"; "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"; "calculator" -> "com.android.calculator2"; "clock" -> "com.android.deskclock"; "files" -> "com.android.documentsui"; "phone" -> "com.android.dialer"; "instagram" -> "com.instagram.android"; "facebook" -> "com.facebook.katana"; "spotify" -> "com.spotify.music"; "netflix" -> "com.netflix.mediaclient"; "telegram" -> "org.telegram.messenger"; else -> null
    }

    private fun getRamUsage(): String { val am = com.aura.ai.AuraApplication.instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); return "${(mi.totalMem-mi.availMem)/(1024*1024*1024)}GB/${mi.totalMem/(1024*1024*1024)}GB" }
    private fun getStorageInfo(): String { val stat = StatFs(Environment.getDataDirectory().path); return "${stat.availableBlocksLong*stat.blockSizeLong/(1024*1024*1024)}GB/${stat.blockCountLong*stat.blockSizeLong/(1024*1024*1024)}GB" }
    private fun getBatteryLevel(): String = try { val bm = com.aura.ai.AuraApplication.instance.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%" } catch (e: Exception) { "Unknown" }
                                                                }
    
