package com.aura.ai.presentation.screens.agent

sealed class AgentEvent {
    data class UpdateInput(val text: String) : AgentEvent()
    object SendMessage : AgentEvent()
    object StopExecution : AgentEvent()
    object ClearConversation : AgentEvent()
    object CheckPermissions : AgentEvent()
    object OpenAccessibilitySettings : AgentEvent()
    object OpenOverlaySettings : AgentEvent()
}
