package com.aura.ai.presentation.screens.agent

import com.aura.ai.presentation.components.ChatMessage

data class AgentState(
    val currentInput: String = "",
    val messages: List<ChatMessage> = listOf(ChatMessage.Agent("Hello! I'm Aura.")),
    val isExecuting: Boolean = false,
    val currentTask: TaskState? = null,
    val hasAccessibilityPermission: Boolean = false
)

data class TaskState(
    val id: String = "",
    val description: String = "",
    val status: String = "PENDING"
)
