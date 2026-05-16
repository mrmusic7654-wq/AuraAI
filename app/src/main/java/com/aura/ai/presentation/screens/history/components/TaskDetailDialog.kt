package com.aura.ai.presentation.screens.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.ai.data.local.entities.TaskEntity
import com.aura.ai.data.models.TaskStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TaskDetailDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Task Details",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = task.status)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Description
                DetailSection(title = "Description") {
                    Text(task.description)
                }
                
                // Timestamps
                DetailSection(title = "Timeline") {
                    TimelineItem("Created", task.createdAt)
                    task.startedAt?.let { TimelineItem("Started", it) }
                    task.completedAt?.let { TimelineItem("Completed", it) }
                }
                
                // Actions
                if (task.actions.isNotEmpty()) {
                    DetailSection(title = "Actions (${task.actions.size})") {
                        task.actions.forEachIndexed { index, action ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = action.type.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (index == task.currentActionIndex && task.status == TaskStatus.EXECUTING) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Error
                task.error?.let { error ->
                    DetailSection(title = "Error") {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                onDelete?.let {
                    TextButton(
                        onClick = it,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
                
                onRetry?.let {
                    Button(onClick = it) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
                
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun StatusBadge(status: TaskStatus) {
    val (text, containerColor, contentColor) = when (status) {
        TaskStatus.COMPLETED -> Triple("Done", 
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer)
        TaskStatus.FAILED -> Triple("Failed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer)
        TaskStatus.EXECUTING -> Triple("Running",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer)
        TaskStatus.PAUSED -> Triple("Paused",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Triple("Pending",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant)
    }
    
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun TimelineItem(label: String, timestamp: Long) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = formatDateTime(timestamp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatDateTime(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return format.format(date)
}
