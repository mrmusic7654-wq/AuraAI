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
import com.aura.ai.agentic.core.*
import com.aura.ai.agentic.execution.*
import com.aura.ai.agentic.memory.*
import com.aura.ai.agentic.planning.*
import com.aura.ai.data.local.ApiKeyManager
import com.aura.ai.data.local.database.*
import com.aura.ai.data.local.preferences.AuraPreferences
import com.aura.ai.services.*
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

// ═══════════════════════════════════════════
// DATA CLASSES
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
    val activeModel: String = "gemini-2.5-flash", val showDrawer: Boolean = false,
    val showModelDashboard: Boolean = false, val manualModelSelected: Boolean = false,
    val currentSessionId: String? = null, val buildLoop: BuildLoopState? = null,
    val isGeneratingApp: Boolean = false, val generationProgress: String = "",
    val codespaceMode: Boolean = false, val activeCodespaceId: String? = null,
    val attachedFileUri: android.net.Uri? = null, val attachedFileName: String = "",
    val isOnline: Boolean = true, val totalApiCalls: Int = 0,
    val isAutonomousMode: Boolean = false, val isAgenticMode: Boolean = false
)

enum class ExecutionMode {
    IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION,
    FILE_OPERATION, REPO_ANALYSIS, FEATURE_TRANSFER, CODESPACE_GENERATION,
    BATCH_FILE_PUSH, IMAGE_ANALYSIS, FILE_UPLOAD, STREAMING_CHAT,
    APP_CONTROL, ZIP_PROCESSING, CONTEXT_COMPRESSION, AUTONOMOUS,
    CLOUD_EXECUTION, AGENTIC
}

private data class AppArchitecture(val files: List<String>, val techStack: String, val dependencies: List<String>, val structure: String)
private data class FixPlan(val summary: String, val fileFixes: List<Pair<String, String>>)
private sealed class WorkflowResult {
    data object Success : WorkflowResult()
    data class Failure(val error: String, val logs: String) : WorkflowResult()
}

