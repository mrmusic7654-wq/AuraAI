package com.aura.ai.presentation.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.presentation.components.AuraTopBar
import com.aura.ai.presentation.components.TaskCard

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AuraTopBar(
            title = "Task History",
            actions = {
                if (state.tasks.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearAllTasks() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All")
                    }
                }
            }
        )
        
        if (state.tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tasks yet.\nGo to Agent to get started!",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.tasks) { task ->
                    TaskCard(
                        task = task,
                        onClick = { viewModel.selectTask(task) },
                        onDelete = { viewModel.deleteTask(task.id) }
                    )
                }
            }
        }
    }
    
    // Task Detail Dialog
    state.selectedTask?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { viewModel.selectTask(null) }
        )
    }
}

@Composable
fun TaskDetailDialog(
    task: com.aura.ai.data.local.entities.TaskEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Task Details") },
        text = {
            Column {
                Text("Description: ${task.description}")
                Text("Status: ${task.status}")
                Text("Created: ${formatTimestamp(task.createdAt)}")
                task.startedAt?.let {
                    Text("Started: ${formatTimestamp(it)}")
                }
                task.completedAt?.let {
                    Text("Completed: ${formatTimestamp(it)}")
                }
                task.error?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}
