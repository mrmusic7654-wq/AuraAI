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

data class ChatMessage(val text: String, val isUser: Boolean)
data class ModelInfo(val name: String, val displayName: String, val strength: String, val dailyRequests: Int, val dailyLimit: Int, val isInCooldown: Boolean, val isSelected: Boolean)
data class BuildLoopState(val attemptNumber: Int = 0, val maxAttempts: Int = 20, val buildStatus: BuildStatus = BuildStatus.IDLE, val workflowRunId: Long? = null, val errorSummary: String = "", val lastFixDescription: String = "", val buildUrl: String = "", val totalFixesApplied: Int = 0)
enum class BuildStatus { IDLE, BUILDING, SUCCESS, ANALYZING_ERROR, FIXING, RETRYING, FAILED }
data class AgentUiState(val messages: List<ChatMessage> = listOf(ChatMessage("AURA AI READY", false)), val input: String = "", val loading: Boolean = false, val isExecuting: Boolean = false, val currentTask: String = "", val executionMode: ExecutionMode = ExecutionMode.IDLE, val activeModel: String = "gemini-3.1-flash-lite", val showDrawer: Boolean = false, val showModelDashboard: Boolean = false, val manualModelSelected: Boolean = false, val currentSessionId: String? = null, val buildLoop: BuildLoopState? = null, val isGeneratingApp: Boolean = false, val generationProgress: String = "")
enum class ExecutionMode { IDLE, CHATTING, GENERATING_APP, PHONE_CONTROL, GITHUB_OPERATION, FILE_OPERATION, REPO_ANALYSIS, FEATURE_TRANSFER }

@HiltViewModel
class AgentViewModel @Inject constructor(private val preferences: AuraPreferences) : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()
    private var taskJob: Job? = null
    private var activeRepo = ""
    private var activeOwner = ""
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val sessionDb by lazy { SessionDatabase.getInstance(com.aura.ai.AuraApplication.instance) }
    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    private val _modelUsage = MutableStateFlow<List<ModelUsageEntity>>(emptyList())

    private val modelRegistry = linkedMapOf(
        "gemini-3.1-flash-lite" to Triple(1000, 15, "Fast"),
        "gemini-2.5-flash" to Triple(250, 10, "Balanced"),
        "gemini-2.0-flash-lite" to Triple(1500, 15, "Legacy")
    )
    private val modelCooldowns = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            sessionDb.sessionDao().getAllSessions().collect { _sessions.value = it }
        }
    }

    fun updateInput(text: String) { _state.value = _state.value.copy(input = text) }
    fun toggleDrawer() { _state.value = _state.value.copy(showDrawer = !_state.value.showDrawer) }
    fun toggleModelDashboard() { _state.value = _state.value.copy(showModelDashboard = !_state.value.showModelDashboard) }
    
    fun getModelInfoList(): List<ModelInfo> {
        return modelRegistry.map { (name, spec) ->
            ModelInfo(name, name, spec.third, 0, spec.first, false, name == _state.value.activeModel)
        }
    }

    fun send() {
        val msg = _state.value.input.trim()
        if (msg.isBlank()) return
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(msg, true), input = "", loading = true)
        taskJob = viewModelScope.launch {
            val result = processCommand(msg)
            _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(result, false), loading = false)
        }
    }

    private suspend fun processCommand(input: String): String {
        val lower = input.lowercase().trim()
        if (lower == "device info") return "Model: ${Build.MODEL}, Android: ${Build.VERSION.RELEASE}"
        if (lower == "time") return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        if (lower.startsWith("list files")) {
            val files = File(Environment.getExternalStorageDirectory().absolutePath).listFiles()?.take(20)
            return if (files.isNullOrEmpty()) "Empty" else files.joinToString("\n") { it.name }
        }
        return executeGeminiChat(input)
    }

    private suspend fun executeGeminiChat(input: String): String {
        val key = preferences.getApiKey() ?: return "No API key"
        return try {
            val model = GenerativeModel("gemini-2.5-flash", key, generationConfig { maxOutputTokens = 60000 })
            model.generateContent(content { text(input) }).text ?: "No response"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun resolveAppPackage(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; else -> null
    }
}
