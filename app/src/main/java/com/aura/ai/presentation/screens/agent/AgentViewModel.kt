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
🐙 GitHub: create repo [name] | list repos | compile repo [owner/repo] | browse repo [owner/repo] | read repo file [o/r] [path]
📂 Files: list files | search files [query] | delete file [path] | read file [path]
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

    // ===== CONTROL =====
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
        val service = AuraAccessibilityService.instance

        if (lower.startsWith("open ")) {
            val appName = lower.removePrefix("open ").trim()
            val pkg = resolveAppPackage(appName)
            if (pkg != null) {
                try {
                    val intent = com.aura.ai.AuraApplication.instance.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); com.aura.ai.AuraApplication.instance.startActivity(intent); return "✅ Opened $appName" }
                } catch (e: Exception) { return "❌ ${e.message}" }
            }
            return "❌ Unknown app: $appName"
        }
        if (lower == "home") { service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); return "🏠 Home" }
        if (lower == "back") { service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); return "⬅️ Back" }
        if (lower == "recents") { service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); return "📱 Recents" }
        if (lower == "notifications") { service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); return "🔔 Notifications" }
        if (lower == "quick settings") { service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); return "⚙️ Quick settings" }
        if (lower == "screenshot") { service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); return "📸 Screenshot" }
        if (lower == "scroll down") { scrollScreen(service, false); return "👇 Scrolled" }
        if (lower == "scroll up") { scrollScreen(service, true); return "👆 Scrolled" }
        if (lower.startsWith("tap on ")) {
            val target = lower.removePrefix("tap on ").trim()
            val success = tapOnText(service, target)
            return if (success) "👆 Tapped '$target'" else "❌ Could not find '$target'"
        }
        if (lower.startsWith("type ")) {
            val text = input.removePrefix("type ").trim()
            val success = typeText(service, text)
            return if (success) "⌨️ Typed" else "❌ Could not type"
        }
        if (lower == "swipe left") { swipeScreen(service, false); return "👈 Swiped" }
        if (lower == "swipe right") { swipeScreen(service, true); return "👉 Swiped" }
        return null
    }

    // ===== FILE COMMANDS =====
    private fun executeFileCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower.startsWith("list files")) {
            val path = input.replace(Regex("(?i)list files"), "").trim().ifBlank { Environment.getExternalStorageDirectory().absolutePath }
            return try {
                val files = File(path).listFiles()?.take(30)
                if (files.isNullOrEmpty()) "📁 Empty directory." else "📁 $path:\n${files.joinToString("\n") { "${if (it.isDirectory) "📁" else "📄"} ${it.name} (${formatSize(it.length())})" }}"
            } catch (e: Exception) { "❌ ${e.message}" }
        }
        if (lower.startsWith("search files")) {
            val query = input.replace(Regex("(?i)search files"), "").trim().ifBlank { return "What should I search for?" }
            return try {
                val results = mutableListOf<String>()
                searchFiles(File(Environment.getExternalStorageDirectory().absolutePath), query, results, 3)
                if (results.isEmpty()) "🔍 No files found." else "🔍 Found:\n${results.joinToString("\n") { "📄 $it" }}"
            } catch (e: Exception) { "❌ ${e.message}" }
        }
        if (lower.startsWith("delete file")) {
            val path = input.replace(Regex("(?i)delete file"), "").trim().ifBlank { return "Which file?" }
            return try { val f = File(path); if (f.exists()) { f.delete(); "✅ Deleted" } else "Not found" } catch (e: Exception) { "❌ ${e.message}" }
        }
        if (lower.startsWith("read file")) {
            val path = input.replace(Regex("(?i)(read|show) file"), "").trim()
            if (path.isBlank()) return "Usage: read file /path/file.txt"
            return try {
                val content = File(path).readText()
                "📄 $path (${content.length} chars):\n\n${if (content.length > 2000) content.take(2000) + "\n\n... (truncated)" else content}"
            } catch (e: Exception) { "❌ ${e.message}" }
        }
        return null
    }

    // ===== GITHUB COMMANDS =====
    private suspend fun executeGitHubCommand(input: String): String? {
        val token = preferences.getGitHubToken() ?: return null
        val lower = input.lowercase()

        if (lower.contains("create") && lower.contains("repo") && !lower.contains("app")) {
            val name = input.replace(Regex("(?i)(create|a|repo|gitHub)"), "").trim().replace(" ", "-").take(39)
            if (name.isBlank()) return "What should I name the repo?"
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
        if (lower.startsWith("browse repo ") || lower.startsWith("read repo ")) {
            val repo = lower.removePrefix("browse repo ").removePrefix("read repo ").trim()
            val parts = repo.split("/"); if (parts.size != 2) return "Format: owner/repo"
            return browseRepository(token, parts[0], parts[1])
        }
        if (lower.startsWith("read repo file ")) {
            val parts = input.replace(Regex("(?i)read repo file "), "").trim().split(" ")
            if (parts.size < 2) return "Format: read repo file owner/repo path/to/file.kt"
            val repoParts = parts[0].split("/"); if (repoParts.size != 2) return "Format: read repo file owner/repo path/to/file.kt"
            return readRepoFile(token, repoParts[0], repoParts[1], parts.drop(1).joinToString(" "))
        }
        return null
    }

    // ===== UTILITY =====
    private fun executeUtilityCommand(input: String): String? {
        val lower = input.lowercase().trim()
        if (lower == "device info") {
            val r = getRamInfo(); val st = getStorageInfo(); val b = getBatteryInfo()
            return "📱 ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\nRAM: $r | Storage: $st | Battery: $b | CPU: ${Runtime.getRuntime().availableProcessors()} cores"
        }
        if (lower == "time") return "🕐 ${SimpleDateFormat("HH:mm:ss · EEEE, MMMM d", Locale.getDefault()).format(Date())}"
        return null
    }

    // ===== GEMINI =====
    private suspend fun askGemini(input: String): String {
        val key = preferences.getApiKey() ?: return "No API key set. Add it in Protocol settings."
        return try {
            GenerativeModel("gemini-2.5-flash", key, generationConfig { temperature = 0.7f; maxOutputTokens = 2048 })
                .generateContent(content { text(input) }).text ?: "No response."
        } catch (e: Exception) { if (e.message?.contains("503") == true) "Busy." else "Error: ${e.message}" }
    }

    // ===== GITHUB API =====
    private suspend fun githubApi(token: String, method: String, url: String, body: String?): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json")
                .apply { if (method == "PUT" || method == "POST") put(body!!.toRequestBody("application/json".toMediaType())) else get() }.build()
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

    private suspend fun browseRepository(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val repoResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            if (!repoResp.isSuccessful) return@withContext "❌ Repo not found."
            val repoJson = org.json.JSONObject(repoResp.body?.string() ?: "{}")
            val desc = repoJson.optString("description", "No description"); val stars = repoJson.optInt("stargazers_count", 0)
            val lang = repoJson.optString("language", "Unknown"); val branch = repoJson.optString("default_branch", "main")

            val treeResp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            val treeJson = org.json.JSONObject(treeResp.body?.string() ?: "{}"); val tree = treeJson.optJSONArray("tree")
            val files = mutableListOf<String>(); val dirs = mutableSetOf<String>()
            if (tree != null) for (i in 0 until minOf(tree.length(), 100)) {
                val item = tree.getJSONObject(i)
                if (item.getString("type") == "tree") dirs.add(item.getString("path")) else files.add(item.getString("path"))
            }
            buildString {
                append("📁 $owner/$repo\n📝 $desc\n⭐ $stars | 💻 $lang | 🌿 $branch\n\n📂 Dirs (${dirs.size}):\n${dirs.take(15).joinToString("\n") { "  📁 $it" }}")
                if (dirs.size > 15) append("\n  ... and ${dirs.size - 15} more")
                append("\n\n📄 Files (${files.size}):\n${files.take(20).joinToString("\n") { "  📄 $it" }}")
                if (files.size > 20) append("\n  ... and ${files.size - 20} more")
            }
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    private suspend fun readRepoFile(token: String, owner: String, repo: String, path: String): String = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "Bearer $token").header("Accept", "application/vnd.github.v3+json").build()).execute()
            if (!resp.isSuccessful) return@withContext "❌ File not found."
            val json = org.json.JSONObject(resp.body?.string() ?: "{}"); val content = json.optString("content", "")
            if (content.isBlank()) return@withContext "Empty file"
            val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
            val size = json.optInt("size", 0)
            if (decoded.length > 3000) "📄 $owner/$repo/$path (${formatSize(size.toLong())})\n\n${decoded.take(3000)}\n\n... (${decoded.length} total chars)"
            else "📄 $owner/$repo/$path (${formatSize(size.toLong())})\n\n$decoded"
        } catch (e: Exception) { "❌ ${e.message}" }
    }

    // ===== ACCESSIBILITY HELPERS =====
    private fun tapOnText(service: AuraAccessibilityService?, text: String): Boolean {
        if (service == null) return false
        val root = service.rootInActiveWindow ?: return false
        val node = findNodeByText(root, text)
        return if (node != null) {
            val rect = android.graphics.Rect(); node.getBoundsInScreen(rect)
            root.recycle(); node.recycle()
            val path = android.graphics.Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)).build()
            service.dispatchGesture(gesture, null, null)
        } else { root.recycle(); false }
    }

    private fun typeText(service: AuraAccessibilityService?, text: String): Boolean {
        if (service == null) return false
        val focused = service.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val result = focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle(); return result
    }

    private fun scrollScreen(service: AuraAccessibilityService?, up: Boolean) {
        if (service == null) return
        val display = service.resources.displayMetrics
        val path = if (up) {
            android.graphics.Path().apply { moveTo(display.widthPixels / 2f, display.heightPixels * 0.3f); lineTo(display.widthPixels / 2f, display.heightPixels * 0.8f) }
        } else {
            android.graphics.Path().apply { moveTo(display.widthPixels / 2f, display.heightPixels * 0.8f); lineTo(display.widthPixels / 2f, display.heightPixels * 0.3f) }
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)).build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun swipeScreen(service: AuraAccessibilityService?, right: Boolean) {
        if (service == null) return
        val display = service.resources.displayMetrics
        val path = if (right) {
            android.graphics.Path().apply { moveTo(display.widthPixels * 0.2f, display.heightPixels / 2f); lineTo(display.widthPixels * 0.8f, display.heightPixels / 2f) }
        } else {
            android.graphics.Path().apply { moveTo(display.widthPixels * 0.8f, display.heightPixels / 2f); lineTo(display.widthPixels * 0.2f, display.heightPixels / 2f) }
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)).build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun swipeScreen(service: AuraAccessibilityService?, right: Boolean) {
        if (service == null) return
        val display = service.resources.displayMetrics
        val path = if (right) {
            android.graphics.Path().apply { moveTo(display.widthPixels * 0.2f, display.heightPixels / 2f); lineTo(display.widthPixels * 0.8f, display.heightPixels / 2f) }
        } else {
            android.graphics.Path().apply { moveTo(display.widthPixels * 0.8f, display.heightPixels / 2f); lineTo(display.widthPixels * 0.2f, display.heightPixels / 2f) }
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)).build()
        service.dispatchGesture(gesture, null, null)
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
