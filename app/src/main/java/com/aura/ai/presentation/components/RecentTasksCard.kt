package com.aura.ai.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RecentTasksCard(
    tasks: List<String>,
    onTaskClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Recent Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                TextButton(onClick = onViewAllClick) { Text("View All") }
            }
            if (tasks.isEmpty()) {
                Text("No tasks yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tasks.take(5).forEach { task ->
                    Text(text = task, modifier = Modifier.fillMaxWidth().clickable { onTaskClick(task) }.padding(vertical = 8.dp))
                }
            }
        }
    }
}
