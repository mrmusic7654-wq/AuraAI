package com.aura.ai.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed class ChatMessage {
    data class User(val content: String) : ChatMessage()
    data class Agent(val content: String) : ChatMessage()
    data class System(val content: String) : ChatMessage()
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (message) {
            is ChatMessage.User -> Arrangement.End
            else -> Arrangement.Start
        }
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (message is ChatMessage.User) 16.dp else 4.dp,
                bottomEnd = if (message is ChatMessage.User) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when (message) {
                    is ChatMessage.User -> MaterialTheme.colorScheme.primary
                    is ChatMessage.Agent -> MaterialTheme.colorScheme.secondaryContainer
                    is ChatMessage.System -> MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Text(
                text = when (message) {
                    is ChatMessage.User -> message.content
                    is ChatMessage.Agent -> message.content
                    is ChatMessage.System -> message.content
                },
                modifier = Modifier.padding(12.dp),
                color = when (message) {
                    is ChatMessage.User -> MaterialTheme.colorScheme.onPrimary
                    is ChatMessage.Agent -> MaterialTheme.colorScheme.onSecondaryContainer
                    is ChatMessage.System -> MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}
