package com.aura.ai.presentation.screens.agent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
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
import com.aura.ai.services.AuraForegroundService
import com.aura.ai.services.AppController
import com.aura.ai.services.BuildTemplates
import com.aura.ai.services.CodespacesManager
import com.aura.ai.services.ZipProcessor
import com.google.ai.client.generativeai.Chat
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

// ═══════════════════════════════════════════════════════════════════
// SECTION 1: PUBLIC DATA CLASSES
// ═══════════════════════════════════════════════════════════════════

data class ChatMessage(val text: String, val isUser: Boolean, val isStreaming: Boolean = false)

data class ModelInfo(
    val name: String, val displayName: String, val strength: String,
    val dailyRequests: Int, val dailyLimit: Int, val isInCooldown: Boolean, val isSelected: Boolean
)

data class BuildLoopState(
    val attemptNumber: Int = 0, val maxAttempts: Int = 20,
    val buildStatus: BuildStatus = BuildStatus.IDLE, val workflowRunId: Long? = null,
    val errorSummary: String = "", val lastFixDescription: String = "",
    val buildUrl: String = "", val totalFixesApplied: Int = 0, val artifactUrl: String? = null
)

enum class BuildStatus {
    IDLE, BUILDING, WAITING_FOR_BUILD, BUILD_SUCCESS, ANALYZING_ERROR,
    FIXING, RETRYING, FAILED, DOWNLOADING_ARTIFACT, VALIDATING
}

data class AgentUiState(
    val messages: List<ChatMessage> = listOf(ChatMessage("AURA AI - NEURAL CORE ACTIVE", false)),
    val input: String = "", val loading: Boolean = false, val isExecuting: Boolean = false,
    val currentTask: String = "", val executionMode: ExecutionMode = ExecutionMode.IDLE,
    val activeModel: String = "gemini-3.5-flash", val showDrawer: Boolean = false,
    val showModelDashboard: Boolean = false, val manualModelSelected: Boolean = false,
    val currentSessionId: String? = null, val buildLoop: BuildLoopState? = null,
    val isGeneratingApp: Boolean = false, val generationProgress: String = "",
    val codespaceMode: Boolean = false, val activeCodespaceId: String? = null,
    val codespaceStatus: String = "", val pendingBatchFiles: Map<String, String> = emptyMap(),
    val isOnline: Boolean = true, val totalApiCalls: Int = 0,
    val attachedFileUri: android.net.Uri? = null, val attachedFileName: String = "",
    val isAutonomousMode: Boolean = false
)

enum class ExecutionMode {
    IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION,
    FILE_OPERATION, REPO_ANALYSIS, FEATURE_TRANSFER, CODESPACE_GENERATION,
    BATCH_FILE_PUSH, IMAGE_ANALYSIS, FILE_UPLOAD, STREAMING_CHAT,
    APP_CONTROL, ZIP_PROCESSING, CONTEXT_COMPRESSION, AUTONOMOUS
}

// ═══════════════════════════════════════════════════════════════════
// SECTION 2: INTERNAL DATA CLASSES
// ═══════════════════════════════════════════════════════════════════

private data class AppArchitecture(
    val files: List<String>, val techStack: String,
    val dependencies: List<String>, val structure: String
)

private data class FixPlan(val summary: String, val fileFixes: List<Pair<String, String>>)
private data class RepoInfo(val description: String, val stars: Int, val forks: Int, val language: String)
private data class RepoAnalysis(val architecture: String, val keyFeatures: List<String>, val fileStructure: Map<String, String>, val dependencies: List<String>, val coreLogic: Map<String, String>)
private data class FeatureTransferRequest(val sourceOwner: String, val sourceRepo: String, val targetFeatures: List<String>, val additionalContext: String)
private data class QueuedCommand(val id: String, val command: String, val timestamp: Long)

private sealed class WorkflowResult {
    data object Success : WorkflowResult()
    data class Failure(val error: String, val logs: String) : WorkflowResult()
    data object Timeout : WorkflowResult()
}

