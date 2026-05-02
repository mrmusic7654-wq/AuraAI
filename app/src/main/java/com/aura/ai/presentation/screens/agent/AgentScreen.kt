package com.aura.ai.presentation.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.presentation.components.AuraTopBar
import com.aura.ai.presentation.components.ChatBubble
import com.aura.ai.presentation.components.ChatMessage

@Composable
fun AgentScreen(viewModel: AgentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AuraTopBar(title = "Aura AI Agent")

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = rememberLazyListState(),
            reverseLayout = true,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages.reversed()) { message ->
                ChatBubble(message = message)
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.currentInput,
                    onValueChange = viewModel::updateInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tell Aura what to do...") },
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (state.isExecuting) {
                    IconButton(
                        onClick = viewModel::stopExecution,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onError)
                    }
                } else {
                    IconButton(
                        onClick = viewModel::sendMessage,
                        enabled = state.currentInput.isNotBlank(),
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(if (state.currentInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send",
                            tint = if (state.currentInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
