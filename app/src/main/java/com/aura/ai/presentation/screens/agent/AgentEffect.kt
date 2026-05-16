package com.aura.ai.presentation.screens.agent

sealed class AgentEffect {
    object NavigateToSettings : AgentEffect()
    object NavigateToAccessibilitySettings : AgentEffect()
    data class ShowSnackbar(val message: String) : AgentEffect()
    data class ShareTask(val taskId: String) : AgentEffect()
}
