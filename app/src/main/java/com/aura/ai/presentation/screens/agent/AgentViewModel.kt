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
║  💻 APP GENERATION                       ║
║  create app [name] [full description]   ║
║  → Multi-pass with context awareness    ║
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
    val activeModel: String = "gemini-3.1-flash-lite"
)

enum class ExecutionMode {
    IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION, FILE_OPERATION
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
            lower.startsWith("explain") || lower.startsWith("analyze") || lower.startsWith("review") -> "complex"
            input.length > 400 -> "complex"
            input.length < 60 -> "chat"
            else -> "general"
        }
    }

    private fun selectOptimalModel(taskType: String, input: String): String {
        if (consecutiveFailures >= 3) {
            return taskModelPriority["high_volume"]!!.first { !isModelInCooldown(it) }
        }
        val candidates = taskModelPriority[taskType] ?: taskModelPriority["general"]!!
        for (model in candidates) {
            if (!isModelInCooldown(model) && !isNearDailyLimit(model)) {
                return model
            }
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
            val result = executeCommandPipeline(msg)
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage(result, false),
                loading = false,
                isExecuting = false,
                currentTask = "",
                executionMode = ExecutionMode.IDLE
            )
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
                    executionMode = ExecutionMode.IDLE
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
                } else "❌ Unknown app: $appName. Try: whatsapp, youtube, chrome, settings, camera, gallery, phone, messages"
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
                if (performTapOnText(service, target)) "👆 Tapped '$target'" else "❌ Could not find '$target' on screen"
            }
            lower.startsWith("type ") -> {
                val text = input.removePrefix("type ").trim()
                if (performTypeText(service, text)) "⌨️ Typed successfully" else "❌ Could not type - no input field focused"
            }
            lower == "swipe left" -> { performSwipe(service, false); "👈 Swiped left" }
            lower == "swipe right" -> { performSwipe(service, true); "👉 Swiped right" }
            else -> null
        }
    }

    // ============================================
    // GITHUB COMMANDS
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
            // CREATE APP - Multi-pass generation
            (lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) && !lower.contains("repo") -> {
                val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
                val appName = appDesc.split(" ").firstOrNull()?.replace(" ", "-")?.take(50) ?: "MyApp"
                val description = if (appDesc.split(" ").size > 1) appDesc.substringAfter(" ").trim() else "A simple application"
                _state.value = _state.value.copy(executionMode = ExecutionMode.GENERATING_APP)
                createFullApplication(token, key, appName, description)
            }
            // CREATE REPO
            lower.contains("create") && lower.contains("repo") -> {
                val name = input.replace(Regex("(?i)(create|a|repo|repository|github)"), "").trim().replace(" ", "-").take(50)
                if (name.isBlank()) "❌ Please specify a repository name."
                else githubApiCall("POST", "https://api.github.com/user/repos", token, """{"name":"$name","private":false,"auto_init":true}""")
            }
            // LIST REPOS
            lower.contains("list") && lower.contains("repo") -> {
                githubApiCall("GET", "https://api.github.com/user/repos?per_page=10&sort=updated", token, null)
            }
            // COMPILE / TRIGGER BUILD
            lower.startsWith("compile ") || lower.startsWith("build ") -> {
                val repo = lower.removePrefix("compile ").removePrefix("build ").trim()
                val parts = repo.split("/")
                if (parts.size != 2) "❌ Format: compile repo owner/repo"
                else triggerWorkflowDispatch(token, parts[0], parts[1])
            }
            // BROWSE REPO
            (lower.startsWith("browse repo ") || lower.startsWith("explore repo ")) -> {
                val repo = lower.removePrefix("browse repo ").removePrefix("explore repo ").trim()
                val parts = repo.split("/")
                if (parts.size != 2) "❌ Format: browse repo owner/repo"
                else browseRepositoryContents(token, parts[0], parts[1])
            }
            // READ REPO FILE
            lower.startsWith("read repo file ") -> {
                val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" ")
                if (parts.size < 2) "❌ Format: read repo file owner/repo path/to/file"
                else {
                    val repoParts = parts[0].split("/")
                    if (repoParts.size != 2) "❌ Format: read repo file owner/repo path"
                    else readRepoFileContents(token, repoParts[0], repoParts[1], parts.drop(1).joinToString(" "))
                }
            }
            // FIX FILE
            lower.startsWith("fix ") || lower.startsWith("fix file ") || lower.startsWith("edit ") -> {
                val remaining = input.replace(Regex("(?i)(fix|fix file|edit|update) "), "")
                val filePath = remaining.substringBefore(":").trim()
                val instruction = remaining.substringAfter(":").trim()
                if (filePath.isBlank() || instruction.isBlank()) "❌ Usage: fix file path/to/file.kt: change button color to red"
                else if (activeRepo.isBlank()) "❌ No active repo. Use 'set repo owner/repo' first."
                else if (key.isBlank()) "❌ No Gemini API key set."
                else repairFileInRepo(token, key, activeOwner, activeRepo, filePath, instruction)
            }
            // ADD FILE
            lower.startsWith("add file ") || lower.startsWith("create file ") -> {
                val remaining = input.replace(Regex("(?i)(add|create) file "), "")
                val filePath = remaining.substringBefore(":").trim()
                val description = remaining.substringAfter(":").trim()
                if (filePath.isBlank() || description.isBlank()) "❌ Usage: add file path/to/file.kt: a login screen"
                else if (activeRepo.isBlank()) "❌ No active repo. Use 'set repo owner/repo' first."
                else if (key.isBlank()) "❌ No Gemini API key set."
                else createFileInRepo(token, key, activeOwner, activeRepo, filePath, description)
            }
            // SET ACTIVE REPO
            lower.startsWith("set repo ") || lower.startsWith("switch to ") -> {
                val repo = lower.removePrefix("set repo ").removePrefix("switch to ").trim()
                val parts = repo.split("/")
                if (parts.size != 2) "❌ Format: set repo owner/repo"
                else { activeOwner = parts[0]; activeRepo = parts[1]; "✅ Active repo: $activeOwner/$activeRepo" }
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
                        if (content.length > 2500) {
                            "📄 $path (${content.length} chars):\n\n${content.take(2500)}\n\n... (use 'ask doc' for querying)"
                        } else "📄 $path (${content.length} chars):\n\n$content"
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
    // GEMINI CHAT WITH AUTO MODEL SELECTION
    // ============================================

    private suspend fun executeGeminiChat(input: String): String {
        val key = preferences.getApiKey()
        if (key.isNullOrBlank()) return "❌ No Gemini API key set. Add it in Protocol settings."

        _state.value = _state.value.copy(executionMode = ExecutionMode.CHATTING)
        val taskType = classifyTask(input)
        val modelName = selectOptimalModel(taskType, input)
        _state.value = _state.value.copy(activeModel = modelName)

        return try {
            val model = GenerativeModel(
                modelName = modelName,
                apiKey = key,
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
                val fallbackModel = selectOptimalModel("high_volume", input)
                if (fallbackModel != modelName) {
                    try {
                        val fb = GenerativeModel(modelName = fallbackModel, apiKey = key,
                            generationConfig = generationConfig { temperature = 0.7f; maxOutputTokens = 2048 })
                        val fbResponse = fb.generateContent(content { text(input) }).text ?: "No response."
                        recordModelUsage(fallbackModel)
                        resetFailureState()
                        "🔄 (Switched from $modelName to $fallbackModel due to rate limits)\n\n$fbResponse"
                    } catch (e2: Exception) {
                        applyModelCooldown(fallbackModel)
                        "❌ All models temporarily unavailable. Please wait 30-60 seconds and try again."
                    }
                } else "⚠️ Current model at capacity. Please try again shortly."
            } else "❌ Error: ${errorMsg}"
        }
    }

    // ============================================
    // FULL APPLICATION GENERATION ENGINE
    // ============================================

    private suspend fun createFullApplication(token: String, key: String, appName: String, description: String): String {
        // Phase 1: Planning
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("🔍 Phase 1/3: Analyzing requirements and planning file structure...", false)
        )

        val fileList = generateProjectFileList(key, appName, description)
        if (fileList.isEmpty()) return "❌ Could not generate file structure. Please provide a more detailed description."

        val totalFiles = fileList.size
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("📋 Planned $totalFiles files across the project.", false)
        )

        // Phase 2: Repository creation
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("📁 Phase 2/3: Creating GitHub repository...", false)
        )

        val createResult = githubApiCall("POST", "https://api.github.com/user/repos", token,
            """{"name":"$appName","private":false,"auto_init":false}""")
        if (createResult.startsWith("❌")) return "❌ Repository creation failed: $createResult"

        val userResult = githubApiCall("GET", "https://api.github.com/user", token, null)
        val owner = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(userResult)?.groupValues?.get(1)
            ?: return "❌ Could not determine GitHub username."
        activeOwner = owner
        activeRepo = appName

        // Phase 3: Multi-pass file generation
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("⚙️ Phase 3/3: Generating and pushing files in batches...", false)
        )

        val batchSize = 40
        val batches = fileList.chunked(batchSize)
        val allGeneratedFiles = mutableMapOf<String, String>()
        var totalPushed = 0
        var generationContext = "Starting fresh project: $appName"

        for ((index, batch) in batches.withIndex()) {
            val batchLabel = "${index + 1}/${batches.size}"
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage("📝 Batch $batchLabel: Generating ${batch.size} files with context awareness...", false)
            )

            val generatedBatch = generateFileBatchWithContext(key, appName, description, batch, allGeneratedFiles, index + 1, batches.size, generationContext)
            allGeneratedFiles.putAll(generatedBatch)

            generatedBatch.forEach { (path, content) ->
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val pushResult = githubApiCall("PUT",
                    "https://api.github.com/repos/$owner/$appName/contents/$path", token,
                    """{"message":"Add $path (batch $batchLabel)","content":"$encoded"}""")
                if (!pushResult.startsWith("❌")) totalPushed++
            }

            generationContext = "After batch $batchLabel: ${allGeneratedFiles.size}/$totalFiles files created. " +
                "Key files: ${allGeneratedFiles.keys.filter { it.contains("Main") || it.contains("build.gradle") || it.contains("AndroidManifest") }.take(5).joinToString(", ")}"

            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage("✅ Batch $batchLabel complete: ${generatedBatch.size} generated, $totalPushed total pushed.", false)
            )
        }

        // Add CI workflow
        if (fileList.any { it.contains("build.gradle") || it.contains("AndroidManifest") }) {
            val workflowYaml = """
name: Build $appName
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {java-version: '17', distribution: 'temurin'}
      - uses: gradle/actions/setup-gradle@v3
      - run: chmod +x gradlew
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: {name: ${appName}-debug, path: app/build/outputs/apk/debug/app-debug.apk}
            """.trimIndent()
            val encodedWorkflow = android.util.Base64.encodeToString(workflowYaml.toByteArray(), android.util.Base64.NO_WRAP)
            githubApiCall("PUT", "https://api.github.com/repos/$owner/$appName/contents/.github/workflows/build.yml", token,
                """{"message":"Add CI workflow","content":"$encodedWorkflow"}""")
        }

        val report = """
✅ APPLICATION GENERATED SUCCESSFULLY

📱 App: $appName
📁 Repository: github.com/$owner/$appName
📄 Files: $totalPushed/$totalFiles
🧠 Generation: ${batches.size} context-aware passes
🔧 CI/CD: ${if (fileList.any { it.contains("build.gradle") }) "Workflow configured" else "Manual build required"}

📋 NEXT STEPS:
• browse repo $owner/$appName - View project structure
• read repo file $owner/$appName [path] - View specific file
• fix file [path]: [instruction] - Modify any file
• add file [path]: [description] - Add new files
• compile repo $owner/$appName - Trigger APK build
        """.trimIndent()

        _state.value = _state.value.copy(executionMode = ExecutionMode.IDLE)
        return report
    }

    private suspend fun generateProjectFileList(key: String, appName: String, description: String): List<String> {
        val modelName = selectOptimalModel("code_gen", "create app $appName")
        val model = GenerativeModel(modelName, key, generationConfig { temperature = 0.15f; maxOutputTokens = 4096 })
        val complexity = if (description.length > 300) "60-120" else "20-40"
        val prompt = """
            Generate a COMPLETE file list for a new project: "$appName".
            Description: $description
            
            Requirements:
            - Include ALL files needed for a compilable, production-ready project
            - Include build configuration, source code, resources, tests, documentation
            - Include CI/CD workflow files
            - Generate $complexity files based on the project complexity
            
            Return ONLY a valid JSON array of file paths.
            Example: ["build.gradle.kts", "app/build.gradle.kts", "app/src/main/AndroidManifest.xml", ...]
        """.trimIndent()
        return try {
            val response = model.generateContent(content { text(prompt) }).text ?: return emptyList()
            recordModelUsage(modelName)
            val jsonStr = response.substringAfter("[").substringBeforeLast("]").let { "[$it]" }
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun generateFileBatchWithContext(
        key: String, appName: String, description: String,
        batch: List<String>, existingFiles: Map<String, String>,
        batchNum: Int, totalBatches: Int, context: String
    ): Map<String, String> {
        val modelName = selectOptimalModel("code_gen", "generate files batch $batchNum")
        val model = GenerativeModel(modelName, key, generationConfig { temperature = 0.15f; maxOutputTokens = 60000 })
        val existingSummary = if (existingFiles.isNotEmpty()) {
            "ALREADY GENERATED FILES:\n${existingFiles.keys.take(15).joinToString("\n") { "  ✅ $it" }}" +
            if (existingFiles.size > 15) "\n  ... and ${existingFiles.size - 15} more" else ""
        } else "No files generated yet (first batch)."
        val prompt = """
            Generate COMPLETE, COMPILABLE code for batch $batchNum of $totalBatches for: "$appName".
            
            PROJECT DESCRIPTION: $description
            CONTEXT FROM PREVIOUS BATCHES: $context
            
            $existingSummary
            
            FILES TO GENERATE NOW (${batch.size} files):
            ${batch.joinToString("\n") { "  📝 $it" }}
            
            CRITICAL REQUIREMENTS:
            1. Each file must contain COMPLETE, working code - NO placeholders or TODOs
            2. All files must be consistent with each other and with previously generated files
            3. Use proper package declarations, imports, and class names
            4. Follow best practices and clean architecture patterns
            5. Include proper error handling and null safety
            
            Return ONLY valid JSON in this exact format:
            {"files":[{"path":"complete/file/path.kt","content":"complete file content with \\n for newlines"}]}
        """.trimIndent()
        return try {
            val response = model.generateContent(content { text(prompt) }).text ?: return emptyMap()
            recordModelUsage(modelName)
            val jsonStr = response.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
            val obj = JSONObject(jsonStr)
            val filesArr = obj.getJSONArray("files")
            val result = mutableMapOf<String, String>()
            for (i in 0 until filesArr.length()) {
                val fileObj = filesArr.getJSONObject(i)
                val path = fileObj.getString("path")
                val content = fileObj.getString("content")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                result[path] = content
            }
            result
        } catch (e: Exception) { emptyMap() }
    }

    // ============================================
    // GITHUB API OPERATIONS
    // ============================================

    private suspend fun githubApiCall(method: String, url: String, token: String, body: String?): String =
        withContext(Dispatchers.IO) {
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
                        method == "POST" && url.contains("/user/repos") -> {
                            val fullName = Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)?.groupValues?.get(1) ?: "created"
                            "✅ Repository created: $fullName"
                        }
                        method == "GET" && url.contains("/user/repos") && !url.contains("/contents") -> {
                            val repos = JSONArray(responseBody)
                            if (repos.length() == 0) "📁 No repositories found."
                            else "📁 Your Repositories:\n" + (0 until minOf(repos.length(), 10)).joinToString("\n") { i ->
                                val repo = repos.getJSONObject(i)
                                "• ${repo.getString("full_name")} ${if (repo.getBoolean("private")) "🔒" else "🌐"} - ${repo.optString("description", "No description")}"
                            }
                        }
                        else -> responseBody
                    }
                } else "❌ GitHub API error: ${response.code} - ${response.message}"
            } catch (e: Exception) { "❌ Network error: ${e.message}" }
        }

    private suspend fun triggerWorkflowDispatch(token: String, owner: String, repo: String): String =
        withContext(Dispatchers.IO) {
            try {
                val listResponse = client.newCall(
                    Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows")
                        .header("Authorization", "Bearer $token").build()
                ).execute()
                val listBody = listResponse.body?.string() ?: ""
                val workflowId = Regex("\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"")
                    .find(listBody)?.groupValues?.get(1)
                if (workflowId == null) return@withContext "❌ No workflows found in $owner/$repo. Add a .github/workflows file first."
                val dispatchResponse = client.newCall(
                    Request.Builder()
                        .url("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches")
                        .header("Authorization", "Bearer $token")
                        .post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()
                if (dispatchResponse.isSuccessful) "🚀 Build triggered for $owner/$repo! Check the Actions tab."
                else "⚠️ Build trigger failed: ${dispatchResponse.message}"
              } catch (e: Exception) { "❌ Error: ${e.message}" }
        }

    private suspend fun browseRepositoryContents(token: String, owner: String, repo: String): String =
        withContext(Dispatchers.IO) {
            try {
                val repoResponse = client.newCall(
                    Request.Builder().url("https://api.github.com/repos/$owner/$repo")
                        .header("Authorization", "Bearer $token").build()
                ).execute()
                if (!repoResponse.isSuccessful) return@withContext "❌ Repository not found: $owner/$repo"
                val repoJson = JSONObject(repoResponse.body?.string() ?: "{}")
                val description = repoJson.optString("description", "No description")
                val stars = repoJson.optInt("stargazers_count", 0)
                val language = repoJson.optString("language", "Unknown")
                val defaultBranch = repoJson.optString("default_branch", "main")
                val treeResponse = client.newCall(
                    Request.Builder()
                        .url("https://api.github.com/repos/$owner/$repo/git/trees/$defaultBranch?recursive=1")
                        .header("Authorization", "Bearer $token").build()
                ).execute()
                val treeJson = JSONObject(treeResponse.body?.string() ?: "{}")
                val tree = treeJson.optJSONArray("tree")
                val directories = mutableSetOf<String>()
                val files = mutableListOf<String>()
                if (tree != null) {
                    for (i in 0 until minOf(tree.length(), 150)) {
                        val item = tree.getJSONObject(i)
                        if (item.getString("type") == "tree") directories.add(item.getString("path"))
                        else files.add(item.getString("path"))
                    }
                }
                buildString {
                    append("📁 $owner/$repo\n")
                    append("📝 $description\n")
                    append("⭐ $stars | 💻 $language | 🌿 $defaultBranch\n")
                    append("\n📂 Directories (${directories.size}):\n")
                    append(directories.take(15).joinToString("\n") { "  📁 $it" })
                    if (directories.size > 15) append("\n  ... and ${directories.size - 15} more")
                    append("\n\n📄 Files (${files.size} shown):\n")
                    append(files.take(25).joinToString("\n") { "  📄 $it" })
                    if (files.size > 25) append("\n  ... and ${files.size - 25} more")
                    if (tree != null && tree.length() > 150) append("\n\n⚠️ Showing 150 of ${tree.length()} items.")
                }
            } catch (e: Exception) { "❌ Error: ${e.message}" }
        }

    private suspend fun readRepoFileContents(token: String, owner: String, repo: String, path: String): String =
        withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(
                    Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path")
                        .header("Authorization", "Bearer $token").build()
                ).execute()
                if (!response.isSuccessful) return@withContext "❌ File not found: $path"
                val json = JSONObject(response.body?.string() ?: "{}")
                val content = json.optString("content", "")
                if (content.isBlank()) return@withContext "📄 $path - Empty file"
                val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
                if (decoded.length > 3000) {
                    "📄 $owner/$repo/$path (${decoded.length} chars):\n\n${decoded.take(3000)}\n\n... (${decoded.length - 3000} more characters)"
                } else "📄 $owner/$repo/$path (${decoded.length} chars):\n\n$decoded"
            } catch (e: Exception) { "❌ Error: ${e.message}" }
        }

    private suspend fun repairFileInRepo(token: String, key: String, owner: String, repo: String, path: String, instruction: String): String =
        withContext(Dispatchers.IO) {
            try {
                val readResponse = client.newCall(
                    Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path")
                        .header("Authorization", "Bearer $token").build()
                ).execute()
                if (!readResponse.isSuccessful) return@withContext "❌ File not found: $path"
                val json = JSONObject(readResponse.body?.string() ?: "{}")
                val currentContent = String(android.util.Base64.decode(json.getString("content"), android.util.Base64.DEFAULT))
                val sha = json.getString("sha")
                val modelName = selectOptimalModel("debug", "fix $path")
                val model = GenerativeModel(modelName, key, generationConfig { temperature = 0.1f; maxOutputTokens = 16384 })
                val fixPrompt = """
                    Fix the following file according to the instruction.
                    
                    CURRENT FILE CONTENT:
                    ```
                    $currentContent
                    ```
                    
                    INSTRUCTION: $instruction
                    
                    Return ONLY the complete corrected file content. No explanations.
                """.trimIndent()
                val newContent = model.generateContent(content { text(fixPrompt) }).text ?: return@withContext "❌ Gemini returned empty response."
                recordModelUsage(modelName)
                val encoded = android.util.Base64.encodeToString(newContent.toByteArray(), android.util.Base64.NO_WRAP)
                val updateResponse = client.newCall(
                    Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path")
                        .header("Authorization", "Bearer $token")
                        .put("""{"message":"Fix: $instruction","content":"$encoded","sha":"$sha"}""".toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()
                if (updateResponse.isSuccessful) "✅ Fixed $path: $instruction"
                else "❌ Update failed: ${updateResponse.message}"
            } catch (e: Exception) { "❌ Error: ${e.message}" }
        }

    private suspend fun createFileInRepo(token: String, key: String, owner: String, repo: String, path: String, description: String): String =
        withContext(Dispatchers.IO) {
            try {
                val modelName = selectOptimalModel("code_gen", "create file $path")
                val model = GenerativeModel(modelName, key, generationConfig { temperature = 0.2f; maxOutputTokens = 8192 })
                val content = model.generateContent(content { text("Create a complete file for path: $path. Description: $description. Return ONLY the file content.") }).text
                    ?: return@withContext "❌ Gemini returned empty."
                recordModelUsage(modelName)
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                val response = client.newCall(
                    Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path")
                        .header("Authorization", "Bearer $token")
                        .put("""{"message":"Add $path: $description","content":"$encoded"}""".toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()
                if (response.isSuccessful) "✅ Created $path"
                else "❌ Creation failed: ${response.message}"
            } catch (e: Exception) { "❌ Error: ${e.message}" }
        }

    // ============================================
    // ACCESSIBILITY SERVICE HELPERS
    // ============================================

    private fun performTapOnText(service: AuraAccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findAccessibilityNode(root, text)
        return if (node != null) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            root.recycle()
            node.recycle()
            val path = android.graphics.Path().apply {
                moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            service.dispatchGesture(gesture, null, null)
        } else {
            root.recycle()
            false
        }
    }

    private fun performTypeText(service: AuraAccessibilityService, text: String): Boolean {
        val focused = service.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        val result = focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        return result
    }

    private fun performScroll(service: AuraAccessibilityService, up: Boolean) {
        val display = service.resources.displayMetrics
        val path = if (up) {
            android.graphics.Path().apply {
                moveTo(display.widthPixels / 2f, display.heightPixels * 0.3f)
                lineTo(display.widthPixels / 2f, display.heightPixels * 0.8f)
            }
        } else {
            android.graphics.Path().apply {
                moveTo(display.widthPixels / 2f, display.heightPixels * 0.8f)
                lineTo(display.widthPixels / 2f, display.heightPixels * 0.3f)
            }
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(service: AuraAccessibilityService, right: Boolean) {
        val display = service.resources.displayMetrics
        val path = if (right) {
            android.graphics.Path().apply {
                moveTo(display.widthPixels * 0.2f, display.heightPixels / 2f)
                lineTo(display.widthPixels * 0.8f, display.heightPixels / 2f)
            }
        } else {
            android.graphics.Path().apply {
                moveTo(display.widthPixels * 0.8f, display.heightPixels / 2f)
                lineTo(display.widthPixels * 0.2f, display.heightPixels / 2f)
            }
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun findAccessibilityNode(node: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.text?.contains(text, ignoreCase = true) == true ||
            node.contentDescription?.contains(text, ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findAccessibilityNode(child, text)?.let { return it }
            }
        }
        return null
    }

    // ============================================
    // UTILITY FUNCTIONS
    // ============================================

    private fun resolveAppPackage(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"
        "youtube" -> "com.google.android.youtube"
        "chrome" -> "com.android.chrome"
        "settings" -> "com.android.settings"
        "camera" -> "com.android.camera"
        "gallery", "photos" -> "com.google.android.apps.photos"
        "gmail" -> "com.google.android.gm"
        "maps" -> "com.google.android.apps.maps"
        "play store" -> "com.android.vending"
        "calculator" -> "com.android.calculator2"
        "calendar" -> "com.android.calendar"
        "clock" -> "com.android.deskclock"
        "files" -> "com.android.documentsui"
        "phone" -> "com.android.dialer"
        "messages" -> "com.google.android.apps.messaging"
        "instagram" -> "com.instagram.android"
        "facebook" -> "com.facebook.katana"
        "twitter", "x" -> "com.twitter.android"
        "spotify" -> "com.spotify.music"
        "netflix" -> "com.netflix.mediaclient"
        "telegram" -> "org.telegram.messenger"
        "chatgpt" -> "com.openai.chatgpt"
        "notes" -> "com.google.android.apps.docs"
        "linkedin" -> "com.linkedin.android"
        "reddit" -> "com.reddit.frontpage"
        "discord" -> "com.discord"
        "snapchat" -> "com.snapchat.android"
        "tiktok" -> "com.zhiliaoapp.musically"
        "pinterest" -> "com.pinterest"
        "amazon" -> "com.amazon.mShop.android.shopping"
        "uber" -> "com.ubercab"
        "zomato" -> "com.application.zomato"
        "swiggy" -> "in.swiggy.android"
        else -> null
    }

    private fun getRamUsage(): String {
        val activityManager = com.aura.ai.AuraApplication.instance
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val usedGB = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024 * 1024)
        val totalGB = memoryInfo.totalMem / (1024 * 1024 * 1024)
        return "${usedGB}GB used / ${totalGB}GB total"
    }

    private fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val availableGB = stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024 * 1024)
        val totalGB = stat.blockCountLong * stat.blockSizeLong / (1024 * 1024 * 1024)
        return "${availableGB}GB free / ${totalGB}GB total"
    }

    private fun getBatteryLevel(): String {
        return try {
            val batteryManager = com.aura.ai.AuraApplication.instance
                .getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            "${batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
        } catch (e: Exception) { "Unknown" }
    }

    private fun recursiveFileSearch(dir: File, query: String, results: MutableList<String>, depth: Int) {
        if (depth < 0 || results.size >= 50) return
        try {
            dir.listFiles()?.forEach { file ->
                if (file.name.contains(query, ignoreCase = true)) {
                    results.add(file.absolutePath)
                }
                if (file.isDirectory && results.size < 50) {
                    recursiveFileSearch(file, query, results, depth - 1)
                }
            }
        } catch (e: Exception) { /* Skip inaccessible directories */ }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
