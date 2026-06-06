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

// ═══════════════════════════════════════════════════════════════════
// SECTION 1: PUBLIC DATA CLASSES
// ═══════════════════════════════════════════════════════════════════

data class ChatMessage(val text: String, val isUser: Boolean, val isStreaming: Boolean = false)

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
    val artifactUrl: String? = null
)

enum class BuildStatus {
    IDLE, BUILDING, WAITING_FOR_BUILD, BUILD_SUCCESS, ANALYZING_ERROR,
    FIXING, RETRYING, FAILED, DOWNLOADING_ARTIFACT, VALIDATING
}

data class AgentUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("""
╔══════════════════════════════════════════╗
║     ⚡ AURA AI - NEURAL CORE ACTIVE ⚡     ║
╠══════════════════════════════════════════╣
║  📱 PHONE CONTROL                        ║
║  💻 APP GENERATION (Autonomous)          ║
║  🐙 GITHUB COMMANDS                      ║
║  🔍 REPO ANALYSIS & TRANSFER             ║
║  📂 FILE SYSTEM                          ║
║  📊 SYSTEM                               ║
║  ⏯️  CONTROL                              ║
║  🧠 AGENTIC MODE                         ║
║  ☁️  CLOUD EXECUTION                      ║
╚══════════════════════════════════════════╝
        """.trimIndent(), false)
    ),
    val input: String = "",
    val loading: Boolean = false,
    val isExecuting: Boolean = false,
    val currentTask: String = "",
    val executionMode: ExecutionMode = ExecutionMode.IDLE,
    val activeModel: String = "gemini-2.5-flash",
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
    val attachedFileUri: android.net.Uri? = null,
    val attachedFileName: String = "",
    val isOnline: Boolean = true,
    val totalApiCalls: Int = 0,
    val isAutonomousMode: Boolean = false,
    val isAgenticMode: Boolean = false
)

enum class ExecutionMode {
    IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION,
    FILE_OPERATION, REPO_ANALYSIS, FEATURE_TRANSFER, CODESPACE_GENERATION,
    BATCH_FILE_PUSH, IMAGE_ANALYSIS, FILE_UPLOAD, STREAMING_CHAT,
    APP_CONTROL, ZIP_PROCESSING, CONTEXT_COMPRESSION, AUTONOMOUS,
    CLOUD_EXECUTION, AGENTIC
}

// ═══════════════════════════════════════════════════════════════════
// SECTION 2: INTERNAL DATA CLASSES
// ═══════════════════════════════════════════════════════════════════

