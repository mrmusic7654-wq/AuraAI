package com.aura.ai.presentation.screens.agent

data class AgentState(
    val currentInput: String = "",
    val messages: List<ChatMessage> = listOf(
        ChatMessage.Agent("Hello! I'm Aura. What would you like me to do?")
    ),
    val isExecuting: Boolean = false,
    val currentTask: TaskState? = null,
    val hasAccessibilityPermission: Boolean = false
)

data class TaskState(
    val id: String = "",
    val description: String = "",
    val status: String = "PENDING",
    val actions: List<String> = emptyList(),
    val currentActionIndex: Int = 0
)
