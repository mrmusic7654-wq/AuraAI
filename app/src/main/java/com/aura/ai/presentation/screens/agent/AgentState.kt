package com.aura.ai.presentation.screens.agent

import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.TaskStatus

data class AgentState(
    val currentInput: String = "",
    val messages: List<ChatMessage> = listOf(
        ChatMessage.Agent("Hello! I'm Aura. What would you like me to do?")
    ),
    val isExecuting: Boolean = false,
    val currentTask: TaskState? = null,
    val hasAccessibilityPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false
)

data class TaskState(
    val id: String,
    val description: String,
    val status: TaskStatus,
    val actions: List<AgentAction> = emptyList(),
    val currentActionIndex: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

sealed class ChatMessage {
    data class User(val content: String) : ChatMessage()
    data class Agent(val content: String) : ChatMessage()
    data class System(val content: String) : ChatMessage()
}
