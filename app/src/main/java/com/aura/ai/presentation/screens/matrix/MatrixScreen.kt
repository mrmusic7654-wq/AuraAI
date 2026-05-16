package com.aura.ai.presentation.screens.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.theme.*

data class LogEntry(val timestamp: String, val level: LogLevel, val message: String)
enum class LogLevel(val label: String, val color: Color) { INFO("INFO", Cyan500), WARN("WARN", Amber400), ERROR("ERROR", Rose400), SUCCESS("OK", Emerald500), DEBUG("DEBUG", TextMuted) }

@Composable
fun MatrixScreen() {
    val logs = remember {
        List(25) { i ->
            val level = LogLevel.entries[i % LogLevel.entries.size]
            val messages = listOf(
                "Neural sync completed: 98.4% integrity",
                "Swarm agent GHOST_FLEET disconnected",
                "Task 'Weather Monitor' completed successfully",
                "API rate limit approaching: 1420/1500 requests used",
                "Gemini response time: 1.2s (normal)",
                "Vault scan completed: 0 threats detected",
                "Scheduler triggered: 'Email Digest' started",
                "NetSurfer cache cleared: 24MB freed"
            )
            LogEntry("0${(i % 12) + 1}:${(i * 3) % 60}:${(i * 7) % 60}", level, messages[i % messages.size])
        }
    }
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Rose400.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Rose400.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Terminal, null, tint = Rose400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("DEEP MATRIX", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("System Logs // Debug Terminal", style = MaterialTheme.typography.labelMedium, color = Rose400)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { filterLevel = null }) { Icon(Icons.Default.Delete, null, tint = Rose400) }
        }

        // Filter chips
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filterLevel == null, onClick = { filterLevel = null }, label = { Text("ALL") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Rose400.copy(alpha = 0.2f)))
            LogLevel.entries.forEach { level ->
                FilterChip(selected = filterLevel == level, onClick = { filterLevel = if (filterLevel == level) null else level },
                    label = { Text(level.label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = level.color.copy(alpha = 0.2f)))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log list
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(logs.filter { filterLevel == null || it.level == filterLevel }) { log ->
                LogRow(log)
            }
        }
    }
}

@Composable
fun LogRow(log: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(log.level.color.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(log.timestamp, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextMuted)
        Spacer(modifier = Modifier.width(12.dp))
        Surface(shape = RoundedCornerShape(4.dp), color = log.level.color.copy(alpha = 0.2f)) {
            Text(log.level.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = log.level.color)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(log.message, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Color.White)
    }
}
