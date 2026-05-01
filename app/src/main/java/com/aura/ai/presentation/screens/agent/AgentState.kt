package com.aura.ai.presentation.screens.agent

data class AgentState(
    val currentInput: String = "",
    val messages: List<String> = listOf("Hello! I'm Aura. What would you like me to do?"),
    val isExecuting: Boolean = false,
    val hasAccessibilityPermission: Boolean = false
)
