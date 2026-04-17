@Composable
fun TaskExecutionCard(task: TaskState, onPause: (() -> Unit)? = null, onResume: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (task.status) {
                TaskStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (task.status) {
                        TaskStatus.EXECUTING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Executing: ${task.description}",
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TaskStatus.PAUSED -> {
                            Icon(
                                Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Paused: ${task.description}",
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TaskStatus.RESUMING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resuming...")
                        }
                        TaskStatus.COMPLETED -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Completed: ${task.description}")
                        }
                        else -> {
                            Text(task.description)
                        }
                    }
                }
                
                // Pause/Resume button inline
                when (task.status) {
                    TaskStatus.EXECUTING -> {
                        IconButton(onClick = { onPause?.invoke() }) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pause"
                            )
                        }
                    }
                    TaskStatus.PAUSED -> {
                        IconButton(onClick = { onResume?.invoke() }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Resume"
                            )
                        }
                    }
                    else -> {}
                }
            }
            
            if (task.status == TaskStatus.EXECUTING && task.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = task.currentActionIndex.toFloat() / task.actions.size,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Step ${task.currentActionIndex + 1} of ${task.actions.size}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            if (task.status == TaskStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Paused at step ${task.currentActionIndex + 1} of ${task.actions.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "You can safely use other apps. Resume when ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