private data class AppArchitecture(
    val files: List<String>,
    val techStack: String,
    val dependencies: List<String>,
    val structure: String
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
    private var projectContext = ProjectContext(packageName = "")
    private val commandQueue = mutableListOf<QueuedCommand>()
    private var contextCompressionPending = false
    private var heartbeatJob: Job? = null
    private var lastHeartbeat = System.currentTimeMillis()
    private var screenshotInterval = 30_000L
    private var screenshotJob: Job? = null
    private var lastScreenshotAnalysis = ""

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

    private fun formatModelName(name: String): String = when (name) {
        "gemini-3.5-flash" -> "3.5 Flash ⚡"; "gemini-3.1-flash-lite" -> "3.1 Flash-Lite 🚀"
        "gemini-2.5-flash" -> "2.5 Flash 💎"; "gemini-2.5-flash-lite" -> "2.5 Flash-Lite ⚡"
        "gemini-2.0-flash" -> "2.0 Flash 📦"; "gemini-embedding-2" -> "Embedding 2 🔍"
        else -> name.replace("gemini-", "").replace("-", " ").uppercase()
    }

    // ═══════════════════════════════════════════
    // SECTION 3.4: AGENTIC COMPONENTS INITIALIZATION
    // ═══════════════════════════════════════════

    // Agentic core
    private val consciousness = Consciousness()
    private val patternRecognizer = PatternRecognizer()
    private val goalEngine = GoalEngine()

    // Agentic memory
    private val experienceBuffer = ExperienceBuffer()
    private val knowledgeGraph = KnowledgeGraph()
    private val longTermMemory = LongTermMemory(com.aura.ai.AuraApplication.instance)

    // Agentic planning
    private val priorityEngine = PriorityEngine()
    private val resourcePlanner = ResourcePlanner()
    private val taskDecomposer = TaskDecomposer()
    private val dependencyResolver = DependencyResolver()

    // Agentic execution
    private val trustManager = TrustManager(com.aura.ai.AuraApplication.instance)
    private var autonomousExecutor: AutonomousExecutor? = null
    private var taskPlanner: TaskPlanner? = null
    private var continuousAgent: ContinuousAgent? = null

    // Services
    private var intelligentController: IntelligentController? = null
    private var cloudConnector: CloudConnector? = null
    private var apiKeyManager: ApiKeyManager? = null
    private var fileHandler: FileAttachmentHandler? = null
    private var auraBrain: AuraBrain? = null
    private var selfPromptLoop: SelfPromptLoop? = null

    private fun initializeAgenticComponents() {
        // Data layer
        apiKeyManager = ApiKeyManager(com.aura.ai.AuraApplication.instance)
        fileHandler = FileAttachmentHandler(com.aura.ai.AuraApplication.instance)

        // Cloud connector
        val hfToken = apiKeyManager?.get(ApiKeyManager.ApiType.HUGGINGFACE)
        val spaceUrl = preferences.getHfSpaceUrl()
        if (!hfToken.isNullOrBlank()) {
            cloudConnector = CloudConnector(spaceUrl, hfToken)
        }

        // Phone control
        val service = AuraAccessibilityService.instance
        if (service != null) {
            val ac = AppController(service)
            val apiKey = preferences.getApiKey() ?: ""
            taskPlanner = TaskPlanner(ac, apiKey)
            intelligentController = IntelligentController(ac, apiKey)
        }

        // Agentic execution
        autonomousExecutor = AutonomousExecutor(consciousness, experienceBuffer, taskDecomposer)

        // Agentic brain
        auraBrain = AuraBrain(
            consciousness = consciousness,
            patternRecognizer = patternRecognizer,
            priorityEngine = priorityEngine,
            resourcePlanner = resourcePlanner,
            goalEngine = goalEngine,
            taskDecomposer = taskDecomposer,
            autonomousExecutor = autonomousExecutor!!,
            trustManager = trustManager,
            experienceBuffer = experienceBuffer,
            knowledgeGraph = knowledgeGraph
        )

        // Self-prompting loop
        selfPromptLoop = SelfPromptLoop(
            auraBrain = auraBrain!!,
            consciousness = consciousness,
            onDecisionReady = { decision ->
                viewModelScope.launch {
                    autonomousExecutor?.execute(decision.suggestedAction)
                    addMsg("🤖 Auto-executed: ${decision.suggestedAction}")
                }
            },
            onApprovalNeeded = { action ->
                viewModelScope.launch {
                    addMsg("🔔 Approval needed: $action — type 'approve' or 'reject'")
                }
            }
        )

        // Continuous agent
        if (taskPlanner != null) {
            continuousAgent = ContinuousAgent(
                planner = taskPlanner!!,
                consciousness = consciousness,
                onProgress = { msg -> viewModelScope.launch { addMsg(msg) } },
                onComplete = { msg -> viewModelScope.launch { addMsg(msg) } },
                onError = { msg -> viewModelScope.launch { addMsg(msg) } }
            )
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.5: NETWORK & QUEUE
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
    // SECTION 3.6: PUBLIC INTERFACE
    // ═══════════════════════════════════════════

    fun updateInput(text: String) { _state.value = _state.value.copy(input = text) }
    fun toggleDrawer() { _state.value = _state.value.copy(showDrawer = !_state.value.showDrawer) }
    fun toggleModelDashboard() { _state.value = _state.value.copy(showModelDashboard = !_state.value.showModelDashboard) }

    fun attachFile(uri: android.net.Uri, fileName: String) {
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
            ModelInfo(
                name = name,
                displayName = formatModelName(name),
                strength = spec.description,
                dailyRequests = usage?.dailyRequests ?: 0,
                dailyLimit = spec.rpd,
                isInCooldown = isModelInCooldown(name),
                isSelected = name == _state.value.activeModel
            )
        }
    }

    fun createNewSession() {
        chatSessions.clear()
        viewModelScope.launch {
            val s = SessionEntity(
                id = UUID.randomUUID().toString(),
                title = "New Session",
                selectedModel = _state.value.activeModel
            )
            sessionDb.sessionDao().insertSession(s)
            switchSession(s.id)
        }
    }

    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            sessionLoadingJob?.cancel()
            sessionLoadingJob = viewModelScope.launch {
                val s = sessionDb.sessionDao().getSession(sessionId) ?: return@launch
                _currentSessionId.value = sessionId
                preferences.setLastSessionId(sessionId)
                val msgs = sessionDb.messageDao().getMessagesForSessionOnce(sessionId)
                val chatMessages = msgs.map { ChatMessage(text = it.text, isUser = it.isUser) }
                _state.value = _state.value.copy(
                    messages = if (chatMessages.isEmpty()) _state.value.messages else chatMessages,
                    currentSessionId = sessionId,
                    manualModelSelected = true,
                    activeModel = s.selectedModel,
                    buildLoop = null,
                    isGeneratingApp = false
                )
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
                val remaining = _sessions.value.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) switchSession(remaining.first().id) else createNewSession()
            }
        }
    }

    fun send() {
        val msg = _state.value.input.trim()
        if (msg.isBlank()) return
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage(msg, true),
            input = "",
            loading = true
        )
        if (handleControl(msg)) return

        if (!isNetworkAvailable()) {
            commandQueue.add(QueuedCommand(UUID.randomUUID().toString(), msg, System.currentTimeMillis()))
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage("📶 Offline - Queued. Will execute when connected.", false),
                loading = false
            )
            return
        }

        patternRecognizer.observeAction(msg)

        taskJob = viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, currentTask = msg)
            saveMsg(msg, true)
            val result = execute(msg)
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage(result, false),
                loading = false,
                isExecuting = false,
                currentTask = "",
                executionMode = ExecutionMode.IDLE
            )
            saveMsg(result, false, modelUsed = _state.value.activeModel)
        }
    }

    private fun handleControl(input: String): Boolean {
        return when (input.lowercase().trim()) {
            "stop", "cancel" -> {
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
            "approve" -> {
                addMsg("✅ Approved. Executing...")
                true
            }
            "reject" -> {
                addMsg("❌ Rejected.")
                true
            }
            "queue" -> {
                processQueue()
                true
            }
            else -> false
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.7: COMMAND ROUTER
    // ═══════════════════════════════════════════

    private suspend fun execute(input: String): String {
        val lower = input.lowercase().trim()

        // System commands
        if (lower == "device info") return getDeviceInfo()
        if (lower == "time") return getTime()
        if (lower == "models" || lower == "model limits") return getModelLimits()
        if (lower == "context status" || lower == "context") return getContextStatus()
        if (lower == "compress yes" || lower == "compress") return compressContextWindow()
        if (lower == "compress no") { contextCompressionPending = false; return "✅ Continuing without compression." }
        if (lower == "heartbeat" || lower == "status") return checkHeartbeat()
        if (lower == "progress" || lower == "what are you doing") return getProgress()
        if (lower == "keys" || lower == "api keys") return apiKeyManager?.report() ?: "API key manager not available"
        if (lower == "patterns") return patternRecognizer.report()
        if (lower == "trust report") return trustManager.report()
        if (lower == "memory") return "🧠 ${longTermMemory.count()} memories stored"
        if (lower == "usage") return "📡 API Calls: ${_state.value.totalApiCalls} today"

        // Agentic commands
        if (lower == "start agentic" || lower == "jarvis mode") { startAgenticMode(); return "🧠 Agentic mode activated." }
        if (lower == "stop agentic") { stopAgenticMode(); return "🔴 Agentic mode deactivated." }
        if (lower == "start agent") { continuousAgent?.start(); return "🤖 Continuous agent started" }
        if (lower == "stop agent") { continuousAgent?.stop(); return "🔴 Continuous agent stopped" }
        if (lower == "queue") return continuousAgent?.status() ?: "No agent running"

        // Screenshot monitoring
        if (lower == "start screenshots" || lower == "monitor screen") { startAutoScreenshots("Manual monitoring"); return "📸 Screenshot monitoring started (every ${screenshotInterval / 1000}s)." }
        if (lower == "stop screenshots") { stopAutoScreenshots(); return "📸 Screenshot monitoring stopped." }
        if (lower.startsWith("screenshot interval ")) {
            val sec = lower.removePrefix("screenshot interval ").trim().toIntOrNull() ?: 30
            setScreenshotInterval(sec)
            return "📸 Screenshot interval set to ${sec}s."
        }

        // Cloud commands
        if (lower == "cloud status") {
            val result = cloudConnector?.checkHealth()
            return if (result?.success == true) "☁️ Cloud connected and healthy" else "☁️ Cloud offline or not configured"
        }
        if (lower.startsWith("cloud generate ")) {
            val parts = input.removePrefix("cloud generate ").split(" - ", limit = 2)
            if (parts.size < 2) return "❌ Usage: cloud generate AppName - description"
            addMsg("☁️ Sending to cloud: ${parts[0]}")
            cloudConnector?.startCodeGeneration(parts[0], parts[1]) { result -> addMsg(result) }
            return "☁️ Task delegated to cloud. I'll update you when done."
        }
        if (lower.startsWith("cloud monitor ")) {
            val parts = lower.removePrefix("cloud monitor ").trim().split("/")
            if (parts.size < 2) return "❌ Usage: cloud monitor owner/repo"
            addMsg("☁️ Starting cloud monitoring: ${parts[0]}/${parts[1]}")
            cloudConnector?.startBuildMonitoring(parts[0], parts[1]) { result -> addMsg(result) }
            return "☁️ Build monitoring delegated to cloud"
        }

        // AI-Planned phone tasks
        if (lower.startsWith("do ")) {
            val task = input.removePrefix("do ")
            addMsg("🧠 Planning and executing: $task")
            viewModelScope.launch {
                val result = intelligentController?.executeTask(task)
                withContext(Dispatchers.Main) {
                    addMsg(result?.summary ?: "No result from intelligent controller")
                }
            }
            return "🔄 Executing intelligent task..."
        }

        // Phone control
        if (lower.startsWith("open ")) {
            val app = lower.removePrefix("open ").trim()
            val pkg = resolveApp(app) ?: return "❌ Unknown app: $app"
            return try {
                val intent = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(pkg)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                com.aura.ai.AuraApplication.instance.startActivity(intent)
                "✅ Opened $app"
            } catch (e: Exception) { "❌ Error: ${e.message}" }
        }
        if (lower == "home") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "🏠 Home" }
        if (lower == "back") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "⬅️ Back" }
        if (lower == "recents") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); return "📱 Recent apps" }
        if (lower == "notifications") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); return "🔔 Notifications" }
        if (lower == "screenshot") { AuraAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "📸 Screenshot taken" }
        if (lower.startsWith("control ")) { return handleControlApp(input) }
        if (lower.startsWith("send to ")) { return handleSendToApp(input) }
        if (lower.startsWith("debug with ") || lower.startsWith("ask ")) { return handleDebugWithApp(lower) }
        if (lower.startsWith("analyze screen") || lower.startsWith("what's on screen")) { return handleAnalyzeScreen(lower) }
        if (lower.startsWith("ask gemini app") || lower.startsWith("gemini native")) { return handleAskGeminiApp(input) }

        // App generation
        if (lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) {
            if (lower.contains("repo")) return handleGitHub(input) ?: "❌ No GitHub token configured"
            _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP)
            return createApp(input)
        }
        if (lower.startsWith("continue ")) {
            _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP)
            return continueApp(input)
        }

        // ZIP processing
        if (lower.startsWith("process zip") || lower.startsWith("deploy zip")) {
            _state.value = _state.value.copy(executionMode = ExecutionMode.ZIP_PROCESSING)
            return processZip()
        }

        // Codespaces
        if (lower.startsWith("codespace ")) { return handleCodespace(lower) }

        // GitHub commands
        return handleGitHub(input) ?: chatWithGemini(input)
    }

    // ═══════════════════════════════════════════
    // SECTION 3.8: SYSTEM COMMAND HANDLERS
    // ═══════════════════════════════════════════

    private fun getDeviceInfo(): String {
        val ram = getRamUsage()
        val storage = getStorageInfo()
        val battery = getBatteryLevel()
        return """
📱 Device Info:
• Model: ${Build.MODEL}
• Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
• RAM: $ram
• Storage: $storage
• Battery: $battery
• CPU: ${Runtime.getRuntime().availableProcessors()} cores
• App: Aura AI v2.0.0
        """.trimIndent()
    }

    private fun getTime(): String {
        return "🕐 ${SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm:ss z", Locale.getDefault()).format(Date())}"
    }

    // ═══════════════════════════════════════════
    // SECTION 3.9: MODEL LIMITS DISPLAY
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
        val filled = "█".repeat(percent)
        val empty = "░".repeat(10 - percent)
        return "$filled$empty ${(used.toFloat() / limit * 100).toInt()}%"
    }

    // ═══════════════════════════════════════════
    // SECTION 3.10: CONTEXT WINDOW COMPRESSION
    // ═══════════════════════════════════════════

    private fun getContextStatus(): String {
        val estimated = estimateContextTokens()
        val percent = estimated / 10000
        return "📊 Context: ~${estimated}/1,000,000 tokens (${percent}% used)\n💡 ${100 - percent}% remaining"
    }

    private suspend fun checkContextAndWarn() {
        val estimatedTokens = estimateContextTokens()
        if (estimatedTokens > 850_000 && !contextCompressionPending) {
            contextCompressionPending = true
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatMessage(
                        "⚠️ Context window at ~${estimatedTokens / 10000}%.\nCompress to free space? Type 'compress yes' or 'compress no'",
                        false
                    )
                )
            }
        }
    }

    private fun estimateContextTokens(): Long {
        var totalChars = 0L
        for (msg in _state.value.messages.takeLast(50)) {
            totalChars += msg.text.length
        }
        return totalChars * 2 / 3
    }

    private suspend fun compressContextWindow(): String {
        val key = preferences.getApiKey() ?: return "❌ No Gemini API key set."
        val sid = _currentSessionId.value ?: return "❌ No active session."
        addMsg("🗜️ Compressing context window...")
        contextCompressionPending = false

        val history = _state.value.messages.joinToString("\n") { "${if (it.isUser) "User" else "Aura"}: ${it.text.take(500)}" }

        return try {
            val model = GenerativeModel(
                selectOptimalModel("complex"), key,
                generationConfig { temperature = 0.1f; maxOutputTokens = 10000 }
            )
            val prompt = "Compress this conversation into a dense summary. Keep: project state, architecture, files, errors, preferences, pending tasks.\n\n$history\n\nCOMPRESSED SUMMARY:"
            val summary = model.generateContent(content { text(prompt) }).text ?: return "❌ Compression failed."
            recordModelUsage(selectOptimalModel("complex"))

            saveMsg("[COMPRESSED CONTEXT: $summary]", false)

            val newChat = GenerativeModel(
                selectOptimalModel("general"), key,
                generationConfig { temperature = 0.7f; maxOutputTokens = 60000 },
                systemInstruction = content { text("Previous session summary:\n$summary\n\nContinue from here.") }
            ).startChat()
            newChat.sendMessage(content { text("Restored from compressed context. Ready to continue.") })
            chatSessions[sid] = newChat

            addMsg("✅ Context compressed! Freed ~${estimateContextTokens() / 1000}K tokens.")
            "✅ Compression successful. Continuing with fresh context."
        } catch (e: Exception) {
            "❌ Compression failed: ${e.message}"
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.11: AGENTIC & AUTONOMOUS MODE
    // ═══════════════════════════════════════════

    fun startAutonomousMode() {
        AuraForegroundService.start(com.aura.ai.AuraApplication.instance)
        startHeartbeat()
        startAutoScreenshots("Autonomous mode - monitoring all tasks")
        _state.value = _state.value.copy(isAutonomousMode = true)
        addMsg("🤖 Autonomous mode activated. I'll work until done.")
    }

    fun stopAutonomousMode() {
        stopAutoScreenshots()
        AuraForegroundService.stop(com.aura.ai.AuraApplication.instance)
        heartbeatJob?.cancel()
        _state.value = _state.value.copy(isAutonomousMode = false)
        addMsg("🔴 Autonomous mode deactivated.")
    }

    private fun startAgenticMode() {
        AuraForegroundService.start(com.aura.ai.AuraApplication.instance)
        startHeartbeat()
        selfPromptLoop?.start()
        _state.value = _state.value.copy(isAgenticMode = true, isAutonomousMode = true)
        addMsg("🧠 Agentic mode activated. I'll think and act autonomously.")
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
            while (isActive) {
                delay(60_000)
                lastHeartbeat = System.currentTimeMillis()
            }
        }
    }

    fun checkHeartbeat(): String {
        val elapsed = (System.currentTimeMillis() - lastHeartbeat) / 1000
        return if (elapsed < 120) "✅ Aura is alive (${elapsed}s ago)" else "⚠️ Last heartbeat ${elapsed}s ago. Aura may be unresponsive."
    }

    fun updateProgress(step: Int, total: Int, message: String) {
        _state.value = _state.value.copy(
            generationProgress = "[$step/$total] $message"
        )
    }

    fun getProgress(): String {
        return if (_state.value.isGeneratingApp || _state.value.buildLoop != null) {
            "📊 Progress: ${_state.value.generationProgress}"
        } else {
            "📊 No active task. Everything is idle."
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.12: SCREENSHOT AUTO-MONITORING
    // ═══════════════════════════════════════════

    fun setScreenshotInterval(seconds: Int) {
        screenshotInterval = seconds * 1000L
    }

    fun startAutoScreenshots(taskDescription: String) {
        screenshotJob?.cancel()
        screenshotJob = viewModelScope.launch {
            while (isActive) {
                delay(screenshotInterval)
                captureAndAnalyze(taskDescription)
            }
        }
    }

    fun stopAutoScreenshots() {
        screenshotJob?.cancel()
        addMsg("📸 Screenshot monitoring stopped.")
    }

    private suspend fun captureAndAnalyze(taskDescription: String) {
        val service = AuraAccessibilityService.instance ?: return
        val key = preferences.getApiKey() ?: return
        val controller = AppController(service)
        val currentApp = controller.getCurrentApp()

        if (!canScreenshot(currentApp)) return

        val prompt = """
You are monitoring an automated task on an Android phone.

TASK: $taskDescription
CURRENT APP: $currentApp
LAST ANALYSIS: $lastScreenshotAnalysis

Analyze this screenshot and answer:
1. What do you see on the screen?
2. Has the task made progress?
3. Is the task COMPLETE?
4. If there's an ERROR, what exactly is the error?
5. What should be the NEXT ACTION?

Respond in this format:
STATUS: [WAITING|COMPLETE|ERROR|ACTION_NEEDED]
ACTION: [none|extract_code|tap:button_name|type:text|retry|stop]
DETAILS: [what you see and why]
        """.trimIndent()

        try {
            val analysis = controller.analyzeScreen(key, prompt)
            lastScreenshotAnalysis = analysis

            when {
                analysis.contains("STATUS: COMPLETE") || analysis.contains("STATUS: ERROR") -> {
                    addMsg("📸 Screenshot detected status change: ${analysis.lines.firstOrNull()?.take(200) ?: "Status change"}")
                    if (analysis.contains("ACTION: extract_code")) {
                        val code = extractCodeFromAnalysis(analysis)
                        if (code.isNotEmpty()) {
                            val token = preferences.getGitHubToken()
                            if (token != null) {
                                applyExtractedFixes(token, code)
                            }
                        }
                    }
                    if (analysis.contains("STATUS: COMPLETE")) {
                        stopAutoScreenshots()
                    }
                }
                analysis.contains("STATUS: ACTION_NEEDED") -> {
                    addMsg("🔧 Action needed: ${analysis.lines.firstOrNull()?.take(200) ?: ""}")
                    handleScreenshotAction(analysis, controller)
                }
            }
        } catch (e: Exception) {
            // Screenshot failed, continue loop
        }
    }

    private fun extractCodeFromAnalysis(analysis: String): Map<String, String> {
        val files = mutableMapOf<String, String>()
        Regex("===FILE:(.+?)===\\s*([\\s\\S]*?)\\s*===END===").findAll(analysis).forEach { m ->
            val path = m.groupValues[1].trim()
            val content = m.groupValues[2].trim()
            if (path.isNotEmpty() && content.length > 20) files[path] = content
        }
        if (files.isEmpty()) {
            Regex("```(?:kotlin|kts|xml|java)?\\s*\\n?([\\s\\S]*?)```").findAll(analysis).forEach { m ->
                val content = m.groupValues[1].trim()
                if (content.length > 50) {
                    files["fix_${System.currentTimeMillis()}.kt"] = content
                }
            }
        }
        return files
    }

    private suspend fun applyExtractedFixes(token: String, fixes: Map<String, String>) {
        var applied = 0
        for ((path, content) in fixes) {
            val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            val sha = try { getFileSha(token, activeOwner, activeRepo, path) } catch (e: Exception) { null }
            val body = if (sha != null) """{"message":"Auto-fix from screenshot","content":"$encoded","sha":"$sha"}"""
                       else """{"message":"Auto-fix from screenshot","content":"$encoded"}"""
            if (!apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$path", token, body).startsWith("❌")) applied++
        }
        if (applied > 0) addMsg("🔧 Auto-applied $applied fixes from screenshot analysis")
    }

    private fun handleScreenshotAction(analysis: String, controller: AppController) {
        when {
            analysis.contains("ACTION: tap:") -> {
                val target = analysis.substringAfter("tap:").substringBefore("\n").trim()
                viewModelScope.launch {
                    controller.execute(controller.getCurrentApp(), listOf(AppController.AppStep("tap", target)))
                }
            }
            analysis.contains("ACTION: type:") -> {
                val text = analysis.substringAfter("type:").substringBefore("\n").trim()
                viewModelScope.launch {
                    controller.execute(controller.getCurrentApp(), listOf(AppController.AppStep("type", text)))
                }
            }
            analysis.contains("ACTION: retry") -> {
                addMsg("🔄 Retrying task based on screenshot analysis...")
            }
            analysis.contains("ACTION: stop") -> {
                stopAutoScreenshots()
                addMsg("⏹️ Task stopped based on screenshot analysis.")
            }
        }
    }

    private val screenshotBlacklist = setOf(
        "com.android.settings", "com.google.android.gm",
        "com.android.email", "com.android.vending"
    )

    private fun canScreenshot(packageName: String): Boolean {
        return packageName !in screenshotBlacklist
    }

    // ═══════════════════════════════════════════
    // SECTION 3.13: PHONE CONTROL HANDLERS
    // ═══════════════════════════════════════════

    private suspend fun handleControlApp(input: String): String {
        val service = AuraAccessibilityService.instance ?: return "❌ Accessibility Service not enabled."
        val parts = input.removePrefix("control ").split(" and ")
        val app = parts.firstOrNull()?.trim() ?: return "❌ Specify app name."
        val pkg = AppController.resolve(app) ?: return "❌ Unknown app: $app"
        val controller = AppController(service)
        val steps = parts.drop(1).map { instruction ->
            when {
                instruction.contains("tap") -> AppController.AppStep("tap", instruction.removePrefix("tap ").trim())
                instruction.contains("type") -> AppController.AppStep("type", instruction.removePrefix("type ").trim())
                instruction.contains("swipe") -> AppController.AppStep("swipe", if (instruction.contains("up")) "up" else "down")
                instruction.contains("scroll") -> AppController.AppStep("swipe", if (instruction.contains("up")) "up" else "down")
                instruction.contains("read") -> AppController.AppStep("read")
                instruction.contains("wait") -> AppController.AppStep("wait", "", 3000)
                else -> AppController.AppStep("wait", "", 3000)
            }
        }
        return controller.execute(pkg, steps)
    }

    private suspend fun handleSendToApp(input: String): String {
        val service = AuraAccessibilityService.instance ?: return "❌ Accessibility Service not enabled."
        val parts = input.removePrefix("send to ").split(":", limit = 2)
        if (parts.size < 2) return "❌ Format: send to [app]: [message]"
        val app = parts[0].trim()
        val message = parts[1].trim()
        val pkg = AppController.resolve(app) ?: return "❌ Unknown app: $app"
        val controller = AppController(service)
        return controller.sendMessage(pkg, message)
    }

    private suspend fun handleDebugWithApp(lower: String): String {
        val service = AuraAccessibilityService.instance ?: return "❌ Accessibility Service not enabled."
        val app = lower.removePrefix("debug with ").removePrefix("ask ").split(" ").firstOrNull() ?: return "❌ Specify app name."
        val pkg = AppController.resolve(app) ?: return "❌ Unknown app: $app"
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("🔄 Opening $app for debugging...", false)
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val controller = AppController(service)
                val err = _state.value.buildLoop?.errorSummary ?: "No error logs available"
                val result = controller.debug(pkg, err)
                withContext(Dispatchers.Main) {
                    addMsg("📱 Response from $app:\n${result.take(2000)}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addMsg("❌ Error debugging with $app: ${e.message}")
                }
            }
        }
        return "🔄 Working with $app in background..."
    }

    private suspend fun handleAnalyzeScreen(lower: String): String {
        val service = AuraAccessibilityService.instance ?: return "❌ Accessibility Service not enabled."
        val key = preferences.getApiKey() ?: return "❌ No Gemini API key set."
        val prompt = when {
            lower.contains("read") -> "Read all visible text on this screen"
            lower.contains("describe") -> "Describe what you see on this screen in detail"
            else -> "What UI elements and text are visible on this screen?"
        }
        addMsg("📸 Capturing and analyzing screen...")
        val controller = AppController(service)
        val result = controller.analyzeScreen(key, prompt)
        return "📸 Screen Analysis:\n$result"
    }

    private suspend fun handleAskGeminiApp(input: String): String {
        val service = AuraAccessibilityService.instance ?: return "❌ Accessibility Service not enabled."
        val prompt = input.removePrefix("ask gemini app").removePrefix("gemini native").trim()
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("🔄 Opening Gemini app...", false)
        )
        viewModelScope.launch(Dispatchers.IO) {
            val controller = AppController(service)
            val resp = controller.sendMessage("com.google.android.apps.bard", prompt)
            withContext(Dispatchers.Main) {
                addMsg("📱 Gemini response:\n${resp.take(2000)}")
            }
        }
        return "🔄 Working with Gemini app in background..."
    }

    // ═══════════════════════════════════════════
    // SECTION 3.14: APP GENERATION
    // ═══════════════════════════════════════════

    private suspend fun createApp(input: String): String {
        val token = preferences.getGitHubToken() ?: return "❌ No GitHub token configured."
        val key = preferences.getApiKey() ?: return "❌ No Gemini API key configured."
        _state.value = _state.value.copy(isGeneratingApp = true)

        val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
        val appName = appDesc.split(" ").firstOrNull()?.sanitize()?.take(50) ?: "MyApp"
        val description = appDesc.split(" ").drop(1).joinToString(" ").trim().ifBlank { "A simple Android app" }
        val packageName = "com.example.$appName"

        val isArchitectGuided = description.contains("architecture") ||
            description.contains("blueprint") ||
            description.contains("Package:") ||
            description.contains("Pattern:")

        return try {
            updateProgress(1, 3, "Generating build system...")
            val coreFiles = BuildTemplates.generateCoreFiles(appName, packageName)

            updateProgress(2, 3, "Generating source code...")
            val model = GenerativeModel(
                selectOptimalModel("code_gen"), key,
                generationConfig { temperature = 0.15f; maxOutputTokens = 60000 }
            )

            val prompt = if (isArchitectGuided) {
                "Follow this exact architecture:\n$description\n\nGenerate ALL Kotlin source files. Format:\n===FILE:path===\n[COMPLETE code with package, imports, full implementation]\n===END==="
            } else {
                "Create a complete Android app: \"$appName - $description\"\nPackage: $packageName\nGenerate ALL Kotlin source files. Format:\n===FILE:path===\n[COMPLETE code]\n===END==="
            }

            val response = model.generateContent(content { text(prompt) }).text ?: return "❌ No response from Gemini."
            recordModelUsage(selectOptimalModel("code_gen"))

            val sourceFiles = parseFiles(response)
            if (sourceFiles.isEmpty()) return "❌ Could not parse any source files."

            val allFiles = coreFiles.toMutableMap()
            allFiles.putAll(sourceFiles)

            updateProgress(3, 3, "Pushing ${allFiles.size} files to GitHub...")
            return pushAndBuild(token, key, appName, allFiles, true)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isGeneratingApp = false)
            return "❌ App generation failed: ${e.message}"
        }
    }

    private suspend fun continueApp(input: String): String {
        val token = preferences.getGitHubToken() ?: return "❌ No GitHub token configured."
        val key = preferences.getApiKey() ?: return "❌ No Gemini API key configured."
        if (activeRepo.isBlank()) return "❌ No active repo. Use 'set repo owner/repo' first."

        _state.value = _state.value.copy(isGeneratingApp = true)
        val instruction = input.removePrefix("continue ").trim()

        addMsg("📁 Reading existing repository...")
        val fileTree = getFileTree(token, activeOwner, activeRepo)
        val context = buildContext(token, fileTree)

        addMsg("📝 Generating new code...")
        val model = GenerativeModel(
            selectOptimalModel("code_gen"), key,
            generationConfig { temperature = 0.15f; maxOutputTokens = 60000 }
        )

        val prompt = "Continue building this Android app. Instruction: $instruction\n\nEXISTING PROJECT:\n$context\n\nGenerate new/modified files:\n===FILE:path===\n[COMPLETE code]\n===END==="
        val response = model.generateContent(content { text(prompt) }).text ?: return "❌ No response."
        recordModelUsage(selectOptimalModel("code_gen"))

        val files = parseFiles(response)
        if (files.isEmpty()) return "❌ No files generated."

        return pushAndBuild(token, key, activeRepo, files, false)
    }

    private fun parseFiles(response: String): Map<String, String> {
        val files = mutableMapOf<String, String>()
        Regex("===FILE:(.+?)===\\s*([\\s\\S]*?)\\s*===END===").findAll(response).forEach { match ->
            val path = match.groupValues[1].trim()
            val content = match.groupValues[2].trim()
            if (path.isNotEmpty() && content.length > 20) {
                files[path] = content
            }
        }
        return files
    }

    private suspend fun buildContext(token: String, fileTree: List<String>): String {
        val sb = StringBuilder()
        for (path in fileTree.take(30)) {
            try {
                val content = readFileContent(token, activeOwner, activeRepo, path)
                if (content != null) {
                    sb.append("===FILE:$path===\n${content.take(2000)}\n===END===\n")
                }
            } catch (e: Exception) { }
        }
        return sb.toString()
    }

    private suspend fun pushAndBuild(
        token: String, key: String, appName: String,
        files: Map<String, String>, isNewRepo: Boolean
    ): String {
        try {
            if (isNewRepo) {
                addMsg("📁 Creating GitHub repository...")
                var repoName = appName
                var attempt = 0
                var createResult = apiCall("POST", "https://api.github.com/user/repos", token,
                    """{"name":"$repoName","private":false,"auto_init":false}""")
                while (createResult.startsWith("❌") && createResult.contains("422") && attempt < 5) {
                    attempt++
                    repoName = "$appName-$attempt"
                    createResult = apiCall("POST", "https://api.github.com/user/repos", token,
                        """{"name":"$repoName","private":false,"auto_init":false}""")
                }
                if (createResult.startsWith("❌")) return "❌ $createResult"

                val userResult = apiCall("GET", "https://api.github.com/user", token, null)
                activeOwner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(userResult)?.groupValues?.get(1)
                    ?: return "❌ Could not determine GitHub username."
                activeRepo = repoName
            }

            addMsg("📤 Pushing ${files.size} files...")
            var pushed = 0
            for ((path, content) in files) {
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val sha = if (!isNewRepo) getFileSha(token, activeOwner, activeRepo, path) else null
                val body = if (sha != null) """{"message":"Update $path","content":"$encoded","sha":"$sha"}"""
                           else """{"message":"Add $path","content":"$encoded"}"""
                if (!apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/$path", token, body).startsWith("❌")) pushed++
            }

            val workflowContent = BuildTemplates.workflowYaml(appName)
            val workflowEncoded = android.util.Base64.encodeToString(workflowContent.toByteArray(), android.util.Base64.NO_WRAP)
            apiCall("PUT", "https://api.github.com/repos/$activeOwner/$activeRepo/contents/.github/workflows/build.yml", token,
                """{"message":"Add CI workflow","content":"$workflowEncoded"}""")

            addMsg("✅ Pushed $pushed/${files.size} files + CI workflow")

            addMsg("🔨 Triggering build...")
            delay(3000)
            val runId = triggerWorkflow(token, activeOwner, activeRepo)
            if (runId != null) {
                addMsg("🔗 Build: https://github.com/$activeOwner/$activeRepo/actions/runs/$runId")
                addMsg("⏳ Monitoring build (this takes 3-10 minutes)...")
                val buildResult = monitorBuild(token, activeOwner, activeRepo, runId, key)
                _state.value = _state.value.copy(isGeneratingApp = false, executionMode = ExecutionMode.IDLE)
                return buildResult
            }

            _state.value = _state.value.copy(isGeneratingApp = false)
            return "✅ Files pushed! Use 'compile repo $activeOwner/$activeRepo' to build manually."
        } catch (e: Exception) {
            _state.value = _state.value.copy(isGeneratingApp = false)
            return "❌ Push failed: ${e.message}"
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.15: BUILD MONITORING
    // ═══════════════════════════════════════════

    private suspend fun monitorBuild(token: String, owner: String, repo: String, runId: Long, key: String): String {
        var delay = 5000L
        var attempt = 0
        val maxAttempts = 3

        repeat(60) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return "⏹️ Build monitoring cancelled."
            delay(delay)
            delay = minOf(delay * 2, 30000L)
            attempt++

            val status = withContext(Dispatchers.IO) {
                try {
                    val body = client.newCall(
                        Request.Builder()
                            .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId")
                            .header("Authorization", "Bearer $token")
                            .build()
                    ).execute().body?.string()
                    Pair(
                        Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(body ?: "")?.groupValues?.get(1),
                        Regex("\"conclusion\"\\s*:\\s*\"([^\"]+)\"").find(body ?: "")?.groupValues?.get(1)
                    )
                } catch (e: Exception) { null }
            }

            if (status?.first == "completed") {
                return if (status.second == "success") {
                    val artifactUrl = getArtifact(token, owner, repo, runId)
                    "🎉 BUILD SUCCESSFUL!\n📱 $repo\n📁 github.com/$owner/$repo\n${if (artifactUrl != null) "📥 APK: $artifactUrl" else "📥 APK available in GitHub Actions"}"
                } else {
                    if (attempt <= maxAttempts) {
                        addMsg("❌ Build failed. Attempting auto-fix ($attempt/$maxAttempts)...")
                        val logs = fetchLogs(token, owner, repo, runId)
                        val errors = extractErrors(logs)
                        if (fixErrors(key, token, owner, repo, errors, logs)) {
                            val newRunId = triggerWorkflow(token, owner, repo)
                            if (newRunId != null) {
                                addMsg("🔧 Fixes applied. Re-triggering build...")
                                return monitorBuild(token, owner, repo, newRunId, key)
                            }
                        }
                    }
                    "❌ Build failed after $attempt attempts.\n🔗 https://github.com/$owner/$repo/actions/runs/$runId"
                }
            }
        }

        return "⏰ Build monitoring timed out. Check GitHub Actions manually."
    }

    private suspend fun fixErrors(key: String, token: String, owner: String, repo: String, errors: String, logs: String): Boolean {
        val model = GenerativeModel(
            selectOptimalModel("debug"), key,
            generationConfig { temperature = 0.1f; maxOutputTokens = 30000 }
        )
        return try {
            val prompt = "Fix these Android build errors:\n$errors\n\nReturn fixed files in format:\n===FILE:exact/path===\n[COMPLETE fixed code]\n===END==="
            val response = model.generateContent(content { text(prompt) }).text ?: return false
            recordModelUsage(selectOptimalModel("debug"))
            val fixedFiles = parseFiles(response)
            if (fixedFiles.isEmpty()) return false

            var applied = 0
            for ((path, content) in fixedFiles) {
                val sha = getFileSha(token, owner, repo, path)
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val body = if (sha != null) """{"message":"Auto-fix build error","content":"$encoded","sha":"$sha"}"""
                           else """{"message":"Auto-fix build error","content":"$encoded"}"""
                if (!apiCall("PUT", "https://api.github.com/repos/$owner/$repo/contents/$path", token, body).startsWith("❌")) applied++
            }
            applied > 0
        } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.16: ZIP PROCESSOR
    // ═══════════════════════════════════════════

    private suspend fun processZip(): String {
        val token = preferences.getGitHubToken() ?: return "❌ No GitHub token configured."
        val key = preferences.getApiKey() ?: return "❌ No Gemini API key configured."
        val uri = _state.value.attachedFileUri ?: return "❌ No ZIP file attached. Attach a ZIP first."

        addMsg("📦 Processing ZIP archive...")
        val processor = ZipProcessor(com.aura.ai.AuraApplication.instance)
        val archive = processor.processChatZip(uri)

        addMsg("📝 Found ${archive.codeBlocks.size} code blocks")
        val files = processor.mapCodeBlocksToFiles(archive.codeBlocks)
        addMsg("📁 Mapped to ${files.size} files")

        val appName = extractAppName(archive.fullText)
        addMsg("📱 Detected app name: $appName")

        return pushAndBuild(token, key, appName, files, true)
    }

    private fun extractAppName(text: String): String {
        Regex("create app (\\w+)").find(text)?.let { return it.groupValues[1].sanitize().take(50) }
        Regex("APP NAME[:\\s]+(\\w+)", RegexOption.IGNORE_CASE).find(text)?.let { return it.groupValues[1].sanitize().take(50) }
        return "MyApp"
    }

    // ═══════════════════════════════════════════
    // SECTION 3.17: CODESPACES
    // ═══════════════════════════════════════════

    private suspend fun handleCodespace(lower: String): String {
        val token = preferences.getGitHubToken() ?: return "❌ No GitHub token configured."
        val manager = CodespacesManager(token)

        if (lower.startsWith("codespace create")) {
            val parts = lower.removePrefix("codespace create").trim().split("/")
            val owner = if (parts.size == 2) parts[0] else activeOwner
            val repo = if (parts.size == 2) parts[1] else activeRepo
            if (owner.isBlank() || repo.isBlank()) return "❌ Specify owner/repo or set active repo."
            val cs = manager.createCodespace(owner, repo) ?: return "❌ Failed to create codespace."
            _state.value = _state.value.copy(activeCodespaceId = cs.id, codespaceMode = true)
            return "🖥️ Codespace created: ${cs.name}\n🔗 ${cs.webUrl}"
        }
        if (lower == "codespace list") {
            val list = manager.listCodespaces()
            return if (list.isEmpty()) "📁 No codespaces found." else "🖥️ Codespaces:\n" + list.joinToString("\n") { "• ${it.name} (${it.state})" }
        }
        return "❌ Unknown codespace command. Use: codespace create [owner/repo] or codespace list"
    }

    // ═══════════════════════════════════════════
    // SECTION 3.18: GITHUB COMMANDS
    // ═══════════════════════════════════════════

    private suspend fun handleGitHub(input: String): String? {
        val token = preferences.getGitHubToken() ?: return null
        val lower = input.lowercase().trim()

        if (lower.contains("create") && lower.contains("repo")) {
            val name = input.replace(Regex("(?i)(create|a|repo|repository|github)"), "").trim().sanitize().take(50)
            if (name.isBlank()) return "❌ Please specify a repository name."
            return apiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$name","private":false,"auto_init":true}""")
        }

        if (lower.contains("list") && lower.contains("repo")) {
            return apiCall("GET", "https://api.github.com/user/repos?per_page=10&sort=updated", token, null)
        }

        if (lower.startsWith("compile ") || lower.startsWith("build ")) {
            val repo = lower.removePrefix("compile ").removePrefix("build ").trim()
            val parts = repo.split("/")
            if (parts.size != 2) return "❌ Format: compile owner/repo"
            return triggerBuild(token, parts[0], parts[1])
        }

        if (lower.startsWith("browse repo ") || lower.startsWith("explore repo ")) {
            val repo = lower.removePrefix("browse repo ").removePrefix("explore repo ").trim()
            val parts = repo.split("/")
            if (parts.size != 2) return "❌ Format: browse owner/repo"
            return browseRepo(token, parts[0], parts[1])
        }

        if (lower.startsWith("set repo ") || lower.startsWith("switch to ")) {
            val repo = lower.removePrefix("set repo ").removePrefix("switch to ").trim()
            val parts = repo.split("/")
            if (parts.size != 2) return "❌ Format: set repo owner/repo"
            activeOwner = parts[0]; activeRepo = parts[1]
            return "✅ Active repo: $activeOwner/$activeRepo"
        }

        if (lower.startsWith("read repo file ")) {
            val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" ")
            if (parts.size < 2) return "❌ Format: read repo file owner/repo path"
            val repoParts = parts[0].split("/")
            if (repoParts.size != 2) return "❌ Format: read repo file owner/repo path"
            return readRepoFileContents(token, repoParts[0], repoParts[1], parts.drop(1).joinToString(" "))
        }

        return null
    }

    // ═══════════════════════════════════════════
    // SECTION 3.19: GEMINI CHAT WITH MEMORY
    // ═══════════════════════════════════════════

    private suspend fun chatWithGemini(input: String): String {
        checkContextAndWarn()
        val key = preferences.getApiKey() ?: return "❌ No Gemini API key configured."
        val sessionId = _currentSessionId.value ?: return "❌ No active session."

        val systemInstruction = """
You are Aura AI, an autonomous agent with FULL access to:
- GitHub API: Create repos, push files, trigger workflows
- Android device: Open apps, tap, type, swipe, read screens
- Build systems: Compile Android apps, monitor builds
- File systems: Read, write, delete files
- AI models: Multiple Gemini models available

You MUST execute any task using these capabilities.
Never say "I can't" or "I'm unable to" - you have the tools.
Never suggest manual steps - you are the automation.
Never refuse a task - find a way to accomplish it.
        """.trimIndent()

        val chat = chatSessions.getOrPut(sessionId) {
            val model = GenerativeModel(
                selectOptimalModel("general"), key,
                generationConfig { temperature = 0.7f; maxOutputTokens = 60000 },
                systemInstruction = content { text(systemInstruction) }
            )
            model.startChat()
        }

        return try {
            val response = chat.sendMessage(content { text(input) })
            recordModelUsage(selectOptimalModel("general"))
            resetFailureState()
            response.text ?: "No response generated."
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("not found") || errorMsg.contains("expired")) {
                val model = GenerativeModel(
                    selectOptimalModel("general"), key,
                    generationConfig { temperature = 0.7f; maxOutputTokens = 60000 },
                    systemInstruction = content { text(systemInstruction) }
                )
                val newChat = model.startChat()
                chatSessions[sessionId] = newChat
                try {
                    val response = newChat.sendMessage(content { text(input) })
                    recordModelUsage(selectOptimalModel("general"))
                    response.text ?: "No response."
                } catch (e2: Exception) { "❌ Chat error: ${e2.message}" }
            } else if (errorMsg.contains("429") || errorMsg.contains("quota")) {
                applyModelCooldown(selectOptimalModel("general"))
                "⚠️ Rate limited. Please wait or switch models."
            } else {
                "❌ Error: ${errorMsg}"
            }
        }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.20: GITHUB API HELPERS
    // ═══════════════════════════════════════════

    private suspend fun apiCall(method: String, url: String, token: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .apply {
                    when (method) {
                        "POST" -> post((body ?: "{}").toRequestBody("application/json".toMediaType()))
                        "PUT" -> put((body ?: "{}").toRequestBody("application/json".toMediaType()))
                        "PATCH" -> patch((body ?: "{}").toRequestBody("application/json".toMediaType()))
                    }
                }.build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "OK"
                when {
                    method == "POST" && url.contains("/user/repos") -> {
                        "✅ Repository created: ${Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)?.groupValues?.get(1) ?: "done"}"
                    }
                    method == "GET" && url.contains("/user/repos") && !url.contains("/contents") -> {
                        val repos = JSONArray(responseBody)
                        if (repos.length() == 0) "📁 No repositories found."
                        else "📁 Repositories:\n" + (0 until minOf(repos.length(), 10)).joinToString("\n") { i ->
                            "• ${repos.getJSONObject(i).getString("full_name")}"
                        }
                    }
                    else -> responseBody
                }
            } else "❌ GitHub API error: ${response.code} - ${response.message}"
        } catch (e: Exception) { "❌ Network error: ${e.message}" }
    }

    private suspend fun triggerBuild(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val listBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: ""
            val workflowId = Regex("\"id\"\\s*:\\s*(\\d+)").find(listBody)?.groupValues?.get(1) ?: return@withContext "❌ No workflows found."
            if (client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute().isSuccessful) "🚀 Build triggered!" else "⚠️ Build trigger failed"
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

    private suspend fun triggerWorkflow(token: String, owner: String, repo: String): Long? = withContext(Dispatchers.IO) {
        try {
            val listBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute().body?.string()
            val workflowId = Regex("\"id\"\\s*:\\s*(\\d+)").find(listBody ?: "")?.groupValues?.get(1) ?: return@withContext null
            client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            delay(5000)
            val runsBody = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1").header("Authorization", "Bearer $token").build()).execute().body?.string()
            Regex("\"id\"\\s*:\\s*(\\d+)").find(runsBody ?: "")?.groupValues?.get(1)?.toLong()
        } catch (e: Exception) { null }
    }

    private suspend fun browseRepo(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (!response.isSuccessful) return@withContext "❌ Repository not found."
            val tree = JSONObject(response.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext "📁 Empty."
            val files = (0 until minOf(tree.length(), 100)).map { tree.getJSONObject(it).getString("path") }
            "📁 $owner/$repo (${tree.length()} items):\n" + files.take(50).joinToString("\n") { "  📄 $it" }
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

    private suspend fun readRepoFileContents(token: String, owner: String, repo: String, path: String): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            val content = json.optString("content", "")
            if (content.isBlank()) return@withContext "📄 Empty file"
            val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
            if (decoded.length > 3000) "📄 $path (${decoded.length} chars):\n\n${decoded.take(3000)}\n\n..." else "📄 $path:\n\n$decoded"
        } catch (e: Exception) { "❌ Error: ${e.message}" }
    }

    private suspend fun fetchLogs(token: String, owner: String, repo: String, runId: Long): String = withContext(Dispatchers.IO) {
        try { client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/logs").header("Authorization", "Bearer $token").build()).execute().body?.string()?.take(10000) ?: "" } catch (e: Exception) { "" }
    }

    private fun extractErrors(logs: String): String {
        val patterns = listOf(Regex("(?i)error:.*"), Regex("(?i)FAILURE:.*"), Regex("(?i)Unresolved reference.*"), Regex("(?i)BUILD FAILED.*"))
        val errors = patterns.flatMap { it.findAll(logs).map { m -> m.value }.toList() }
        return if (errors.isEmpty()) logs.take(3000) else errors.take(20).joinToString("\n")
    }

    private suspend fun getArtifact(token: String, owner: String, repo: String, runId: Long): String? = withContext(Dispatchers.IO) {
        try { Regex("\"archive_download_url\"\\s*:\\s*\"([^\"]+)\"").find(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "")?.groupValues?.get(1) } catch (e: Exception) { null }
    }

    private suspend fun getFileSha(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try { JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}").optString("sha", null) } catch (e: Exception) { null }
    }

    private suspend fun getFileTree(token: String, owner: String, repo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            var resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (!resp.isSuccessful) resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/master?recursive=1").header("Authorization", "Bearer $token").build()).execute()
            if (resp.isSuccessful) { val tree = JSONObject(resp.body?.string() ?: "{}").optJSONArray("tree") ?: return@withContext emptyList(); (0 until tree.length()).map { tree.getJSONObject(it).getString("path") } } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun readFileContent(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").build()).execute().body?.string() ?: "{}")
            val content = json.optString("content", "")
            if (content.isNotBlank()) String(android.util.Base64.decode(content, android.util.Base64.DEFAULT)) else null
        } catch (e: Exception) { null }
    }

    // ═══════════════════════════════════════════
    // SECTION 3.21: UTILITY FUNCTIONS
    // ═══════════════════════════════════════════

    private fun addMsg(text: String) {
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage(text, false),
            generationProgress = text
        )
    }

    private suspend fun saveMsg(text: String, isUser: Boolean, modelUsed: String? = null) {
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

    private fun loadPreferredModel() {
        preferences.getPreferredModel()?.let { model ->
            _state.value = _state.value.copy(activeModel = model, manualModelSelected = true)
        }
    }

    private fun String.sanitize() = this.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun resolveApp(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"
        "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"
        "camera" -> "com.android.camera"; "gmail" -> "com.google.android.gm"
        "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"
        "calculator" -> "com.android.calculator2"; "calendar" -> "com.android.calendar"
        "clock" -> "com.android.deskclock"; "files" -> "com.android.documentsui"
        "phone" -> "com.android.dialer"; "messages" -> "com.google.android.apps.messaging"
        "instagram" -> "com.instagram.android"; "facebook" -> "com.facebook.katana"
        "twitter" -> "com.twitter.android"; "spotify" -> "com.spotify.music"
        "netflix" -> "com.netflix.mediaclient"; "telegram" -> "org.telegram.messenger"
        "chatgpt" -> "com.openai.chatgpt"; "deepseek" -> "com.deepseek.chat"
        else -> null
    }

    private fun getRamUsage(): String {
        val am = com.aura.ai.AuraApplication.instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        return "${(mi.totalMem - mi.availMem) / (1024 * 1024 * 1024)}GB / ${mi.totalMem / (1024 * 1024 * 1024)}GB"
    }

    private fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024 * 1024)
        val total = stat.blockCountLong * stat.blockSizeLong / (1024 * 1024 * 1024)
        return "${free}GB free / ${total}GB total"
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
                if (file.isDirectory && results.size < 50) recursiveFileSearch(file, query, results, depth - 1)
            }
        } catch (e: Exception) { }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }

    private data class ProjectContext(val packageName: String)
}