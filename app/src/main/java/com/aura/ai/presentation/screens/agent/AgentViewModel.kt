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
            💻 Code: create app [name] [full description] | fix file [path]: [instruction] | add file [path]: [description] | set repo [owner/repo]
            📂 Files: list files | search files [query] | delete file [path] | read file [path] | ask doc [path]: [question] | summarize file [path]
            🐙 GitHub: create repo [name] | list repos | compile repo [owner/repo]
            🤖 Swarm: spawn agent [name] [type] | list agents | kill agent [id]
            📋 Tasks: do task [full description] | list templates | delete template [name]
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
                ?: executeUtilityCommand(msg)
                ?: askGemini(msg)
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(result, false), loading = false, isExecuting = false, currentTask = "")
        }
    }

    // ===== CONTROL COMMANDS =====
    private fun handleControlCommand(input: String): Boolean {
        when (input.lowercase().trim()) {
            "pause", "pause task" -> {
                if (_state.value.isExecuting) { isPaused = true; taskJob?.cancel(); _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⏸️ Paused. Type 'resume' or 'stop'.", false), loading = false) }
                else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("No task running.", false), loading = false)
                return true
            }
            "resume", "resume task" -> {
                if (isPaused) { isPaused = false; _state.value = _state.value.copy(loading = true); send() }
                else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("Nothing to resume.", false), loading = false)
                return true
            }
            "stop", "cancel", "stop task" -> {
                if (_state.value.isExecuting || isPaused) { taskJob?.cancel(); TaskOrchestrator.cancel(); isPaused = false; _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⏹️ Stopped.", false), loading = false, isExecuting = false, currentTask = "") }
                else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("No task running.", false), loading = false)
                return true
            }
        }
        return false
    }

    // ===== PHONE CONTROL =====
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

    // ===== TEMPLATE COMMANDS =====
    private fun executeTemplateCommand(input: String): String? {
        val lower = input.lowercase().trim()
        val ctx = com.aura.ai.AuraApplication.instance
        if (lower == "list templates" || lower == "show templates") {
            val templates = TemplateStorage.listTemplates(ctx)
            if (templates.isEmpty()) return "No saved templates. Complete a 'do task' first to auto-save."
            return "📋 Templates:\n${templates.joinToString("\n") { "• ${it.name} (${it.useCount}x used)" }}"
        }
        if (lower.startsWith("delete template ")) {
            val name = input.removePrefix("delete template ").trim()
            TemplateStorage.deleteTemplate(ctx, name)
            return "🗑️ Deleted template: $name"
        }
        // Check if input matches a saved template
        val template = TemplateStorage.findTemplate(ctx, input)
        if (template != null) {
            TemplateStorage.incrementUseCount(ctx, template.id)
            TaskOrchestrator.loadTemplate(template)
            viewModelScope.launch {
                TaskOrchestrator.execute(
                    onProgress = { progress -> viewModelScope.launch { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(progress, false)) } },
                    onComplete = { _state.value = _state.value.copy(loading = false, isExecuting = false) }
                )
            }
            _state.value = _state.value.copy(isExecuting = true)
            return "▶️ Replaying template: ${template.name}"
        }
        return null
    }

    // ===== TASK ORCHESTRATOR =====
    private suspend fun executeTaskOrchestratorCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("do task ") || lower.startsWith("run task ") || lower.startsWith("execute ")) {
            val taskCommand = input.replace(Regex("(?i)(do task|run task|execute) "), "").trim()
            if (taskCommand.isBlank()) return "What should I do? Describe the full workflow."
            val key = preferences.getApiKey().ifBlank { return "No Gemini API key set." }
            TaskOrchestrator.init(com.aura.ai.AuraApplication.instance, key)
            val planResult = TaskOrchestrator.planTask(taskCommand)
            if (planResult.isFailure) return "❌ Planning failed: ${planResult.exceptionOrNull()?.message}"
            val sections = planResult.getOrThrow()
            val sectionList = sections.joinToString("\n") { "${it.id + 1}. ${it.title} (${it.steps.size} steps)" }
            viewModelScope.launch {
                TaskOrchestrator.execute(
                    onProgress = { progress -> viewModelScope.launch { _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(progress, false)) } },
                    onComplete = { _state.value = _state.value.copy(loading = false, isExecuting = false) }
                )
            }
            _state.value = _state.value.copy(isExecuting = true)
            return "📋 Task Plan:\n$sectionList\n\n⚠️ Execution started. Use 'pause'/'resume'/'stop' to control."
        }
        return null
    }
    
    // ===== CODE COMMANDS (Hybrid System) =====
    private suspend fun executeCodeCommand(input: String): String? {
        val token = preferences.getGitHubToken().ifBlank { return null }
        val lower = input.lowercase()
        val key = preferences.getApiKey().ifBlank { return "No Gemini API key for code generation." }

        // CREATE APP
        if ((lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) && !lower.contains("repo")) {
            val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
            val appName = appDesc.split(" ").firstOrNull()?.replace(" ", "-")?.take(39) ?: "MyApp"
            val description = if (appDesc.split(" ").size > 1) appDesc.substringAfter(" ").trim() else "A simple Android app"
            return createFullApp(token, key, appName, description)
        }

        // FIX FILE
        if (lower.startsWith("fix ") || lower.startsWith("fix file ") || lower.startsWith("edit ") || lower.startsWith("update ")) {
            val filePath = input.replace(Regex("(?i)(fix|fix file|edit|update) "), "").substringBefore(":").trim()
            val instruction = input.substringAfter(":").trim().ifBlank { input.substringAfter(filePath).trim() }
            if (filePath.isBlank() || instruction.isBlank()) return "Usage: fix file path/to/file.kt: change the button color to red"
            if (activeRepo.isBlank()) return "No active repo. Create an app first or use 'set repo owner/repo'."
            return fixFile(token, key, activeOwner, activeRepo, filePath, instruction)
        }

        // ADD FILE
        if (lower.startsWith("add file ") || lower.startsWith("create file ")) {
            val filePath = input.replace(Regex("(?i)(add|create) file "), "").substringBefore(":").trim()
            val desc = input.substringAfter(":").trim().ifBlank { input.substringAfter(filePath).trim() }
            if (filePath.isBlank() || desc.isBlank()) return "Usage: add file path/to/file.kt: description of what this file should contain"
            if (activeRepo.isBlank()) return "No active repo. Create an app first."
            return addFile(token, key, activeOwner, activeRepo, filePath, desc)
        }

        // SET ACTIVE REPO
        if (lower.startsWith("set repo ") || lower.startsWith("switch to ")) {
            val repo = input.replace(Regex("(?i)(set repo|switch to) "), "").trim()
            val parts = repo.split("/")
            if (parts.size != 2) return "Format: owner/repo"
            activeOwner = parts[0]; activeRepo = parts[1]
            return "✅ Active repo: $activeOwner/$activeRepo"
        }

        return null
    }

    private suspend fun createFullApp(token: String, key: String, appName: String, description: String): String {
        val descLength = description.length
        val tokenBudget = when {
            descLength > 2000 -> 900000; descLength > 1000 -> 500000
            descLength > 500 -> 250000; descLength > 200 -> 100000; else -> 50000
        }
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("📜 Script mode: ${tokenBudget/1000}K tokens allocated.", false))

        val createResult = githubApi(token, "POST", "https://api.github.com/user/repos", """{"name":"$appName","private":false,"auto_init":false}""")
        if (createResult.startsWith("❌")) return "Step 1 failed: $createResult"

        val userResult = githubApi(token, "GET", "https://api.github.com/user", null)
        val owner = Regex("\"login\":\"([^\"]+)\"").find(userResult)?.groupValues?.get(1) ?: return "Couldn't get username."
        activeOwner = owner; activeRepo = appName

        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = tokenBudget })
        val prompt = """
            Generate a COMPLETE shell script creating an entire Android project: "$appName"
            DESCRIPTION: $description
            Use heredoc syntax. Include: build.gradle.kts, app/build.gradle.kts, settings.gradle.kts, gradle.properties, AndroidManifest.xml, MainActivity.kt, themes.xml, strings.xml, .github/workflows/build.yml
            AGP 8.2.0, Kotlin 1.9.22, Compose BOM 2023.10.01, Min SDK 26, Compose Compiler 1.5.10
            Fill ALL template sections with COMPLETE compilable code. No placeholders. Use up to $tokenBudget tokens.
            Return ONLY the shell script.
        """.trimIndent()
        val response = model.generateContent(content { text(prompt) }).text ?: return "Gemini returned empty."

        val script = when {
            response.contains("```bash") -> response.substringAfter("```bash").substringBefore("```").trim()
            response.contains("#!/bin/bash") -> response.substring(response.indexOf("#!/bin/bash")).trim()
            else -> response.trim()
        }

        val encoded = android.util.Base64.encodeToString(script.toByteArray(), android.util.Base64.NO_WRAP)
        githubApi(token, "PUT", "https://api.github.com/repos/$owner/$appName/contents/setup.sh", """{"message":"Add setup script (${script.length} chars)","content":"$encoded"}""")

        val workflowYml = """
name: Build $appName
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: bash setup.sh
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

        return "✅ App '$appName' created!\n📁 github.com/$owner/$appName\n📜 Script: ${script.length} chars\n🧠 Budget: ${tokenBudget/1000}K tokens\n🚀 Build triggered"
    }

    private suspend fun fixFile(token: String, key: String, owner: String, repo: String, filePath: String, instruction: String): String {
        val readResult = githubApi(token, "GET", "https://api.github.com/repos/$owner/$repo/contents/$filePath", null)
        if (readResult.startsWith("❌")) return "File not found: $filePath"
        return try {
            val json = JSONObject(readResult)
            val current = String(android.util.Base64.decode(json.getString("content"), android.util.Base64.DEFAULT))
            val sha = json.getString("sha")
            val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.1f; maxOutputTokens = 8192 })
            val newContent = model.generateContent(content { text("Current:\n```\n$current\n```\n\nInstruction: $instruction\n\nReturn ONLY complete updated file.") }).text ?: return "Gemini returned empty."
            val encoded = android.util.Base64.encodeToString(newContent.toByteArray(), android.util.Base64.NO_WRAP)
            githubApi(token, "PUT", "https://api.github.com/repos/$owner/$repo/contents/$filePath", """{"message":"Fix: $instruction","content":"$encoded","sha":"$sha"}""")
            "✅ Fixed $filePath: $instruction"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun addFile(token: String, key: String, owner: String, repo: String, filePath: String, desc: String): String {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 4096 })
        val content = model.generateContent(content { text("Generate Kotlin file for Android. Path: $filePath. Description: $desc. Return ONLY file content.") }).text ?: return "Gemini returned empty."
        val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        val result = githubApi(token, "PUT", "https://api.github.com/repos/$owner/$repo/contents/$filePath", """{"message":"Add $filePath","content":"$encoded"}""")
        return if (result.startsWith("❌")) result else "✅ Added $filePath"
    }

    // ===== FILE COMMANDS =====
    private fun executeFileCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("list files")) { val p = input.replace(Regex("(?i)list files"), "").trim().ifBlank { Environment.getExternalStorageDirectory().absolutePath }; return try { val f = File(p).listFiles()?.take(30); if (f.isNullOrEmpty()) "Empty" else "📁 $p:\n${f.joinToString("\n") { "${if (it.isDirectory) "📁" else "📄"} ${it.name} (${formatSize(it.length())})" }}" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower.startsWith("search files")) { val q = input.replace(Regex("(?i)search files"), "").trim().ifBlank { return "What?" }; return try { val r = mutableListOf<String>(); searchFiles(File(Environment.getExternalStorageDirectory().absolutePath), q, r, 3); if (r.isEmpty()) "Not found" else "🔍\n${r.joinToString("\n") { "📄 $it" }}" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower.startsWith("delete file")) { val p = input.replace(Regex("(?i)delete file"), "").trim().ifBlank { return "Which?" }; return try { val f = File(p); if (f.exists()) { f.delete(); "✅ Deleted" } else "Not found" } catch (e: Exception) { "❌ ${e.message}" } }
        return null
    }

    // ===== RAG COMMANDS =====
    private fun executeRagCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("read file")) { val path = input.replace(Regex("(?i)(read|show) file"), "").trim(); if (path.isBlank()) return "Usage: read file /sdcard/file.txt"; val content = DocumentRAGHelper.readDocument(com.aura.ai.AuraApplication.instance, path); if (content.startsWith("Error") || content.startsWith("File not found") || content.startsWith("Binary")) return content; return "📄 $path (${content.length} chars):\n\n${if (content.length > 2000) content.take(2000) + "\n\n... (use 'ask doc' to query)" else content}" }
        if (lower.startsWith("ask doc") || lower.startsWith("ask about")) { val parts = input.replace(Regex("(?i)(ask doc|ask about|question)"), "").trim(); val fp = parts.substringBefore(":").trim(); val q = parts.substringAfter(":").trim(); if (fp.isBlank() || q.isBlank()) return "Usage: ask doc /sdcard/file.txt: your question"; return "RAG query sent. (Gemini will search the document.)" }
        if (lower.startsWith("summarize file")) { val path = input.replace(Regex("(?i)summarize file"), "").trim(); if (path.isBlank()) return "Usage: summarize file /sdcard/file.txt"; return "Summarize request sent." }
        return null
    }

    // ===== GITHUB COMMANDS =====
    private suspend fun executeGitHubCommand(input: String): String? {
        val token = preferences.getGitHubToken().ifBlank { return null }
        val lower = input.lowercase()
        if (lower.contains("create") && lower.contains("repo") && !lower.contains("app")) { val name = input.replace(Regex("(?i)(create|a|repo|gitHub)"), "").trim().replace(" ", "-").take(39); if (name.isBlank()) return "Repo name?"; return githubApi(token, "POST", "https://api.github.com/user/repos", """{"name":"$name","private":false,"auto_init":true}""") }
        if (lower.contains("list") && lower.contains("repo")) return githubApi(token, "GET", "https://api.github.com/user/repos?per_page=5&sort=updated", null)
        if (lower.startsWith("compile ") || lower.startsWith("build ")) { val repo = lower.removePrefix("compile ").removePrefix("build ").trim(); val parts = repo.split("/"); if (parts.size != 2) return "Format: owner/repo"; return triggerWorkflow(token, parts[0], parts[1]) }
        return null
    }

    // ===== SUB-AGENT COMMANDS =====
    private suspend fun executeSubAgentCommand(input: String): String? {
        val lower = input.lowercase()
        if (lower.startsWith("spawn agent ") || lower.startsWith("create agent ")) { val parts = input.replace(Regex("(?i)(spawn|create) agent "), "").trim(); val words = parts.split(" "); val name = words.firstOrNull() ?: return "Usage: spawn agent Name Type"; val type = if (words.size > 1) words.drop(1).joinToString(" ") else "General"; val validTypes = listOf("Scraper", "Coder", "Monitor", "Social", "General"); val agentType = validTypes.find { type.equals(it, ignoreCase = true) } ?: "General"; _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("🔄 Spawning $name ($agentType)...", false)); return SubAgentManager.spawnAgent(com.aura.ai.AuraApplication.instance, preferences, name, agentType) }
        if (lower == "list agents") { val agents = SubAgentManager.getAgents(); return if (agents.isEmpty()) "No sub-agents. Try: spawn agent MyScraper Scraper" else "🤖 (${agents.size}):\n${agents.joinToString("\n") { "• ${it.name} [${it.type}] - ${it.status}" }}" }
        if (lower.startsWith("kill agent ")) { val id = input.replace(Regex("(?i)(kill|remove) agent "), "").trim(); return if (SubAgentManager.killAgent(id)) "🗑️ Terminated." else "❌ Not found." }
        return null
    }

    // ===== UTILITY COMMANDS =====
    private fun executeUtilityCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower == "device info") { val r = getRamInfo(); val st = getStorageInfo(); val b = getBatteryInfo(); return "📱 ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\nRAM: $r | Storage: $st | Battery: $b | CPU: ${Runtime.getRuntime().availableProcessors()} cores" }
        if (lower == "time") return "🕐 ${SimpleDateFormat("HH:mm:ss · EEEE, MMMM d", Locale.getDefault()).format(Date())}"
        return null
    }

    // ===== GEMINI FALLBACK =====
    private suspend fun askGemini(input: String): String {
        val key = preferences.getApiKey().ifBlank { return "No API key set." }
        return try { GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.7f; maxOutputTokens = 2048 }).generateContent(content { text(input) }).text ?: "No response." } catch (e: Exception) { if (e.message?.contains("503") == true) "Busy." else "Error: ${e.message}" }
    }

    // ===== GITHUB API HELPERS =====
    private suspend fun githubApi(token: String, method: String, url: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").apply { if (method == "PUT" || method == "POST") put(body!!.toRequestBody("application/json".toMediaType())) else get() }.build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) resp.body?.string() ?: "OK" else "❌ ${resp.message}"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun triggerWorkflow(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val listResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute()
            val body = listResp.body?.string() ?: return@withContext "⚠️ No workflows."
            val wid = Regex("\"id\":(\\d+)").find(body)?.groupValues?.get(1) ?: return@withContext "⚠️ No workflow ID."
            val dResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            if (dResp.isSuccessful) "🚀 Build triggered" else "⚠️ ${dResp.message}"
        } catch (e: Exception) { "⚠️ ${e.message}" }
    }

    // ===== UTILITY FUNCTIONS =====
    private fun resolveAppPackage(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; "camera" -> "com.android.camera"; "gallery","photos" -> "com.google.android.apps.photos"; "gmail" -> "com.google.android.gm"; "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"; "calculator" -> "com.android.calculator2"; "calendar" -> "com.android.calendar"; "clock" -> "com.android.deskclock"; "files" -> "com.android.documentsui"; "phone" -> "com.android.dialer"; "messages" -> "com.google.android.apps.messaging"; "instagram" -> "com.instagram.android"; "facebook" -> "com.facebook.katana"; "twitter","x" -> "com.twitter.android"; "spotify" -> "com.spotify.music"; "netflix" -> "com.netflix.mediaclient"; "telegram" -> "org.telegram.messenger"; "chatgpt" -> "com.openai.chatgpt"; "meta ai" -> "com.facebook.meta_ai"; "copilot" -> "com.microsoft.copilot"; "notes" -> "com.google.android.apps.docs"; else -> null
    }
    private fun getRamInfo(): String { val am = com.aura.ai.AuraApplication.instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val mem = ActivityManager.MemoryInfo(); am.getMemoryInfo(mem); return "${(mem.totalMem - mem.availMem) / (1024*1024*1024)}GB / ${mem.totalMem / (1024*1024*1024)}GB" }
    private fun getStorageInfo(): String { val stat = StatFs(Environment.getDataDirectory().path); return "${stat.availableBlocksLong * stat.blockSizeLong / (1024*1024*1024)}GB free" }
    private fun getBatteryInfo(): String = try { val bm = com.aura.ai.AuraApplication.instance.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%" } catch (e: Exception) { "?" }
    private fun searchFiles(dir: File, q: String, r: MutableList<String>, d: Int) { if (d < 0) return; dir.listFiles()?.forEach { f -> if (f.name.contains(q, true)) r.add(f.absolutePath); if (f.isDirectory && r.size < 20) searchFiles(f, q, r, d - 1) } }
    private fun formatSize(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024*1024 -> "${bytes/1024} KB"; else -> "${bytes/(1024*1024)} MB" }
}
