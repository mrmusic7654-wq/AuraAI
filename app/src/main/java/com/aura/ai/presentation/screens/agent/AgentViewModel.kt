package com.aura.ai.presentation.screens.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.data.local.preferences.AuraPreferences
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val text: String, val isUser: Boolean)

data class AgentUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("Hello! I'm Aura AI. How can I help you?", false)
    ),
    val input: String = "",
    val loading: Boolean = false
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val preferences: AuraPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private fun getModel(): GenerativeModel? {
        val key = preferences.getApiKey()
        if (key.isNullOrBlank()) return null
        return GenerativeModel(
            modelName = "gemini-2.0-flash-exp",
            apiKey = key,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048
            }
        )
    }

    fun updateInput(text: String) {
        _state.value = _state.value.copy(input = text)
    }

    fun send() {
        val userMessage = _state.value.input.trim()
        if (userMessage.isBlank()) return

        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage(userMessage, true),
            input = "",
            loading = true
        )

        viewModelScope.launch {
            try {
                val model = getModel()
                if (model == null) {
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + ChatMessage(
                            "No API key found. Please add your Gemini API key in Settings.",
                            false
                        ),
                        loading = false
                    )
                    return@launch
                }

                val response = model.generateContent(
                    content { text("You are Aura AI, a helpful phone assistant. User: $userMessage") }
                )
                val reply = response.text ?: "I couldn't process that."

                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatMessage(reply, false),
                    loading = false
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatMessage("Error: ${e.message}", false),
                    loading = false
                )
            }
        }
    }
}
