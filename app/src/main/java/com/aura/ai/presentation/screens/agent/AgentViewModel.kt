package com.aura.ai.presentation.screens.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class AgentViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = "AIzaSyBdYMFp6n-VdPNXco9VtZRhlwrngd1Rwvo",
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 2048
        }
    )

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
                val response = model.generateContent(
                    content { text(userMessage) }
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
