package com.aura.ai.presentation.screens.scheduler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun SchedulerScreen() {
    var taskName by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf("Timer") }
    var triggerTime by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCreated by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A)))).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Violet400.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Violet400.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Schedule, null, tint = Violet400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("SCHEDULER", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("Create Timed & Conditional Tasks", style = MaterialTheme.typography.labelMedium, color = Violet400)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Task Name
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Task Name", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = taskName, onValueChange = { taskName = it },
                    placeholder = { Text("e.g., Weather Monitor", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(cursorColor = Violet500, focusedBorderColor = Violet500, unfocusedBorderColor = CyberBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Task Type
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Task Type", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Timer", "Interval", "Condition", "Event").forEach { type ->
                        FilterChip(
                            selected = taskType == type,
                            onClick = { taskType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Violet500.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trigger Configuration
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Trigger", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                when (taskType) {
                    "Timer", "Interval" -> {
                        OutlinedTextField(
                            value = triggerTime, onValueChange = { triggerTime = it },
                            placeholder = { Text(if (taskType == "Timer") "e.g., 14:30 or 2:30 PM" else "e.g., 30 min", color = TextMuted) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(cursorColor = Violet500, focusedBorderColor = Violet500, unfocusedBorderColor = CyberBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                    "Condition" -> {
                        OutlinedTextField(
                            value = condition, onValueChange = { condition = it },
                            placeholder = { Text("e.g., battery > 20% AND wifi == true", color = TextMuted) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(cursorColor = Violet500, focusedBorderColor = Violet500, unfocusedBorderColor = CyberBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                    "Event" -> {
                        OutlinedTextField(
                            value = condition, onValueChange = { condition = it },
                            placeholder = { Text("e.g., notification from WhatsApp", color = TextMuted) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(cursorColor = Violet500, focusedBorderColor = Violet500, unfocusedBorderColor = CyberBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Create Button
        Button(
            onClick = { showCreated = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = taskName.isNotBlank() && (triggerTime.isNotBlank() || condition.isNotBlank()),
            colors = ButtonDefaults.buttonColors(containerColor = Violet500),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Task", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (showCreated) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Emerald500.copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Emerald400, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Task '$taskName' created successfully!", color = Emerald400, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
