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
// DATA CLASSES
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
    val totalFixesApplied: Int = 0
)

enum class BuildStatus {
    IDLE, BUILDING, SUCCESS, ANALYZING_ERROR, FIXING, RETRYING, FAILED
}

data class AgentUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("""
╔══════════════════════════════════════════╗
║     ⚡ AURA AI - NEURAL CORE ACTIVE ⚡     ║
╠══════════════════════════════════════════╣
║                                          ║
║  📱 PHONE CONTROL                        ║
║  open [app] • home • back • recents     ║
║  screenshot • notifications             ║
║  scroll down/up • tap on [text]         ║
║  type [text] • swipe left/right         ║
║                                          ║
║  💻 APP GENERATION (Autonomous)          ║
║  create app [name] [description]        ║
║  → Self-healing build loop (20 retries) ║
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
║                                          ║
║  📊 SYSTEM                               ║
║  device info • time                     ║
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
    val generationProgress: String = ""
)

enum class ExecutionMode {
    IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION, FILE_OPERATION, REPO_ANALYSIS, FEATURE_TRANSFER
}

// ============================================
// INTERNAL DATA CLASSES
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
// VIEWMODEL
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

    // ===== SESSION MANAGEMENT =====
    private val sessionDb by lazy {
        SessionDatabase.getInstance(com.aura.ai.AuraApplication.instance)
    }
    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    private val _modelUsage = MutableStateFlow<List<ModelUsageEntity>>(emptyList())
    private var sessionLoadingJob: Job? = null

    init {
    init {
    loadSessions()
    loadModelUsage()
    loadPreferredModel()
    viewModelScope.launch {
        resetDailyCountersIfNeeded()
    }
    }

    // ============================================
    // SMART MODEL SELECTION SYSTEM
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
    private var totalApiCalls = 0

    private fun classifyTask(input: String): String {
        val lower = input.lowercase().trim()
        return when {
            lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app") -> "code_gen"
            lower.startsWith("fix") || lower.startsWith("debug") || lower.startsWith("refactor") -> "debug"
            lower.startsWith("analyze repo") || lower.startsWith("study repo") -> "complex"
            lower.startsWith("transfer") || lower.startsWith("merge repo") -> "complex"
            lower.startsWith("explain") || lower.startsWith("analyze") || lower.startsWith("review") -> "complex"
            input.length > 400 -> "complex"
            input.length < 60 -> "chat"
            else -> "general"
        }
    }

    private fun selectOptimalModel(taskType: String, input: String): String {
        if (_state.value.manualModelSelected) return _state.value.activeModel
        if (consecutiveFailures >= 3) {
            return taskModelPriority["high_volume"]!!.first { !isModelInCooldown(it) }
        }
        val candidates = taskModelPriority[taskType] ?: taskModelPriority["general"]!!
        for (model in candidates) {
            if (!isModelInCooldown(model) && !isNearDailyLimit(model)) return model
        }
        for (model in taskModelPriority["high_volume"]!!) {
            if (!isModelInCooldown(model)) return model
        }
        return "gemini-2.0-flash-lite"
    }

    private fun isModelInCooldown(model: String): Boolean {
        return modelCooldowns[model]?.let { System.currentTimeMillis() < it } ?: false
    }

    private fun isNearDailyLimit(model: String): Boolean {
        val usage = modelDailyUsage[model] ?: 0
        val limit = modelRegistry[model]?.rpd ?: 1000
        return usage >= (limit * 0.75).toInt()
    }

    private fun recordModelUsage(model: String) {
        modelDailyUsage[model] = (modelDailyUsage[model] ?: 0) + 1
        totalApiCalls++
        viewModelScope.launch {
            val existing = sessionDb.modelUsageDao().getModelUsage(model)
            val updated = ModelUsageEntity(
                modelName = model,
                dailyRequests = (existing?.dailyRequests ?: 0) + 1,
                dailyLimit = modelRegistry[model]?.rpd ?: 1500,
                strength = modelRegistry[model]?.description ?: ""
            )
            sessionDb.modelUsageDao().insertOrUpdateModelUsage(updated)
        }
    }

    private fun applyModelCooldown(model: String) {
        consecutiveFailures++
        val duration = when {
            consecutiveFailures == 1 -> 10_000L
            consecutiveFailures == 2 -> 25_000L
            consecutiveFailures == 3 -> 60_000L
            else -> 120_000L
        }
        modelCooldowns[model] = System.currentTimeMillis() + duration
    }

    private fun resetFailureState() { consecutiveFailures = 0 }

    // ============================================
    // SESSION MANAGEMENT
    // ============================================

    private fun loadPreferredModel() {
        preferences.getPreferredModel()?.let { model ->
            _state.value = _state.value.copy(activeModel = model, manualModelSelected = true)
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            sessionDb.sessionDao().getAllSessions().collect { sessionList ->
                _sessions.value = sessionList
                if (_currentSessionId.value == null && sessionList.isNotEmpty()) {
                    switchSession(sessionList.first().id)
                } else if (sessionList.isEmpty()) {
                    createNewSession()
                }
            }
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val session = SessionEntity(
                id = UUID.randomUUID().toString(),
                title = "New Session",
                selectedModel = _state.value.activeModel
            )
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
                _state.value = _state.value.copy(
                    messages = if (chatMessages.isEmpty()) _state.value.messages else chatMessages,
                    currentSessionId = sessionId,
                    manualModelSelected = true,
                    activeModel = session.selectedModel,
                    buildLoop = null,
                    isGeneratingApp = false
                )
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
                if (remaining.isNotEmpty()) switchSession(remaining.first().id)
                else createNewSession()
            }
        }
    }

    private suspend fun saveMessage(text: String, isUser: Boolean, modelUsed: String? = null) {
        val sessionId = _currentSessionId.value ?: return
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            text = text,
            isUser = isUser,
            modelUsed = modelUsed
        )
        sessionDb.messageDao().insertMessage(message)
        val session = sessionDb.sessionDao().getSession(sessionId)
        if (session?.title == "New Session" && isUser) {
            val title = if (text.length > 30) text.take(30) + "..." else text
            sessionDb.sessionDao().updateSession(sessionId, System.currentTimeMillis(), title)
        }
    }

    private fun addProgressMessage(text: String) {
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage(text, false),
            generationProgress = text
        )
    }

    // ============================================
    // MODEL MANAGEMENT
    // ============================================

    private fun loadModelUsage() {
        viewModelScope.launch {
            sessionDb.modelUsageDao().getAllModelUsage().collect { usage ->
                _modelUsage.value = usage
            }
        }
    }

    private suspend fun resetDailyCountersIfNeeded() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        sessionDb.modelUsageDao().resetDailyCounters(today)
    }

    fun toggleModelDashboard() {
        _state.value = _state.value.copy(showModelDashboard = !_state.value.showModelDashboard)
    }

    fun selectModel(modelName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                activeModel = modelName,
                manualModelSelected = true,
                showModelDashboard = false
            )
            _currentSessionId.value?.let { sessionId ->
                sessionDb.sessionDao().updateSelectedModel(sessionId, modelName)
            }
            preferences.setPreferredModel(modelName)
        }
    }

    fun getModelInfoList(): List<ModelInfo> {
        return modelRegistry.map { (name, spec) ->
            val usage = _modelUsage.value.find { it.modelName == name }
            ModelInfo(
                name = name,
                displayName = name.replace("gemini-", "").replace("-", " ").uppercase(),
                strength = spec.description,
                dailyRequests = usage?.dailyRequests ?: 0,
                dailyLimit = spec.rpd,
                isInCooldown = isModelInCooldown(name),
                isSelected = name == _state.value.activeModel
            )
        }
    }

    fun toggleDrawer() {
        _state.value = _state.value.copy(showDrawer = !_state.value.showDrawer)
    }

    // ============================================
    // PUBLIC INTERFACE
    // ============================================

    fun updateInput(text: String) {
        _state.value = _state.value.copy(input = text)
    }

    fun send() {
        val msg = _state.value.input.trim()
        if (msg.isBlank()) return
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage(msg, true),
            input = "",
            loading = true
        )
        if (handleControlCommand(msg)) return

        taskJob = viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, currentTask = msg)
            saveMessage(msg, isUser = true)
            val result = executeCommandPipeline(msg)
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage(result, false),
                loading = false,
                isExecuting = false,
                currentTask = "",
                executionMode = ExecutionMode.IDLE
            )
            saveMessage(result, isUser = false, modelUsed = _state.value.activeModel)
        }
    }

    // ============================================
    // COMMAND PIPELINE
    // ============================================

    private suspend fun executeCommandPipeline(input: String): String {
        return executePhoneCommand(input)
            ?: executeGitHubCommand(input)
            ?: executeFileCommand(input)
            ?: executeSystemCommand(input)
            ?: executeGeminiChat(input)
    }

    // ============================================
    // CONTROL COMMANDS
    // ============================================

    private fun handleControlCommand(input: String): Boolean {
        return when (input.lowercase().trim()) {
            "pause", "pause task" -> {
                if (_state.value.isExecuting) {
                    isPaused = true
                    taskJob?.cancel()
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + ChatMessage("⏸️ Task paused. Type 'resume' to continue.", false),
                        loading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + ChatMessage("ℹ️ No active task to pause.", false),
                        loading = false
                    )
                }
                true
            }
            "resume", "resume task" -> {
                if (isPaused) {
                    isPaused = false
                    _state.value = _state.value.copy(loading = true)
                    send()
                } else {
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + ChatMessage("ℹ️ No paused task to resume.", false),
                        loading = false
                    )
                }
                true
            }
            "stop", "cancel", "stop task" -> {
                taskJob?.cancel()
                isPaused = false
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatMessage("⏹️ Task stopped.", false),
                    loading = false,
                    isExecuting = false,
                    currentTask = "",
                    executionMode = ExecutionMode.IDLE,
                    isGeneratingApp = false,
                    buildLoop = null
                )
                true
            }
            else -> false
        }
    }

    // ============================================
    // PHONE CONTROL
    // ============================================

    private suspend fun executePhoneCommand(input: String): String? {
        val lower = input.lowercase().trim()
        val service = AuraAccessibilityService.instance ?: return null
        _state.value = _state.value.copy(executionMode = ExecutionMode.PHONE_CONTROL)
        return when {
            lower.startsWith("open ") -> {
                val appName = lower.removePrefix("open ").trim()
                val pkg = resolveAppPackage(appName)
                if (pkg != null) {
                    try {
                        val intent = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            com.aura.ai.AuraApplication.instance.startActivity(intent)
                            "✅ Opened $appName"
                        } else "❌ Could not open $appName"
                    } catch (e: Exception) { "❌ Error: ${e.message}" }
                } else "❌ Unknown app: $appName"
            }
            lower == "home" -> { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); "🏠 Home" }
            lower == "back" -> { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); "⬅️ Back" }
            lower == "recents" -> { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); "📱 Recent apps" }
            lower == "notifications" -> { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); "🔔 Notifications" }
            lower == "quick settings" -> { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); "⚙️ Quick settings" }
            lower == "screenshot" -> { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); "📸 Screenshot taken" }
            lower == "scroll down" -> { performScroll(service, false); "👇 Scrolled down" }
            lower == "scroll up" -> { performScroll(service, true); "👆 Scrolled up" }
            lower.startsWith("tap on ") -> {
                val target = lower.removePrefix("tap on ").trim()
                if (performTapOnText(service, target)) "👆 Tapped '$target'" else "❌ Could not find '$target'"
            }
            lower.startsWith("type ") -> {
                val text = input.removePrefix("type ").trim()
                if (performTypeText(service, text)) "⌨️ Typed successfully" else "❌ Could not type"
            }
            lower == "swipe left" -> { performSwipe(service, false); "👈 Swiped left" }
            lower == "swipe right" -> { performSwipe(service, true); "👉 Swiped right" }
            else -> null
        }
    }

    // ============================================
    // GITHUB COMMANDS (with Repo Analysis & Transfer)
    // ============================================

    private suspend fun executeGitHubCommand(input: String): String? {
        val token = preferences.getGitHubToken()
        if (token.isNullOrBlank()) return null
        val apiKey = preferences.getApiKey()
        if (apiKey.isNullOrBlank() && input.lowercase().contains("create app")) {
            return "❌ No Gemini API key set. Add it in Protocol settings."
        }
        val key = apiKey ?: ""
        val lower = input.lowercase().trim()
        _state.value = _state.value.copy(executionMode = ExecutionMode.GITHUB_OPERATION)

        return when {
            // App Generation
            (lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) && !lower.contains("repo") -> {
                val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
                val appName = appDesc.split(" ").firstOrNull()?.replace(" ", "-")?.take(50) ?: "MyApp"
                val description = if (appDesc.split(" ").size > 1) appDesc.substringAfter(" ").trim() else "A simple application"
                _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP)
                createFullApplication(token, key, appName, description)
            }
            // Create Repo
            lower.contains("create") && lower.contains("repo") -> {
                val name = input.replace(Regex("(?i)(create|a|repo|repository|github)"), "").trim().replace(" ", "-").take(50)
                if (name.isBlank()) "❌ Please specify a repository name."
                else githubApiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$name","private":false,"auto_init":true}""")
            }
            // List Repos
            lower.contains("list") && lower.contains("repo") -> {
                githubApiCall("GET", "https://api.github.com/user/repos?per_page=10&sort=updated", token, null)
            }
            // Compile/Build
            lower.startsWith("compile ") || lower.startsWith("build ") -> {
                val repo = lower.removePrefix("compile ").removePrefix("build ").trim()
                val parts = repo.split("/")
                if (parts.size != 2) "❌ Format: compile repo owner/repo"
                else triggerWorkflowDispatch(token, parts[0], parts[1])
            }
            // Browse Repo
            (lower.startsWith("browse repo ") || lower.startsWith("explore repo ")) -> {
                val repo = lower.removePrefix("browse repo ").removePrefix("explore repo ").trim()
                val parts = repo.split("/")
                if (parts.size != 2) "❌ Format: browse repo owner/repo"
                else browseRepositoryContents(token, parts[0], parts[1])
            }
            // Read Repo File
            lower.startsWith("read repo file ") -> {
                val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" ")
                if (parts.size < 2) "❌ Format: read repo file owner/repo path"
                else {
                    val repoParts = parts[0].split("/")
                    if (repoParts.size != 2) "❌ Format: read repo file owner/repo path"
                    else readRepoFileContents(token, repoParts[0], repoParts[1], parts.drop(1).joinToString(" "))
                }
            }
            // Fix File
            lower.startsWith("fix ") || lower.startsWith("fix file ") || lower.startsWith("edit ") -> {
                val remaining = input.replace(Regex("(?i)(fix|fix file|edit|update) "), "")
                val filePath = remaining.substringBefore(":").trim()
                val instruction = remaining.substringAfter(":").trim()
                if (filePath.isBlank() || instruction.isBlank()) "❌ Usage: fix file path/to/file.kt: change button color to red"
                else if (activeRepo.isBlank()) "❌ No active repo. Use 'set repo owner/repo' first."
                else if (key.isBlank()) "❌ No Gemini API key set."
                else repairFileInRepo(token, key, activeOwner, activeRepo, filePath, instruction)
            }
            // Add File
            lower.startsWith("add file ") || lower.startsWith("create file ") -> {
                val remaining = input.replace(Regex("(?i)(add|create) file "), "")
                val filePath = remaining.substringBefore(":").trim()
                val description = remaining.substringAfter(":").trim()
                if (filePath.isBlank() || description.isBlank()) "❌ Usage: add file path/to/file.kt: a login screen"
                else if (activeRepo.isBlank()) "❌ No active repo."
                else if (key.isBlank()) "❌ No Gemini API key set."
                else createFileInRepo(token, key, activeOwner, activeRepo, filePath, description)
            }
            // Set Active Repo
            lower.startsWith("set repo ") || lower.startsWith("switch to ") -> {
                val repo = lower.removePrefix("set repo ").removePrefix("switch to ").trim()
                val parts = repo.split("/")
                if (parts.size != 2) "❌ Format: set repo owner/repo"
                else { activeOwner = parts[0]; activeRepo = parts[1]; "✅ Active repo: $activeOwner/$activeRepo" }
            }
            // ===== NEW: REPO ANALYSIS & FEATURE TRANSFER =====
            // Analyze Repo
            lower.startsWith("analyze repo ") || lower.startsWith("study repo ") -> {
                val repo = input.replace(Regex("(?i)(analyze|study) repo "), "").trim()
                val parts = repo.split("/")
                if (parts.size != 2) "❌ Format: analyze repo owner/repo"
                else {
                    _state.value = _state.value.copy(executionMode = ExecutionMode.REPO_ANALYSIS)
                    analyzePublicRepo(token, key, parts[0], parts[1])
                }
            }
            // Transfer Features
            lower.startsWith("transfer ") || lower.startsWith("port ") || lower.startsWith("add feature ") -> {
                val instruction = input.replace(Regex("(?i)(transfer|port|add feature) "), "")
                if (activeRepo.isBlank()) "❌ No active repo. Use 'set repo owner/repo' first."
                else if (key.isBlank()) "❌ No Gemini API key set."
                else {
                    _state.value = _state.value.copy(executionMode = ExecutionMode.FEATURE_TRANSFER)
                    transferFeaturesFromRepo(token, key, instruction)
                }
            }
            // Merge Repo
            lower.startsWith("merge repo ") || lower.startsWith("clone features from ") -> {
                val sourceRepo = input.replace(Regex("(?i)(merge repo|clone features from) "), "").trim()
                val parts = sourceRepo.split("/")
                if (parts.size != 2) "❌ Format: merge repo owner/repo"
                else if (activeRepo.isBlank()) "❌ No active repo. Use 'set repo owner/repo' first."
                else {
                    _state.value = _state.value.copy(executionMode = ExecutionMode.FEATURE_TRANSFER)
                    mergeRepositoryFeatures(token, key, parts[0], parts[1])
                }
            }
            else -> null
        }
    }

    // ============================================
    // FILE COMMANDS
    // ============================================

    private fun executeFileCommand(input: String): String? {
        val lower = input.lowercase().trim()
        _state.value = _state.value.copy(executionMode = ExecutionMode.FILE_OPERATION)
        return when {
            lower.startsWith("list files") -> {
                val path = input.replace(Regex("(?i)list files"), "").trim().ifBlank {
                    Environment.getExternalStorageDirectory().absolutePath
                }
                try {
                    val files = File(path).listFiles()?.take(40)
                    if (files.isNullOrEmpty()) "📁 Empty directory."
                    else "📁 $path:\n" + files.joinToString("\n") {
                        "${if (it.isDirectory) "📁" else "📄"} ${it.name} (${formatFileSize(it.length())})"
                    }
                } catch (e: Exception) { "❌ Error: ${e.message}" }
            }
            lower.startsWith("search files") -> {
                val query = input.replace(Regex("(?i)search files"), "").trim()
                if (query.isBlank()) "❌ What should I search for?"
                else {
                    try {
                        val results = mutableListOf<String>()
                        recursiveFileSearch(File(Environment.getExternalStorageDirectory().absolutePath), query, results, 4)
                        if (results.isEmpty()) "🔍 No files found matching '$query'"
                        else "🔍 Found ${results.size} files:\n" + results.take(25).joinToString("\n") { "📄 $it" }
                    } catch (e: Exception) { "❌ Error: ${e.message}" }
                }
            }
            lower.startsWith("delete file") -> {
                val path = input.replace(Regex("(?i)delete file"), "").trim()
                if (path.isBlank()) "❌ Which file should I delete?"
                else {
                    try {
                        val file = File(path)
                        if (file.exists()) { file.delete(); "✅ Deleted: ${file.name}" }
                        else "❌ File not found: $path"
                    } catch (e: Exception) { "❌ Error: ${e.message}" }
                }
            }
            lower.startsWith("read file") || lower.startsWith("show file") -> {
                val path = input.replace(Regex("(?i)(read|show) file"), "").trim()
                if (path.isBlank()) "❌ Usage: read file /path/to/file.txt"
                else {
                    try {
                        val content = File(path).readText()
                        if (content.length > 2500) "📄 $path (${content.length} chars):\n\n${content.take(2500)}\n\n..."
                        else "📄 $path (${content.length} chars):\n\n$content"
                    } catch (e: Exception) { "❌ Error: ${e.message}" }
                }
            }
            else -> null
        }
    }

    // ============================================
    // SYSTEM COMMANDS
    // ============================================

    private fun executeSystemCommand(input: String): String? {
        val lower = input.lowercase().trim()
        return when {
            lower == "device info" || lower == "system info" -> {
                val ram = getRamUsage()
                val storage = getStorageInfo()
                val battery = getBatteryLevel()
                "📱 Device Info:\n• Model: ${Build.MODEL}\n• Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n• RAM: $ram\n• Storage: $storage\n• Battery: $battery\n• CPU: ${Runtime.getRuntime().availableProcessors()} cores\n• App: Aura AI v2.0.0"
            }
            lower == "time" || lower == "what time is it" -> {
                "🕐 ${SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm:ss z", Locale.getDefault()).format(Date())}"
            }
            else -> null
        }
    }

    // ============================================
    // GEMINI CHAT
    // ============================================

    private suspend fun executeGeminiChat(input: String): String {
        val key = preferences.getApiKey()
        if (key.isNullOrBlank()) return "❌ No Gemini API key set."
        _state.value = _state.value.copy(executionMode = ExecutionMode.CHATTING)
        val taskType = classifyTask(input)
        val modelName = selectOptimalModel(taskType, input)
        _state.value = _state.value.copy(activeModel = modelName)
        return try {
            val model = GenerativeModel(
                modelName = modelName, apiKey = key,
                generationConfig = generationConfig {
                    temperature = when (taskType) { "code_gen", "debug" -> 0.15f; "complex" -> 0.25f; else -> 0.7f }
                    topK = when (taskType) { "code_gen", "debug" -> 50; else -> 40 }
                    topP = 0.95f
                    maxOutputTokens = when (taskType) { "code_gen" -> 32768; "debug" -> 16384; "complex" -> 8192; else -> 4096 }
                }
            )
            val response = model.generateContent(content { text(input) }).text ?: "No response generated."
            recordModelUsage(modelName)
            resetFailureState()
            response
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("503") || errorMsg.contains("429") || errorMsg.contains("UNAVAILABLE") ||
                errorMsg.contains("quota") || errorMsg.contains("rate") || errorMsg.contains("RESOURCE_EXHAUSTED")) {
                applyModelCooldown(modelName)
                if (_state.value.manualModelSelected) {
                    "⚠️ Selected model '$modelName' is rate-limited. Please choose another model."
                } else {
                    val fallbackModel = selectOptimalModel("high_volume", input)
                    if (fallbackModel != modelName) {
                        try {
                            val fb = GenerativeModel(modelName = fallbackModel, apiKey = key,
                                generationConfig = generationConfig { temperature = 0.7f; maxOutputTokens = 2048 })
                            val fbResponse = fb.generateContent(content { text(input) }).text ?: "No response."
                            recordModelUsage(fallbackModel)
                            resetFailureState()
                            "🔄 (Switched to $fallbackModel)\n\n$fbResponse"
                        } catch (e2: Exception) {
                            applyModelCooldown(fallbackModel)
                            "❌ All models temporarily unavailable."
                        }
                    } else "⚠️ Current model at capacity."
                }
            } else "❌ Error: ${errorMsg}"
        }
    }

    // ========================================================================
    // AUTONOMOUS APP GENERATION WITH SELF-HEALING BUILD LOOP
    // ========================================================================

    private suspend fun createFullApplication(
        token: String, key: String, appName: String, description: String
    ): String {
        _state.value = _state.value.copy(isGeneratingApp = true, generationProgress = "🚀 Starting autonomous app generation")
        try {
            addProgressMessage("🧠 Phase 1/5: Deep analysis and architecture planning...")
            val architecture = planAppArchitecture(key, appName, description)
            if (architecture.files.isEmpty()) return "❌ Architecture planning failed."
            addProgressMessage("📋 Planned ${architecture.files.size} files - ${architecture.techStack}")
            addProgressMessage("📦 Dependencies: ${architecture.dependencies.take(5).joinToString(", ")}...")
            
            addProgressMessage("📁 Phase 2/5: Creating GitHub repository...")
            val createResult = githubApiCall("POST", "https://api.github.com/user/repos", token,
                """{"name":"$appName","private":false,"auto_init":false}""")
            if (createResult.startsWith("❌")) { _state.value = _state.value.copy(isGeneratingApp = false); return "❌ $createResult" }
            
            val userResult = githubApiCall("GET", "https://api.github.com/user", token, null)
            val owner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(userResult)?.groupValues?.get(1) ?: return "❌ Could not determine GitHub username."
            activeOwner = owner; activeRepo = appName
            
            addProgressMessage("⚙️ Phase 3/5: Generating ${architecture.files.size} files with context awareness...")
            val generatedFiles = generateAllFilesWithContext(key, appName, description, architecture)
            if (generatedFiles.isEmpty()) { _state.value = _state.value.copy(isGeneratingApp = false); return "❌ File generation failed." }
            addProgressMessage("📝 Generated ${generatedFiles.size} files successfully")
            
            addProgressMessage("📤 Phase 4/5: Pushing files to GitHub...")
            var pushedCount = 0
            generatedFiles.forEach { (path, content) ->
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val result = githubApiCall("PUT",
                    "https://api.github.com/repos/$owner/$appName/contents/$path", token,
                    """{"message":"Add $path","content":"$encoded"}""")
                if (!result.startsWith("❌")) pushedCount++
            }
            addWorkflowFile(token, owner, appName, appName)
            addProgressMessage("✅ Pushed $pushedCount/${generatedFiles.size} files")
            
            addProgressMessage("🔄 Phase 5/5: Autonomous build verification with self-healing...")
            val buildResult = executeBuildLoop(token, key, owner, appName)
            _state.value = _state.value.copy(isGeneratingApp = false, executionMode = ExecutionMode.IDLE)
            return buildResult
        } catch (e: Exception) {
            _state.value = _state.value.copy(isGeneratingApp = false)
            return "❌ Generation failed: ${e.message}"
        }
    }

    private suspend fun planAppArchitecture(key: String, appName: String, description: String): AppArchitecture {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 8192 })
        val prompt = """
Plan a COMPLETE, production-ready Android app.
App: $appName | Description: $description
Return JSON: {"files":["path.kt",...], "techStack":"...", "dependencies":["..."], "structure":"..."}
Include ALL files needed for compilation. Return ONLY valid JSON.
        """.trimIndent()
        return try {
            val response = model.generateContent(content { text(prompt) }).text ?: return AppArchitecture(emptyList(), "", emptyList(), "")
            recordModelUsage("gemini-2.5-flash")
            val jsonStr = response.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
            val obj = JSONObject(jsonStr)
            AppArchitecture(
                (0 until obj.getJSONArray("files").length()).map { obj.getJSONArray("files").getString(it) },
                obj.optString("techStack", ""),
                (0 until obj.getJSONArray("dependencies").length()).map { obj.getJSONArray("dependencies").getString(it) },
                obj.optString("structure", "")
            )
        } catch (e: Exception) {
    val fallbackFiles = generateProjectFileList(key, appName, description)
    AppArchitecture(
        files = fallbackFiles,
        techStack = "Standard Android",
        dependencies = emptyList(),
        structure = "Basic"
    )
        }

    private suspend fun generateAllFilesWithContext(
        key: String, appName: String, description: String,
        fileList: List<String>, architecture: AppArchitecture
    ): Map<String, String> {
        val allFiles = mutableMapOf<String, String>()
        val batches = fileList.chunked(50)
        for ((index, batch) in batches.withIndex()) {
            val batchLabel = "${index + 1}/${batches.size}"
            addProgressMessage("📝 Batch $batchLabel: Generating ${batch.size} files...")
            val existingSummary = if (allFiles.isNotEmpty()) "ALREADY GENERATED (${allFiles.size} files):\n${allFiles.keys.take(10).joinToString("\n") { "  ✅ $it" }}" else "First batch."
            val model = GenerativeModel(selectOptimalModel("code_gen", "batch $batchLabel"), key,
                generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
            val prompt = """
Generate COMPLETE code for: "$appName" | $description | Stack: ${architecture.techStack}
$existingSummary
FILES: ${batch.joinToString("\n") { "  📝 $it" }}
RULES: Complete code only, no TODOs, package: com.example.${appName.lowercase().replace("-", "")}
Return JSON: {"files":[{"path":"path.kt","content":"code"}]}
            """.trimIndent()
            try {
                val response = model.generateContent(content { text(prompt) }).text ?: continue
                recordModelUsage(model.modelName)
                val jsonStr = response.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
                val filesArr = JSONObject(jsonStr).getJSONArray("files")
                for (i in 0 until filesArr.length()) {
                    val f = filesArr.getJSONObject(i)
                    allFiles[f.getString("path")] = f.getString("content").replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"")
                }
            } catch (e: Exception) { addProgressMessage("⚠️ Batch $batchLabel partial: ${e.message}") }
        }
        return allFiles
    }

    // ============================================
    // SELF-HEALING BUILD LOOP
    // ============================================

    private suspend fun executeBuildLoop(token: String, key: String, owner: String, repo: String): String {
        val maxAttempts = 20; var attempt = 0; var totalFixes = 0
        _state.value = _state.value.copy(buildLoop = BuildLoopState(maxAttempts = maxAttempts))
        addProgressMessage("🔨 Triggering initial build...")
        var currentRunId = triggerWorkflowAndGetRunId(token, owner, repo) ?: return "⚠️ Files pushed. Use 'compile repo $owner/$repo' manually."

        while (attempt < maxAttempts) {
            attempt++
            _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(attemptNumber = attempt, buildStatus = BuildStatus.BUILDING, workflowRunId = currentRunId))
            addProgressMessage("🔨 Build $attempt/$maxAttempts...")
            
            when (val result = waitForWorkflowCompletion(token, owner, repo, currentRunId)) {
                is WorkflowResult.Success -> {
                    _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(buildStatus = BuildStatus.SUCCESS, buildUrl = "https://github.com/$owner/$repo/actions/runs/$currentRunId"))
                    val artifactUrl = getArtifactDownloadUrl(token, owner, repo, currentRunId)
                    return "✅ BUILD SUCCESSFUL!\n📱 $repo | github.com/$owner/$repo\n🔄 Attempts: $attempt | 🔧 Fixes: $totalFixes\n${if (artifactUrl != null) "📥 APK: $artifactUrl" else ""}"
                }
                is WorkflowResult.Failure -> {
                    _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(buildStatus = BuildStatus.ANALYZING_ERROR, errorSummary = result.error.take(500)))
                    addProgressMessage("❌ Failed. Analyzing errors...")
                    val fixPlan = analyzeBuildError(key, result.error, result.logs)
                    if (fixPlan == null) { currentRunId = retriggerBuild(token, owner, repo) ?: break; continue }
                    _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(buildStatus = BuildStatus.FIXING, lastFixDescription = fixPlan.summary))
                    addProgressMessage("🔧 Fixing: ${fixPlan.summary}")
                    if (applyBuildFixes(token, key, owner, repo, fixPlan)) {
                        totalFixes++
                        _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(totalFixesApplied = totalFixes, buildStatus = BuildStatus.RETRYING))
                        addProgressMessage("✅ Fixes applied. Retrying...")
                    }
                    currentRunId = retriggerBuild(token, owner, repo) ?: break
                }
            }
        }
        _state.value = _state.value.copy(buildLoop = _state.value.buildLoop?.copy(buildStatus = BuildStatus.FAILED))
        return "⚠️ BUILD LOOP EXHAUSTED ($maxAttempts attempts)\n📱 $repo | github.com/$owner/$repo\n🔧 Fixes: $totalFixes\nUse 'fix file [path]: [instruction]' to manually fix."
    }

    private suspend fun triggerWorkflowAndGetRunId(token: String, owner: String, repo: String): Long? = withContext(Dispatchers.IO) {
        try {
            val listBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: return@withContext null
            val workflowId = Regex("\"id\"\\s*:\\s*(\\d+)").find(listBody)?.groupValues?.get(1) ?: return@withContext null
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
                    val body = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: return@withContext null
                    Pair(Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1), Regex("\"conclusion\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1))
                } catch (e: Exception) { null }
            }
            if (status?.first == "completed") {
                return if (status.second == "success") WorkflowResult.Success
                else WorkflowResult.Failure(extractKeyErrors(fetchWorkflowLogs(token, owner, repo, runId)), fetchWorkflowLogs(token, owner, repo, runId))
            }
        }
        return WorkflowResult.Failure("Build timed out", "")
    }

    private suspend fun fetchWorkflowLogs(token: String, owner: String, repo: String, runId: Long): String = withContext(Dispatchers.IO) {
        try { client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/logs").header("Authorization", "Bearer $token").build()).execute().body?.string()?.take(10000) ?: "" } catch (e: Exception) { "" }
    }

    private fun extractKeyErrors(logs: String): String {
        val patterns = listOf(Regex("error:.*", RegexOption.IGNORE_CASE), Regex("FAILURE:.*"), Regex("Unresolved reference.*"), Regex(".*not found.*"))
        val errors = mutableListOf<String>()
        patterns.forEach { p -> p.findAll(logs).forEach { errors.add(it.value) } }
        return if (errors.isEmpty()) logs.take(2000) else errors.take(20).joinToString("\n")
    }

    private suspend fun analyzeBuildError(key: String, error: String, fullLogs: String): FixPlan? {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.1f; maxOutputTokens = 8192 })
        return try {
            val response = model.generateContent(content { text("Analyze this build error and create fix plan.\nERROR: $error\nLOGS: ${fullLogs.take(5000)}\nReturn JSON: {\"summary\":\"...\",\"fixes\":[{\"file\":\"path\",\"instruction\":\"fix\"}]}") }).text ?: return null
            recordModelUsage("gemini-2.5-flash")
            val jsonStr = response.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
            val obj = JSONObject(jsonStr)
            FixPlan(obj.getString("summary"), (0 until obj.getJSONArray("fixes").length()).map { val f = obj.getJSONArray("fixes").getJSONObject(it); Pair(f.getString("file"), f.getString("instruction")) })
        } catch (e: Exception) { null }
    }

    private suspend fun applyBuildFixes(token: String, key: String, owner: String, repo: String, plan: FixPlan): Boolean {
        var success = true
        plan.fileFixes.forEach { (file, instruction) -> if (repairFileInRepo(token, key, owner, repo, file, instruction).startsWith("❌")) success = false }
        return success
    }

    private suspend fun retriggerBuild(token: String, owner: String, repo: String) = triggerWorkflowAndGetRunId(token, owner, repo)
    private suspend fun getArtifactDownloadUrl(token: String, owner: String, repo: String, runId: Long): String? = withContext(Dispatchers.IO) {
        try { Regex("\"archive_download_url\"\\s*:\\s*\"([^\"]+)\"").find(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "")?.groupValues?.get(1) } catch (e: Exception) { null }
    }

    private suspend fun addWorkflowFile(token: String, owner: String, repo: String, appName: String) {
        val yaml = "name: Build $appName\non: [push, workflow_dispatch]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - uses: actions/setup-java@v4\n        with: {java-version: '17', distribution: 'temurin'}\n      - uses: gradle/actions/setup-gradle@v3\n      - run: chmod +x gradlew\n      - run: ./gradlew assembleDebug\n      - uses: actions/upload-artifact@v4\n        with: {name: ${appName}-debug, path: app/build/outputs/apk/debug/app-debug.apk}"
        githubApiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/.github/workflows/build.yml", token, """{"message":"Add CI workflow","content":"${android.util.Base64.encodeToString(yaml.toByteArray(), android.util.Base64.NO_WRAP)}"}""")
    }

    // ============================================
    // CROSS-REPO FEATURE TRANSFER ENGINE
    // ============================================

    private suspend fun analyzePublicRepo(token: String, key: String, owner: String, repo: String): String {
        addProgressMessage("🔍 Analyzing $owner/$repo...")
        return withContext(Dispatchers.IO) {
            try {
                val repoInfo = getRepoInfo(token, owner, repo)
                val fileTree = getFileTree(token, owner, repo)
                if (fileTree.isEmpty()) return@withContext "❌ Could not read repository structure."
                addProgressMessage("📁 Found ${fileTree.size} files. Deep analyzing...")
                val keyFiles = readKeyFilesForAnalysis(token, owner, repo, fileTree)
                val analysis = analyzeRepoWithAI(key, repoInfo, keyFiles, fileTree)
                buildString {
                    append("📊 ANALYSIS: $owner/$repo\n\n📝 ${repoInfo.description}\n⭐ ${repoInfo.stars} | 🍴 ${repoInfo.forks} | 💻 ${repoInfo.language}\n\n🏗️ ${analysis.architecture}\n\n🔑 Features:\n${analysis.keyFeatures.joinToString("\n") { "  • $it" }}\n\n📦 Dependencies:\n${analysis.dependencies.take(10).joinToString("\n") { "  • $it" }}\n\n📁 Structure (${fileTree.size} files):\n${fileTree.take(15).joinToString("\n") { "  📄 $it" }}${if (fileTree.size > 15) "\n  ... ${fileTree.size - 15} more" else ""}\n\n💡 transfer [feature] from $owner/$repo | merge repo $owner/$repo")
                }
            } catch (e: Exception) { "❌ Analysis failed: ${e.message}" }
        }
    }
        private suspend fun transferFeaturesFromRepo(
        token: String, key: String, instruction: String
    ): String {
        if (activeOwner.isBlank() || activeRepo.isBlank()) return "❌ Set active repo first."
        addProgressMessage("🧠 Understanding transfer: $instruction")
        return withContext(Dispatchers.IO) {
            try {
                val analysis = parseFeatureTransferRequest(key, instruction) ?: return@withContext "❌ Could not understand request."
                addProgressMessage("📁 Analyzing source: ${analysis.sourceOwner}/${analysis.sourceRepo}")
                val sourceFiles = getFileTree(token, analysis.sourceOwner, analysis.sourceRepo)
                val relevantFiles = sourceFiles.filter { p -> 
                    analysis.targetFeatures.any { p.lowercase().contains(it.lowercase()) } || 
                    p.lowercase().contains(analysis.sourceRepo.lowercase()) 
                }
                addProgressMessage("📋 Found ${relevantFiles.size} relevant files. Adapting...")
                val currentFiles = getFileTree(token, activeOwner, activeRepo)
                var created = 0
                var modified = 0
                for (sourcePath in relevantFiles.take(25)) {
                    try {
                        val sourceContent = readFileContent(token, analysis.sourceOwner, analysis.sourceRepo, sourcePath) ?: continue
                        val adaptedFile = adaptFileForTargetRepo(key, sourcePath, sourceContent, analysis.sourceOwner, analysis.sourceRepo, activeOwner, activeRepo, instruction, currentFiles) ?: continue
                        val targetPath = determineTargetPath(sourcePath, activeRepo)
                        val encoded = android.util.Base64.encodeToString(adaptedFile.toByteArray(), android.util.Base64.NO_WRAP)
                        val exists = currentFiles.any { it.equals(targetPath, true) }
                        if (exists) {
                            val sha = getFileSha(token, activeOwner, activeRepo, targetPath)
                            if (sha != null) {
                                if (!githubApiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$targetPath", token, """{"message":"Transfer: ${analysis.sourceRepo} - $instruction","content":"$encoded","sha":"$sha"}""").startsWith("❌")) modified++
                            }
                        } else {
                            if (!githubApiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$targetPath", token, """{"message":"Added: ${analysis.sourceRepo} - $targetPath","content":"$encoded"}""").startsWith("❌")) created++
                        }
                    } catch (e: Exception) { continue }
                }
                "✅ TRANSFER COMPLETE\n📥 ${analysis.sourceOwner}/${analysis.sourceRepo}\n📤 $activeOwner/$activeRepo\n📄 Created: $created | ✏️ Modified: $modified\n• compile repo $activeOwner/$activeRepo"
            } catch (e: Exception) { "❌ Transfer failed: ${e.message}" }
        }
    }

    private suspend fun mergeRepositoryFeatures(token: String, key: String, sourceOwner: String, sourceRepo: String): String {
        addProgressMessage("🔄 Merging $sourceOwner/$sourceRepo...")
        analyzePublicRepo(token, key, sourceOwner, sourceRepo)
        return transferFeaturesFromRepo(token, key, "transfer all features from $sourceOwner/$sourceRepo")
    }

    // ============================================
    // GITHUB HELPERS
    // ============================================

    private suspend fun getRepoInfo(token: String, owner: String, repo: String): RepoInfo = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            RepoInfo(json.optString("description", "No description"), json.optInt("stargazers_count", 0), json.optInt("forks_count", 0), json.optString("language", "Unknown"))
        } catch (e: Exception) { RepoInfo("Error", 0, 0, "Unknown") }
    }

    private suspend fun getFileTree(token: String, owner: String, repo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            var response = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (!response.isSuccessful) response = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/master?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val tree = json.optJSONArray("tree") ?: return@withContext emptyList()
                (0 until tree.length()).map { tree.getJSONObject(it).getString("path") }.filter { it !in listOf(".gitignore", "README.md", "LICENSE") }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun readKeyFilesForAnalysis(token: String, owner: String, repo: String, fileTree: List<String>): Map<String, String> {
        val keyPatterns = listOf("build.gradle", "AndroidManifest.xml", "MainActivity", "Application", "ViewModel", "Repository", "Model", "Entity", "Dao", "Database")
        val keyFiles = fileTree.filter { p -> keyPatterns.any { p.contains(it, true) } }.take(15)
        val contents = mutableMapOf<String, String>()
        keyFiles.forEach { f -> try { readFileContent(token, owner, repo, f)?.let { contents[f] = it.take(3000) } } catch (e: Exception) {} }
        return contents
    }

    private suspend fun readFileContent(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            val content = json.optString("content", "")
            if (content.isNotBlank()) String(android.util.Base64.decode(content, android.util.Base64.DEFAULT)) else null
        } catch (e: Exception) { null }
    }

    private suspend fun getFileSha(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try { JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}").optString("sha", null) } catch (e: Exception) { null }
    }

    // ============================================
    // AI ANALYSIS HELPERS
    // ============================================

    private suspend fun analyzeRepoWithAI(key: String, repoInfo: RepoInfo, keyFiles: Map<String, String>, fileTree: List<String>): RepoAnalysis {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 8192 })
        val filesSummary = keyFiles.entries.joinToString("\n") { (p, c) -> "FILE: $p\n```\n${c.take(1500)}\n```\n" }
        return try {
            val response = model.generateContent(content { text("Analyze this Android repo.\nREPO: ${repoInfo.description}\nFILES (${fileTree.size}):\n${fileTree.take(30).joinToString("\n")}\nKEY FILES:\n$filesSummary\nReturn JSON: {\"architecture\":\"...\",\"keyFeatures\":[\"...\"],\"dependencies\":[\"...\"],\"coreLogic\":{\"file.kt\":\"desc\"}}") }).text ?: RepoAnalysis("Unknown", emptyList(), emptyMap(), emptyList(), emptyMap())
            recordModelUsage("gemini-2.5-flash")
            val obj = JSONObject(response.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
            RepoAnalysis(
                obj.optString("architecture", ""),
                (0 until obj.getJSONArray("keyFeatures").length()).map { obj.getJSONArray("keyFeatures").getString(it) },
                fileTree.associateWith { "File" },
                (0 until obj.getJSONArray("dependencies").length()).map { obj.getJSONArray("dependencies").getString(it) },
                emptyMap()
            )
        } catch (e: Exception) { RepoAnalysis("Analysis failed", emptyList(), emptyMap(), emptyList(), emptyMap()) }
    }

    private suspend fun parseFeatureTransferRequest(key: String, instruction: String): FeatureTransferRequest? {
        val model = GenerativeModel("gemini-3.1-flash-lite", key, generationConfig { temperature = 0.1f; maxOutputTokens = 2048 })
        return try {
            val response = model.generateContent(content { text("Parse: \"$instruction\"\nActive: $activeOwner/$activeRepo\nReturn JSON: {\"sourceOwner\":\"...\",\"sourceRepo\":\"...\",\"targetFeatures\":[\"...\"],\"additionalContext\":\"...\"}") }).text ?: return null
            recordModelUsage("gemini-3.1-flash-lite")
            val obj = JSONObject(response.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
            FeatureTransferRequest(
                obj.optString("sourceOwner"),
                obj.optString("sourceRepo"),
                (0 until obj.getJSONArray("targetFeatures").length()).map { obj.getJSONArray("targetFeatures").getString(it) },
                obj.optString("additionalContext")
            )
        } catch (e: Exception) { null }
    }

    private suspend fun adaptFileForTargetRepo(
        key: String, sourcePath: String, sourceContent: String,
        sourceOwner: String, sourceRepo: String, targetOwner: String, targetRepo: String,
        instruction: String, currentTargetFiles: List<String>
    ): String? {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.15f; maxOutputTokens = 16384 })
        return try {
            val response = model.generateContent(content { text("Adapt code.\nSOURCE: $sourceOwner/$sourceRepo\nTARGET: $targetOwner/$targetRepo\nPATH: $sourcePath\nINSTRUCTION: $instruction\nTARGET FILES:\n${currentTargetFiles.take(20).joinToString("\n")}\nSOURCE CODE:\n```\n$sourceContent\n```\nReturn ONLY adapted code.") }).text
            recordModelUsage("gemini-2.5-flash")
            response
        } catch (e: Exception) { null }
    }

    private fun determineTargetPath(sourcePath: String, targetRepo: String): String {
        return when {
            sourcePath.contains("src/main/java/") -> "app/src/main/java/" + sourcePath.substringAfter("src/main/java/")
            sourcePath.contains("src/main/res/") -> "app/$sourcePath"
            sourcePath.startsWith("app/") -> sourcePath
            else -> "app/src/main/java/com/example/${targetRepo.lowercase()}/" + sourcePath.substringAfterLast("/")
        }
    }

    // ============================================
    // GITHUB API OPERATIONS
    // ============================================

    private suspend fun githubApiCall(method: String, url: String, token: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .apply {
                    when (method) {
                        "POST" -> post((body ?: "{}").toRequestBody("application/json".toMediaType()))
                        "PUT" -> put((body ?: "{}").toRequestBody("application/json".toMediaType()))
                    }
                }.build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "OK"
                when {
                    method == "POST" && url.contains("/user/repos") -> "✅ Repository created: ${Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)?.groupValues?.get(1) ?: "created"}"
                    method == "GET" && url.contains("/user/repos") && !url.contains("/contents") -> {
                        val repos = JSONArray(responseBody)
                        if (repos.length() == 0) "📁 No repos."
                        else "📁 Repos:\n" + (0 until minOf(repos.length(), 10)).joinToString("\n") { "• ${repos.getJSONObject(it).getString("full_name")}" }
                    }
                    else -> responseBody
                }
            } else "❌ GitHub API error: ${response.code}"
        } catch (e: Exception) { "❌ Network error: ${e.message}" }
    }

    private suspend fun triggerWorkflowDispatch(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val listBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: ""
            val workflowId = Regex("\"id\"\\s*:\\s*(\\d+)").find(listBody)?.groupValues?.get(1) ?: return@withContext "❌ No workflows found."
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "🚀 Build triggered!" else "⚠️ Build trigger failed"
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

    private suspend fun browseRepositoryContents(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (!response.isSuccessful) return@withContext "❌ Not found."
            val tree = JSONObject(response.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext "📁 Empty."
            val files = (0 until minOf(tree.length(), 100)).map { tree.getJSONObject(it).getString("path") }
            "📁 $owner/$repo (${tree.length()} items):\n" + files.take(50).joinToString("\n") { "  📄 $it" } + if (files.size > 50) "\n  ... more" else ""
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

    private suspend fun readRepoFileContents(token: String, owner: String, repo: String, path: String): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            val content = json.optString("content", "")
            if (content.isBlank()) return@withContext "📄 Empty"
            val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
            if (decoded.length > 3000) "📄 $path (${decoded.length} chars):\n\n${decoded.take(3000)}\n\n..." else "📄 $path:\n\n$decoded"
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

    private suspend fun repairFileInRepo(token: String, key: String, owner: String, repo: String, path: String, instruction: String): String = withContext(Dispatchers.IO) {
        try {
            val readJson = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            val currentContent = String(android.util.Base64.decode(readJson.getString("content"), android.util.Base64.DEFAULT))
            val sha = readJson.getString("sha")
            val model = GenerativeModel(selectOptimalModel("debug", "fix $path"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 16384 })
            val newContent = model.generateContent(content { text("Fix file.\nCURRENT:\n```\n$currentContent\n```\nINSTRUCTION: $instruction\nReturn ONLY fixed code.") }).text ?: return@withContext "❌ Empty"
            recordModelUsage(model.modelName)
            val encoded = android.util.Base64.encodeToString(newContent.toByteArray(), android.util.Base64.NO_WRAP)
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").put("""{"message":"Fix: $instruction","content":"$encoded","sha":"$sha"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "✅ Fixed $path" else "❌ Update failed"
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

    private suspend fun createFileInRepo(token: String, key: String, owner: String, repo: String, path: String, description: String): String = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel(selectOptimalModel("code_gen", "create $path"), key, generationConfig { temperature = 0.2f; maxOutputTokens = 8192 })
            val content = model.generateContent(content { text("Create: $path. Description: $description. Return ONLY file content.") }).text ?: return@withContext "❌ Empty"
            recordModelUsage(model.modelName)
            val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").put("""{"message":"Add $path","content":"$encoded"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "✅ Created $path" else "❌ Failed"
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

    // ============================================
    // ACCESSIBILITY HELPERS
    // ============================================

    private fun performTapOnText(service: AuraAccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findAccessibilityNode(root, text)
        return if (node != null) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            root.recycle()
            node.recycle()
            service.dispatchGesture(
                android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                        android.graphics.Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }, 0, 100))
                    .build(), null, null
            )
            true
        } else {
            root.recycle()
            false
        }
    }

    private fun performTypeText(service: AuraAccessibilityService, text: String): Boolean {
        val focused = service.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        return result
    }

    private fun performScroll(service: AuraAccessibilityService, up: Boolean) {
        val d = service.resources.displayMetrics
        val path = if (up) {
            android.graphics.Path().apply { moveTo(d.widthPixels/2f, d.heightPixels*0.3f); lineTo(d.widthPixels/2f, d.heightPixels*0.8f) }
        } else {
            android.graphics.Path().apply { moveTo(d.widthPixels/2f, d.heightPixels*0.8f); lineTo(d.widthPixels/2f, d.heightPixels*0.3f) }
        }
        service.dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300))
                .build(), null, null
        )
    }

    private fun performSwipe(service: AuraAccessibilityService, right: Boolean) {
        val d = service.resources.displayMetrics
        val path = if (right) {
            android.graphics.Path().apply { moveTo(d.widthPixels*0.2f, d.heightPixels/2f); lineTo(d.widthPixels*0.8f, d.heightPixels/2f) }
        } else {
            android.graphics.Path().apply { moveTo(d.widthPixels*0.8f, d.heightPixels/2f); lineTo(d.widthPixels*0.2f, d.heightPixels/2f) }
        }
        service.dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300))
                .build(), null, null
        )
    }

    private fun findAccessibilityNode(node: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.text?.contains(text, true) == true || node.contentDescription?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findAccessibilityNode(it, text)?.let { return it } }
        }
        return null
    }

    // ============================================
    // UTILITY FUNCTIONS
    // ============================================

    private fun resolveAppPackage(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"
        "settings" -> "com.android.settings"; "camera" -> "com.android.camera"
        "gallery", "photos" -> "com.google.android.apps.photos"; "gmail" -> "com.google.android.gm"
        "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"
        else -> null
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

    private fun getBatteryLevel(): String = try {
        val bm = com.aura.ai.AuraApplication.instance.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
    } catch (e: Exception) { "Unknown" }

    private fun recursiveFileSearch(dir: File, query: String, results: MutableList<String>, depth: Int) {
        if (depth < 0 || results.size >= 50) return
        try {
            dir.listFiles()?.forEach { file ->
                if (file.name.contains(query, true)) results.add(file.absolutePath)
                if (file.isDirectory && results.size < 50) recursiveFileSearch(file, query, results, depth-1)
            }
        } catch (e: Exception) { }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024*1024 -> "${bytes/1024} KB"
        bytes < 1024*1024*1024 -> "${bytes/(1024*1024)} MB"
        else -> "${bytes/(1024*1024*1024)} GB"
    }

    private suspend fun generateProjectFileList(key: String, appName: String, description: String): List<String> {
        val model = GenerativeModel(selectOptimalModel("code_gen", appName), key, generationConfig { temperature = 0.15f; maxOutputTokens = 4096 })
        return try {
            val response = model.generateContent(content { text("Generate file list for: $appName - $description. Return JSON array.") }).text ?: return emptyList()
            recordModelUsage(model.modelName)
            val jsonStr = response.substringAfter("[").substringBeforeLast("]").let { "[$it]" }
            (0 until JSONArray(jsonStr).length()).map { JSONArray(jsonStr).getString(it) }
        } catch (e: Exception) { emptyList() }
    }
  }                        
}