// ═══════════════════════════════════════════════════════════════════
// SECTION 3: VIEWMODEL CLASS
// ═══════════════════════════════════════════════════════════════════

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val preferences: AuraPreferences
) : ViewModel() {

    // ═══════════════════════════════════════════
    // SECTION 3.1: STATE MANAGEMENT
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
    private val commandQueue = mutableListOf<QueuedCommand>()
    private var contextCompressionPending = false
    private var heartbeatJob: Job? = null
    private var lastHeartbeat = System.currentTimeMillis()
    private var screenshotInterval = 30_000L

    // ═══════════════════════════════════════════
    // SECTION 3.2: SESSION MANAGEMENT
    // ═══════════════════════════════════════════

    private val sessionDb by lazy { SessionDatabase.getInstance(com.aura.ai.AuraApplication.instance) }
    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    private val _modelUsage = MutableStateFlow<List<ModelUsageEntity>>(emptyList())
    private var sessionLoadingJob: Job? = null
    private val chatSessions = mutableMapOf<String, Chat>()

    init {
        loadSessions()
        loadModelUsage()
        loadPreferredModel()
        viewModelScope.launch {
            resetDailyCountersIfNeeded()
            val lastId = preferences.getLastSessionId()
            if (lastId != null) switchSession(lastId)
            else if (_sessions.value.isEmpty()) createNewSession()
        }
        _state.value = _state.value.copy(isOnline = isNetworkAvailable())
        _state.value = _state.value.copy(totalApiCalls = preferences.getTotalApiCalls())
    }

    // ═══════════════════════════════════════════
    // SECTION 3.3: MODEL REGISTRY (Updated June 2025)
    // ═══════════════════════════════════════════

    private data class ModelSpec(val rpd: Int, val rpm: Int, val tpm: Int, val description: String)

    private val modelRegistry = linkedMapOf(
        "gemini-3.5-flash" to ModelSpec(1500, 15, 1_000_000, "Latest - Fastest & most capable"),
        "gemini-3.1-flash-lite" to ModelSpec(1500, 30, 1_000_000, "Speed king - Highest RPM"),
        "gemini-2.5-flash" to ModelSpec(1500, 15, 1_000_000, "Balanced - Best for debugging & code"),
        "gemini-2.5-flash-lite" to ModelSpec(1500, 30, 1_000_000, "Fast & efficient - High volume"),
        "gemini-2.0-flash" to ModelSpec(1500, 15, 1_000_000, "Legacy - Stable & reliable"),
        "gemini-embedding-2" to ModelSpec(1500, 1500, 10_000_000, "Vector embeddings - Unlimited RPD")
    )

    private val taskModelPriority = mapOf(
        "chat" to listOf("gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite"),
        "code_gen" to listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-3.1-flash-lite"),
        "debug" to listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-2.5-flash-lite"),
        "complex" to listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-2.0-flash"),
        "high_volume" to listOf("gemini-2.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.0-flash"),
        "general" to listOf("gemini-3.1-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash"),
        "embedding" to listOf("gemini-embedding-2")
    )

    private val modelCooldowns = mutableMapOf<String, Long>()
    private val modelDailyUsage = mutableMapOf<String, Int>()
    private var consecutiveFailures = 0

    private fun selectOptimalModel(taskType: String): String {
        if (_state.value.manualModelSelected) return _state.value.activeModel
        if (consecutiveFailures >= 3) return taskModelPriority["high_volume"]!!.first { !isModelInCooldown(it) }
        val candidates = taskModelPriority[taskType] ?: taskModelPriority["general"]!!
        for (model in candidates) { if (!isModelInCooldown(model) && !isNearDailyLimit(model)) return model }
        return "gemini-2.0-flash"
    }

    private fun isModelInCooldown(model: String) = modelCooldowns[model]?.let { System.currentTimeMillis() < it } ?: false

    private fun isNearDailyLimit(model: String): Boolean {
        val usage = modelDailyUsage[model] ?: 0
        val limit = modelRegistry[model]?.rpd ?: 1500
        return usage >= (limit * 0.80).toInt()
    }

    private fun recordModelUsage(model: String) {
        modelDailyUsage[model] = (modelDailyUsage[model] ?: 0) + 1
        val newTotal = preferences.getTotalApiCalls() + 1
        preferences.setTotalApiCalls(newTotal)
        _state.value = _state.value.copy(totalApiCalls = newTotal)
        viewModelScope.launch {
            val existing = sessionDb.modelUsageDao().getModelUsage(model)
            sessionDb.modelUsageDao().insertOrUpdateModelUsage(
                ModelUsageEntity(model, (existing?.dailyRequests ?: 0) + 1, modelRegistry[model]?.rpd ?: 1500, "", 0, modelRegistry[model]?.description ?: "")
            )
        }
    }

    private fun applyModelCooldown(model: String) { consecutiveFailures++; modelCooldowns[model] = System.currentTimeMillis() + 60000 }
    private fun resetFailureState() { consecutiveFailures = 0 }

    // ═══════════════════════════════════════════
    // SECTION 3.4: NETWORK & QUEUE
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
    // SECTION 3.5: PUBLIC INTERFACE
    // ═══════════════════════════════════════════

    fun updateInput(text: String) { _state.value = _state.value.copy(input = text) }
    fun toggleDrawer() { _state.value = _state.value.copy(showDrawer = !_state.value.showDrawer) }
    fun toggleModelDashboard() { _state.value = _state.value.copy(showModelDashboard = !_state.value.showModelDashboard) }

    fun attachFile(uri: android.net.Uri, fileName: String) {
        _state.value = _state.value.copy(attachedFileUri = uri, attachedFileName = fileName)
    }

    fun clearAttachment() { _state.value = _state.value.copy(attachedFileUri = null, attachedFileName = "") }

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
            ModelInfo(name = name, displayName = formatModelName(name), strength = spec.description, dailyRequests = usage?.dailyRequests ?: 0, dailyLimit = spec.rpd, isInCooldown = isModelInCooldown(name), isSelected = name == _state.value.activeModel)
        }
    }

    private fun formatModelName(name: String): String = when (name) {
        "gemini-3.5-flash" -> "3.5 Flash ⚡"; "gemini-3.1-flash-lite" -> "3.1 Flash-Lite 🚀"
        "gemini-2.5-flash" -> "2.5 Flash 💎"; "gemini-2.5-flash-lite" -> "2.5 Flash-Lite ⚡"
        "gemini-2.0-flash" -> "2.0 Flash 📦"; "gemini-embedding-2" -> "Embedding 2 🔍"
        else -> name.replace("gemini-", "").replace("-", " ").uppercase()
    }

    fun createNewSession() {
        chatSessions.clear()
        viewModelScope.launch {
            val s = SessionEntity(id = UUID.randomUUID().toString(), title = "New Session", selectedModel = _state.value.activeModel)
            sessionDb.sessionDao().insertSession(s); switchSession(s.id)
        }
    }

    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            sessionLoadingJob?.cancel()
            sessionLoadingJob = viewModelScope.launch {
                val s = sessionDb.sessionDao().getSession(sessionId) ?: return@launch
                _currentSessionId.value = sessionId; preferences.setLastSessionId(sessionId)
                val msgs = sessionDb.messageDao().getMessagesForSessionOnce(sessionId)
                _state.value = _state.value.copy(messages = if (msgs.isEmpty()) _state.value.messages else msgs.map { ChatMessage(it.text, it.isUser) }, currentSessionId = sessionId, manualModelSelected = true, activeModel = s.selectedModel, buildLoop = null, isGeneratingApp = false)
                sessionDb.sessionDao().updateSession(sessionId, System.currentTimeMillis(), s.title)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        chatSessions.remove(sessionId)
        viewModelScope.launch {
            sessionDb.sessionDao().deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
                val r = _sessions.value.filter { it.id != sessionId }
                if (r.isNotEmpty()) switchSession(r.first().id) else createNewSession()
            }
        }
    }

    fun send() {
        val msg = _state.value.input.trim()
        if (msg.isBlank()) return
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(msg, true), input = "", loading = true)
        if (handleControl(msg)) return
        if (!isNetworkAvailable()) {
            commandQueue.add(QueuedCommand(UUID.randomUUID().toString(), msg, System.currentTimeMillis()))
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("📶 Offline - Queued.", false), loading = false)
            return
        }
        taskJob = viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, currentTask = msg)
            saveMsg(msg, true)
            val result = execute(msg)
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(result, false), loading = false, isExecuting = false, currentTask = "", executionMode = ExecutionMode.IDLE)
            saveMsg(result, false, modelUsed = _state.value.activeModel)
        }
    }

    private fun handleControl(input: String): Boolean = when (input.lowercase().trim()) {
        "stop", "cancel" -> { taskJob?.cancel(); _state.value = _state.value.copy(loading = false, isExecuting = false, isGeneratingApp = false, buildLoop = null); true }
        "queue" -> { processQueue(); true }
        else -> false
    }

    // ═══════════════════════════════════════════
    // SECTION 3.6: COMMAND ROUTER
    // ═══════════════════════════════════════════

    private suspend fun execute(input: String): String {
        val lower = input.lowercase().trim()
        if (lower == "device info") return "📱 ${Build.MODEL}\n🤖 ${Build.VERSION.RELEASE}\n💾 ${getRamUsage()}\n🔋 ${getBatteryLevel()}"
        if (lower == "time") return "🕐 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
        if (lower == "models" || lower == "model limits") return getModelLimits()
        if (lower == "context status" || lower == "context") return getContextStatus()
        if (lower == "compress yes" || lower == "compress") return compressContextWindow()
        if (lower == "compress no") { contextCompressionPending = false; return "✅ Continuing without compression." }
        if (lower == "heartbeat" || lower == "status") return checkHeartbeat()
        if (lower == "progress" || lower == "what are you doing") return getProgress()
        if (lower == "start autonomous" || lower == "auto mode on") { startAutonomousMode(); return "🤖 Autonomous mode activated." }
        if (lower == "stop autonomous" || lower == "auto mode off") { stopAutonomousMode(); return "🔴 Autonomous mode stopped." }
        if (lower.startsWith("open ")) { val app = lower.removePrefix("open ").trim(); val pkg = resolveApp(app) ?: return "❌ Unknown app"; return try { val i = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(pkg); i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); com.aura.ai.AuraApplication.instance.startActivity(i); "✅ Opened $app" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower == "home") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "🏠 Home" }
        if (lower == "back") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "⬅️ Back" }
        if (lower == "screenshot") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "📸 Screenshot taken" }
        if (lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) { if (lower.contains("repo")) return githubCommand(input) ?: "❌ No GitHub token."; _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP); return createApp(input) }
        if (lower.startsWith("continue ")) { _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP); return continueApp(input) }
        if (lower.startsWith("codespace ")) { return codespaceCommand(lower) }
        if (lower.startsWith("process zip") || lower.startsWith("deploy zip")) { return processZip() }
        if (lower.startsWith("debug with ") || lower.startsWith("ask ")) { return debugWithApp(lower) }
        if (lower.startsWith("control ")) { return controlApp(input) }
        if (lower.startsWith("send to ")) { return sendToApp(input) }
        if (lower.startsWith("analyze screen") || lower.startsWith("what's on screen")) { return analyzeScreenCmd(lower) }
        if (lower.startsWith("ask gemini app") || lower.startsWith("gemini native")) { return askGeminiApp(input) }
        return githubCommand(input) ?: chatWithGemini(input)
    }

    // ═══════════════════════════════════════════
    // SECTION 3.7: MODEL LIMITS DISPLAY
    // ═══════════════════════════════════════════

    private fun getModelLimits(): String {
        val sb = StringBuilder("📊 MODEL LIMITS (Free Tier):\n\n")
        for ((name, spec) in modelRegistry) {
            val used = modelDailyUsage[name] ?: 0
            val bar = generateUsageBar(used, spec.rpd)
            sb.append("${formatModelName(name)}: $used/${spec.rpd} req/day $bar\n")
        }
        sb.append("\n💡 Context: 1M input / 65K output per request")
        return sb.toString()
    }

    private fun generateUsageBar(used: Int, limit: Int): String {
        val percent = (used.toFloat() / limit * 10).toInt().coerceIn(0, 10)
        val filled = "█".repeat(percent); val empty = "░".repeat(10 - percent)
        return "$filled$empty ${(used.toFloat() / limit * 100).toInt()}%"
    }

    // ═══════════════════════════════════════════
    // SECTION 3.8: CONTEXT WINDOW COMPRESSION
    // ═══════════════════════════════════════════

    private fun getContextStatus(): String {
        val estimated = estimateContextTokens(); val percent = estimated / 10000
        return "📊 Context: ~${estimated}/1,000,000 tokens (${percent}% used)\n💡 ${100 - percent}% remaining"
    }

    private suspend fun checkContextAndWarn() {
        val estimatedTokens = estimateContextTokens()
        if (estimatedTokens > 850_000 && !contextCompressionPending) {
            contextCompressionPending = true
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⚠️ Context window at ~${estimatedTokens / 10000}%.\nCompress to free space? Type 'compress yes' or 'compress no'", false))
            }
        }
    }

    private fun estimateContextTokens(): Long {
        var totalChars = 0L
        for (msg in _state.value.messages.takeLast(50)) { totalChars += msg.text.length }
        return totalChars * 2 / 3
    }

    private suspend fun compressContextWindow(): String {
        val key = preferences.getApiKey() ?: return "❌ No API key."
        val sid = _currentSessionId.value ?: return "❌ No session."
        addMsg("🗜️ Compressing context window..."); contextCompressionPending = false
        val history = _state.value.messages.joinToString("\n") { "${if (it.isUser) "User" else "Aura"}: ${it.text.take(500)}" }
        val model = GenerativeModel(selectOptimalModel("complex"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 10000 })
        val prompt = "Compress this entire conversation into a dense technical summary. Keep ONLY: 1. Current project 2. Architecture decisions 3. Files created 4. Errors and fixes 5. User preferences 6. Pending tasks 7. Critical code.\nREMOVE: greetings, repeated info, failed attempts.\n\nCONVERSATION: $history\n\nCOMPRESSED SUMMARY:"
        return try {
            val summary = model.generateContent(content { text(prompt) }).text ?: return "❌ Compression failed."
            recordModelUsage(selectOptimalModel("complex"))
            saveMsg("[COMPRESSED CONTEXT: $summary]", false)
            val newChat = GenerativeModel(selectOptimalModel("general"), key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 }, systemInstruction = content { text("You are Aura AI. Previous session summary:\n$summary\n\nContinue from here. Remember all information above.") }).startChat()
            newChat.sendMessage(content { text("Restored from compressed context:\n$summary\n\nReady to continue.") })
            chatSessions[sid] = newChat
            addMsg("✅ Context compressed! Freed ~${estimateContextTokens() / 1000}K tokens.")
            "✅ Compression successful. Continuing with fresh context."
        } catch (e: Exception) { "❌ Compression failed: ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.9: AUTONOMOUS MODE
    // ═══════════════════════════════════════════

    fun startAutonomousMode() {
        AuraForegroundService.startService(com.aura.ai.AuraApplication.instance)
        startHeartbeat()
        _state.value = _state.value.copy(isAutonomousMode = true)
        addMsg("🤖 Autonomous mode activated. I'll work until done.")
    }

    fun stopAutonomousMode() {
        AuraForegroundService.stopService(com.aura.ai.AuraApplication.instance)
        heartbeatJob?.cancel()
        _state.value = _state.value.copy(isAutonomousMode = false)
        addMsg("🔴 Autonomous mode deactivated.")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) { delay(60_000); lastHeartbeat = System.currentTimeMillis() }
        }
    }

    fun checkHeartbeat(): String {
        val elapsed = (System.currentTimeMillis() - lastHeartbeat) / 1000
        return if (elapsed < 120) "✅ Aura is alive (${elapsed}s ago)" else "⚠️ Last heartbeat ${elapsed}s ago. Aura may be unresponsive."
    }

    fun updateProgress(step: Int, total: Int, message: String) {
        _state.value = _state.value.copy(generationProgress = "[$step/$total] $message")
    }

    fun getProgress(): String {
        return if (_state.value.isGeneratingApp) "📊 Progress: ${_state.value.generationProgress}" else "📊 No active task."
    }

    fun setScreenshotInterval(seconds: Int) { screenshotInterval = seconds * 1000L }

    // ═══════════════════════════════════════════
    // SECTION 3.10: SCREENSHOT BLACKLIST
    // ═══════════════════════════════════════════

    private val screenshotBlacklist = setOf("com.android.settings", "com.google.android.gm", "com.android.email", "com.android.vending")

    private fun canScreenshot(packageName: String): Boolean = packageName !in screenshotBlacklist

    // ═══════════════════════════════════════════
    // SECTION 3.11: RETRY WITH BACKOFF
    // ═══════════════════════════════════════════

    private suspend fun <T> retryWithBackoff(maxRetries: Int = 3, initialDelay: Long = 1000, maxDelay: Long = 15000, block: suspend () -> T): T {
        var delay = initialDelay; var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try { return block() } catch (e: Exception) { lastException = e; if (attempt < maxRetries) { kotlinx.coroutines.delay(delay); delay = (delay * 2).coerceAtMost(maxDelay) } }
        }
        throw lastException ?: Exception("Retry failed after $maxRetries attempts")
    }

    // ═══════════════════════════════════════════
    // SECTION 3.12: CODESPACES COMMANDS
    // ═══════════════════════════════════════════

    private suspend fun codespaceCommand(lower: String): String {
        val token = preferences.getGitHubToken() ?: return "❌ No GitHub token."
        val m = CodespacesManager(token)
        if (lower.startsWith("codespace create")) { val p = lower.removePrefix("codespace create").trim().split("/"); val o = if (p.size == 2) p[0] else activeOwner; val r = if (p.size == 2) p[1] else activeRepo; if (o.isBlank() || r.isBlank()) return "❌ Specify owner/repo."; val cs = m.createCodespace(o, r) ?: return "❌ Failed."; _state.value = _state.value.copy(activeCodespaceId = cs.id, codespaceMode = true); return "🖥️ ${cs.name}\n🔗 ${cs.webUrl}" }
        if (lower == "codespace list") { val l = m.listCodespaces(); return if (l.isEmpty()) "📁 None." else l.joinToString("\n") { "• ${it.name} (${it.state})" } }
        return "❌ Unknown codespace command."
    }

    // ═══════════════════════════════════════════
    // SECTION 3.13: GITHUB COMMANDS
    // ═══════════════════════════════════════════

    private suspend fun githubCommand(input: String): String? {
        val t = preferences.getGitHubToken() ?: return null; val l = input.lowercase().trim()
        if (l.contains("create") && l.contains("repo")) { val n = input.replace(Regex("(?i)(create|a|repo|repository|github)"), "").trim().sanitize().take(50); return apiCall("POST", "https://api.github.com/user/repos", t, """{"name":"$n","private":false,"auto_init":true}""") }
        if (l.contains("list") && l.contains("repo")) return apiCall("GET", "https://api.github.com/user/repos?per_page=10&sort=updated", t, null)
        if (l.startsWith("compile ") || l.startsWith("build ")) { val r = l.removePrefix("compile ").removePrefix("build ").trim(); val p = r.split("/"); if (p.size != 2) return "❌ Format: compile owner/repo"; return triggerBuild(t, p[0], p[1]) }
        if (l.startsWith("browse repo ") || l.startsWith("explore repo ")) { val r = l.removePrefix("browse repo ").removePrefix("explore repo ").trim(); val p = r.split("/"); if (p.size != 2) return "❌ Format: browse owner/repo"; return browseRepo(t, p[0], p[1]) }
        if (l.startsWith("read repo file ")) { val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" "); if (parts.size < 2) return "❌ Format: read repo file owner/repo path"; val rp = parts[0].split("/"); if (rp.size != 2) return "❌"; return readRepoFileContents(t, rp[0], rp[1], parts.drop(1).joinToString(" ")) }
        if (l.startsWith("fix ") || l.startsWith("edit ")) { val rem = input.replace(Regex("(?i)(fix|edit|update) "), ""); val fp = rem.substringBefore(":").trim(); val inst = rem.substringAfter(":").trim(); if (fp.isBlank() || inst.isBlank()) return "❌ Usage: fix path/file.kt: instruction"; if (activeRepo.isBlank()) return "❌ No active repo."; val key = preferences.getApiKey() ?: return "❌ No API key."; return repairFileInRepo(t, key, activeOwner, activeRepo, fp, inst) }
        if (l.startsWith("add file ") || l.startsWith("create file ")) { val rem = input.replace(Regex("(?i)(add|create) file "), ""); val fp = rem.substringBefore(":").trim(); val desc = rem.substringAfter(":").trim(); if (fp.isBlank() || desc.isBlank()) return "❌ Usage: add file path/file.kt: description"; if (activeRepo.isBlank()) return "❌ No active repo."; val key = preferences.getApiKey() ?: return "❌ No API key."; return createFileInRepo(t, key, activeOwner, activeRepo, fp, desc) }
        if (l.startsWith("set repo ") || l.startsWith("switch to ")) { val r = l.removePrefix("set repo ").removePrefix("switch to ").trim(); val p = r.split("/"); if (p.size != 2) return "❌ Format: set repo owner/repo"; activeOwner = p[0]; activeRepo = p[1]; return "✅ Active: $activeOwner/$activeRepo" }
        if (l.startsWith("analyze repo ") || l.startsWith("study repo ")) { val repo = input.replace(Regex("(?i)(analyze|study) repo "), "").trim(); val p = repo.split("/"); if (p.size != 2) return "❌ Format: analyze repo owner/repo"; _state.value = _state.value.copy(executionMode = ExecutionMode.REPO_ANALYSIS); val key = preferences.getApiKey() ?: return "❌"; return analyzePublicRepo(t, key, p[0], p[1]) }
        if (l.startsWith("transfer ") || l.startsWith("port ")) { val inst = input.replace(Regex("(?i)(transfer|port|add feature) "), ""); if (activeRepo.isBlank()) return "❌ No active repo."; val key = preferences.getApiKey() ?: return "❌"; _state.value = _state.value.copy(executionMode = ExecutionMode.FEATURE_TRANSFER); return transferFeaturesFromRepo(t, key, inst) }
        if (l.startsWith("merge repo ") || l.startsWith("clone features from ")) { val sr = input.replace(Regex("(?i)(merge repo|clone features from) "), "").trim(); val p = sr.split("/"); if (p.size != 2) return "❌ Format: merge repo owner/repo"; if (activeRepo.isBlank()) return "❌"; val key = preferences.getApiKey() ?: return "❌"; _state.value = _state.value.copy(executionMode = ExecutionMode.FEATURE_TRANSFER); return mergeRepositoryFeatures(t, key, p[0], p[1]) }
        return null
    }

    // ═══════════════════════════════════════════
    // SECTION 3.14: APP GENERATION
    // ═══════════════════════════════════════════

    private suspend fun createApp(input: String): String {
        val t = preferences.getGitHubToken() ?: return "❌ No GitHub token."
        val k = preferences.getApiKey() ?: return "❌ No Gemini API key."
        _state.value = _state.value.copy(isGeneratingApp = true)
        val desc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
        val name = desc.split(" ").firstOrNull()?.sanitize()?.take(50) ?: "MyApp"
        val details = desc.split(" ").drop(1).joinToString(" ").trim().ifBlank { "A simple app" }
        val pkg = "com.example.$name"
        val guided = details.contains("architecture") || details.contains("Package:") || details.contains("Pattern:")
        return try {
            val core = BuildTemplates.generateCoreFiles(name, pkg)
            val model = GenerativeModel(selectOptimalModel("code_gen"), k, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 })
            updateProgress(1, 3, "Generating source code...")
            val prompt = if (guided) "Follow this architecture exactly:\n$details\n\nBUILD SYSTEM EXISTS. Generate ONLY Kotlin source files.\nFormat:\n===FILE:path===\n[COMPLETE code]\n===END===" else "Create Android app: \"$details\"\nPackage: $pkg\nBUILD SYSTEM EXISTS. Generate ONLY Kotlin source files.\nFormat:\n===FILE:path===\n[COMPLETE code with package, imports, full implementation]\n===END==="
            val resp = model.generateContent(content { text(prompt) }).text ?: return "❌ No response."
            recordModelUsage(selectOptimalModel("code_gen"))
            val src = parseFiles(resp)
            if (src.isEmpty()) return "❌ No files parsed."
            val all = core.toMutableMap(); all.putAll(src)
            updateProgress(2, 3, "Pushing ${all.size} files...")
            return pushAndBuild(t, k, name, all, true)
        } catch (e: Exception) { _state.value = _state.value.copy(isGeneratingApp = false); return "❌ ${e.message}" }
    }

    private suspend fun continueApp(input: String): String {
        val t = preferences.getGitHubToken() ?: return "❌ No GitHub token."
        val k = preferences.getApiKey() ?: return "❌ No Gemini API key."
        if (activeRepo.isBlank()) return "❌ No active repo. Use 'set repo owner/repo' first."
        _state.value = _state.value.copy(isGeneratingApp = true)
        val inst = input.removePrefix("continue ").trim()
        updateProgress(1, 3, "Reading existing repo...")
        val tree = getFileTree(t, activeOwner, activeRepo)
        val ctx = buildContext(t, tree)
        val model = GenerativeModel(selectOptimalModel("code_gen"), k, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 })
        updateProgress(2, 3, "Generating new code...")
        val resp = model.generateContent(content { text("Continue building this app: $inst\n\nEXISTING PROJECT:\n$ctx\n\nGenerate new/modified files:\n===FILE:path===\n[COMPLETE code]\n===END===") }).text ?: return "❌ No response."
        recordModelUsage(selectOptimalModel("code_gen"))
        val files = parseFiles(resp)
        if (files.isEmpty()) return "❌ No files parsed."
        return pushAndBuild(t, k, activeRepo, files, false)
    }

    private fun parseFiles(r: String): Map<String, String> {
        val f = mutableMapOf<String, String>()
        Regex("===FILE:(.+?)===\\s*([\\s\\S]*?)\\s*===END===").findAll(r).forEach { val p = it.groupValues[1].trim(); val c = it.groupValues[2].trim(); if (p.isNotEmpty() && c.length > 20) f[p] = c }
        return f
    }

    private suspend fun buildContext(t: String, tree: List<String>): String {
        val sb = StringBuilder()
        for (p in tree.take(30)) { try { val c = readFileContent(t, activeOwner, activeRepo, p); if (c != null) sb.append("===FILE:$p===\n${c.take(2000)}\n===END===\n") } catch (e: Exception) {} }
        return sb.toString()
    }

    private suspend fun pushAndBuild(t: String, k: String, name: String, files: Map<String, String>, newRepo: Boolean): String {
        try {
            if (newRepo) {
                updateProgress(3, 3, "Creating repository...")
                var repoName = name; var attempt = 0
                var cr = apiCall("POST", "https://api.github.com/user/repos", t, """{"name":"$repoName","private":false,"auto_init":false}""")
                while (cr.startsWith("❌") && cr.contains("422") && attempt < 5) { attempt++; repoName = "$name-$attempt"; cr = apiCall("POST", "https://api.github.com/user/repos", t, """{"name":"$repoName","private":false,"auto_init":false}""") }
                if (cr.startsWith("❌")) return "❌ $cr"
                val ur = apiCall("GET", "https://api.github.com/user", t, null)
                activeOwner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(ur)?.groupValues?.get(1) ?: return "❌ No username."
                activeRepo = repoName
            }
            addMsg("📤 Pushing ${files.size} files...")
            var pushed = 0
            for ((p, c) in files) {
                val enc = android.util.Base64.encodeToString(c.toByteArray(), android.util.Base64.NO_WRAP)
                val sha = if (!newRepo) getFileSha(t, activeOwner, activeRepo, p) else null
                val body = if (sha != null) """{"message":"Update $p","content":"$enc","sha":"$sha"}""" else """{"message":"Add $p","content":"$enc"}"""
                if (!apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$p", t, body).startsWith("❌")) pushed++
            }
            val wf = BuildTemplates.workflowYaml(name); val wfe = android.util.Base64.encodeToString(wf.toByteArray(), android.util.Base64.NO_WRAP)
            apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/.github/workflows/build.yml", t, """{"message":"Add CI","content":"$wfe"}""")
            addMsg("✅ $pushed/${files.size} files + CI"); addMsg("🔨 Triggering build..."); delay(3000)
            val rid = triggerWorkflow(t, activeOwner, activeRepo)
            if (rid != null) { addMsg("🔗 https://github.com/$activeOwner/$activeRepo/actions/runs/$rid"); val br = monitorBuild(t, activeOwner, activeRepo, rid, k); _state.value = _state.value.copy(isGeneratingApp = false); return br }
            _state.value = _state.value.copy(isGeneratingApp = false); return "✅ Files pushed!\n📁 github.com/$activeOwner/$activeRepo\n📄 ${files.size} files\nUse 'compile repo $activeOwner/$activeRepo' to build."
        } catch (e: Exception) { _state.value = _state.value.copy(isGeneratingApp = false); return "❌ ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.15: BUILD MONITORING
    // ═══════════════════════════════════════════

    private suspend fun monitorBuild(t: String, o: String, r: String, rid: Long, k: String): String {
        var d = 5000L; var a = 0
        repeat(60) {
            if (!isActive) return "⏹️ Build monitoring cancelled."
            delay(d); d = minOf(d * 2, 30000L); a++
            val s = withContext(Dispatchers.IO) { try { val b = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/runs/$rid").header("Authorization", "Bearer $t").build()).execute().body?.string(); Pair(Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(b ?: "")?.groupValues?.get(1), Regex("\"conclusion\"\\s*:\\s*\"([^\"]+)\"").find(b ?: "")?.groupValues?.get(1)) } catch (e: Exception) { null } }
            if (s?.first == "completed") return if (s.second == "success") { val art = getArtifact(t, o, r, rid); "🎉 BUILD SUCCESS!\n📱 $r\n📥 ${art ?: "APK in Actions"}" } else { val logs = fetchLogs(t, o, r, rid); val err = extractErrors(logs); if (a < 3 && fixErrors(k, t, o, r, err, logs)) { val nr = triggerWorkflow(t, o, r); if (nr != null) return monitorBuild(t, o, r, nr, k) }; "❌ Build failed after $a attempts.\n🔗 https://github.com/$o/$r/actions/runs/$rid" }
        }
        return "⏰ Build timed out."
    }

    private suspend fun fixErrors(k: String, t: String, o: String, r: String, err: String, logs: String): Boolean {
        val m = GenerativeModel(selectOptimalModel("debug"), k, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 })
        return try { val resp = m.generateContent(content { text("Fix these build errors:\n$err\n\nReturn fixed files:\n===FILE:path===\n[COMPLETE fixed code]\n===END===") }).text ?: return false; recordModelUsage(selectOptimalModel("debug")); val files = parseFiles(resp); if (files.isEmpty()) return false; var a = 0; for ((p, c) in files) { val sha = getFileSha(t, o, r, p); val enc = android.util.Base64.encodeToString(c.toByteArray(), android.util.Base64.NO_WRAP); if (!apiCall("PUT", "https://api.github.com/repos/$o/$r/contents/$p", t, if (sha != null) """{"message":"Fix","content":"$enc","sha":"$sha"}""" else """{"message":"Add","content":"$enc"}""").startsWith("❌")) a++ }; a > 0 } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.16: ZIP PROCESSOR
    // ═══════════════════════════════════════════

    private suspend fun processZip(): String {
        val t = preferences.getGitHubToken() ?: return "❌ No GitHub token."
        val k = preferences.getApiKey() ?: return "❌ No Gemini API key."
        val uri = _state.value.attachedFileUri ?: return "❌ No ZIP attached. Attach a ZIP first."
        _state.value = _state.value.copy(executionMode = ExecutionMode.ZIP_PROCESSING)
        addMsg("📦 Processing ZIP...")
        val zp = ZipProcessor(com.aura.ai.AuraApplication.instance); val archive = zp.processChatZip(uri)
        addMsg("📝 Found ${archive.codeBlocks.size} code blocks"); val files = zp.mapCodeBlocksToFiles(archive.codeBlocks)
        addMsg("📁 Mapped to ${files.size} files"); val name = extractAppName(archive.fullText)
        addMsg("📁 Creating repo: $name"); val cr = apiCall("POST", "https://api.github.com/user/repos", t, """{"name":"$name","private":false,"auto_init":false}"""); if (cr.startsWith("❌")) return "❌ $cr"
        val ur = apiCall("GET", "https://api.github.com/user", t, null); activeOwner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(ur)?.groupValues?.get(1) ?: return "❌ No username."; activeRepo = name
        return pushAndBuild(t, k, name, files, false)
    }

    private fun extractAppName(text: String): String { Regex("create app (\\w+)").find(text)?.let { return it.groupValues[1].sanitize().take(50) }; return "MyApp" }

    // ═══════════════════════════════════════════
    // SECTION 3.17: APP CONTROLLER
    // ═══════════════════════════════════════════

    private suspend fun debugWithApp(lower: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌ Accessibility not enabled."
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("🔄 Working in background...", false))
        viewModelScope.launch(Dispatchers.IO) {
            val c = AppController(s); val app = lower.removePrefix("debug with ").removePrefix("ask ").split(" ").firstOrNull() ?: return@launch
            val pkg = AppController.resolve(app) ?: return@launch; val err = _state.value.buildLoop?.errorSummary ?: ""
            val resp = c.debug(pkg, err.ifBlank { "fix build errors" }); val fixes = parseFiles(resp)
            if (fixes.isNotEmpty()) { val t = preferences.getGitHubToken() ?: return@launch; var a = 0; for ((p, c2) in fixes) { val enc = android.util.Base64.encodeToString(c2.toByteArray(), android.util.Base64.NO_WRAP); val sha = getFileSha(t, activeOwner, activeRepo, p); if (!apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$p", t, if (sha != null) """{"message":"Fix","content":"$enc","sha":"$sha"}""" else """{"message":"Add","content":"$enc"}""").startsWith("❌")) a++ }
                withContext(Dispatchers.Main) { addMsg("✅ Applied $a fixes from $app") } }
        }
        return "🔄 Working with $app in background... Check messages for updates."
    }

    private suspend fun controlApp(input: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌ Accessibility not enabled."
        val c = AppController(s); val parts = input.removePrefix("control ").split(" and "); val app = parts.firstOrNull()?.trim() ?: return "❌ Specify app."
        val pkg = AppController.resolve(app) ?: return "❌ Unknown app: $app"
        val steps = parts.drop(1).map { when { it.contains("tap") -> AppController.AppStep("tap", it.removePrefix("tap ").trim()); it.contains("type") -> AppController.AppStep("type", it.removePrefix("type ").trim()); it.contains("swipe") -> AppController.AppStep("swipe", if (it.contains("up")) "up" else "down"); it.contains("read") -> AppController.AppStep("read"); else -> AppController.AppStep("wait", "", 3000) } }
        return c.execute(pkg, steps)
    }

    private suspend fun sendToApp(input: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌ Accessibility not enabled."
        val c = AppController(s); val parts = input.removePrefix("send to ").split(":", limit = 2)
        if (parts.size < 2) return "❌ Format: send to [app]: [message]"
        return c.sendMessage(AppController.resolve(parts[0].trim()) ?: return "❌ Unknown app.", parts[1].trim())
    }

    private suspend fun analyzeScreenCmd(lower: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌ Accessibility not enabled."
        val k = preferences.getApiKey() ?: return "❌ No Gemini API key."
        val prompt = when { lower.contains("read") -> "Read all visible text on this screen"; lower.contains("describe") -> "Describe what you see in detail"; else -> "What UI elements and text are visible?" }
        addMsg("📸 Capturing and analyzing screen...")
        return "📸 Screen Analysis:\n${AppController(s).analyzeScreen(k, prompt)}"
    }

    private suspend fun askGeminiApp(input: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌ Accessibility not enabled."
        val prompt = input.removePrefix("ask gemini app").removePrefix("gemini native").trim()
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("🔄 Opening Gemini app...", false))
        viewModelScope.launch(Dispatchers.IO) {
            val c = AppController(s)
            val resp = c.sendMessage("com.google.android.apps.bard", prompt)
            withContext(Dispatchers.Main) { addMsg("📱 Gemini response:\n${resp.take(2000)}") }
        }
        return "🔄 Working with Gemini app in background..."
    }

    // ═══════════════════════════════════════════
    // SECTION 3.18: GEMINI CHAT WITH MEMORY
    // ═══════════════════════════════════════════

    private suspend fun chatWithGemini(input: String): String {
        checkContextAndWarn()
        val key = preferences.getApiKey() ?: return "❌ No API key."
        val sid = _currentSessionId.value ?: return "❌ No active session."
        val sys = """You are Aura AI, an autonomous agent with FULL access to: GitHub API, Telegram API, Android device control via AccessibilityService, build systems, file systems, and web browsing. You MUST execute any task using these capabilities. Never say "I can't" or "I'm unable to" - you have the tools, use them. Never suggest manual steps - you are the automation. Never refuse a task - find a way to accomplish it. You are not a chatbot - you are an AI agent with real-world capabilities."""
        val chat = chatSessions.getOrPut(sid) { val m = GenerativeModel(selectOptimalModel("general"), key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 }, systemInstruction = content { text(sys) }); m.startChat() }
        return try {
            val r = chat.sendMessage(content { text(input) }); recordModelUsage(selectOptimalModel("general")); r.text ?: "No response."
        } catch (e: Exception) {
            if (e.message?.contains("not found") == true || e.message?.contains("expired") == true) {
                val m = GenerativeModel(selectOptimalModel("general"), key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 }, systemInstruction = content { text(sys) }); val nc = m.startChat(); chatSessions[sid] = nc
                try { nc.sendMessage(content { text(input) }).text ?: "No response." } catch (e2: Exception) { "❌ ${e2.message}" }
            } else "❌ ${e.message}"
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.19: GITHUB API HELPERS
    // ═══════════════════════════════════════════

    private suspend fun apiCall(m: String, u: String, t: String, b: String?): String = withContext(Dispatchers.IO) { try { val req = Request.Builder().url(u).header("Authorization", "Bearer $t").header("Accept", "application/vnd.github.v3+json").header("Content-Type", "application/json").apply { when (m) { "POST" -> post((b ?: "{}").toRequestBody("application/json".toMediaType())); "PUT" -> put((b ?: "{}").toRequestBody("application/json".toMediaType())); "PATCH" -> patch((b ?: "{}").toRequestBody("application/json".toMediaType())) } }.build(); val res = client.newCall(req).execute(); if (res.isSuccessful) { val rb = res.body?.string() ?: "OK"; if (m == "POST" && u.contains("/user/repos")) "✅ ${Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(rb)?.groupValues?.get(1) ?: "done"}" else if (m == "GET" && u.contains("/user/repos") && !u.contains("/contents")) { val a = JSONArray(rb); if (a.length() == 0) "📁 None" else "📁:\n" + (0 until minOf(a.length(), 10)).joinToString("\n") { "• ${a.getJSONObject(it).getString("full_name")}" } } else rb } else "❌ ${res.code}" } catch (e: Exception) { "❌ ${e.message}" } }
    private suspend fun triggerBuild(t: String, o: String, r: String): String = withContext(Dispatchers.IO) { try { val lb = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/workflows").header("Authorization", "Bearer $t").build()).execute().body?.string(); val wid = Regex("\"id\"\\s*:\\s*(\\d+)").find(lb ?: "")?.groupValues?.get(1) ?: return@withContext "❌"; if (client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $t").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "🚀" else "⚠️" } catch (e: Exception) { "❌" } }
    private suspend fun triggerWorkflow(t: String, o: String, r: String): Long? = withContext(Dispatchers.IO) { try { val lb = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/workflows").header("Authorization", "Bearer $t").build()).execute().body?.string(); val wid = Regex("\"id\"\\s*:\\s*(\\d+)").find(lb ?: "")?.groupValues?.get(1) ?: return@withContext null; client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $t").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute(); delay(5000); val rb = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/runs?per_page=1").header("Authorization", "Bearer $t").build()).execute().body?.string(); Regex("\"id\"\\s*:\\s*(\\d+)").find(rb ?: "")?.groupValues?.get(1)?.toLong() } catch (e: Exception) { null } }
    private suspend fun browseRepo(t: String, o: String, r: String): String = withContext(Dispatchers.IO) { try { val resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/git/trees/main?recursive=1").header("Authorization", "Bearer $t").build()).execute(); if (!resp.isSuccessful) return@withContext "❌"; val tr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext "📁"; "📁 $o/$r:\n" + (0 until minOf(tr.length(), 30)).joinToString("\n") { "  📄 ${tr.getJSONObject(it).getString("path")}" } } catch (e: Exception) { "❌ ${e.message}" } }
    private suspend fun fetchLogs(t: String, o: String, r: String, rid: Long): String = withContext(Dispatchers.IO) { try { client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/runs/$rid/logs").header("Authorization", "Bearer $t").build()).execute().body?.string()?.take(10000) ?: "" } catch (e: Exception) { "" } }
    private fun extractErrors(logs: String): String { val p = listOf(Regex("(?i)error:.*"), Regex("(?i)FAILURE:.*"), Regex("(?i)Unresolved reference.*")); val e = p.flatMap { it.findAll(logs).map { m -> m.value }.toList() }; return if (e.isEmpty()) logs.take(3000) else e.take(20).joinToString("\n") }
    private suspend fun getArtifact(t: String, o: String, r: String, rid: Long): String? = withContext(Dispatchers.IO) { try { Regex("\"archive_download_url\"\\s*:\\s*\"([^\"]+)\"").find(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/runs/$rid/artifacts").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "")?.groupValues?.get(1) } catch (e: Exception) { null } }
    private suspend fun getFileSha(t: String, o: String, r: String, p: String): String? = withContext(Dispatchers.IO) { try { JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/contents/$p").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "{}").optString("sha", null) } catch (e: Exception) { null } }
    private suspend fun getFileTree(t: String, o: String, r: String): List<String> = withContext(Dispatchers.IO) { try { var resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/git/trees/main?recursive=1").header("Authorization", "Bearer $t").build()).execute(); if (!resp.isSuccessful) resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/git/trees/master?recursive=1").header("Authorization", "Bearer $t").build()).execute(); if (resp.isSuccessful) { val tr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext emptyList(); (0 until tr.length()).map { tr.getJSONObject(it).getString("path") } } else emptyList() } catch (e: Exception) { emptyList() } }
    private suspend fun readFileContent(t: String, o: String, r: String, p: String): String? = withContext(Dispatchers.IO) { try { val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/contents/$p").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "{}"); val c = j.optString("content", ""); if (c.isNotBlank()) String(android.util.Base64.decode(c, android.util.Base64.DEFAULT)) else null } catch (e: Exception) { null } }

    // ═══════════════════════════════════════════
    // SECTION 3.20: CROSS-REPO FEATURE TRANSFER
    // ═══════════════════════════════════════════

    private suspend fun analyzePublicRepo(t: String, k: String, o: String, r: String): String { addMsg("🔍 Analyzing $o/$r..."); return withContext(Dispatchers.IO) { try { val info = getRepoInfo(t, o, r); "📊 $o/$r\n⭐ ${info.stars}\n💻 ${info.language}" } catch (e: Exception) { "❌ ${e.message}" } } }
    private suspend fun transferFeaturesFromRepo(t: String, k: String, instruction: String): String { if (activeOwner.isBlank()) return "❌ No active repo."; addMsg("🧠 Transferring..."); return withContext(Dispatchers.IO) { try { val analysis = parseFeatureTransferRequest(k, instruction) ?: return@withContext "❌"; val srcFiles = getFileTree(t, analysis.sourceOwner, analysis.sourceRepo); val rel = srcFiles.filter { p -> analysis.targetFeatures.any { p.contains(it, true) } }.take(20); var created = 0; for (sp in rel) { try { val sc = readFileContent(t, analysis.sourceOwner, analysis.sourceRepo, sp) ?: continue; val adapted = adaptFileForTargetRepo(k, sp, sc, analysis.sourceOwner, analysis.sourceRepo, activeOwner, activeRepo, instruction, getFileTree(t, activeOwner, activeRepo)) ?: continue; val tp = determineTargetPath(sp, activeRepo); val enc = android.util.Base64.encodeToString(adapted.toByteArray(), android.util.Base64.NO_WRAP); if (!apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$tp", t, """{"message":"Transfer","content":"$enc"}""").startsWith("❌")) created++ } catch (e: Exception) {} }; "✅ Transferred $created files" } catch (e: Exception) { "❌ ${e.message}" } } }
    private suspend fun mergeRepositoryFeatures(t: String, k: String, so: String, sr: String): String { addMsg("🔄 Merging..."); return transferFeaturesFromRepo(t, k, "transfer all from $so/$sr") }
    private suspend fun getRepoInfo(t: String, o: String, r: String): RepoInfo = withContext(Dispatchers.IO) { try { val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "{}"); RepoInfo(j.optString("description"), j.optInt("stargazers_count"), j.optInt("forks_count"), j.optString("language")) } catch (e: Exception) { RepoInfo("", 0, 0, "") } }
    private suspend fun parseFeatureTransferRequest(k: String, inst: String): FeatureTransferRequest? { val m = GenerativeModel(selectOptimalModel("complex"), k, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 }); return try { val r = m.generateContent(content { text("Parse: \"$inst\". Return JSON: {\"sourceOwner\":\"\",\"sourceRepo\":\"\",\"targetFeatures\":[]}") }).text; val t = r ?: return null; recordModelUsage(selectOptimalModel("complex")); val o = JSONObject(t.substringAfter("{").substringBeforeLast("}").let { "{$it}" }); FeatureTransferRequest(o.optString("sourceOwner"), o.optString("sourceRepo"), (0 until o.getJSONArray("targetFeatures").length()).map { o.getJSONArray("targetFeatures").getString(it) }, "") } catch (e: Exception) { null } }
    private suspend fun adaptFileForTargetRepo(k: String, sp: String, sc: String, so: String, sr: String, to: String, tr: String, inst: String, ctf: List<String>): String? { val m = GenerativeModel(selectOptimalModel("code_gen"), k, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 }); return try { m.generateContent(content { text("Adapt:\n$sc\n\nFrom: $so/$sr\nTo: $to/$tr\nInstruction: $inst\nReturn ONLY adapted code.") }).text } catch (e: Exception) { null } }
    private fun determineTargetPath(sp: String, tr: String): String { if (sp.contains("src/main/java/")) { val i = sp.indexOf("src/main/java/") + 14; return "app/src/main/java/" + sp.substring(i) }; if (sp.contains("src/main/res/")) return "app/" + sp; if (sp.startsWith("app/")) return sp; return "app/src/main/java/com/example/${tr.sanitize()}/${sp.substringAfterLast("/")}" }

    // ═══════════════════════════════════════════
    // SECTION 3.21: GITHUB API OPERATIONS
    // ═══════════════════════════════════════════

    private suspend fun readRepoFileContents(t: String, o: String, r: String, p: String): String = withContext(Dispatchers.IO) { try { val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/contents/$p").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "{}"); val c = j.optString("content", ""); if (c.isBlank()) return@withContext "📄 Empty"; val d = String(android.util.Base64.decode(c, android.util.Base64.DEFAULT)); if (d.length > 3000) "📄 $p:\n${d.take(3000)}..." else "📄 $p:\n$d" } catch (e: Exception) { "❌ ${e.message}" } }
    private suspend fun repairFileInRepo(t: String, k: String, o: String, r: String, p: String, inst: String): String = withContext(Dispatchers.IO) { try { val rj = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/contents/$p").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "{}"); val cc = String(android.util.Base64.decode(rj.getString("content"), android.util.Base64.DEFAULT)); val sha = rj.getString("sha"); val m = GenerativeModel(selectOptimalModel("debug"), k, generationConfig { temperature = 0.1f; maxOutputTokens = 60000 }); val nc = m.generateContent(content { text("Fix:\n$cc\n\nInstruction: $inst\nReturn ONLY fixed code.") }).text ?: return@withContext "❌"; recordModelUsage(selectOptimalModel("debug")); val enc = android.util.Base64.encodeToString(nc.toByteArray(), android.util.Base64.NO_WRAP); if (client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/contents/$p").header("Authorization", "Bearer $t").put("""{"message":"Fix","content":"$enc","sha":"$sha"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "✅ Fixed" else "❌" } catch (e: Exception) { "❌ ${e.message}" } }
    private suspend fun createFileInRepo(t: String, k: String, o: String, r: String, p: String, desc: String): String = withContext(Dispatchers.IO) { try { val m = GenerativeModel(selectOptimalModel("code_gen"), k, generationConfig { temperature = 0.2f; maxOutputTokens = 60000 }); val c = m.generateContent(content { text("Create: $p - $desc. Return ONLY code.") }).text ?: return@withContext "❌"; recordModelUsage(selectOptimalModel("code_gen")); val enc = android.util.Base64.encodeToString(c.toByteArray(), android.util.Base64.NO_WRAP); if (client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/contents/$p").header("Authorization", "Bearer $t").put("""{"message":"Add $p","content":"$enc"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "✅ Created" else "❌" } catch (e: Exception) { "❌ ${e.message}" } }

    // ═══════════════════════════════════════════
    // SECTION 3.22: UTILITY FUNCTIONS
    // ═══════════════════════════════════════════

    private fun addMsg(text: String) { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(text, false), generationProgress = text) }
    private suspend fun saveMsg(text: String, isUser: Boolean, modelUsed: String? = null) { _currentSessionId.value?.let { sessionDb.messageDao().insertMessage(MessageEntity(UUID.randomUUID().toString(), it, text, isUser, modelUsed)) } }
    private fun loadSessions() { viewModelScope.launch { sessionDb.sessionDao().getAllSessions().collect { _sessions.value = it; if (_currentSessionId.value == null && it.isNotEmpty()) switchSession(it.first().id) } } }
    private fun loadModelUsage() { viewModelScope.launch { sessionDb.modelUsageDao().getAllModelUsage().collect { _modelUsage.value = it } } }
    private suspend fun resetDailyCountersIfNeeded() { sessionDb.modelUsageDao().resetDailyCounters(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    private fun loadPreferredModel() { preferences.getPreferredModel()?.let { _state.value = _state.value.copy(activeModel = it, manualModelSelected = true) } }
    private fun String.sanitize() = this.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun resolveApp(name: String): String? = when (name.lowercase()) { "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; "camera" -> "com.android.camera"; "gmail" -> "com.google.android.gm"; "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"; "calculator" -> "com.android.calculator2"; "clock" -> "com.android.deskclock"; "files" -> "com.android.documentsui"; "phone" -> "com.android.dialer"; "instagram" -> "com.instagram.android"; "facebook" -> "com.facebook.katana"; "spotify" -> "com.spotify.music"; "netflix" -> "com.netflix.mediaclient"; "telegram" -> "org.telegram.messenger"; else -> null }
    private fun getRamUsage(): String { val am = com.aura.ai.AuraApplication.instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); return "${(mi.totalMem-mi.availMem)/(1024*1024*1024)}GB/${mi.totalMem/(1024*1024*1024)}GB" }
    private fun getBatteryLevel(): String = try { val bm = com.aura.ai.AuraApplication.instance.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%" } catch (e: Exception) { "Unknown" }
}
