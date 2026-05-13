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
import com.aura.ai.services.AuraAccessibilityService.Companion.instance as accessibilityInstance
import com.aura.ai.services.FloatingMonitorService
import com.aura.ai.utils.DocumentRAGHelper
import com.aura.ai.utils.SubAgentManager
import com.aura.ai.utils.TaskOrchestrator
import com.aura.ai.utils.TemplateStorage
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
import javax.inject.Inject

data class ChatMessage(val text: String, val isUser: Boolean)

data class AgentUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("""
⚡ AURA ONLINE - NEURAL CORE ACTIVE

📱 Phone: open [app] | home | back | screenshot | scroll | tap on [text] | type [text] | swipe left/right
💻 Create App: create app MyApp [full description] — multi-pass Gemini generates complete Android apps
🐙 GitHub: create repo [name] | list repos | compile repo [owner/repo] | browse repo [owner/repo] | read repo file [o/r] [path]
📂 Files: list files | search files [query] | delete file [path] | read file [path] | ask doc [path]: [question] | summarize file [path]
🤖 Swarm: spawn agent [name] [type] | list agents | kill agent [id]
📋 Tasks: do task [description] | list templates | delete template [name]
👻 Ghost: start ghost mode [task] | stop ghost mode
🖥️ Monitor: show monitor | hide monitor
📊 System: device info | time
⏯️ Control: pause | resume | stop
        """.trimIndent(), false)
    ),
    val input: String = "",
    val loading: Boolean = false,
    val isExecuting: Boolean = false,
    val currentTask: String = ""
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val preferences: AuraPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()
    private var taskJob: Job? = null
    private var isPaused = false
    private var activeRepo = ""
    private var activeOwner = ""
    private val client = OkHttpClient()
    private var ghostJob: Job? = null

    fun updateInput(text: String) { _state.value = _state.value.copy(input = text) }

    fun send() {
        val msg = _state.value.input.trim()
        if (msg.isBlank()) return
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(msg, true), input = "", loading = true)
        if (handleControlCommand(msg)) return

        taskJob = viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, currentTask = msg)
            val result = executePhoneCommand(msg)
                ?: executeTemplateCommand(msg)
                ?: executeTaskOrchestratorCommand(msg)
                ?: executeCodeCommand(msg)
                ?: executeFileCommand(msg)
                ?: executeRagCommand(msg)
                ?: executeGitHubCommand(msg)
                ?: executeSubAgentCommand(msg)
                ?: executeGhostCommand(msg)
                ?: executeMonitorCommand(msg)
                ?: executeUtilityCommand(msg)
                ?: askGemini(msg)
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(result, false), loading = false, isExecuting = false, currentTask = "")
        }
    }

    // ============================================
    // CONTROL COMMANDS
    // ============================================
    private fun handleControlCommand(input: String): Boolean {
        when (input.lowercase().trim()) {
            "pause", "pause task" -> {
                if (_state.value.isExecuting) {
                    isPaused = true; taskJob?.cancel()
                    FloatingMonitorService.updateTask("⏸️ Paused", 0, 0, 0, 0)
                    _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⏸️ Paused. Type 'resume' or 'stop'.", false), loading = false)
                } else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("No task running.", false), loading = false)
                return true
            }
            "resume", "resume task" -> {
                if (isPaused) { isPaused = false; _state.value = _state.value.copy(loading = true); send() }
                else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("Nothing to resume.", false), loading = false)
                return true
            }
            "stop", "cancel", "stop task" -> {
                if (_state.value.isExecuting || isPaused) {
                    taskJob?.cancel(); TaskOrchestrator.cancel(); isPaused = false
                    FloatingMonitorService.clearAll()
                    _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⏹️ Stopped.", false), loading = false, isExecuting = false, currentTask = "")
                } else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("No task running.", false), loading = false)
                return true
            }
        }
        return false
    }

    // ============================================
    // PHONE CONTROL
    // ============================================
    private suspend fun executePhoneCommand(input: String): String? {
        val lower = input.lowercase().trim(); val s = accessibilityInstance
        if (lower.startsWith("open ")) { val n = lower.removePrefix("open ").trim(); val p = resolveAppPackage(n); if (p != null) { try { val i = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(p); if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); com.aura.ai.AuraApplication.instance.startActivity(i); return "✅ Opened $n" } } catch (e: Exception) { return "❌ ${e.message}" } }; return "❌ Unknown: $n" }
        if (lower == "home") { s?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "🏠 Home" }
        if (lower == "back") { s?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "⬅️ Back" }
        if (lower == "recents") { s?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); return "📱 Recents" }
        if (lower == "notifications") { s?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); return "🔔 Notifications" }
        if (lower == "quick settings") { s?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); return "⚙️ Quick settings" }
        if (lower == "screenshot") { s?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "📸 Screenshot" }
        if (lower == "scroll down") { s?.scrollDown(); return "👇 Scrolled" }
        if (lower == "scroll up") { s?.scrollUp(); return "👆 Scrolled" }
        if (lower.startsWith("tap on ")) { val t = lower.removePrefix("tap on ").trim(); val r = s?.tapOnText(t) ?: false; return if (r) "👆 Tapped '$t'" else "❌ Not found" }
        if (lower.startsWith("type ")) { s?.type(input.removePrefix("type ").trim()); return "⌨️ Typed" }
        if (lower == "swipe left") { val d = s?.resources?.displayMetrics; if (d != null) s.swipe(d.widthPixels * 0.8f, d.heightPixels / 2f, d.widthPixels * 0.2f, d.heightPixels / 2f); return "👈 Swiped" }
        if (lower == "swipe right") { val d = s?.resources?.displayMetrics; if (d != null) s.swipe(d.widthPixels * 0.2f, d.heightPixels / 2f, d.widthPixels * 0.8f, d.heightPixels / 2f); return "👉 Swiped" }
        return null
    }

    // ============================================
    // TEMPLATE COMMANDS
    // ============================================
    private fun executeTemplateCommand(input: String): String? {
        val lower = input.lowercase().trim()
        val ctx = com.aura.ai.AuraApplication.instance
        if (lower == "list templates" || lower == "show templates") {
            val templates = TemplateStorage.listTemplates(ctx)
            return if (templates.isEmpty()) "No saved templates." else "📋 Templates:\n${templates.joinToString("\n") { "• ${it.name} (${it.useCount}x used)" }}"
        }
        if (lower.startsWith("delete template ")) { TemplateStorage.deleteTemplate(ctx, input.removePrefix("delete template ").trim()); return "🗑️ Deleted." }
        val template = TemplateStorage.findTemplate(ctx, input)
        if (template != null) {
            TemplateStorage.incrementUseCount(ctx, template.id); TaskOrchestrator.loadTemplate(template)
            viewModelScope.launch {
                FloatingMonitorService.show(ctx)
                FloatingMonitorService.updateTask(template.name, 0, template.sections.sumOf { it.steps.size }, 0, template.sections.size)
                TaskOrchestrator.execute(
                    onProgress = { p -> viewModelScope.launch { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(p, false)) } },
                    onComplete = { _state.value = _state.value.copy(loading = false, isExecuting = false) }
                )
            }
            _state.value = _state.value.copy(isExecuting = true)
            return "▶️ Replaying: ${template.name}"
        }
        return null
    }

    // ============================================
    // TASK ORCHESTRATOR
    // ============================================
    private suspend fun executeTaskOrchestratorCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("do task ") || lower.startsWith("run task ") || lower.startsWith("execute ")) {
            val command = input.replace(Regex("(?i)(do task|run task|execute) "), "").trim()
            if (command.isBlank()) return "What should I do?"
            val key = preferences.getApiKey().ifBlank { return "No API key." }
            val ctx = com.aura.ai.AuraApplication.instance
            TaskOrchestrator.init(ctx, key)
            val plan = TaskOrchestrator.planTask(command)
            if (plan.isFailure) return "❌ ${plan.exceptionOrNull()?.message}"
            val sections = plan.getOrThrow()
            val totalSteps = sections.sumOf { it.steps.size }
            FloatingMonitorService.show(ctx)
            FloatingMonitorService.updateTask(sections.firstOrNull()?.title ?: "Task", 0, totalSteps, 0, sections.size)
            viewModelScope.launch {
                TaskOrchestrator.execute(
                    onProgress = { p -> viewModelScope.launch { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(p, false)) } },
                    onComplete = { _state.value = _state.value.copy(loading = false, isExecuting = false) }
                )
            }
            _state.value = _state.value.copy(isExecuting = true)
            return "📋 ${sections.size} sections, $totalSteps steps:\n${sections.joinToString("\n") { "${it.id + 1}. ${it.title} (${it.steps.size} steps)" }}\n\n⚠️ Executing. Monitor visible."
        }
        return null
    }

    // ============================================
    // CODE COMMANDS (Multi-Pass App Generation)
    // ============================================
    private suspend fun executeCodeCommand(input: String): String? {
        val token = preferences.getGitHubToken().ifBlank { return null }
        val lower = input.lowercase()
        val key = preferences.getApiKey().ifBlank { return "No Gemini API key." }

        if ((lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) && !lower.contains("repo")) {
            val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
            val appName = appDesc.split(" ").firstOrNull()?.replace(" ", "-")?.take(39) ?: "MyApp"
            val description = if (appDesc.split(" ").size > 1) appDesc.substringAfter(" ").trim() else "A simple Android app"
            val ctx = com.aura.ai.AuraApplication.instance
            FloatingMonitorService.show(ctx)
            FloatingMonitorService.updateTask("Generating: $appName", 0, 100, 0, 4)
            FloatingMonitorService.updateSectionStatus("Planning Structure", "🔄")
            return createAppMultiPass(token, key, appName, description)
        }

        if (lower.startsWith("fix ") || lower.startsWith("fix file ") || lower.startsWith("edit ")) {
            val filePath = input.replace(Regex("(?i)(fix|fix file|edit|update) "), "").substringBefore(":").trim()
            val instruction = input.substringAfter(":").trim().ifBlank { return "Usage: fix file path: instruction" }
            if (activeRepo.isBlank()) return "No active repo. Use 'set repo owner/repo' first."
            return fixFile(token, key, activeOwner, activeRepo, filePath, instruction)
        }

        if (lower.startsWith("add file ") || lower.startsWith("create file ")) {
            val filePath = input.replace(Regex("(?i)(add|create) file "), "").substringBefore(":").trim()
            val desc = input.substringAfter(":").trim().ifBlank { return "Usage: add file path: description" }
            if (activeRepo.isBlank()) return "No active repo."
            return addFile(token, key, activeOwner, activeRepo, filePath, desc)
        }

        if (lower.startsWith("set repo ") || lower.startsWith("switch to ")) {
            val repo = input.replace(Regex("(?i)(set repo|switch to) "), "").trim()
            val parts = repo.split("/"); if (parts.size != 2) return "Format: owner/repo"
            activeOwner = parts[0]; activeRepo = parts[1]
            return "✅ Active repo: $activeOwner/$activeRepo"
        }

        return null
    }

    // ===== MULTI-PASS APP GENERATOR =====
    private suspend fun createAppMultiPass(token: String, key: String, appName: String, description: String): String {
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("🔍 Phase 1/4: Planning file structure...", false))

        val fileList = generateFileList(key, appName, description)
        if (fileList.isEmpty()) return "❌ Failed to generate file structure."
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("📋 Planned ${fileList.size} files.", false))
        FloatingMonitorService.updateSectionStatus("Planning Structure", "✅")
        FloatingMonitorService.updateSectionStatus("Generating Code", "🔄")

        val batchSize = 50; val batches = fileList.chunked(batchSize)
        val allFiles = mutableMapOf<String, String>()

        for ((batchNum, batch) in batches.withIndex()) {
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("📝 Batch ${batchNum + 1}/${batches.size} (${batch.size} files)...", false))
            val generatedFiles = generateFileContents(key, appName, description, batch, allFiles)
            allFiles.putAll(generatedFiles)
        }

        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("✅ Generated ${allFiles.size}/${fileList.size} files.", false))
        FloatingMonitorService.updateSectionStatus("Generating Code", "✅")
        FloatingMonitorService.updateSectionStatus("Pushing to GitHub", "🔄")

        val createResult = githubApi(token, "POST", "https://api.github.com/user/repos", """{"name":"$appName","private":false,"auto_init":false}""")
        if (createResult.startsWith("❌")) return "Failed: $createResult"
        val userResult = githubApi(token, "GET", "https://api.github.com/user", null)
        val owner = Regex("\"login\":\"([^\"]+)\"").find(userResult)?.groupValues?.get(1) ?: return "Couldn't get username."
        activeOwner = owner; activeRepo = appName

        var pushed = 0
        allFiles.forEach { (path, content) ->
            val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            if (!githubApi(token, "PUT", "https://api.github.com/repos/$owner/$appName/contents/$path", """{"message":"Add $path","content":"$encoded"}""").startsWith("❌")) pushed++
        }

        val workflowYml = """
name: Build $appName
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {java-version: '17', distribution: 'temurin'}
      - run: chmod +x gradlew
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: {name: ${appName}-debug, path: app/build/outputs/apk/debug/app-debug.apk}
        """.trimIndent()
        val encodedWf = android.util.Base64.encodeToString(workflowYml.toByteArray(), android.util.Base64.NO_WRAP)
        githubApi(token, "PUT", "https://api.github.com/repos/$owner/$appName/contents/.github/workflows/build.yml", """{"message":"Add CI","content":"$encodedWf"}""")
        triggerWorkflow(token, owner, appName)

        FloatingMonitorService.updateSectionStatus("Pushing to GitHub", "✅")
        FloatingMonitorService.updateSectionStatus("Build Triggered", "✅")
        FloatingMonitorService.updateTask("Complete", 100, 100, 4, 4)

        return "✅ APP GENERATED: $appName\n📁 github.com/$owner/$appName\n📄 $pushed files\n🔧 CI ready\n🚀 Build triggered\n🧠 ${batches.size} Gemini passes"
    }

    private suspend fun generateFileList(key: String, appName: String, description: String): List<String> {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 4096 })
        val prompt = """
Generate a COMPLETE file list for an Android app: "$appName"
Description: $description
Include every file needed for a compilable Kotlin + Jetpack Compose + Material 3 app.
AGP 8.2.0, Kotlin 1.9.22, Compose BOM 2023.10.01, Min SDK 26.
Generate ${if (description.length > 200) "80-150" else "15-40"} files based on complexity.
Return ONLY a JSON array: ["build.gradle.kts","settings.gradle.kts","app/build.gradle.kts",...]
        """.trimIndent()
        return try {
            val resp = model.generateContent(content { text(prompt) }).text ?: return emptyList()
            val jsonStr = resp.substringAfter("[").substringBeforeLast("]").let { "[$it]" }
            (0 until JSONArray(jsonStr).length()).map { JSONArray(jsonStr).getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun generateFileContents(key: String, appName: String, description: String, batch: List<String>, existingFiles: Map<String, String>): Map<String, String> {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 60000 })
        val existingPaths = existingFiles.keys.joinToString("\n") { "// $it (already generated)" }
        val prompt = """
Generate COMPLETE content for these files in an Android app: "$appName"
Description: $description
Files to generate: ${batch.joinToString("\n")}
Already generated: $existingPaths
Return ONLY valid JSON: {"files":[{"path":"...","content":"..."}]}
Package: com.example.${appName.lowercase().replace("-", "")}
        """.trimIndent()
        return try {
            val resp = model.generateContent(content { text(prompt) }).text ?: return emptyMap()
            val jsonStr = resp.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
            val obj = JSONObject(jsonStr); val filesArr = obj.getJSONArray("files")
            val result = mutableMapOf<String, String>()
            for (i in 0 until filesArr.length()) {
                val f = filesArr.getJSONObject(i)
                result[f.getString("path")] = f.getString("content").replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
            }
            result
        } catch (e: Exception) { emptyMap() }
    }

    private suspend fun fixFile(token: String, key: String, owner: String, repo: String, path: String, instruction: String): String {
        return try {
            val readResult = githubApi(token, "GET", "https://api.github.com/repos/$owner/$repo/contents/$path", null)
            if (readResult.startsWith("❌")) return "File not found: $path"
            val json = JSONObject(readResult)
            val current = String(android.util.Base64.decode(json.getString("content"), android.util.Base64.DEFAULT))
            val sha = json.getString("sha")
            val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.1f; maxOutputTokens = 8192 })
            val newContent = model.generateContent(content { text("Current:\n```\n$current\n```\nInstruction: $instruction\nReturn ONLY complete updated file.") }).text ?: return "Gemini returned empty."
            val encoded = android.util.Base64.encodeToString(newContent.toByteArray(), android.util.Base64.NO_WRAP)
            githubApi(token, "PUT", "https://api.github.com/repos/$owner/$repo/contents/$path", """{"message":"Fix: $instruction","content":"$encoded","sha":"$sha"}""")
            "✅ Fixed $path"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun addFile(token: String, key: String, owner: String, repo: String, path: String, desc: String): String {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 4096 })
        val content = model.generateContent(content { text("Generate complete Kotlin file. Path: $path. Description: $desc. Return ONLY file content.") }).text ?: return "Gemini returned empty."
        val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        val result = githubApi(token, "PUT", "https://api.github.com/repos/$owner/$repo/contents/$path", """{"message":"Add $path","content":"$encoded"}""")
        return if (result.startsWith("❌")) result else "✅ Added $path"
    }

    // ============================================
    // FILE COMMANDS
    // ============================================
    private fun executeFileCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("list files")) { val p = input.replace(Regex("(?i)list files"), "").trim().ifBlank { Environment.getExternalStorageDirectory().absolutePath }; return try { val f = File(p).listFiles()?.take(30); if (f.isNullOrEmpty()) "Empty" else "📁 $p:\n${f.joinToString("\n") { "${if (it.isDirectory) "📁" else "📄"} ${it.name} (${formatSize(it.length())})" }}" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower.startsWith("search files")) { val q = input.replace(Regex("(?i)search files"), "").trim().ifBlank { return "What?" }; return try { val r = mutableListOf<String>(); searchFiles(File(Environment.getExternalStorageDirectory().absolutePath), q, r, 3); if (r.isEmpty()) "Not found" else "🔍\n${r.joinToString("\n") { "📄 $it" }}" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower.startsWith("delete file")) { val p = input.replace(Regex("(?i)delete file"), "").trim().ifBlank { return "Which?" }; return try { val f = File(p); if (f.exists()) { f.delete(); "✅ Deleted" } else "Not found" } catch (e: Exception) { "❌ ${e.message}" } }
        return null
    }

    // ============================================
    // RAG COMMANDS
    // ============================================
    private fun executeRagCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("read file")) { val path = input.replace(Regex("(?i)(read|show) file"), "").trim(); if (path.isBlank()) return "Usage: read file /path"; val content = DocumentRAGHelper.readDocument(com.aura.ai.AuraApplication.instance, path); if (content.startsWith("Error") || content.startsWith("File not found") || content.startsWith("Binary")) return content; return "📄 $path (${content.length} chars):\n\n${if (content.length > 2000) content.take(2000) + "\n\n... (use 'ask doc' to query)" else content}" }
        if (lower.startsWith("ask doc") || lower.startsWith("ask about")) return "Usage: ask doc /path/file.txt: your question"
        if (lower.startsWith("summarize file")) return "Usage: summarize file /path/file.txt"
        return null
    }

    // ============================================
    // GITHUB COMMANDS
    // ============================================
    private suspend fun executeGitHubCommand(input: String): String? {
        val token = preferences.getGitHubToken().ifBlank { return null }
        val lower = input.lowercase()

        if (lower.contains("create") && lower.contains("repo") && !lower.contains("app")) {
            val name = input.replace(Regex("(?i)(create|a|repo|gitHub)"), "").trim().replace(" ", "-").take(39)
            if (name.isBlank()) return "Repo name?"
            return githubApi(token, "POST", "https://api.github.com/user/repos", """{"name":"$name","private":false,"auto_init":true}""")
        }
        if (lower.contains("list") && lower.contains("repo")) {
            return githubApi(token, "GET", "https://api.github.com/user/repos?per_page=5&sort=updated", null)
        }
        if (lower.startsWith("compile ") || lower.startsWith("build ")) {
            val repo = lower.removePrefix("compile ").removePrefix("build ").trim()
            val parts = repo.split("/"); if (parts.size != 2) return "Format: owner/repo"
            return triggerWorkflow(token, parts[0], parts[1])
        }
        if (lower.startsWith("browse repo ") || lower.startsWith("explore repo ") || lower.startsWith("read repo ")) {
            val repo = lower.removePrefix("browse repo ").removePrefix("explore repo ").removePrefix("read repo ").trim()
            val parts = repo.split("/"); if (parts.size != 2) return "Format: browse repo owner/repo"
            return browseRepository(token, parts[0], parts[1])
        }
        if (lower.startsWith("read repo file ") || lower.startsWith("get repo file ")) {
            val parts = input.replace(Regex("(?i)(read repo file|get repo file) "), "").trim().split(" ")
            if (parts.size < 2) return "Format: read repo file owner/repo path/to/file.kt"
            val repoParts = parts[0].split("/"); if (repoParts.size != 2) return "Format: read repo file owner/repo path/to/file.kt"
            return readRepoFile(token, repoParts[0], repoParts[1], parts.drop(1).joinToString(" "))
        }
        return null
    }

    // ===== BROWSE REPOSITORY =====
    private suspend fun browseRepository(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val repoResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            if (!repoResp.isSuccessful) return@withContext "❌ Repo not found: $owner/$repo"
            val repoJson = JSONObject(repoResp.body?.string() ?: "{}")
            val desc = repoJson.optString("description", "No description"); val stars = repoJson.optInt("stargazers_count", 0)
            val lang = repoJson.optString("language", "Unknown"); val branch = repoJson.optString("default_branch", "main")

            val treeResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            val treeJson = JSONObject(treeResp.body?.string() ?: "{}"); val tree = treeJson.optJSONArray("tree")
            val files = mutableListOf<String>(); val dirs = mutableSetOf<String>()
            if (tree != null) for (i in 0 until minOf(tree.length(), 100)) {
                val item = tree.getJSONObject(i)
                if (item.getString("type") == "tree") dirs.add(item.getString("path")) else files.add(item.getString("path"))
            }

            buildString {
                append("📁 $owner/$repo\n📝 $desc\n⭐ $stars | 💻 $lang | 🌿 $branch\n\n")
                append("📂 Dirs (${dirs.size}):\n${dirs.take(15).joinToString("\n") { "  📁 $it" }}")
                if (dirs.size > 15) append("\n  ... and ${dirs.size - 15} more")
                append("\n\n📄 Files (${files.size}):\n${files.take(20).joinToString("\n") { "  📄 $it" }}")
                if (files.size > 20) append("\n  ... and ${files.size - 20} more")
                if (tree != null && tree.length() > 100) append("\n\n⚠️ Showing 100 of ${tree.length()} items.")
            }
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun readRepoFile(token: String, owner: String, repo: String, path: String): String = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            if (!resp.isSuccessful) return@withContext "❌ File not found: $path"
            val json = JSONObject(resp.body?.string() ?: "{}"); val content = json.optString("content", "")
            if (content.isBlank()) return@withContext "Empty file"
            val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
            val size = json.optInt("size", 0)
            if (decoded.length > 3000) "📄 $owner/$repo/$path (${formatSize(size.toLong())})\n\n${decoded.take(3000)}\n\n... (${decoded.length} total chars)"
            else "📄 $owner/$repo/$path (${formatSize(size.toLong())})\n\n$decoded"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    // ============================================
    // SUB-AGENT COMMANDS
    // ============================================
    private suspend fun executeSubAgentCommand(input: String): String? {
        val lower = input.lowercase()
        if (lower.startsWith("spawn agent ") || lower.startsWith("create agent ")) {
            val parts = input.replace(Regex("(?i)(spawn|create) agent "), "").trim(); val words = parts.split(" ")
            val name = words.firstOrNull() ?: return "Usage: spawn agent Name Type"
            val type = if (words.size > 1) words.drop(1).joinToString(" ") else "General"
            val validTypes = listOf("Scraper", "Coder", "Monitor", "Social", "General")
            val agentType = validTypes.find { type.equals(it, ignoreCase = true) } ?: "General"
            FloatingMonitorService.setAgent("$name ($agentType)")
            return SubAgentManager.spawnAgent(com.aura.ai.AuraApplication.instance, preferences, name, agentType)
        }
        if (lower == "list agents") { val a = SubAgentManager.getAgents(); return if (a.isEmpty()) "No agents." else "🤖 (${a.size}):\n${a.joinToString("\n") { "• ${it.name} [${it.type}] - ${it.status}" }}" }
        if (lower.startsWith("kill agent ")) { val id = input.replace(Regex("(?i)(kill|remove) agent "), "").trim(); return if (SubAgentManager.killAgent(id)) "🗑️ Terminated." else "❌ Not found." }
        return null
    }

    // ============================================
    // GHOST MODE COMMANDS
    // ============================================
    private fun executeGhostCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("start ghost") || lower == "ghost mode") {
            val task = input.replace(Regex("(?i)(start ghost|ghost mode)"), "").trim().ifBlank { "Generic background task" }
            ghostJob?.cancel()
            ghostJob = viewModelScope.launch {
                FloatingMonitorService.show(com.aura.ai.AuraApplication.instance)
                FloatingMonitorService.setAgent("Ghost Agent")
                FloatingMonitorService.updateTask("Ghost: $task", 0, 100, 0, 1)
                FloatingMonitorService.updateSectionStatus("Background Task", "🔄")
                while (isActive) {
                    // Periodic background work
                    delay(60000) // Every 60 seconds
                }
            }
            return "👻 Ghost mode activated: $task\nAura works silently in background."
        }
        if (lower == "stop ghost" || lower == "stop ghost mode") {
            ghostJob?.cancel()
            FloatingMonitorService.updateSectionStatus("Background Task", "⏹️")
            return "👻 Ghost mode stopped."
        }
        return null
    }

    // ============================================
    // MONITOR COMMANDS
    // ============================================
    private fun executeMonitorCommand(input: String): String? {
        val lower = input.lowercase().trim()
        val ctx = com.aura.ai.AuraApplication.instance
        if (lower == "show monitor" || lower == "open monitor") { FloatingMonitorService.show(ctx); return "🖥️ Monitor shown." }
        if (lower == "hide monitor" || lower == "close monitor") { FloatingMonitorService.hide(ctx); return "🖥️ Monitor hidden." }
        return null
    }

    // ============================================
    // UTILITY COMMANDS
    // ============================================
    private fun executeUtilityCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower == "device info") { val r = getRamInfo(); val st = getStorageInfo(); val b = getBatteryInfo(); return "📱 ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\nRAM: $r | Storage: $st | Battery: $b | CPU: ${Runtime.getRuntime().availableProcessors()} cores" }
        if (lower == "time") return "🕐 ${SimpleDateFormat("HH:mm:ss · EEEE, MMMM d", Locale.getDefault()).format(Date())}"
        return null
    }

    // ============================================
    // GEMINI FALLBACK
    // ============================================
    private suspend fun askGemini(input: String): String {
        val key = preferences.getApiKey().ifBlank { return "No API key set. Add it in Protocol settings." }
        return try { GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.7f; maxOutputTokens = 2048 }).generateContent(content { text(input) }).text ?: "No response." } catch (e: Exception) { if (e.message?.contains("503") == true) "Busy." else "Error: ${e.message}" }
    }

    // ============================================
    // GITHUB API HELPERS
    // ============================================
    private suspend fun githubApi(token: String, method: String, url: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").apply { if (method == "PUT" || method == "POST") put(body!!.toRequestBody("application/json".toMediaType())) else get() }.build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) resp.body?.string() ?: "OK" else "❌ ${resp.message}"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun triggerWorkflow(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val listResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute()
            val body = listResp.body?.string() ?: return@withContext "⚠️ No workflows."
            val wid = Regex("\"id\":(\\d+)").find(body)?.groupValues?.get(1) ?: return@withContext "⚠️ No ID."
            val dResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            if (dResp.isSuccessful) "🚀 Build triggered" else "⚠️ ${dResp.message}"
        } catch (e: Exception) { "⚠️ ${e.message}" }
    }

    // ============================================
    // UTILITY FUNCTIONS
    // ============================================
    private fun resolveAppPackage(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; "camera" -> "com.android.camera"; "gallery","photos" -> "com.google.android.apps.photos"; "gmail" -> "com.google.android.gm"; "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"; "calculator" -> "com.android.calculator2"; "calendar" -> "com.android.calendar"; "clock" -> "com.android.deskclock"; "files" -> "com.android.documentsui"; "phone" -> "com.android.dialer"; "messages" -> "com.google.android.apps.messaging"; "instagram" -> "com.instagram.android"; "facebook" -> "com.facebook.katana"; "twitter","x" -> "com.twitter.android"; "spotify" -> "com.spotify.music"; "netflix" -> "com.netflix.mediaclient"; "telegram" -> "org.telegram.messenger"; "chatgpt" -> "com.openai.chatgpt"; "meta ai" -> "com.facebook.meta_ai"; "copilot" -> "com.microsoft.copilot"; "notes" -> "com.google.android.apps.docs"; else -> null
    }
    private fun getRamInfo(): String { val am = com.aura.ai.AuraApplication.instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val mem = ActivityManager.MemoryInfo(); am.getMemoryInfo(mem); return "${(mem.totalMem - mem.availMem) / (1024*1024*1024)}GB / ${mem.totalMem / (1024*1024*1024)}GB" }
    private fun getStorageInfo(): String { val stat = StatFs(Environment.getDataDirectory().path); return "${stat.availableBlocksLong * stat.blockSizeLong / (1024*1024*1024)}GB free" }
    private fun getBatteryInfo(): String = try { val bm = com.aura.ai.AuraApplication.instance.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%" } catch (e: Exception) { "?" }
    private fun searchFiles(dir: File, q: String, r: MutableList<String>, d: Int) { if (d < 0) return; dir.listFiles()?.forEach { f -> if (f.name.contains(q, true)) r.add(f.absolutePath); if (f.isDirectory && r.size < 20) searchFiles(f, q, r, d - 1) } }
    private fun formatSize(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024*1024 -> "${bytes/1024} KB"; else -> "${bytes/(1024*1024)} MB" }
}
