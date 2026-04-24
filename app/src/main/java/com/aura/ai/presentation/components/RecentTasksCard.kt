package com.aura.ai.presentation.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun RecentTasksCard(
    tasks: List<TaskEntity>,
    onTaskClick: (TaskEntity) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Tasks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = onViewAllClick) {
                    Text("View All")
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks.take(5)) { task ->
                        RecentTaskItem(
                            task = task,
                            onClick = { onTaskClick(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTaskItem(
    task: TaskEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (task.status) {
                TaskStatus.COMPLETED -> Icons.Default.CheckCircle
                TaskStatus.FAILED -> Icons.Default.Error
                TaskStatus.EXECUTING -> Icons.Default.PlayArrow
                TaskStatus.PAUSED -> Icons.Default.Pause
                else -> Icons.Default.Schedule
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when (task.status) {
                TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                text = formatRelativeTime(task.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000} min ago"
        diff < 86400000 -> "${diff / 3600000} hr ago"
        else -> "${diff / 86400000} days ago"
    }
}
