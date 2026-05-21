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

// ============================================
// SECTION 1: PUBLIC DATA CLASSES
// ============================================

data class ChatMessage(val text: String, val isUser: Boolean)

data class ModelInfo(
    val name: String,
    val displayName: String,
    val strength: String,
    val dailyRequests: Int,
    val dailyLimit: Int,
    val isInCooldown: Boolean,
    val isSelected: Boolean
)

data class BuildLoopState(
    val attemptNumber: Int = 0,
    val maxAttempts: Int = 20,
    val buildStatus: BuildStatus = BuildStatus.IDLE,
    val workflowRunId: Long? = null,
    val errorSummary: String = "",
    val lastFixDescription: String = "",
    val buildUrl: String = "",
    val totalFixesApplied: Int = 0,
    val codespaceId: String? = null
)

enum class BuildStatus {
    IDLE, BUILDING, SUCCESS, ANALYZING_ERROR, FIXING, RETRYING, FAILED, CODESPACE_BUILDING
}

data class AgentUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("""
╔══════════════════════════════════════════╗
║     ⚡ AURA AI - NEURAL CORE ACTIVE ⚡     ║
╠══════════════════════════════════════════╣
║  📱 PHONE CONTROL                        ║
║  open [app] • home • back • recents     ║
║  screenshot • notifications             ║
║  scroll down/up • tap on [text]         ║
║  type [text] • swipe left/right         ║
║                                          ║
║  💻 APP GENERATION (Autonomous)          ║
║  create app [name] [description]        ║
║  → Self-healing build loop (20 retries) ║
║  → Codespaces batch generation          ║
║                                          ║
║  🖥️  CODESPACES COMMANDS                  ║
║  codespace create [owner/repo]          ║
║  codespace batch push                   ║
║  codespace build                        ║
║  codespace list • delete                ║
║                                          ║
║  🐙 GITHUB COMMANDS                      ║
║  create repo [name] • list repos        ║
║  compile repo [owner/repo]              ║
║  browse repo [owner/repo]               ║
║  read repo file [o/r] [path]            ║
║  fix file [path]: [instruction]         ║
║  add file [path]: [description]         ║
║  set repo [owner/repo]                  ║
║                                          ║
║  🔍 REPO ANALYSIS & TRANSFER             ║
║  analyze repo [owner/repo]              ║
║  transfer [feature] from [o/r]          ║
║  merge repo [owner/repo]                ║
║                                          ║
║  📂 FILE SYSTEM                          ║
║  list files • search files [q]          ║
║  delete file [path] • read file [path]  ║
║  duplicate cleaner [path]               ║
║                                          ║
║  📊 SYSTEM                               ║
║  device info • time • battery           ║
║  storage • ram • cpu                    ║
║                                          ║
║  ⏯️  CONTROL                              ║
║  pause • resume • stop                  ║
║                                          ║
╚══════════════════════════════════════════╝
        """.trimIndent(), false)
    ),
    val input: String = "",
    val loading: Boolean = false,
    val isExecuting: Boolean = false,
    val currentTask: String = "",
    val executionMode: ExecutionMode = ExecutionMode.IDLE,
    val activeModel: String = "gemini-3.1-flash-lite",
    val showDrawer: Boolean = false,
    val showModelDashboard: Boolean = false,
    val manualModelSelected: Boolean = false,
    val currentSessionId: String? = null,
    val buildLoop: BuildLoopState? = null,
    val isGeneratingApp: Boolean = false,
    val generationProgress: String = "",
    val codespaceMode: Boolean = false,
    val activeCodespaceId: String? = null,
    val codespaceStatus: String = "",
    val pendingBatchFiles: Map<String, String> = emptyMap(),
    val duplicateScanResults: List<String> = emptyList()
)

enum class ExecutionMode {
    IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION,
    FILE_OPERATION, REPO_ANALYSIS, FEATURE_TRANSFER, CODESPACE_GENERATION,
    BATCH_FILE_PUSH, DUPLICATE_CLEANER
}

// ============================================
// SECTION 2: INTERNAL DATA CLASSES
// ============================================

private data class AppArchitecture(
    val files: List<String>,
    val techStack: String,
    val dependencies: List<String>,
    val structure: String
)

private data class FixPlan(val summary: String, val fileFixes: List<Pair<String, String>>)

private data class RepoInfo(
    val description: String, val stars: Int, val forks: Int, val language: String
)

private data class RepoAnalysis(
    val architecture: String,
    val keyFeatures: List<String>,
    val fileStructure: Map<String, String>,
    val dependencies: List<String>,
    val coreLogic: Map<String, String>
)

private data class FeatureTransferRequest(
    val sourceOwner: String,
    val sourceRepo: String,
    val targetFeatures: List<String>,
    val additionalContext: String
)

private sealed class WorkflowResult {
    data object Success : WorkflowResult()
    data class Failure(val error: String, val logs: String) : WorkflowResult()
}

