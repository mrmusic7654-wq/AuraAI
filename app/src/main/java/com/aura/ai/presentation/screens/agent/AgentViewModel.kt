package com.aura.ai.presentation.screens.agent

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    fun updateInput(input: String) { _state.value = _state.value.copy(currentInput = input) }
    fun sendMessage() {
        val input = _state.value.currentInput
        if (input.isNotBlank()) {
            _state.value = _state.value.copy(
                messages = _state.value.messages + "You: $input" + "Aura: Processing...",
                currentInput = ""
            )
        }
    }
    fun stopExecution() {}
    fun clearConversation() { _state.value = AgentState() }
    fun checkAccessibilityStatus() {}
}