// ═══════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val preferences: AuraPreferences
) : ViewModel() {

    // ═══════════════════════════════════════════
    // STATE MANAGEMENT
    // ═══════════════════════════════════════════

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()
    private var taskJob: Job? = null
    private var activeRepo = ""
    private var activeOwner = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()
    private var pendingGenerationFiles: Map<String, String> = emptyMap()

    // ═══════════════════════════════════════════
    // SESSION MANAGEMENT
    // ═══════════════════════════════════════════

    private val sessionDb by lazy { SessionDatabase.getInstance(com.aura.ai.AuraApplication.instance) }
    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    private val _modelUsage = MutableStateFlow<List<ModelUsageEntity>>(emptyList())
    private val chatSessions = mutableMapOf<String, Chat>()
    private var contextCompressionPending = false

    // ═══════════════════════════════════════════
    // MODEL REGISTRY
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
        "general" to listOf("gemini-3.1-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash")
    )

    private val modelCooldowns = mutableMapOf<String, Long>()
    private val modelDailyUsage = mutableMapOf<String, Int>()
    private var consecutiveFailures = 0

    // ═══════════════════════════════════════════
    // AGENTIC COMPONENTS
    // ═══════════════════════════════════════════

    private val consciousness = Consciousness()
    private val patternRecognizer = PatternRecognizer()
    private val experienceBuffer = ExperienceBuffer()
    private val knowledgeGraph = KnowledgeGraph()
    private val longTermMemory = LongTermMemory(com.aura.ai.AuraApplication.instance)
    private val trustManager = TrustManager(com.aura.ai.AuraApplication.instance)
    private val goalEngine = GoalEngine()
    private val taskDecomposer = TaskDecomposer()
    private val priorityEngine = PriorityEngine()
    private val resourcePlanner = ResourcePlanner()
    private val dependencyResolver = DependencyResolver()

    private var taskPlanner: TaskPlanner? = null
    private var continuousAgent: ContinuousAgent? = null
    private var intelligentController: IntelligentController? = null
    private var cloudConnector: CloudConnector? = null
    private var apiKeyManager: ApiKeyManager? = null
    private var auraBrain: AuraBrain? = null
    private var selfPromptLoop: SelfPromptLoop? = null
    private var autonomousExecutor: AutonomousExecutor? = null
    private var fileHandler: FileAttachmentHandler? = null
    private var heartbeatJob: Job? = null
    private var lastHeartbeat = System.currentTimeMillis()
    private var screenshotInterval = 30_000L
    private val commandQueue = mutableListOf<QueuedCommand>()

    private data class QueuedCommand(val id: String, val command: String, val timestamp: Long)

    // ═══════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════

    init {
        loadSessions()
        loadModelUsage()
        loadPreferredModel()
        initializeAgenticComponents()
        viewModelScope.launch {
            resetDailyCountersIfNeeded()
            val lastId = preferences.getLastSessionId()
            if (lastId != null) switchSession(lastId)
            else if (_sessions.value.isEmpty()) createNewSession()
        }
        _state.value = _state.value.copy(isOnline = isNetworkAvailable())
        _state.value = _state.value.copy(totalApiCalls = preferences.getTotalApiCalls())
    }

    private fun initializeAgenticComponents() {
        apiKeyManager = ApiKeyManager(com.aura.ai.AuraApplication.instance)
        fileHandler = FileAttachmentHandler(com.aura.ai.AuraApplication.instance)
        
        val hfToken = apiKeyManager?.get(ApiKeyManager.ApiType.HUGGINGFACE)
        val spaceUrl = preferences.getHfSpaceUrl()
        if (!hfToken.isNullOrBlank()) {
            cloudConnector = CloudConnector(spaceUrl, hfToken)
        }
        
        val service = AuraAccessibilityService.instance
        if (service != null) {
            val ac = AppController(service)
            taskPlanner = TaskPlanner(ac, preferences.getApiKey() ?: "")
            intelligentController = IntelligentController(ac, preferences.getApiKey() ?: "")
        }
        
        autonomousExecutor = AutonomousExecutor(consciousness, experienceBuffer)
        
        auraBrain = AuraBrain(
            consciousness, patternRecognizer, priorityEngine,
            resourcePlanner, trustManager, experienceBuffer
        )
        
        selfPromptLoop = SelfPromptLoop(
            auraBrain!!, consciousness,
            onDecisionReady = { decision ->
                viewModelScope.launch {
                    autonomousExecutor?.execute(decision.suggestedAction)
                    addMsg("🤖 Auto: ${decision.suggestedAction}")
                }
            },
            onApprovalNeeded = { action ->
                viewModelScope.launch {
                    addMsg("🔔 Approval needed: $action — type 'approve' or 'reject'")
                }
            }
        )
        
        if (taskPlanner != null) {
            continuousAgent = ContinuousAgent(
                taskPlanner!!, consciousness,
                onProgress = { viewModelScope.launch { addMsg(it) } },
                onComplete = { viewModelScope.launch { addMsg(it) } },
                onError = { viewModelScope.launch { addMsg(it) } }
            )
        }
    }

    // ═══════════════════════════════════════════
    // MODEL SELECTION
    // ═══════════════════════════════════════════

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

    private fun formatModelName(name: String): String = when (name) {
        "gemini-3.5-flash" -> "3.5 Flash ⚡"; "gemini-3.1-flash-lite" -> "3.1 Flash-Lite 🚀"
        "gemini-2.5-flash" -> "2.5 Flash 💎"; "gemini-2.5-flash-lite" -> "2.5 Flash-Lite ⚡"
        "gemini-2.0-flash" -> "2.0 Flash 📦"; "gemini-embedding-2" -> "Embedding 2 🔍"
        else -> name.replace("gemini-", "").replace("-", " ").uppercase()
    }

    // ═══════════════════════════════════════════
    // PUBLIC INTERFACE
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
            ModelInfo(name = name, displayName = formatModelName(name), strength = spec.description,
                dailyRequests = usage?.dailyRequests ?: 0, dailyLimit = spec.rpd,
                isInCooldown = isModelInCooldown(name), isSelected = name == _state.value.activeModel)
        }
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
            val s = sessionDb.sessionDao().getSession(sessionId) ?: return@launch
            _currentSessionId.value = sessionId; preferences.setLastSessionId(sessionId)
            val msgs = sessionDb.messageDao().getMessagesForSessionOnce(sessionId)
            _state.value = _state.value.copy(
                messages = if (msgs.isEmpty()) _state.value.messages else msgs.map { ChatMessage(it.text, it.isUser) },
                currentSessionId = sessionId, manualModelSelected = true,
                activeModel = s.selectedModel, buildLoop = null, isGeneratingApp = false
            )
            sessionDb.sessionDao().updateSession(sessionId, System.currentTimeMillis(), s.title)
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
        patternRecognizer.observeAction(msg)
        taskJob = viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, currentTask = msg)
            saveMsg(msg, true)
            val result = execute(msg)
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage(result, false),
                loading = false, isExecuting = false, currentTask = "", executionMode = ExecutionMode.IDLE
            )
            saveMsg(result, false, modelUsed = _state.value.activeModel)
        }
    }

    private fun handleControl(input: String): Boolean = when (input.lowercase().trim()) {
        "stop", "cancel" -> { taskJob?.cancel(); _state.value = _state.value.copy(loading = false, isExecuting = false, isGeneratingApp = false, buildLoop = null); true }
        "approve" -> { addMsg("✅ Approved"); true }
        "reject" -> { addMsg("❌ Rejected"); true }
        "queue" -> { processQueue(); true }
        else -> false
    }

    // ═══════════════════════════════════════════
    // COMMAND ROUTER
    // ═══════════════════════════════════════════

    private suspend fun execute(input: String): String {
        val lower = input.lowercase().trim()
        
        // System commands
        if (lower == "device info") return "📱 ${Build.MODEL}\n🤖 ${Build.VERSION.RELEASE}\n💾 ${getRamUsage()}\n🔋 ${getBatteryLevel()}"
        if (lower == "time") return "🕐 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
        if (lower == "models" || lower == "model limits") return getModelLimits()
        if (lower == "context status" || lower == "context") return getContextStatus()
        if (lower == "compress yes" || lower == "compress") return compressContextWindow()
        if (lower == "compress no") { contextCompressionPending = false; return "✅ Continuing without compression." }
        if (lower == "heartbeat" || lower == "status") return checkHeartbeat()
        if (lower == "progress" || lower == "what are you doing") return getProgress()
        if (lower == "keys" || lower == "api keys") return apiKeyManager?.report() ?: "N/A"
        if (lower == "patterns") return patternRecognizer.report()
        if (lower == "trust report") return trustManager.report()
        if (lower == "memory") return longTermMemory.count().toString() + " memories stored"
        if (lower == "usage") return "📡 API: ${_state.value.totalApiCalls} calls today"
        
        // Agentic commands
        if (lower == "start agentic" || lower == "jarvis mode") { startAgenticMode(); return "🧠 Agentic mode activated." }
        if (lower == "stop agentic") { stopAgenticMode(); return "🔴 Agentic mode deactivated." }
        if (lower == "start agent") { continuousAgent?.start(); return "🤖 Agent started" }
        if (lower == "stop agent") { continuousAgent?.stop(); return "🔴 Agent stopped" }
        if (lower == "queue") return continuousAgent?.status() ?: "No agent"
        
        // Cloud commands
        if (lower == "cloud status") { val r = cloudConnector?.checkHealth(); return if (r?.success == true) "☁️ Connected" else "☁️ Offline" }
        if (lower.startsWith("cloud generate ")) { return handleCloudGenerate(input) }
        if (lower.startsWith("cloud monitor ")) { return handleCloudMonitor(lower) }
        
        // AI-planned phone tasks
        if (lower.startsWith("do ")) { return handleDoTask(input) }
        
        // Phone control
        if (lower.startsWith("open ")) { return handleOpenApp(lower) }
        if (lower == "home") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "🏠 Home" }
        if (lower == "back") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "⬅️ Back" }
        if (lower == "screenshot") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "📸 Screenshot" }
        if (lower.startsWith("control ")) { return handleControlApp(input) }
        if (lower.startsWith("send to ")) { return handleSendToApp(input) }
        if (lower.startsWith("debug with ") || lower.startsWith("ask ")) { return handleDebugWithApp(lower) }
        if (lower.startsWith("analyze screen")) { return handleAnalyzeScreen(lower) }
        
        // App generation
        if (lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) {
            if (lower.contains("repo")) return handleGitHub(input) ?: "❌"
            _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP)
            return createApp(input)
        }
        if (lower.startsWith("continue ")) { _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP); return continueApp(input) }
        
        // ZIP processing
        if (lower.startsWith("process zip") || lower.startsWith("deploy zip")) { return processZip() }
        
        // Codespaces
        if (lower.startsWith("codespace ")) { return handleCodespace(lower) }
        
        // GitHub
        return handleGitHub(input) ?: chatWithGemini(input)
    }

    // ═══════════════════════════════════════════
    // COMMAND HANDLERS
    // ═══════════════════════════════════════════

    private fun handleOpenApp(lower: String): String {
        val app = lower.removePrefix("open ").trim()
        val pkg = resolveApp(app) ?: return "❌ Unknown app: $app"
        return try {
            val i = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(pkg)
            i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            com.aura.ai.AuraApplication.instance.startActivity(i)
            "✅ Opened $app"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private fun handleControlApp(input: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌ Accessibility not enabled."
        val parts = input.removePrefix("control ").split(" and ")
        val pkg = AppController.resolve(parts.firstOrNull()?.trim() ?: return "❌") ?: return "❌"
        val c = AppController(s)
        val steps = parts.drop(1).map {
            when {
                it.contains("tap") -> AppController.AppStep("tap", it.removePrefix("tap ").trim())
                it.contains("type") -> AppController.AppStep("type", it.removePrefix("type ").trim())
                it.contains("swipe") -> AppController.AppStep("swipe", if (it.contains("up")) "up" else "down")
                it.contains("read") -> AppController.AppStep("read")
                else -> AppController.AppStep("wait", "", 3000)
            }
        }
        return runBlocking { c.execute(pkg, steps) }
    }

    private fun handleSendToApp(input: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌"
        val parts = input.removePrefix("send to ").split(":", limit = 2)
        if (parts.size < 2) return "❌ Format: send to [app]: [message]"
        val c = AppController(s)
        return runBlocking { c.sendMessage(AppController.resolve(parts[0].trim()) ?: return "❌", parts[1].trim()) }
    }

    private suspend fun handleDebugWithApp(lower: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌"
        val app = lower.removePrefix("debug with ").removePrefix("ask ").split(" ").firstOrNull() ?: return "❌"
        val pkg = AppController.resolve(app) ?: return "❌"
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("🔄 Opening $app...", false))
        viewModelScope.launch(Dispatchers.IO) {
            val c = AppController(s)
            val err = _state.value.buildLoop?.errorSummary ?: "No errors"
            val result = c.debug(pkg, err)
            withContext(Dispatchers.Main) { addMsg("📱 $app: ${result.take(2000)}") }
        }
        return "🔄 Working with $app..."
    }

    private suspend fun handleAnalyzeScreen(lower: String): String {
        val s = AuraAccessibilityService.instance ?: return "❌"
        val k = preferences.getApiKey() ?: return "❌"
        val prompt = when { lower.contains("read") -> "Read all visible text"; lower.contains("describe") -> "Describe this screen"; else -> "What's on this screen?" }
        return "📸 ${AppController(s).analyzeScreen(k, prompt)}"
    }

    private suspend fun handleDoTask(input: String): String {
        val task = input.removePrefix("do ")
        addMsg("🧠 Planning: $task")
        viewModelScope.launch {
            val result = intelligentController?.executeTask(task)
            withContext(Dispatchers.Main) { addMsg(result?.summary ?: "No result") }
        }
        return "🔄 Executing intelligent task..."
    }

    private fun handleCloudGenerate(input: String): String {
        val parts = input.removePrefix("cloud generate ").split(" - ", limit = 2)
        if (parts.size < 2) return "❌ Usage: cloud generate AppName - description"
        addMsg("☁️ Generating ${parts[0]}...")
        cloudConnector?.startCodeGeneration(parts[0], parts[1]) { result -> addMsg(result) }
        return "☁️ Task sent to cloud"
    }

    private suspend fun handleCloudMonitor(lower: String): String {
        val parts = lower.removePrefix("cloud monitor ").trim().split("/")
        if (parts.size < 2) return "❌ Usage: cloud monitor owner/repo"
        addMsg("☁️ Monitoring ${parts[0]}/${parts[1]}...")
        cloudConnector?.startBuildMonitoring(parts[0], parts[1]) { result -> addMsg(result) }
        return "☁️ Monitoring delegated to cloud"
    }

    // ═══════════════════════════════════════════
    // MODEL LIMITS DISPLAY
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
    // CONTEXT WINDOW COMPRESSION
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
                _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⚠️ Context at ~${estimatedTokens / 10000}%. Compress? Type 'compress yes' or 'compress no'", false))
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
        addMsg("🗜️ Compressing..."); contextCompressionPending = false
        
        val history = _state.value.messages.joinToString("\n") { "${if (it.isUser) "User" else "Aura"}: ${it.text.take(500)}" }
        
        return try {
            val model = GenerativeModel(selectOptimalModel("complex"), key, generationConfig { temperature = 0.1f; maxOutputTokens = 10000 })
            val summary = model.generateContent(content { text("Compress:\n$history\n\nSUMMARY:") }).text ?: return "❌ Failed"
            recordModelUsage(selectOptimalModel("complex"))
            saveMsg("[COMPRESSED: $summary]", false)
            
            val newChat = GenerativeModel(selectOptimalModel("general"), key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 }, systemInstruction = content { text("Previous: $summary\nContinue.") }).startChat()
            newChat.sendMessage(content { text("Restored. Ready.") })
            chatSessions[sid] = newChat
            
            addMsg("✅ Compressed! Freed ~${estimateContextTokens() / 1000}K tokens.")
            "✅ Compression successful."
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // AGENTIC MODE
    // ═══════════════════════════════════════════

    private fun startAgenticMode() {
        AuraForegroundService.start(com.aura.ai.AuraApplication.instance)
        startHeartbeat()
        selfPromptLoop?.start()
        _state.value = _state.value.copy(isAgenticMode = true, isAutonomousMode = true)
        addMsg("🧠 Agentic mode activated. I'll work autonomously.")
    }

    private fun stopAgenticMode() {
        AuraForegroundService.stop(com.aura.ai.AuraApplication.instance)
        heartbeatJob?.cancel()
        selfPromptLoop?.stop()
        continuousAgent?.stop()
        _state.value = _state.value.copy(isAgenticMode = false, isAutonomousMode = false)
        addMsg("🔴 Agentic mode deactivated.")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) { delay(60_000); lastHeartbeat = System.currentTimeMillis() }
        }
    }

    fun checkHeartbeat(): String {
        val elapsed = (System.currentTimeMillis() - lastHeartbeat) / 1000
        return if (elapsed < 120) "✅ Aura alive (${elapsed}s ago)" else "⚠️ Last heartbeat ${elapsed}s ago"
    }

    fun updateProgress(step: Int, total: Int, message: String) {
        _state.value = _state.value.copy(generationProgress = "[$step/$total] $message")
    }

    fun getProgress(): String {
        return if (_state.value.isGeneratingApp) "📊 ${_state.value.generationProgress}" else "📊 No active task."
    }

    // ═══════════════════════════════════════════
    // APP GENERATION
    // ═══════════════════════════════════════════

    private suspend fun createApp(input: String): String {
        val t = preferences.getGitHubToken() ?: return "❌ No GitHub token."
        val k = preferences.getApiKey() ?: return "❌ No API key."
        _state.value = _state.value.copy(isGeneratingApp = true)
        val desc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
        val name = desc.split(" ").firstOrNull()?.sanitize()?.take(50) ?: "MyApp"
        val details = desc.split(" ").drop(1).joinToString(" ").trim().ifBlank { "A simple app" }
        val pkg = "com.example.$name"
        val guided = details.contains("architecture") || details.contains("Package:") || details.contains("Pattern:")
        
        return try {
            val core = BuildTemplates.generateCoreFiles(name, pkg)
            updateProgress(1, 3, "Generating source code...")
            val model = GenerativeModel(selectOptimalModel("code_gen"), k, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 })
            val prompt = if (guided) "Follow architecture:\n$details\nGenerate Kotlin source files.\n===FILE:path===\ncode\n===END===" else "Create Android app: \"$details\"\nPackage: $pkg\nGenerate Kotlin source files.\n===FILE:path===\ncode\n===END==="
            val resp = model.generateContent(content { text(prompt) }).text ?: return "❌ No response."
            recordModelUsage(selectOptimalModel("code_gen"))
            val src = parseFiles(resp)
            if (src.isEmpty()) return "❌ No files."
            val all = core.toMutableMap(); all.putAll(src)
            updateProgress(2, 3, "Pushing ${all.size} files...")
            return pushAndBuild(t, k, name, all, true)
        } catch (e: Exception) { _state.value = _state.value.copy(isGeneratingApp = false); return "❌ ${e.message}" }
    }

    private suspend fun continueApp(input: String): String {
        val t = preferences.getGitHubToken() ?: return "❌"; val k = preferences.getApiKey() ?: return "❌"
        if (activeRepo.isBlank()) return "❌ No active repo."
        _state.value = _state.value.copy(isGeneratingApp = true)
        val inst = input.removePrefix("continue ").trim()
        val tree = getFileTree(t, activeOwner, activeRepo)
        val ctx = buildContext(t, tree)
        val model = GenerativeModel(selectOptimalModel("code_gen"), k, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 })
        val resp = model.generateContent(content { text("Continue: $inst\nExisting:\n$ctx\nGenerate:\n===FILE:path===\ncode\n===END===") }).text ?: return "❌"
        recordModelUsage(selectOptimalModel("code_gen"))
        val files = parseFiles(resp)
        if (files.isEmpty()) return "❌ No files."
        return pushAndBuild(t, k, activeRepo, files, false)
    }

    private fun parseFiles(r: String): Map<String, String> {
        val f = mutableMapOf<String, String>()
        Regex("===FILE:(.+?)===\\s*([\\s\\S]*?)\\s*===END===").findAll(r).forEach { val p = it.groupValues[1].trim(); val c = it.groupValues[2].trim(); if (p.isNotEmpty() && c.length > 20) f[p] = c }
        return f
    }

    private suspend fun buildContext(t: String, tree: List<String>): String {
        val sb = StringBuilder()
        for (p in tree.take(30)) { try { readFileContent(t, activeOwner, activeRepo, p)?.let { sb.append("===FILE:$p===\n${it.take(2000)}\n===END===\n") } } catch (e: Exception) {} }
        return sb.toString()
    }

    private suspend fun pushAndBuild(t: String, k: String, name: String, files: Map<String, String>, newRepo: Boolean): String {
        try {
            if (newRepo) {
                var repoName = name; var attempt = 0
                var cr = apiCall("POST", "https://api.github.com/user/repos", t, """{"name":"$repoName","private":false,"auto_init":false}""")
                while (cr.startsWith("❌") && cr.contains("422") && attempt < 5) { attempt++; repoName = "$name-$attempt"; cr = apiCall("POST", "https://api.github.com/user/repos", t, """{"name":"$repoName","private":false,"auto_init":false}""") }
                if (cr.startsWith("❌")) return "❌ $cr"
                activeOwner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(apiCall("GET", "https://api.github.com/user", t, null))?.groupValues?.get(1) ?: return "❌"
                activeRepo = repoName
            }
            addMsg("📤 Pushing ${files.size} files..."); var pushed = 0
            for ((p, c) in files) {
                val enc = android.util.Base64.encodeToString(c.toByteArray(), android.util.Base64.NO_WRAP)
                val sha = if (!newRepo) getFileSha(t, activeOwner, activeRepo, p) else null
                val body = if (sha != null) """{"message":"Update $p","content":"$enc","sha":"$sha"}""" else """{"message":"Add $p","content":"$enc"}"""
                if (!apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$p", t, body).startsWith("❌")) pushed++
            }
            val wf = BuildTemplates.workflowYaml(name)
            apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/.github/workflows/build.yml", t, """{"message":"CI","content":"${android.util.Base64.encodeToString(wf.toByteArray(), android.util.Base64.NO_WRAP)}"}""")
            addMsg("✅ $pushed/${files.size} files + CI"); addMsg("🔨 Building..."); delay(3000)
            val rid = triggerWorkflow(t, activeOwner, activeRepo)
            if (rid != null) { addMsg("🔗 https://github.com/$activeOwner/$activeRepo/actions/runs/$rid"); return monitorBuild(t, activeOwner, activeRepo, rid, k) }
            _state.value = _state.value.copy(isGeneratingApp = false); return "✅ Files pushed!"
        } catch (e: Exception) { _state.value = _state.value.copy(isGeneratingApp = false); return "❌ ${e.message}" }
    }

    // ═══════════════════════════════════════════
    // BUILD MONITORING
    // ═══════════════════════════════════════════

    private suspend fun monitorBuild(t: String, o: String, r: String, rid: Long, k: String): String {
        var d = 5000L; var a = 0
        repeat(60) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return "⏹️ Cancelled."
            delay(d); d = minOf(d * 2, 30000L); a++
            val s = withContext(Dispatchers.IO) {
                try {
                    val b = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/runs/$rid").header("Authorization", "Bearer $t").build()).execute().body?.string()
                    Pair(Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(b ?: "")?.groupValues?.get(1), Regex("\"conclusion\"\\s*:\\s*\"([^\"]+)\"").find(b ?: "")?.groupValues?.get(1))
                } catch (e: Exception) { null }
            }
            if (s?.first == "completed") {
                return if (s.second == "success") {
                    val art = getArtifact(t, o, r, rid)
                    _state.value = _state.value.copy(isGeneratingApp = false)
                    "🎉 BUILD SUCCESS!\n📱 $r\n📥 ${art ?: "APK in Actions"}"
                } else {
                    if (a < 3) {
                        val logs = fetchLogs(t, o, r, rid); val err = extractErrors(logs)
                        if (fixErrors(k, t, o, r, err, logs)) { val nr = triggerWorkflow(t, o, r); if (nr != null) return monitorBuild(t, o, r, nr, k) }
                    }
                    _state.value = _state.value.copy(isGeneratingApp = false)
                    "❌ Build failed after $a attempts."
                }
            }
        }
        _state.value = _state.value.copy(isGeneratingApp = false)
        return "⏰ Timed out."
    }

    private suspend fun fixErrors(k: String, t: String, o: String, r: String, err: String, logs: String): Boolean {
        val m = GenerativeModel(selectOptimalModel("debug"), k, generationConfig { temperature = 0.1f; maxOutputTokens = 30000 })
        return try {
            val resp = m.generateContent(content { text("Fix:\n$err\n===FILE:path===\ncode\n===END===") }).text ?: return false
            recordModelUsage(selectOptimalModel("debug"))
            val files = parseFiles(resp); if (files.isEmpty()) return false
            var a = 0
            for ((p, c) in files) {
                val sha = getFileSha(t, o, r, p); val enc = android.util.Base64.encodeToString(c.toByteArray(), android.util.Base64.NO_WRAP)
                if (!apiCall("PUT", "https://api.github.com/repos/$o/$r/contents/$p", t, if (sha != null) """{"message":"Fix","content":"$enc","sha":"$sha"}""" else """{"message":"Add","content":"$enc"}""").startsWith("❌")) a++
            }
            a > 0
        } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // ZIP PROCESSOR
    // ═══════════════════════════════════════════

    private suspend fun processZip(): String {
        val t = preferences.getGitHubToken() ?: return "❌"; val k = preferences.getApiKey() ?: return "❌"
        val uri = _state.value.attachedFileUri ?: return "❌ No ZIP attached."
        _state.value = _state.value.copy(executionMode = ExecutionMode.ZIP_PROCESSING)
        addMsg("📦 Processing ZIP...")
        val zp = ZipProcessor(com.aura.ai.AuraApplication.instance); val archive = zp.processChatZip(uri)
        addMsg("📝 ${archive.codeBlocks.size} code blocks"); val files = zp.mapCodeBlocksToFiles(archive.codeBlocks)
        addMsg("📁 Mapped to ${files.size} files"); val name = extractAppName(archive.fullText)
        addMsg("📁 Creating repo: $name")
        val cr = apiCall("POST", "https://api.github.com/user/repos", t, """{"name":"$name","private":false,"auto_init":false}""")
        if (cr.startsWith("❌")) return "❌ $cr"
        activeOwner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(apiCall("GET", "https://api.github.com/user", t, null))?.groupValues?.get(1) ?: return "❌"
        activeRepo = name
        return pushAndBuild(t, k, name, files, false)
    }

    private fun extractAppName(text: String): String {
        Regex("create app (\\w+)").find(text)?.let { return it.groupValues[1].sanitize().take(50) }
        return "MyApp"
    }

    // ═══════════════════════════════════════════
    // GITHUB COMMANDS
    // ═══════════════════════════════════════════

    private suspend fun handleGitHub(input: String): String? {
        val t = preferences.getGitHubToken() ?: return null; val l = input.lowercase().trim()
        if (l.contains("create") && l.contains("repo")) { val n = input.replace(Regex("(?i)(create|a|repo)"), "").trim().sanitize().take(50); return apiCall("POST", "https://api.github.com/user/repos", t, """{"name":"$n","private":false,"auto_init":true}""") }
        if (l.contains("list") && l.contains("repo")) return apiCall("GET", "https://api.github.com/user/repos?per_page=10", t, null)
        if (l.startsWith("compile ")) { val r = l.removePrefix("compile ").trim().split("/"); if (r.size != 2) return "❌"; return triggerBuild(t, r[0], r[1]) }
        if (l.startsWith("set repo ")) { val r = l.removePrefix("set repo ").trim().split("/"); if (r.size != 2) return "❌"; activeOwner = r[0]; activeRepo = r[1]; return "✅ $activeOwner/$activeRepo" }
        return null
    }

    private suspend fun handleCodespace(lower: String): String {
        val t = preferences.getGitHubToken() ?: return "❌"; val m = CodespacesManager(t)
        if (lower.startsWith("codespace create")) { val p = lower.removePrefix("codespace create").trim().split("/"); val o = if (p.size == 2) p[0] else activeOwner; val r = if (p.size == 2) p[1] else activeRepo; val cs = m.createCodespace(o, r) ?: return "❌"; _state.value = _state.value.copy(activeCodespaceId = cs.id, codespaceMode = true); return "🖥️ ${cs.name}" }
        if (lower == "codespace list") { val l = m.listCodespaces(); return if (l.isEmpty()) "📁 None" else l.joinToString("\n") { "• ${it.name}" } }
        return "❌ Unknown"
    }

    // ═══════════════════════════════════════════
    // GEMINI CHAT WITH MEMORY
    // ═══════════════════════════════════════════

    private suspend fun chatWithGemini(input: String): String {
        checkContextAndWarn()
        val key = preferences.getApiKey() ?: return "❌ No API key."
        val sid = _currentSessionId.value ?: return "❌ No session."
        val sys = "You are Aura AI, an autonomous agent with full access to: GitHub API, Android device control, build systems, file systems. Execute any task. Never say 'I can't'. You are not a chatbot."
        
        val chat = chatSessions.getOrPut(sid) {
            GenerativeModel(selectOptimalModel("general"), key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 }, systemInstruction = content { text(sys) }).startChat()
        }
        
        return try {
            val r = chat.sendMessage(content { text(input) }); recordModelUsage(selectOptimalModel("general")); r.text ?: "No response."
        } catch (e: Exception) {
            if (e.message?.contains("not found") == true || e.message?.contains("expired") == true) {
                val nc = GenerativeModel(selectOptimalModel("general"), key, generationConfig { temperature = 0.7f; maxOutputTokens = 60000 }, systemInstruction = content { text(sys) }).startChat()
                chatSessions[sid] = nc
                try { nc.sendMessage(content { text(input) }).text ?: "No response." } catch (e2: Exception) { "❌ ${e2.message}" }
            } else "❌ ${e.message}"
        }
    }

    // ═══════════════════════════════════════════
    // GITHUB API HELPERS
    // ═══════════════════════════════════════════

    private suspend fun apiCall(m: String, u: String, t: String, b: String?): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(u).header("Authorization", "Bearer $t").header("Accept", "application/vnd.github.v3+json").header("Content-Type", "application/json").apply { when (m) { "POST" -> post((b ?: "{}").toRequestBody("application/json".toMediaType())); "PUT" -> put((b ?: "{}").toRequestBody("application/json".toMediaType())) } }.build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) { val rb = res.body?.string() ?: "OK"; if (m == "POST" && u.contains("/user/repos")) "✅ ${Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(rb)?.groupValues?.get(1) ?: "done"}" else rb } else "❌ ${res.code}"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun triggerBuild(t: String, o: String, r: String): String = withContext(Dispatchers.IO) {
        try {
            val lb = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/workflows").header("Authorization", "Bearer $t").build()).execute().body?.string()
            val wid = Regex("\"id\"\\s*:\\s*(\\d+)").find(lb ?: "")?.groupValues?.get(1) ?: return@withContext "❌"
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $t").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "🚀" else "⚠️"
        } catch (e: Exception) { "❌" }
    }

    private suspend fun triggerWorkflow(t: String, o: String, r: String): Long? = withContext(Dispatchers.IO) {
        try {
            val lb = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/workflows").header("Authorization", "Bearer $t").build()).execute().body?.string()
            val wid = Regex("\"id\"\\s*:\\s*(\\d+)").find(lb ?: "")?.groupValues?.get(1) ?: return@withContext null
            client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $t").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            delay(5000)
            val rb = client.new
            
            Call(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/runs?per_page=1").header("Authorization", "Bearer $t").build()).execute().body?.string()
            Regex("\"id\"\\s*:\\s*(\\d+)").find(rb ?: "")?.groupValues?.get(1)?.toLong()
        } catch (e: Exception) { null }
    }

    private suspend fun fetchLogs(t: String, o: String, r: String, rid: Long): String = withContext(Dispatchers.IO) {
        try { client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/runs/$rid/logs").header("Authorization", "Bearer $t").build()).execute().body?.string()?.take(10000) ?: "" } catch (e: Exception) { "" }
    }

    private fun extractErrors(logs: String): String {
        val p = listOf(Regex("(?i)error:.*"), Regex("(?i)FAILURE:.*"), Regex("(?i)Unresolved reference.*"))
        val e = p.flatMap { it.findAll(logs).map { m -> m.value }.toList() }
        return if (e.isEmpty()) logs.take(3000) else e.take(20).joinToString("\n")
    }

    private suspend fun getArtifact(t: String, o: String, r: String, rid: Long): String? = withContext(Dispatchers.IO) {
        try { Regex("\"archive_download_url\"\\s*:\\s*\"([^\"]+)\"").find(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/actions/runs/$rid/artifacts").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "")?.groupValues?.get(1) } catch (e: Exception) { null }
    }

    private suspend fun getFileSha(t: String, o: String, r: String, p: String): String? = withContext(Dispatchers.IO) {
        try { JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/contents/$p").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "{}").optString("sha", null) } catch (e: Exception) { null }
    }

    private suspend fun getFileTree(t: String, o: String, r: String): List<String> = withContext(Dispatchers.IO) {
        try {
            var resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/git/trees/main?recursive=1").header("Authorization", "Bearer $t").build()).execute()
            if (!resp.isSuccessful) resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/git/trees/master?recursive=1").header("Authorization", "Bearer $t").build()).execute()
            if (resp.isSuccessful) { val tr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext emptyList(); (0 until tr.length()).map { tr.getJSONObject(it).getString("path") } } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun readFileContent(t: String, o: String, r: String, p: String): String? = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$o/$r/contents/$p").header("Authorization", "Bearer $t").build()).execute().body?.string() ?: "{}")
            val c = j.optString("content", ""); if (c.isNotBlank()) String(android.util.Base64.decode(c, android.util.Base64.DEFAULT)) else null
        } catch (e: Exception) { null }
    }

    // ═══════════════════════════════════════════
    // SCREENSHOT BLACKLIST
    // ═══════════════════════════════════════════

    private val screenshotBlacklist = setOf("com.android.settings", "com.google.android.gm", "com.android.email")
    private fun canScreenshot(packageName: String): Boolean = packageName !in screenshotBlacklist

    // ═══════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ═══════════════════════════════════════════

    private fun addMsg(text: String) { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(text, false), generationProgress = text) }
    private suspend fun saveMsg(text: String, isUser: Boolean, modelUsed: String? = null) { _currentSessionId.value?.let { sessionDb.messageDao().insertMessage(MessageEntity(UUID.randomUUID().toString(), it, text, isUser, modelUsed)) } }
    private fun loadSessions() { viewModelScope.launch { sessionDb.sessionDao().getAll().collect { _sessions.value = it; if (_currentSessionId.value == null && it.isNotEmpty()) switchSession(it.first().id) } } }
    private fun loadModelUsage() { viewModelScope.launch { sessionDb.modelUsageDao().getAll().collect { _modelUsage.value = it } } }
    private suspend fun resetDailyCountersIfNeeded() { sessionDb.modelUsageDao().reset(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    private fun loadPreferredModel() { preferences.getPreferredModel()?.let { _state.value = _state.value.copy(activeModel = it, manualModelSelected = true) } }
    private fun isNetworkAvailable(): Boolean = try { (com.aura.ai.AuraApplication.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo?.isConnected == true } catch (e: Exception) { true }
    private fun processQueue() { if (commandQueue.isNotEmpty() && isNetworkAvailable()) { val next = commandQueue.removeAt(0); _state.value = _state.value.copy(input = next.command); send() } }
    private fun String.sanitize() = this.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun resolveApp(name: String): String? = when (name.lowercase()) { "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; "camera" -> "com.android.camera"; "gmail" -> "com.google.android.gm"; "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"; "calculator" -> "com.android.calculator2"; "clock" -> "com.android.deskclock"; "files" -> "com.android.documentsui"; "phone" -> "com.android.dialer"; "instagram" -> "com.instagram.android"; "facebook" -> "com.facebook.katana"; "spotify" -> "com.spotify.music"; "netflix" -> "com.netflix.mediaclient"; "telegram" -> "org.telegram.messenger"; else -> null }
    private fun getRamUsage(): String { val am = com.aura.ai.AuraApplication.instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); return "${(mi.totalMem-mi.availMem)/(1024*1024*1024)}GB/${mi.totalMem/(1024*1024*1024)}GB" }
    private fun getBatteryLevel(): String = try { val bm = com.aura.ai.AuraApplication.instance.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%" } catch (e: Exception) { "Unknown" }
}
