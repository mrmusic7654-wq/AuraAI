package com.aura.ai.presentation.screens.agent.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.ai.data.models.AgentAction
import com.aura.ai.data.models.TaskStatus

@Composable
fun TaskExecutionView(
    taskDescription: String,
    status: TaskStatus,
    actions: List<AgentAction>,
    currentActionIndex: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                TaskStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                TaskStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with status and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StatusIcon(status = status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = taskDescription,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2
                    )
                }
                
                // Control buttons
                Row {
                    when (status) {
                        TaskStatus.EXECUTING -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }
                            IconButton(onClick = onStop) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                            }
                        }
                        TaskStatus.PAUSED -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            }
                            IconButton(onClick = onStop) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                            }
                        }
                        else -> {}
                    }
                }
            }
            
            // Progress bar
            if (actions.isNotEmpty() && status != TaskStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = (currentActionIndex + 1).toFloat() / actions.size,
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "Step ${currentActionIndex + 1} of ${actions.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Current action
            AnimatedVisibility(
                visible = currentActionIndex < actions.size,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                if (currentActionIndex < actions.size) {
                    CurrentActionCard(action = actions[currentActionIndex])
                }
            }
            
            // Paused indicator
            if (status == TaskStatus.PAUSED) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PauseCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Task paused. You can safely use other apps.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: TaskStatus) {
    val icon = when (status) {
        TaskStatus.EXECUTING -> Icons.Default.PlayArrow
        TaskStatus.PAUSED -> Icons.Default.Pause
        TaskStatus.COMPLETED -> Icons.Default.CheckCircle
        TaskStatus.FAILED -> Icons.Default.Error
        TaskStatus.PLANNING -> Icons.Default.Autorenew
        TaskStatus.RESUMING -> Icons.Default.Refresh
        else -> Icons.Default.Schedule
    }
    
    val color = when (status) {
        TaskStatus.EXECUTING -> MaterialTheme.colorScheme.primary
        TaskStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (status == TaskStatus.EXECUTING) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = color,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = color
            )
        }
    }
}

@Composable
private fun CurrentActionCard(action: AgentAction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (action.type) {
                    com.aura.ai.data.models.ActionType.TAP -> Icons.Default.TouchApp
                    com.aura.ai.data.models.ActionType.SWIPE -> Icons.Default.Swipe
                    com.aura.ai.data.models.ActionType.TYPE -> Icons.Default.Keyboard
                    com.aura.ai.data.models.ActionType.BACK -> Icons.Default.ArrowBack
                    com.aura.ai.data.models.ActionType.HOME -> Icons.Default.Home
                    com.aura.ai.data.models.ActionType.OPEN_APP -> Icons.Default.Apps
                    com.aura.ai.data.models.ActionType.WAIT -> Icons.Default.HourglassEmpty
                    else -> Icons.Default.Settings
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = action.type.name,
                    style = MaterialTheme.typography.labelLarge
                )
                when {
                    action.target != null -> {
                        Text(
                            text = "Target: ${action.target}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    action.text != null -> {
                        Text(
                            text = "Text: ${action.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    action.packageName != null -> {
                        Text(
                            text = "App: ${action.packageName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
