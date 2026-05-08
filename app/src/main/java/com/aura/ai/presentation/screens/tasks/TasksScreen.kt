package com.aura.ai.presentation.screens.tasks

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.theme.*

data class AuraTask(
    val id: String,
    val title: String,
    val type: TaskType,
    val status: TaskStatus,
    val progress: Float = 0f,
    val description: String = ""
)

enum class TaskType(val label: String) { TIMER("Timer"), INTERVAL("Interval"), CONDITION("Condition"), EVENT("Event") }
enum class TaskStatus(val label: String) { RUNNING("Running"), PAUSED("Paused"), COMPLETED("Completed"), FAILED("Failed"), WAITING("Waiting") }

@Composable
fun TasksScreen() {
    val tasks = remember {
        listOf(
            AuraTask("1", "Weather Monitor", TaskType.INTERVAL, TaskStatus.RUNNING, 0.65f, "Checks weather every 30 min"),
            AuraTask("2", "GitHub Backup", TaskType.TIMER, TaskStatus.WAITING, 0f, "Daily backup at 2:00 AM"),
            AuraTask("3", "Reddit Scanner", TaskType.CONDITION, TaskStatus.RUNNING, 0.4f, "Scans when WiFi connected"),
            AuraTask("4", "Email Digest", TaskType.EVENT, TaskStatus.PAUSED, 0.8f, "Triggers on new email"),
            AuraTask("5", "Swarm Sync", TaskType.INTERVAL, TaskStatus.COMPLETED, 1f, "Syncs agents every hour"),
            AuraTask("6", "Job Finder", TaskType.CONDITION, TaskStatus.FAILED, 0.2f, "Scrapes when battery > 50%")
        )
    }
    var filterStatus by remember { mutableStateOf<TaskStatus?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Amber400.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Amber400.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Assignment, null, tint = Amber400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("TASK ORCHESTRATOR", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("Active Directives: ${tasks.count { it.status == TaskStatus.RUNNING }}", style = MaterialTheme.typography.labelMedium, color = Amber400)
            }
        }

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filterStatus == null, onClick = { filterStatus = null }, label = { Text("All") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Amber400.copy(alpha = 0.2f)))
            TaskStatus.entries.forEach { status ->
                FilterChip(
                    selected = filterStatus == status,
                    onClick = { filterStatus = if (filterStatus == status) null else status },
                    label = { Text(status.label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = statusColor(status).copy(alpha = 0.2f))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Task List
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tasks.filter { filterStatus == null || it.status == filterStatus }) { task ->
                TaskCard(task)
            }
        }
    }
}

@Composable
fun TaskCard(task: AuraTask) {
    val accent = statusColor(task.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(typeIcon(task.type), null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(task.description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.2f)) {
                    Text(task.status.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }
            if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PAUSED) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = task.progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = accent, trackColor = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("${(task.progress * 100).toInt()}% complete", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.RUNNING -> Cyan500; TaskStatus.PAUSED -> Amber400; TaskStatus.COMPLETED -> Emerald500
    TaskStatus.FAILED -> Rose400; TaskStatus.WAITING -> TextMuted
}

fun typeIcon(type: TaskType) = when (type) {
    TaskType.TIMER -> Icons.Default.Timer; TaskType.INTERVAL -> Icons.Default.Repeat
    TaskType.CONDITION -> Icons.Default.Rule; TaskType.EVENT -> Icons.Default.Notifications
}