// ============================================
// SECTION 3: VIEWMODEL
// ============================================

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val preferences: AuraPreferences
) : ViewModel() {

    // ===== STATE MANAGEMENT =====
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()
    private var taskJob: Job? = null
    private var isPaused = false
    private var activeRepo = ""
    private var activeOwner = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private var pendingGenerationFiles: Map<String, String> = emptyMap()

    // ===== SESSION MANAGEMENT =====
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
        viewModelScope.launch { resetDailyCountersIfNeeded() }
    }

    // ============================================
    // SECTION 4: MODEL REGISTRY & SELECTION
    // ============================================

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
    private fun recordModelUsage(model: String) { modelDailyUsage[model] = (modelDailyUsage[model] ?: 0) + 1 }
    private fun applyModelCooldown(model: String) { consecutiveFailures++; modelCooldowns[model] = System.currentTimeMillis() + 60000 }
    private fun resetFailureState() { consecutiveFailures = 0 }

    // ============================================
    // SECTION 5: PUBLIC INTERFACE
    // ============================================

    fun updateInput(text: String) { _state.value = _state.value.copy(input = text) }
    fun toggleDrawer() { _state.value = _state.value.copy(showDrawer = !_state.value.showDrawer) }
    fun toggleModelDashboard() { _state.value = _state.value.copy(showModelDashboard = !_state.value.showModelDashboard) }

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
        taskJob = viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, currentTask = msg)
            saveMessage(msg, isUser = true)
            val result = executeCommandPipeline(msg)
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(result, false), loading = false, isExecuting = false, currentTask = "", executionMode = ExecutionMode.IDLE)
            saveMessage(result, isUser = false, modelUsed = _state.value.activeModel)
        }
    }

    // ============================================
    // SECTION 6: COMMAND PIPELINE
    // ============================================

    private suspend fun executeCommandPipeline(input: String): String {
        return executeCodespaceCommand(input)
            ?: executePhoneCommand(input)
            ?: executeGitHubCommand(input)
            ?: executeFileCommand(input)
            ?: executeDuplicateCleanerCommand(input)
            ?: executeSystemCommand(input)
            ?: executeGeminiChat(input)
    }

    // ============================================
    // SECTION 7: CONTROL COMMANDS
    // ============================================

    private fun handleControlCommand(input: String): Boolean {
        return when (input.lowercase().trim()) {
            "pause", "pause task" -> {
                if (_state.value.isExecuting) { isPaused = true; taskJob?.cancel(); _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("\u23f8\ufe0f Task paused.", false), loading = false) }
                else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("\u2139\ufe0f No active task.", false), loading = false)
                true
            }
            "stop", "cancel", "stop task" -> {
                taskJob?.cancel(); isPaused = false
                _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("\u23f9\ufe0f Stopped.", false), loading = false, isExecuting = false, executionMode = ExecutionMode.IDLE, isGeneratingApp = false, buildLoop = null)
                true
            }
            else -> false
        }
    }

    // ============================================
    // SECTION 8: CODESPACES COMMANDS
    // ============================================

    private suspend fun executeCodespaceCommand(input: String): String? {
        val token = preferences.getGitHubToken() ?: return null
        val lower = input.lowercase().trim()
        val manager = CodespacesManager(token)

        if (lower.startsWith("codespace create") || lower.startsWith("create codespace")) {
            val parts = lower.removePrefix("codespace create").removePrefix("create codespace").trim().split("/")
            val owner = if (parts.size == 2) parts[0] else activeOwner
            val repo = if (parts.size == 2) parts[1] else activeRepo
            if (owner.isBlank() || repo.isBlank()) return "\u274c Specify owner/repo or set active repo."
            _state.value = _state.value.copy(codespaceStatus = "Creating codespace...")
            val cs = manager.createCodespace(owner, repo)
            if (cs == null) { _state.value = _state.value.copy(codespaceStatus = ""); return "\u274c Failed to create codespace." }
            _state.value = _state.value.copy(activeCodespaceId = cs.id, codespaceStatus = "Created: ${cs.name}", codespaceMode = true)
            return "\ud83d\udda5\ufe0f Codespace created: ${cs.name}\n\ud83d\udd17 ${cs.webUrl}\n\ud83d\udccb Use 'codespace batch push' to push files."
        }

        if (lower.startsWith("codespace batch") || lower.startsWith("batch push")) {
            if (_state.value.activeCodespaceId.isNullOrBlank()) return "\u274c No active codespace."
            _state.value = _state.value.copy(codespaceStatus = "Waiting for codespace...")
            val ready = manager.waitForCodespaceReady(_state.value.activeCodespaceId!!)
            if (!ready) { _state.value = _state.value.copy(codespaceStatus = ""); return "\u274c Codespace not ready." }
            if (pendingGenerationFiles.isEmpty()) return "\u274c No pending files. Generate an app first."
            _state.value = _state.value.copy(codespaceStatus = "Pushing ${pendingGenerationFiles.size} files...")
            val success = manager.batchGenerateFiles(_state.value.activeCodespaceId!!, pendingGenerationFiles, activeOwner, activeRepo)
            _state.value = _state.value.copy(codespaceStatus = if (success) "Batch push complete!" else "Batch push failed")
            return if (success) "\u2705 Batch pushed ${pendingGenerationFiles.size} files via Codespaces!" else "\u274c Batch push failed."
        }

        if (lower.startsWith("codespace build")) {
            if (_state.value.activeCodespaceId.isNullOrBlank()) return "\u274c No active codespace."
            _state.value = _state.value.copy(codespaceStatus = "Building in codespace...")
            val result = manager.runBuildInCodespace(_state.value.activeCodespaceId!!, activeRepo)
            _state.value = _state.value.copy(codespaceStatus = "")
            return "\ud83d\udd28 Build result:\n$result"
        }

        if (lower.startsWith("codespace list") || lower == "list codespaces") {
            val list = manager.listCodespaces()
            return if (list.isEmpty()) "\ud83d\udcc1 No codespaces." else "\ud83d\udda5\ufe0f Codespaces:\n" + list.joinToString("\n") { "\u2022 ${it.name} (${it.state})" }
        }

        if (lower.startsWith("codespace delete")) {
            val id = lower.removePrefix("codespace delete").trim()
            if (id.isBlank()) return "\u274c Specify codespace ID."
            return if (manager.deleteCodespace(id)) "\ud83d\uddd1\ufe0f Codespace deleted." else "\u274c Failed."
        }

        return null
    }

    // ============================================
    // SECTION 9: PHONE CONTROL
    // ============================================

    private suspend fun executePhoneCommand(input: String): String? {
        val lower = input.lowercase().trim()
        val service = AuraAccessibilityService.instance ?: return null
        _state.value = _state.value.copy(executionMode = ExecutionMode.PHONE_CONTROL)

        if (lower.startsWith("open ")) {
            val appName = lower.removePrefix("open ").trim()
            val pkg = resolveAppPackage(appName) ?: return "\u274c Unknown app: $appName"
            return try {
                val intent = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); com.aura.ai.AuraApplication.instance.startActivity(intent); "\u2705 Opened $appName" }
                else "\u274c Could not open $appName"
            } catch (e: Exception) { "\u274c Error: ${e.message}" }
        }
        if (lower == "home") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "\ud83c\udfe0 Home" }
        if (lower == "back") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "\u2b05\ufe0f Back" }
        if (lower == "recents") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); return "\ud83d\udcf1 Recent apps" }
        if (lower == "notifications") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); return "\ud83d\udd14 Notifications" }
        if (lower == "screenshot") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "\ud83d\udcf8 Screenshot taken" }
        if (lower == "scroll down") { performScroll(service, false); return "\ud83d\udc47 Scrolled down" }
        if (lower == "scroll up") { performScroll(service, true); return "\ud83d\udc46 Scrolled up" }
        if (lower.startsWith("tap on ")) { val target = lower.removePrefix("tap on ").trim(); return if (performTapOnText(service, target)) "\ud83d\udc46 Tapped '$target'" else "\u274c Could not find '$target'" }
        if (lower.startsWith("type ")) { val text = input.removePrefix("type ").trim(); return if (performTypeText(service, text)) "\u2328\ufe0f Typed" else "\u274c Could not type" }
        if (lower == "swipe left") { performSwipe(service, false); return "\ud83d\udc48 Swiped left" }
        if (lower == "swipe right") { performSwipe(service, true); return "\ud83d\udc49 Swiped right" }
        return null
    }

    // ============================================
    // SECTION 10: GITHUB COMMANDS (if/return - NO when BLOCK)
    // ============================================

    private suspend fun executeGitHubCommand(input: String): String? {
        val token = preferences.getGitHubToken() ?: return null
        val apiKey = preferences.getApiKey()
        val key = apiKey ?: ""
        val lower = input.lowercase().trim()
        _state.value = _state.value.copy(executionMode = ExecutionMode.GITHUB_OPERATION)

        // App Generation
        if (lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) {
            if (!lower.contains("repo")) {
                val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
                val appName = appDesc.split(" ").firstOrNull()?.replace(" ", "-")?.take(50) ?: "MyApp"
                val parts = appDesc.split(" ")
                val description = if (parts.size > 1) parts.drop(1).joinToString(" ").trim() else "A simple application"
                _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP)
                return createFullApplication(token, key, appName, description)
            }
        }

        // Create Repo
        if (lower.contains("create") && lower.contains("repo")) {
            val name = input.replace(Regex("(?i)(create|a|repo|repository|github)"), "").trim().replace(" ", "-").take(50)
            return if (name.isBlank()) "\u274c Please specify a repository name."
            else githubApiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$name","private":false,"auto_init":true}""")
        }

        // List Repos
        if (lower.contains("list") && lower.contains("repo")) {
            return githubApiCall("GET", "https://api.github.com/user/repos?per_page=10&sort=updated", token, null)
        }

        // Compile/Build
        if (lower.startsWith("compile ") || lower.startsWith("build ")) {
            val repo = lower.removePrefix("compile ").removePrefix("build ").trim()
            val parts = repo.split("/")
            return if (parts.size != 2) "\u274c Format: compile repo owner/repo" else triggerWorkflowDispatch(token, parts[0], parts[1])
        }

        // Browse Repo
        if (lower.startsWith("browse repo ") || lower.startsWith("explore repo ")) {
            val repo = lower.removePrefix("browse repo ").removePrefix("explore repo ").trim()
            val parts = repo.split("/")
            return if (parts.size != 2) "\u274c Format: browse repo owner/repo" else browseRepositoryContents(token, parts[0], parts[1])
        }

        // Read Repo File
        if (lower.startsWith("read repo file ")) {
            val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" ")
            if (parts.size < 2) return "\u274c Format: read repo file owner/repo path"
            val repoParts = parts[0].split("/")
            if (repoParts.size != 2) return "\u274c Format: read repo file owner/repo path"
            return readRepoFileContents(token, repoParts[0], repoParts[1], parts.drop(1).joinToString(" "))
        }

        // Fix File
        if (lower.startsWith("fix ") || lower.startsWith("edit ")) {
            val remaining = input.replace(Regex("(?i)(fix|edit|update) "), "")
            val filePath = remaining.substringBefore(":").trim()
            val instruction = remaining.substringAfter(":").trim()
            if (filePath.isBlank() || instruction.isBlank()) return "\u274c Usage: fix file path/to/file.kt: instruction"
            if (activeRepo.isBlank()) return "\u274c No active repo. Use 'set repo owner/repo' first."
            return repairFileInRepo(token, key, activeOwner, activeRepo, filePath, instruction)
        }

        // Add File
        if (lower.startsWith("add file ") || lower.startsWith("create file ")) {
            val remaining = input.replace(Regex("(?i)(add|create) file "), "")
            val filePath = remaining.substringBefore(":").trim()
            val description = remaining.substringAfter(":").trim()
            if (filePath.isBlank() || description.isBlank()) return "\u274c Usage: add file path/to/file.kt: description"
            if (activeRepo.isBlank()) return "\u274c No active repo."
            return createFileInRepo(token, key, activeOwner, activeRepo, filePath, description)
        }

        // Set Active Repo
        if (lower.startsWith("set repo ") || lower.startsWith("switch to ")) {
            val repo = lower.removePrefix("set repo ").removePrefix("switch to ").trim()
            val parts = repo.split("/")
            if (parts.size != 2) return "\u274c Format: set repo owner/repo"
            activeOwner = parts[0]; activeRepo = parts[1]
            return "\u2705 Active repo: $activeOwner/$activeRepo"
        }

        // Analyze Repo
        if (lower.startsWith("analyze repo ") || lower.startsWith("study repo ")) {
            val repo = input.replace(Regex("(?i)(analyze|study) repo "), "").trim()
            val parts = repo.split("/")
            if (parts.size != 2) return "\u274c Format: analyze repo owner/repo"
            _state.value = _state.value.copy(executionMode = ExecutionMode.REPO_ANALYSIS)
            return analyzePublicRepo(token, key, parts[0], parts[1])
        }

        // Transfer Features
        if (lower.startsWith("transfer ") || lower.startsWith("port ")) {
            val instruction = input.replace(Regex("(?i)(transfer|port|add feature) "), "")
            if (activeRepo.isBlank()) return "\u274c No active repo."
            _state.value = _state.value.copy(executionMode = ExecutionMode.FEATURE_TRANSFER)
            return transferFeaturesFromRepo(token, key, instruction)
        }

        // Merge Repo
        if (lower.startsWith("merge repo ") || lower.startsWith("clone features from ")) {
            val sourceRepo = input.replace(Regex("(?i)(merge repo|clone features from) "), "").trim()
            val parts = sourceRepo.split("/")
            if (parts.size != 2) return "\u274c Format: merge repo owner/repo"
            if (activeRepo.isBlank()) return "\u274c No active repo."
            _state.value = _state.value.copy(executionMode = ExecutionMode.FEATURE_TRANSFER)
            return mergeRepositoryFeatures(token, key, parts[0], parts[1])
        }

        return null
    }

    // ============================================
    // SECTION 11: FILE COMMANDS
    // ============================================

    private fun executeFileCommand(input: String): String? {
        val lower = input.lowercase().trim()
        _state.value = _state.value.copy(executionMode = ExecutionMode.FILE_OPERATION)

        if (lower.startsWith("list files")) {
            val path = input.replace(Regex("(?i)list files"), "").trim().ifBlank { Environment.getExternalStorageDirectory().absolutePath }
            return try {
                val files = File(path).listFiles()?.take(40)
                if (files.isNullOrEmpty()) "\ud83d\udcc1 Empty directory." else "\ud83d\udcc1 $path:\n" + files.joinToString("\n") { "${if (it.isDirectory) "\ud83d\udcc1" else "\ud83d\udcc4"} ${it.name} (${formatFileSize(it.length())})" }
            } catch (e: Exception) { "\u274c Error: ${e.message}" }
        }

        if (lower.startsWith("search files")) {
            val query = input.replace(Regex("(?i)search files"), "").trim()
            if (query.isBlank()) return "\u274c What should I search for?"
            return try {
                val results = mutableListOf<String>()
                recursiveFileSearch(File(Environment.getExternalStorageDirectory().absolutePath), query, results, 4)
                if (results.isEmpty()) "\ud83d\udd0d No files found matching '$query'" else "\ud83d\udd0d Found ${results.size} files:\n" + results.take(25).joinToString("\n") { "\ud83d\udcc4 $it" }
            } catch (e: Exception) { "\u274c Error: ${e.message}" }
        }

        if (lower.startsWith("read file") || lower.startsWith("show file")) {
            val path = input.replace(Regex("(?i)(read|show) file"), "").trim()
            if (path.isBlank()) return "\u274c Usage: read file /path/to/file.txt"
            return try {
                val content = File(path).readText()
                if (content.length > 2500) "\ud83d\udcc4 $path (${content.length} chars):\n\n${content.take(2500)}\n\n..." else "\ud83d\udcc4 $path:\n\n$content"
            } catch (e: Exception) { "\u274c Error: ${e.message}" }
        }

        if (lower.startsWith("delete file")) {
            val path = input.replace(Regex("(?i)delete file"), "").trim()
            if (path.isBlank()) return "\u274c Which file should I delete?"
            return try { val f = File(path); if (f.exists()) { f.delete(); "\u2705 Deleted: ${f.name}" } else "\u274c File not found" } catch (e: Exception) { "\u274c Error: ${e.message}" }
        }

        return null
    }

    // ============================================
    // SECTION 12: DUPLICATE CLEANER
    // ============================================

    private suspend fun executeDuplicateCleanerCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (!lower.startsWith("duplicate") && !lower.startsWith("find duplicate") && !lower.startsWith("clean duplicate")) return null

        _state.value = _state.value.copy(executionMode = ExecutionMode.DUPLICATE_CLEANER)
        val path = lower.replace(Regex("(?i)(find |clean |delete )?duplicates?( in | from)?"), "").trim().ifBlank { Environment.getExternalStorageDirectory().absolutePath + "/DCIM/Camera" }

        if (lower.startsWith("find duplicate")) {
            val duplicates = findDuplicateFiles(path)
            _state.value = _state.value.copy(duplicateScanResults = duplicates)
            return if (duplicates.isEmpty()) "\u2705 No duplicates found in $path" else "\ud83d\udd0d Found ${duplicates.size} potential duplicates:\n" + duplicates.take(20).joinToString("\n") { "\ud83d\udcc4 $it" }
        }

        if (lower.startsWith("clean duplicate") || lower.startsWith("delete duplicate")) {
            val duplicates = _state.value.duplicateScanResults.ifEmpty { findDuplicateFiles(path) }
            if (duplicates.isEmpty()) return "\u2705 No duplicates to clean."
            var deleted = 0
            for (filePath in duplicates) {
                try { if (File(filePath).delete()) deleted++ } catch (e: Exception) { }
            }
            _state.value = _state.value.copy(duplicateScanResults = emptyList())
            return "\ud83d\uddd1\ufe0f Cleaned $deleted duplicate files from $path"
        }

        return null
    }

    private fun findDuplicateFiles(path: String): List<String> {
        val duplicates = mutableListOf<String>()
        try {
            val dir = File(path)
            if (!dir.exists()) return duplicates
            val filesBySize = mutableMapOf<Long, MutableList<String>>()
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.contains(Regex("\\(\\d+\\)|copy|Copy|\\s-\\s"))) {
                    filesBySize.getOrPut(file.length()) { mutableListOf() }.add(file.absolutePath)
                }
            }
            filesBySize.values.filter { it.size > 1 }.forEach { duplicates.addAll(it.drop(1)) }
        } catch (e: Exception) { }
        return duplicates
    }

    // ============================================
    // SECTION 13: SYSTEM COMMANDS
    // ============================================

    private fun executeSystemCommand(input: String): String? {
        val lower = input.lowercase().trim()

        if (lower == "device info" || lower == "system info") {
            val ram = getRamUsage(); val storage = getStorageInfo(); val battery = getBatteryLevel()
            return "\ud83d\udcf1 Model: ${Build.MODEL}\n\ud83e\uddbe Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n\ud83d\udcbe RAM: $ram\n\ud83d\udcc0 Storage: $storage\n\ud83d\udd0b Battery: $battery\n\u2699\ufe0f CPU: ${Runtime.getRuntime().availableProcessors()} cores"
        }
        if (lower == "time" || lower == "what time is it") return "\ud83d\udd50 ${SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm:ss z", Locale.getDefault()).format(Date())}"
        if (lower == "battery") return "\ud83d\udd0b ${getBatteryLevel()}"
        if (lower == "storage") return "\ud83d\udcc0 ${getStorageInfo()}"
        if (lower == "ram" || lower == "memory") return "\ud83d\udcbe ${getRamUsage()}"
        return null
    }

    // ============================================
    // SECTION 14: GEMINI CHAT
    // ============================================

    private suspend fun executeGeminiChat(input: String): String {
        val key = preferences.getApiKey() ?: return "\u274c No Gemini API key set."
        _state.value = _state.value.copy(executionMode = ExecutionMode.CHATTING)
        val modelName = if (_state.value.manualModelSelected) _state.value.activeModel else selectOptimalModel("general")
        _state.value = _state.value.copy(activeModel = modelName)
        return try {
            val model = GenerativeModel(modelName, key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 })
            val response = model.generateContent(content { text(input) }).text ?: "No response generated."
            recordModelUsage(modelName); resetFailureState(); response
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("429") || errorMsg.contains("quota") || errorMsg.contains("503")) {
                applyModelCooldown(modelName)
                if (_state.value.manualModelSelected) "\u26a0\ufe0f Model '$modelName' is rate-limited. Choose another."
                else {
                    val fallback = selectOptimalModel("high_volume")
                    try {
                        val fb = GenerativeModel(fallback, key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 })
                        val fbResponse = fb.generateContent(content { text(input) }).text ?: "No response."
                        recordModelUsage(fallback); resetFailureState()
                        "\ud83d\udd04 (Switched to $fallback)\n\n$fbResponse"
                    } catch (e2: Exception) { "\u274c All models unavailable." }
                }
            } else "\u274c Error: ${errorMsg}"
        }
    }

    // ============================================
    // SECTION 15: AUTONOMOUS APP GENERATION
    // ============================================

    private suspend fun createFullApplication(token: String, key: String, appName: String, description: String): String {
        _state.value = _state.value.copy(isGeneratingApp = true, generationProgress = "\ud83d\ude80 Starting")
        pendingGenerationFiles = emptyMap()

        try {
            addProgressMessage("\ud83e\udde0 Phase 1/5: Planning architecture...")
            val architecture = planAppArchitecture(key, appName, description)
            if (architecture.files.isEmpty()) { _state.value = _state.value.copy(isGeneratingApp = false); return "\u274c Planning failed." }
            addProgressMessage("\ud83d\udccb ${architecture.files.size} files planned - ${architecture.techStack}")

            addProgressMessage("\ud83d\udcc1 Phase 2/5: Creating GitHub repository...")
            val createResult = githubApiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$appName","private":false,"auto_init":false}""")
            if (createResult.startsWith("\u274c")) { _state.value = _state.value.copy(isGeneratingApp = false); return "\u274c $createResult" }
            val userResult = githubApiCall("GET", "https://api.github.com/user", token, null)
            val owner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(userResult)?.groupValues?.get(1) ?: return "\u274c Could not determine username."
            activeOwner = owner; activeRepo = appName

            addProgressMessage("\u2699\ufe0f Phase 3/5: Generating ${architecture.files.size} files...")
            val generatedFiles = generateAllFilesWithContext(key, appName, description, architecture.files, architecture)
            if (generatedFiles.isEmpty()) { _state.value = _state.value.copy(isGeneratingApp = false); return "\u274c Generation failed." }
            pendingGenerationFiles = generatedFiles
            addProgressMessage("\ud83d\udcdd Generated ${generatedFiles.size} files")

            // If codespace is active, use batch push
            if (_state.value.activeCodespaceId != null && _state.value.codespaceMode) {
                addProgressMessage("\ud83d\udda5\ufe0f Phase 4/5: Batch pushing via Codespaces...")
                val manager = CodespacesManager(token)
                val ready = manager.waitForCodespaceReady(_state.value.activeCodespaceId!!)
                if (ready) {
                    val success = manager.batchGenerateFiles(_state.value.activeCodespaceId!!, generatedFiles, owner, appName)
                    if (success) {
                        addWorkflowFile(token, owner, appName, appName)
                        addProgressMessage("\u2705 Pushed ${generatedFiles.size} files via Codespaces")
                        addProgressMessage("\ud83d\udd04 Phase 5/5: Build loop...")
                        val buildResult = executeBuildLoop(token, key, owner, appName)
                        _state.value = _state.value.copy(isGeneratingApp = false, executionMode = ExecutionMode.IDLE)
                        return buildResult
                    }
                }
                addProgressMessage("\u26a0\ufe0f Codespace push failed, falling back to API...")
            }

            // Fallback to per-file API push
            addProgressMessage("\ud83d\udce4 Phase 4/5: Pushing files via API...")
            var pushed = 0
            generatedFiles.forEach { (path, content) ->
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                if (!githubApiCall("PUT", "https://api.github.com/repos/$owner/$appName/contents/$path", token, """{"message":"Add $path","content":"$encoded"}""").startsWith("\u274c")) pushed++
            }
            addWorkflowFile(token, owner, appName, appName)
            addProgressMessage("\u2705 Pushed $pushed/${generatedFiles.size} files")

            addProgressMessage("\ud83d\udd04 Phase 5/5: Autonomous build verification...")
            val buildResult = executeBuildLoop(token, key, owner, appName)
            _state.value = _state.value.copy(isGeneratingApp = false, executionMode = ExecutionMode.IDLE)
            return buildResult
        } catch (e: Exception) {
            _state.value = _state.value.copy(isGeneratingApp = false)
            return "\u274c Generation failed: ${e.message}"
        }
    }

    private suspend fun planAppArchitecture(key: String, appName: String, description: String): AppArchitecture {
        val model = GenerativeModel(selectOptimalModel("code_gen"), key, generationConfig { temperature = 0.2f; maxOutputTokens = 60000 })
        return try {
            val response = model.generateContent(content { text("Plan Android app: $appName - $description. Return JSON: {\"files\":[],\"techStack\":\"\",\"dependencies\":[],\"structure\":\"\"}") }).text
            val text = response ?: return AppArchitecture(emptyList(), "", emptyList(), "")
            recordModelUsage(selectOptimalModel("code_gen"))
            val jsonStr = text.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
            val obj = JSONObject(jsonStr)
            AppArchitecture(
                (0 until obj.getJSONArray("files").length()).map { obj.getJSONArray("files").getString(it) },
                obj.optString("techStack", ""),
                (0 until obj.getJSONArray("dependencies").length()).map { obj.getJSONArray("dependencies").getString(it) },
                obj.optString("structure", "")
            )
        } catch (e: Exception) { AppArchitecture(emptyList(), "Standard", emptyList(), "Basic") }
    }

    private suspend fun generateAllFilesWithContext(key: String, appName: String, description: String, fileList: List<String>, architecture: AppArchitecture): Map<String, String> {
        val allFiles = mutableMapOf<String, String>()
        fileList.chunked(50).forEachIndexed { index, batch ->
            addProgressMessage("\ud83d\udcdd Batch ${index + 1}/${(fileList.size + 49) / 50}: ${batch.size} files")
            val model = GenerativeModel(selectOptimalModel("code_gen"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
            try {
                val existingSummary = if (allFiles.isNotEmpty()) "ALREADY GENERATED:\n${allFiles.keys.take(10).joinToString("\n")}" else "First batch."
                val prompt = "Generate code for: $appName\n$existingSummary\nFiles: ${batch.joinToString("\n")}\nReturn JSON: {\"files\":[{\"path\":\"\",\"content\":\"\"}]}"
                val response = model.generateContent(content { text(prompt) }).text
                val text = response ?: return@forEachIndexed
                recordModelUsage(selectOptimalModel("code_gen"))
                val obj = JSONObject(text.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
                val arr = obj.getJSONArray("files")
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    allFiles[f.getString("path")] = f.getString("content").replace("\\n", "\n")
                }
            } catch (e: Exception) { addProgressMessage("\u26a0\ufe0f Batch ${index + 1} partial: ${e.message}") }
        }
        return allFiles
    }

    // ============================================
    // SECTION 16: SELF-HEALING BUILD LOOP
    // ============================================

    private suspend fun executeBuildLoop(token: String, key: String, owner: String, repo: String): String {
        var attempt = 0; val maxAttempts = 20; var totalFixes = 0
        _state.value = _state.value.copy(buildLoop = BuildLoopState(maxAttempts = maxAttempts))
        addProgressMessage("\ud83d\udd28 Triggering build...")
        var runId: Long? = triggerWorkflowAndGetRunId(token, owner, repo)
        if (runId == null) return "\u26a0\ufe0f Files pushed. Use 'compile repo $owner/$repo'."

        while (attempt < maxAttempts) {
            attempt++
            addProgressMessage("\ud83d\udd28 Build $attempt/$maxAttempts...")
            when (val result = waitForWorkflowCompletion(token, owner, repo, runId!!)) {
                is WorkflowResult.Success -> {
                    val artifactUrl = getArtifactDownloadUrl(token, owner, repo, runId)
                    _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(buildStatus = BuildStatus.SUCCESS))
                    return buildString {
                        append("\u2705 BUILD SUCCESSFUL!\n\ud83d\udcf1 $repo\n\ud83d\udd04 Attempts: $attempt\n\ud83d\udd27 Fixes: $totalFixes\n")
                        if (artifactUrl != null) append("\ud83d\udce5 APK: $artifactUrl") else append("\ud83d\udce5 APK in GitHub Actions artifacts")
                    }
                }
                is WorkflowResult.Failure -> {
                    addProgressMessage("\u274c Failed. Analyzing errors...")
                    val fixPlan = analyzeBuildError(key, result.error, result.logs)
                    if (fixPlan != null) {
                        addProgressMessage("\ud83d\udd27 Fixing: ${fixPlan.summary}")
                        if (applyBuildFixes(token, key, owner, repo, fixPlan)) totalFixes++
                    }
                    runId = retriggerBuild(token, owner, repo)
                    if (runId == null) break
                }
            }
        }
        _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(buildStatus = BuildStatus.FAILED))
        return "\u26a0\ufe0f Build loop exhausted ($maxAttempts attempts)\n\ud83d\udd27 Fixes: $totalFixes"
    }

    private suspend fun triggerWorkflowAndGetRunId(token: String, owner: String, repo: String): Long? = withContext(Dispatchers.IO) {
        try {
            val listBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string()
            val workflowId = Regex("\"id\"\\s*:\\s*(\\d+)").find(listBody ?: "")?.groupValues?.get(1) ?: return@withContext null
            client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            delay(3000)
            val runsBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1").header("Authorization", "Bearer $token").build()).execute().body?.string()
            Regex("\"id\"\\s*:\\s*(\\d+)").find(runsBody ?: "")?.groupValues?.get(1)?.toLong()
        } catch (e: Exception) { null }
    }

    private suspend fun waitForWorkflowCompletion(token: String, owner: String, repo: String, runId: Long): WorkflowResult {
        repeat(60) {
            delay(5000)
            val status = withContext(Dispatchers.IO) {
                try {
                    val body = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId").header("Authorization", "Bearer $token").build()).execute().body?.string()
                    Pair(Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(body ?: "")?.groupValues?.get(1), Regex("\"conclusion\"\\s*:\\s*\"([^\"]+)\"").find(body ?: "")?.groupValues?.get(1))
                } catch (e: Exception) { null }
            }
            if (status?.first == "completed") return if (status.second == "success") WorkflowResult.Success else WorkflowResult.Failure("Build failed", "")
        }
        return WorkflowResult.Failure("Timed out", "")
    }

    private suspend fun analyzeBuildError(key: String, error: String, fullLogs: String): FixPlan? {
        val model = GenerativeModel(selectOptimalModel("debug"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
        return try {
            val response = model.generateContent(content { text("Fix build error: $error\nReturn JSON: {\"summary\":\"\",\"fixes\":[{\"file\":\"\",\"instruction\":\"\"}]}") }).text
            val text = response ?: return null
            recordModelUsage(selectOptimalModel("debug"))
            val obj = JSONObject(text.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
            val arr = obj.getJSONArray("fixes")
            FixPlan(obj.getString("summary"), (0 until arr.length()).map { val f = arr.getJSONObject(it); Pair(f.getString("file"), f.getString("instruction")) })
        } catch (e: Exception) { null }
    }

    private suspend fun applyBuildFixes(token: String, key: String, owner: String, repo: String, plan: FixPlan): Boolean {
        var success = true
        plan.fileFixes.forEach { (file, instruction) -> if (repairFileInRepo(token, key, owner, repo, file, instruction).startsWith("\u274c")) success = false }
        return success
    }

    private suspend fun retriggerBuild(token: String, owner: String, repo: String) = triggerWorkflowAndGetRunId(token, owner, repo)

    private suspend fun getArtifactDownloadUrl(token: String, owner: String, repo: String, runId: Long): String? = withContext(Dispatchers.IO) {
        try { Regex("\"archive_download_url\"\\s*:\\s*\"([^\"]+)\"").find(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "")?.groupValues?.get(1) } catch (e: Exception) { null }
    }

    private suspend fun addWorkflowFile(token: String, owner: String, repo: String, appName: String) {
        val yaml = "name: Build $appName\non: [push, workflow_dispatch]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - uses: actions/setup-java@v4\n        with: {java-version: '17', distribution: 'temurin'}\n      - run: chmod +x gradlew\n      - run: ./gradlew assembleDebug\n      - uses: actions/upload-artifact@v4\n        with: {name: ${appName}-debug, path: app/build/outputs/apk/debug/app-debug.apk}"
        githubApiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/.github/workflows/build.yml", token, """{"message":"Add CI","content":"${android.util.Base64.encodeToString(yaml.toByteArray(), android.util.Base64.NO_WRAP)}"}""")
    }

    // ============================================
    // SECTION 17: CROSS-REPO FEATURE TRANSFER
    // ============================================

    private suspend fun analyzePublicRepo(token: String, key: String, owner: String, repo: String): String {
        addProgressMessage("\ud83d\udd0d Analyzing $owner/$repo...")
        return withContext(Dispatchers.IO) {
            try {
                val repoInfo = getRepoInfo(token, owner, repo)
                val fileTree = getFileTree(token, owner, repo)
                val keyFiles = readKeyFilesForAnalysis(token, owner, repo, fileTree)
                val analysis = analyzeRepoWithAI(key, repoInfo, keyFiles, fileTree)
                "\ud83d\udcca $owner/$repo\n\u2b50 ${repoInfo.stars}\n\ud83c\udfd7\ufe0f ${analysis.architecture}\n\ud83d\udd11 ${analysis.keyFeatures.take(5).joinToString()}"
            } catch (e: Exception) { "\u274c ${e.message}" }
        }
    }

    private suspend fun transferFeaturesFromRepo(token: String, key: String, instruction: String): String {
        if (activeOwner.isBlank()) return "\u274c No active repo."
        addProgressMessage("\ud83e\udde0 Transferring: $instruction")
        return withContext(Dispatchers.IO) {
            try {
                val analysis = parseFeatureTransferRequest(key, instruction) ?: return@withContext "\u274c Could not understand."
                val sourceFiles = getFileTree(token, analysis.sourceOwner, analysis.sourceRepo)
                val relevant = sourceFiles.filter { p -> analysis.targetFeatures.any { p.contains(it, true) } }.take(25)
                var created = 0; var modified = 0
                val currentFiles = getFileTree(token, activeOwner, activeRepo)
                for (sourcePath in relevant) {
                    try {
                        val sourceContent = readFileContent(token, analysis.sourceOwner, analysis.sourceRepo, sourcePath) ?: continue
                        val adapted = adaptFileForTargetRepo(key, sourcePath, sourceContent, analysis.sourceOwner, analysis.sourceRepo, activeOwner, activeRepo, instruction, currentFiles) ?: continue
                        val targetPath = determineTargetPath(sourcePath, activeRepo)
                        val encoded = android.util.Base64.encodeToString(adapted.toByteArray(), android.util.Base64.NO_WRAP)
                        if (currentFiles.any { it.equals(targetPath, true) }) {
                            val sha = getFileSha(token, activeOwner, activeRepo, targetPath)
                            if (sha != null && !githubApiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$targetPath", token, """{"message":"Transfer","content":"$encoded","sha":"$sha"}""").startsWith("\u274c")) modified++
                        } else {
                            if (!githubApiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$targetPath", token, """{"message":"Add $targetPath","content":"$encoded"}""").startsWith("\u274c")) created++
                        }
                    } catch (e: Exception) { }
                }
                "\u2705 Transfer complete\n\ud83d\udcc4 Created: $created | \u270f\ufe0f Modified: $modified"
            } catch (e: Exception) { "\u274c ${e.message}" }
        }
    }

    private suspend fun mergeRepositoryFeatures(token: String, key: String, sourceOwner: String, sourceRepo: String): String {
        addProgressMessage("\ud83d\udd04 Merging $sourceOwner/$sourceRepo...")
        return transferFeaturesFromRepo(token, key, "transfer all from $sourceOwner/$sourceRepo")
    }

    // ============================================
    // SECTION 18: GITHUB HELPERS
    // ============================================

    private suspend fun getRepoInfo(token: String, owner: String, repo: String): RepoInfo = withContext(Dispatchers.IO) {
        try { val json = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}"); RepoInfo(json.optString("description"), json.optInt("stargazers_count"), json.optInt("forks_count"), json.optString("language")) } catch (e: Exception) { RepoInfo("", 0, 0, "") }
    }

    private suspend fun getFileTree(token: String, owner: String, repo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            var resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (!resp.isSuccessful) resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/master?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (resp.isSuccessful) { val tree = JSONObject(resp.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext emptyList(); (0 until tree.length()).map { tree.getJSONObject(it).getString("path") }.filter { it !in listOf(".gitignore", "README.md", "LICENSE") } } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun readKeyFilesForAnalysis(token: String, owner: String, repo: String, fileTree: List<String>): Map<String, String> {
        val patterns = listOf("build.gradle", "AndroidManifest", "MainActivity", "ViewModel")
        return fileTree.filter { p -> patterns.any { p.contains(it, true) } }.take(15).mapNotNull { f -> readFileContent(token, owner, repo, f)?.let { f to it.take(3000) } }.toMap()
    }

    private suspend fun readFileContent(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try { val json = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}"); val content = json.optString("content", ""); if (content.isNotBlank()) String(android.util.Base64.decode(content, android.util.Base64.DEFAULT)) else null } catch (e: Exception) { null }
    }

    private suspend fun getFileSha(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try { JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}").optString("sha", null) } catch (e: Exception) { null }
    }

    private suspend fun analyzeRepoWithAI(key: String, repoInfo: RepoInfo, keyFiles: Map<String, String>, fileTree: List<String>): RepoAnalysis {
        val model = GenerativeModel(selectOptimalModel("complex"), key, generationConfig { temperature = 0.2f; maxOutputTokens = 60000 })
        return try {
            val response = model.generateContent(content { text("Analyze repo. Files: ${fileTree.take(30).joinToString()}. Return JSON: {\"architecture\":\"\",\"keyFeatures\":[],\"dependencies\":[]}") }).text
            val text = response ?: return RepoAnalysis("", emptyList(), emptyMap(), emptyList(), emptyMap())
            recordModelUsage(selectOptimalModel("complex"))
            val obj = JSONObject(text.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
            RepoAnalysis(obj.optString("architecture"), (0 until obj.getJSONArray("keyFeatures").length()).map { obj.getJSONArray("keyFeatures").getString(it) }, emptyMap(), (0 until obj.getJSONArray("dependencies").length()).map { obj.getJSONArray("dependencies").getString(it) }, emptyMap())
        } catch (e: Exception) { RepoAnalysis("", emptyList(), emptyMap(), emptyList(), emptyMap()) }
    }

    private suspend fun parseFeatureTransferRequest(key: String, instruction: String): FeatureTransferRequest? {
        val model = GenerativeModel(selectOptimalModel("complex"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
        return try {
            val response = model.generateContent(content { text("Parse: \"$instruction\". Return JSON: {\"sourceOwner\":\"\",\"sourceRepo\":\"\",\"targetFeatures\":[]}") }).text
            val text = response ?: return null
            recordModelUsage(selectOptimalModel("complex"))
            val obj = JSONObject(text.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
            FeatureTransferRequest(obj.optString("sourceOwner"), obj.optString("sourceRepo"), (0 until obj.getJSONArray("targetFeatures").length()).map { obj.getJSONArray("targetFeatures").getString(it) }, "")
        } catch (e: Exception) { null }
    }

    private suspend fun adaptFileForTargetRepo(key: String, sourcePath: String, sourceContent: String, sourceOwner: String, sourceRepo: String, targetOwner: String, targetRepo: String, instruction: String, currentTargetFiles: List<String>): String? {
        val model = GenerativeModel(selectOptimalModel("code_gen"), key, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 })
        return try { model.generateContent(content { text("Adapt code from $sourceOwner/$sourceRepo to $targetOwner/$targetRepo\nPath: $sourcePath\nInstruction: $instruction\nSource:\n```\n$sourceContent\n```\nReturn ONLY adapted code.") }).text } catch (e: Exception) { null }
    }

    private fun determineTargetPath(sourcePath: String, targetRepo: String): String {
        if (sourcePath.contains("src/main/java/")) { val idx = sourcePath.indexOf("src/main/java/") + "src/main/java/".length; return "app/src/main/java/" + sourcePath.substring(idx) }
        if (sourcePath.contains("src/main/res/")) return "app/" + sourcePath
        if (sourcePath.startsWith("app/")) return sourcePath
        val lastSlash = sourcePath.lastIndexOf("/"); val fileName = if (lastSlash >= 0) sourcePath.substring(lastSlash + 1) else sourcePath
        return "app/src/main/java/com/example/" + targetRepo.lowercase() + "/" + fileName
    }

    // ============================================
    // SECTION 19: GITHUB API OPERATIONS
    // ============================================

    private suspend fun githubApiCall(method: String, url: String, token: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").header("Content-Type", "application/json").apply { when (method) { "POST" -> post((body ?: "{}").toRequestBody("application/json".toMediaType())); "PUT" -> put((body ?: "{}").toRequestBody("application/json".toMediaType())) } }.build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "OK"
                if (method == "POST" && url.contains("/user/repos")) "\u2705 Repo created: ${Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)?.groupValues?.get(1) ?: "done"}"
                else if (method == "GET" && url.contains("/user/repos") && !url.contains("/contents")) { val repos = JSONArray(responseBody); if (repos.length() == 0) "\ud83d\udcc1 No repos." else "\ud83d\udcc1 Repos:\n" + (0 until minOf(repos.length(), 10)).joinToString("\n") { "\u2022 ${repos.getJSONObject(it).getString("full_name")}" } }
                else responseBody
            } else "\u274c GitHub API error: ${response.code}"
        } catch (e: Exception) { "\u274c Network error: ${e.message}" }
    }

    private suspend fun triggerWorkflowDispatch(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val listBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: ""
            val workflowId = Regex("\"id\"\\s*:\\s*(\\d+)").find(listBody)?.groupValues?.get(1) ?: return@withContext "\u274c No workflows."
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "\ud83d\ude80 Build triggered!" else "\u26a0\ufe0f Failed"
        } catch (e: Exception) { "\u274c Error: ${e.message}" }
    }

    private suspend fun browseRepositoryContents(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (!resp.isSuccessful) return@withContext "\u274c Not found."
            val tree = JSONObject(resp.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext "\ud83d\udcc1 Empty."
            val files = (0 until minOf(tree.length(), 100)).map { tree.getJSONObject(it).getString("path") }
            "\ud83d\udcc1 $owner/$repo (${tree.length()} items):\n" + files.take(50).joinToString("\n") { "  \ud83d\udcc4 $it" }
        } catch (e: Exception) { "\u274c Error: ${e.message}" }
    }

    private suspend fun readRepoFileContents(token: String, owner: String, repo: String, path: String): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            val content = json.optString("content", ""); if (content.isBlank()) return@withContext "\ud83d\udcc4 Empty"
            val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
            if (decoded.length > 3000) "\ud83d\udcc4 $path (${decoded.length} chars):\n\n${decoded.take(3000)}\n\n..." else "\ud83d\udcc4 $path:\n\n$decoded"
        } catch (e: Exception) { "\u274c Error: ${e.message}" }
    }

    private suspend fun repairFileInRepo(token: String, key: String, owner: String, repo: String, path: String, instruction: String): String = withContext(Dispatchers.IO) {
        try {
            val readJson = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            val currentContent = String(android.util.Base64.decode(readJson.getString("content"), android.util.Base64.DEFAULT))
            val sha = readJson.getString("sha")
            val model = GenerativeModel(selectOptimalModel("debug"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
            val newContent = model.generateContent(content { text("Fix file.\nCURRENT:\n```\n$currentContent\n```\nINSTRUCTION: $instruction\nReturn ONLY fixed code.") }).text ?: return@withContext "\u274c Empty"
            recordModelUsage(selectOptimalModel("debug"))
            val encoded = android.util.Base64.encodeToString(newContent.toByteArray(), android.util.Base64.NO_WRAP)
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").put("""{"message":"Fix: $instruction","content":"$encoded","sha":"$sha"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "\u2705 Fixed $path" else "\u274c Update failed"
        } catch (e: Exception) { "\u274c Error: ${e.message}" }
    }

    private suspend fun createFileInRepo(token: String, key: String, owner: String, repo: String, path: String, description: String): String = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel(selectOptimalModel("code_gen"), key, generationConfig { temperature = 0.2f; maxOutputTokens = 60000 })
            val content = model.generateContent(content { text("Create: $path. Description: $description. Return ONLY file content.") }).text ?: return@withContext "\u274c Empty"
            recordModelUsage(selectOptimalModel("code_gen"))
            val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").put("""{"message":"Add $path","content":"$encoded"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "\u2705 Created $path" else "\u274c Failed"
        } catch (e: Exception) { "\u274c Error: ${e.message}" }
    }

    // ============================================
    // SECTION 20: ACCESSIBILITY HELPERS
    // ============================================

    private fun performTapOnText(service: AuraAccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findAccessibilityNode(root, text) ?: return false.also { root.recycle() }
        val rect = android.graphics.Rect(); node.getBoundsInScreen(rect); root.recycle(); node.recycle()
        val path = android.graphics.Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
        service.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)).build(), null, null)
        return true
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

    private fun performSwipe(service: AuraAccessibilityService, right: Boolean) {
        val d = service.resources.displayMetrics
        val path = if (right) android.graphics.Path().apply { moveTo(d.widthPixels*0.2f, d.heightPixels/2f); lineTo(d.widthPixels*0.8f, d.heightPixels/2f) } else android.graphics.Path().apply { moveTo(d.widthPixels*0.8f, d.heightPixels/2f); lineTo(d.widthPixels*0.2f, d.heightPixels/2f) }
        service.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)).build(), null, null)
    }

    private fun findAccessibilityNode(node: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.text?.contains(text, true) == true || node.contentDescription?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) { node.getChild(i)?.let { findAccessibilityNode(it, text)?.let { return it } } }
        return null
    }

    // ============================================
    // SECTION 21: UTILITY FUNCTIONS
    // ============================================

    private fun addProgressMessage(text: String) { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(text, false), generationProgress = text) }
    private suspend fun saveMessage(text: String, isUser: Boolean, modelUsed: String? = null) { _currentSessionId.value?.let { sessionDb.messageDao().insertMessage(MessageEntity(UUID.randomUUID().toString(), it, text, isUser, modelUsed)) } }

    private fun loadSessions() { viewModelScope.launch { sessionDb.sessionDao().getAllSessions().collect { _sessions.value = it; if (_currentSessionId.value == null && it.isNotEmpty()) switchSession(it.first().id) } } }
    private fun loadModelUsage() { viewModelScope.launch { sessionDb.modelUsageDao().getAllModelUsage().collect { _modelUsage.value = it } } }
    private suspend fun resetDailyCountersIfNeeded() { sessionDb.modelUsageDao().resetDailyCounters(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }

    private fun resolveAppPackage(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; "camera" -> "com.android.camera"; "gmail" -> "com.google.android.gm"; "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"; "calculator" -> "com.android.calculator2"; "calendar" -> "com.android.calendar"; "clock" -> "com.android.deskclock"; "files" -> "com.android.documentsui"; "phone" -> "com.android.dialer"; "messages" -> "com.google.android.apps.messaging"; "instagram" -> "com.instagram.android"; "facebook" -> "com.facebook.katana"; "twitter" -> "com.twitter.android"; "spotify" -> "com.spotify.music"; "netflix" -> "com.netflix.mediaclient"; "telegram" -> "org.telegram.messenger"; else -> null
    }

    private fun getRamUsage(): String {
        val am = com.aura.ai.AuraApplication.instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        return "${(mi.totalMem-mi.availMem)/(1024*1024*1024)}GB/${mi.totalMem/(1024*1024*1024)}GB"
    }

    private fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        return "${stat.availableBlocksLong*stat.blockSizeLong/(1024*1024*1024)}GB/${stat.blockCountLong*stat.blockSizeLong/(1024*1024*1024)}GB"
    }

    private fun getBatteryLevel(): String = try { val bm = com.aura.ai.AuraApplication.instance.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%" } catch (e: Exception) { "Unknown" }

    private fun recursiveFileSearch(dir: File, query: String, results: MutableList<String>, depth: Int) {
        if (depth < 0 || results.size >= 50) return
        try { dir.listFiles()?.forEach { if (it.name.contains(query, true)) results.add(it.absolutePath); if (it.isDirectory && results.size < 50) recursiveFileSearch(it, query, results, depth-1) } } catch (e: Exception) { }
    }

    private fun formatFileSize(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024*1024 -> "${bytes/1024} KB"; bytes < 1024*1024*1024 -> "${bytes/(1024*1024)} MB"; else -> "${bytes/(1024*1024*1024)} GB" }
}
