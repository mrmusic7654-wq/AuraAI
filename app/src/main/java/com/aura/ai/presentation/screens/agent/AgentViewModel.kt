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
💻 Create App: create app MyApp [description] — generates complete apps via multi-pass Gemini
🐙 GitHub: create repo [name] | list repos | compile repo [o/r] | browse repo [o/r] | read repo file [o/r] [path]
📂 Files: list files | search files [q] | delete file [path] | read file [path]
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

    fun updateInput(text: String) { _state.value = _state.value.copy(input = text) }

    fun send() {
        val msg = _state.value.input.trim()
        if (msg.isBlank()) return
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(msg, true), input = "", loading = true)
        if (handleControlCommand(msg)) return

        taskJob = viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, currentTask = msg)
            val result = executePhoneCommand(msg)
                ?: executeFileCommand(msg)
                ?: executeGitHubCommand(msg)
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
                if (_state.value.isExecuting || isPaused) { taskJob?.cancel(); isPaused = false; _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("⏹️ Stopped.", false), loading = false, isExecuting = false, currentTask = "") }
                else _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("No task running.", false), loading = false)
                return true
            }
        }
        return false
    }

    // ===== PHONE CONTROL =====
    private suspend fun executePhoneCommand(input: String): String? {
        val lower = input.lowercase().trim()
        val service = AuraAccessibilityService.instance ?: return null

        if (lower.startsWith("open ")) { val n = lower.removePrefix("open ").trim(); val p = resolveAppPackage(n); if (p != null) { try { val i = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(p); if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); com.aura.ai.AuraApplication.instance.startActivity(i); return "✅ Opened $n" } } catch (e: Exception) { return "❌ ${e.message}" } }; return "❌ Unknown: $n" }
        if (lower == "home") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "🏠 Home" }
        if (lower == "back") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "⬅️ Back" }
        if (lower == "recents") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); return "📱 Recents" }
        if (lower == "notifications") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); return "🔔 Notifications" }
        if (lower == "quick settings") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); return "⚙️ Quick settings" }
        if (lower == "screenshot") { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "📸 Screenshot" }
        if (lower == "scroll down") { scrollScreen(service, false); return "👇 Scrolled" }
        if (lower == "scroll up") { scrollScreen(service, true); return "👆 Scrolled" }
        if (lower.startsWith("tap on ")) { val t = lower.removePrefix("tap on ").trim(); return if (tapOnText(service, t)) "👆 Tapped '$t'" else "❌ Not found" }
        if (lower.startsWith("type ")) { return if (typeText(service, input.removePrefix("type ").trim())) "⌨️ Typed" else "❌ Failed" }
        if (lower == "swipe left") { swipeScreen(service, false); return "👈 Swiped" }
        if (lower == "swipe right") { swipeScreen(service, true); return "👉 Swiped" }
        return null
    }

    // ===== FILE COMMANDS =====
    private fun executeFileCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("list files")) { val p = input.replace(Regex("(?i)list files"), "").trim().ifBlank { Environment.getExternalStorageDirectory().absolutePath }; return try { val f = File(p).listFiles()?.take(30); if (f.isNullOrEmpty()) "Empty" else "📁 $p:\n${f.joinToString("\n") { "${if (it.isDirectory) "📁" else "📄"} ${it.name} (${formatSize(it.length())})" }}" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower.startsWith("search files")) { val q = input.replace(Regex("(?i)search files"), "").trim().ifBlank { return "What?" }; return try { val r = mutableListOf<String>(); searchFiles(File(Environment.getExternalStorageDirectory().absolutePath), q, r, 3); if (r.isEmpty()) "Not found" else "🔍\n${r.joinToString("\n") { "📄 $it" }}" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower.startsWith("delete file")) { val p = input.replace(Regex("(?i)delete file"), "").trim().ifBlank { return "Which?" }; return try { val f = File(p); if (f.exists()) { f.delete(); "✅ Deleted" } else "Not found" } catch (e: Exception) { "❌ ${e.message}" } }
        if (lower.startsWith("read file")) { val p = input.replace(Regex("(?i)(read|show) file"), "").trim(); if (p.isBlank()) return "Usage: read file /path"; return try { val c = File(p).readText(); "📄 $p (${c.length} chars):\n\n${if (c.length > 2000) c.take(2000) + "\n\n... (truncated)" else c}" } catch (e: Exception) { "❌ ${e.message}" } }
        return null
    }

    // ===== GITHUB COMMANDS =====
    private suspend fun executeGitHubCommand(input: String): String? {
        val token = preferences.getGitHubToken().ifBlank { return null }
        val lower = input.lowercase()
        val key = preferences.getApiKey().ifBlank { return "No Gemini API key set." }

        // CREATE APP - Multi-pass generation
        if ((lower.startsWith("create app") || lower.startsWith("build app") || lower.startsWith("make app")) && !lower.contains("repo")) {
            val appDesc = input.replace(Regex("(?i)(create|build|make) app"), "").trim()
            val appName = appDesc.split(" ").firstOrNull()?.replace(" ", "-")?.take(39) ?: "MyApp"
            val description = if (appDesc.split(" ").size > 1) appDesc.substringAfter(" ").trim() else "A simple app"
            return createAppFromDescription(token, key, appName, description)
        }

        // CREATE REPO
        if (lower.contains("create") && lower.contains("repo")) {
            val name = input.replace(Regex("(?i)(create|a|repo|gitHub)"), "").trim().replace(" ", "-").take(39)
            if (name.isBlank()) return "Repo name?"
            return githubApi(token, "POST", "https://api.github.com/user/repos", """{"name":"$name","private":false,"auto_init":true}""")
        }

        // LIST REPOS
        if (lower.contains("list") && lower.contains("repo")) {
            return githubApi(token, "GET", "https://api.github.com/user/repos?per_page=5&sort=updated", null)
        }

        // COMPILE REPO
        if (lower.startsWith("compile ") || lower.startsWith("build ")) {
            val repo = lower.removePrefix("compile ").removePrefix("build ").trim()
            val parts = repo.split("/"); if (parts.size != 2) return "Format: owner/repo"
            return triggerWorkflow(token, parts[0], parts[1])
        }

        // BROWSE REPO
        if (lower.startsWith("browse repo ") || lower.startsWith("read repo ") && !lower.contains("file")) {
            val repo = lower.removePrefix("browse repo ").removePrefix("read repo ").trim()
            val parts = repo.split("/"); if (parts.size != 2) return "Format: owner/repo"
            return browseRepository(token, parts[0], parts[1])
        }

        // READ REPO FILE
        if (lower.startsWith("read repo file ")) {
            val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" ")
            if (parts.size < 2) return "Format: read repo file owner/repo path/to/file.kt"
            val repoParts = parts[0].split("/"); if (repoParts.size != 2) return "Format: read repo file owner/repo path/to/file.kt"
            return readRepoFile(token, repoParts[0], repoParts[1], parts.drop(1).joinToString(" "))
        }

        // FIX FILE
        if (lower.startsWith("fix ") || lower.startsWith("fix file ") || lower.startsWith("edit ")) {
            val filePath = input.replace(Regex("(?i)(fix|fix file|edit|update) "), "").substringBefore(":").trim()
            val instruction = input.substringAfter(":").trim().ifBlank { return "Usage: fix file path: instruction" }
            if (activeRepo.isBlank()) return "No active repo. Use 'set repo owner/repo' first."
            return fixFile(token, key, activeOwner, activeRepo, filePath, instruction)
        }

        // ADD FILE
        if (lower.startsWith("add file ") || lower.startsWith("create file ")) {
            val filePath = input.replace(Regex("(?i)(add|create) file "), "").substringBefore(":").trim()
            val desc = input.substringAfter(":").trim().ifBlank { return "Usage: add file path: description" }
            if (activeRepo.isBlank()) return "No active repo."
            return addFile(token, key, activeOwner, activeRepo, filePath, desc)
        }

        // SET ACTIVE REPO
        if (lower.startsWith("set repo ") || lower.startsWith("switch to ")) {
            val repo = input.replace(Regex("(?i)(set repo|switch to) "), "").trim()
            val parts = repo.split("/"); if (parts.size != 2) return "Format: owner/repo"
            activeOwner = parts[0]; activeRepo = parts[1]
            return "✅ Active repo: $activeOwner/$activeRepo"
        }

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
        val key = preferences.getApiKey().ifBlank { return "No API key set. Add it in Protocol settings." }
        return try { GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.7f; maxOutputTokens = 2048 }).generateContent(content { text(input) }).text ?: "No response." } catch (e: Exception) { if (e.message?.contains("503") == true) "Busy." else "Error: ${e.message}" }
    }

    // ============================================
    // MULTI-PASS APP GENERATION
    // ============================================

    private suspend fun createAppFromDescription(token: String, key: String, appName: String, description: String): String {
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("🔍 Planning file structure for '$appName'...", false))
        
        val fileList = generateFileList(key, appName, description)
        if (fileList.isEmpty()) return "❌ Could not generate file structure. Try a more detailed description."
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("📋 Planned ${fileList.size} files.", false))
        
        val createResult = githubApi(token, "POST", "https://api.github.com/user/repos", """{"name":"$appName","private":false,"auto_init":false}""")
        if (createResult.startsWith("❌")) return "❌ Failed to create repo: $createResult"
        val userResult = githubApi(token, "GET", "https://api.github.com/user", null)
        val owner = Regex("\"login\":\"([^\"]+)\"").find(userResult)?.groupValues?.get(1) ?: return "Could not get GitHub username."
        activeOwner = owner; activeRepo = appName

        val batchSize = 40; val batches = fileList.chunked(batchSize)
        val allFiles = mutableMapOf<String, String>()
        var contextSummary = "Starting: $appName"; var totalPushed = 0

        for ((batchNum, batch) in batches.withIndex()) {
            val label = "${batchNum + 1}/${batches.size}"
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("📝 Batch $label: ${batch.size} files...", false))
            val generated = generateBatchFiles(key, appName, description, batch, allFiles, batchNum + 1, batches.size, contextSummary)
            allFiles.putAll(generated)
            generated.forEach { (path, content) ->
                val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
                if (!githubApi(token, "PUT", "https://api.github.com/repos/$owner/$appName/contents/$path", """{"message":"Add $path","content":"$encoded"}""").startsWith("❌")) totalPushed++
            }
            contextSummary = buildContextSummary(allFiles, batchNum + 1, batches.size)
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage("✅ Batch $label: ${generated.size} generated, $totalPushed pushed.", false))
        }

        if (description.contains("Android") || description.contains("Kotlin") || description.contains("Compose") || fileList.any { it.contains("build.gradle") }) {
            val wf = """name: Build $appName\non: [push, workflow_dispatch]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - uses: actions/setup-java@v4\n        with: {java-version: '17', distribution: 'temurin'}\n      - run: chmod +x gradlew\n      - run: ./gradlew assembleDebug\n      - uses: actions/upload-artifact@v4\n        with: {name: ${appName}-debug, path: app/build/outputs/apk/debug/app-debug.apk}"""
            val ewf = android.util.Base64.encodeToString(wf.toByteArray(), android.util.Base64.NO_WRAP)
            githubApi(token, "PUT", "https://api.github.com/repos/$owner/$appName/contents/.github/workflows/build.yml", """{"message":"Add CI","content":"$ewf"}""")
        }

        return "✅ APP CREATED: $appName\n📁 github.com/$owner/$appName\n📄 $totalPushed/${fileList.size} files\n🧠 ${batches.size} passes\n\n🔧 Commands:\n• browse repo $owner/$appName\n• fix file path: instruction\n• add file path: description"
    }

    private suspend fun generateFileList(key: String, appName: String, description: String): List<String> {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 4096 })
        val prompt = """
            Generate a COMPLETE file list for: "$appName". Description: $description
            ${if (description.contains("Android") || description.contains("Kotlin") || description.contains("Compose")) "Use AGP 8.2.0, Kotlin 1.9.22, Compose BOM 2023.10.01, Min SDK 26. Include build files, manifest, Kotlin sources, resources, CI." else "Generate appropriate files for this project type."}
            Return ONLY a JSON array: ["path/file1","path/file2",...]
            Generate ${if (description.length > 200) "40-80" else "15-30"} files.
        """.trimIndent()
        return try {
            val resp = model.generateContent(content { text(prompt) }).text ?: return emptyList()
            val arr = org.json.JSONArray(resp.substringAfter("[").substringBeforeLast("]").let { "[$it]" })
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun generateBatchFiles(key: String, appName: String, description: String, batch: List<String>, existingFiles: Map<String, String>, batchNum: Int, totalBatches: Int, contextSummary: String): Map<String, String> {
        val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 60000 })
        val existing = if (existingFiles.isNotEmpty()) "ALREADY GENERATED:\n${existingFiles.keys.take(15).joinToString("\n") { "  ✅ $it" }}" else "No files yet."
        val prompt = """
            Generate batch $batchNum/$totalBatches for: "$appName". Description: $description
            Context: $contextSummary. $existing
            TO GENERATE (${batch.size}): ${batch.joinToString("\n") { "  ⏳ $it" }}
            Return ONLY JSON: {"files":[{"path":"...","content":"..."}]}. Complete code, no placeholders.
        """.trimIndent()
        return try {
            val resp = model.generateContent(content { text(prompt) }).text ?: return emptyMap()
            val obj = org.json.JSONObject(resp.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
            val arr = obj.getJSONArray("files"); val result = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) { val f = arr.getJSONObject(i); result[f.getString("path")] = f.getString("content").replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\") }
            result
        } catch (e: Exception) { emptyMap() }
    }

    private fun buildContextSummary(files: Map<String, String>, passNum: Int, totalPasses: Int): String {
        val kf = files.keys.filter { it.contains("Main") || it.contains("App") || it.contains("build.gradle") || it.contains("AndroidManifest") || it.contains("main") || it.contains("index") || it.contains("package.json") }.take(5)
        return "Pass $passNum/$totalPasses done. ${files.size} files. Key: ${kf.joinToString(", ")}."
    }

    // ===== GITHUB API HELPERS =====
    private suspend fun githubApi(token: String, method: String, url: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").apply { if (method == "PUT" || method == "POST") put(body!!.toRequestBody("application/json".toMediaType())) else get() }.build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) resp.body?.string() ?: "OK" else "❌ ${resp.message}"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun triggerWorkflow(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val lr = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows").header("Authorization", "Bearer $token").build()).execute()
            val wid = Regex("\"id\":(\\d+)").find(lr.body?.string() ?: "")?.groupValues?.get(1) ?: return@withContext "⚠️ No workflows."
            val dr = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/workflows/$wid/dispatches").header("Authorization", "Bearer $token").post("""{"ref":"main"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            if (dr.isSuccessful) "🚀 Build triggered" else "⚠️ ${dr.message}"
        } catch (e: Exception) { "⚠️ ${e.message}" }
    }

    private suspend fun browseRepository(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val rr = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            if (!rr.isSuccessful) return@withContext "❌ Not found."
            val rj = org.json.JSONObject(rr.body?.string() ?: "{}")
            val desc = rj.optString("description", "No desc"); val stars = rj.optInt("stargazers_count", 0); val lang = rj.optString("language", "?"); val branch = rj.optString("default_branch", "main")
            val tr = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            val tj = org.json.JSONObject(tr.body?.string() ?: "{}"); val tree = tj.optJSONArray("tree")
            val dirs = mutableSetOf<String>(); val files = mutableListOf<String>()
            if (tree != null) for (i in 0 until minOf(tree.length(), 100)) { val item = tree.getJSONObject(i); if (item.getString("type") == "tree") dirs.add(item.getString("path")) else files.add(item.getString("path")) }
            buildString {
                append("📁 $owner/$repo\n📝 $desc\n⭐ $stars | 💻 $lang | 🌿 $branch\n\n📂 Dirs (${dirs.size}):\n${dirs.take(15).joinToString("\n") { "  📁 $it" }}")
                if (dirs.size > 15) append("\n  ... +${dirs.size - 15} more")
                append("\n\n📄 Files (${files.size}):\n${files.take(20).joinToString("\n") { "  📄 $it" }}")
                if (files.size > 20) append("\n  ... +${files.size - 20} more")
            }
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun readRepoFile(token: String, owner: String, repo: String, path: String): String = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            if (!resp.isSuccessful) return@withContext "❌ Not found."
            val json = org.json.JSONObject(resp.body?.string() ?: "{}"); val content = json.optString("content", "")
            if (content.isBlank()) return@withContext "Empty"
            val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
            if (decoded.length > 3000) "📄 $owner/$repo/$path\n\n${decoded.take(3000)}\n\n... (${decoded.length} total)" else "📄 $owner/$repo/$path\n\n$decoded"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun fixFile(token: String, key: String, owner: String, repo: String, path: String, instruction: String): String = withContext(Dispatchers.IO) {
        try {
            val rr = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            if (!rr.isSuccessful) return@withContext "❌ File not found."
            val json = org.json.JSONObject(rr.body?.string() ?: "{}")
            val current = String(android.util.Base64.decode(json.getString("content"), android.util.Base64.DEFAULT)); val sha = json.getString("sha")
            val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.1f; maxOutputTokens = 8192 })
            val newContent = model.generateContent(content { text("Current:\n```\n$current\n```\nInstruction: $instruction\nReturn ONLY complete file.") }).text ?: return@withContext "Gemini empty."
            val encoded = android.util.Base64.encodeToString(newContent.toByteArray(), android.util.Base64.NO_WRAP)
            val pr = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").put("""{"message":"Fix: $instruction","content":"$encoded","sha":"$sha"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            if (pr.isSuccessful) "✅ Fixed $path" else "❌ ${pr.message}"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun addFile(token: String, key: String, owner: String, repo: String, path: String, desc: String): String = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.2f; maxOutputTokens = 4096 })
            val content = model.generateContent(content { text("Generate complete file. Path: $path. Description: $desc. Return ONLY file content.") }).text ?: return@withContext "Gemini empty."
            val encoded = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            val pr = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").put("""{"message":"Add $path","content":"$encoded"}""".toRequestBody("application/json".toMediaType())).build()).execute()
            if (pr.isSuccessful) "✅ Added $path" else "❌ ${pr.message}"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    // ===== ACCESSIBILITY HELPERS =====
    private fun tapOnText(service: AuraAccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findNodeByText(root, text)
        return if (node != null) { val r = android.graphics.Rect(); node.getBoundsInScreen(r); root.recycle(); node.recycle(); val p = android.graphics.Path().apply { moveTo(r.centerX().toFloat(), r.centerY().toFloat()) }; service.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 100)).build(), null, null) } else { root.recycle(); false }
    }

    private fun typeText(service: AuraAccessibilityService, text: String): Boolean {
        val focused = service.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val r = focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args); focused.recycle(); return r
    }

    private fun scrollScreen(service: AuraAccessibilityService, up: Boolean) {
        val d = service.resources.displayMetrics
        val p = if (up) android.graphics.Path().apply { moveTo(d.widthPixels / 2f, d.heightPixels * 0.3f); lineTo(d.widthPixels / 2f, d.heightPixels * 0.8f) } else android.graphics.Path().apply { moveTo(d.widthPixels / 2f, d.heightPixels * 0.8f); lineTo(d.widthPixels / 2f, d.heightPixels * 0.3f) }
        service.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 300)).build(), null, null)
    }

    private fun swipeScreen(service: AuraAccessibilityService, right: Boolean) {
        val d = service.resources.displayMetrics
        val p = if (right) android.graphics.Path().apply { moveTo(d.widthPixels * 0.2f, d.heightPixels / 2f); lineTo(d.widthPixels * 0.8f, d.heightPixels / 2f) } else android.graphics.Path().apply { moveTo(d.widthPixels * 0.8f, d.heightPixels / 2f); lineTo(d.widthPixels * 0.2f, d.heightPixels / 2f) }
        service.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 300)).build(), null, null)
    }

    private fun findNodeByText(node: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.text?.contains(text, true) == true || node.contentDescription?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) { node.getChild(i)?.let { findNodeByText(it, text) }?.let { return it } }
        return null
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
