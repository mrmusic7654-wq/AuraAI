package com.aura.ai.presentation.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SessionDrawer(
    viewModel: AgentViewModel,
    onSessionSelected: (String) -> Unit = {}
) {
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617))))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Fuchsia500.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Psychology, null, tint = Fuchsia400, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("AURA", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("Neural Sessions", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // New Session Button
        Button(
            onClick = { viewModel.createNewSession() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan500.copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.Add, null, tint = Cyan400, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Session", color = Cyan400, style = MaterialTheme.typography.labelLarge)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Model Dashboard Button
        OutlinedButton(
            onClick = { viewModel.toggleModelDashboard() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Fuchsia400),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(Fuchsia500.copy(alpha = 0.3f), Cyan500.copy(alpha = 0.3f)))
            )
        ) {
            Icon(Icons.Default.Tune, null, tint = Fuchsia400, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Models", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sessions List
        Text("SESSIONS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sessions) { session ->
                SessionItem(
                    title = session.title,
                    date = formatSessionDate(session.lastActiveAt),
                    isActive = session.id == currentSessionId,
                    onClick = { viewModel.switchSession(session.id) },
                    onDelete = { viewModel.deleteSession(session.id) }
                )
            }
        }
    }
}

@Composable
private fun SessionItem(
    title: String,
    date: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) Cyan500.copy(alpha = 0.15f) else Color.Transparent,
        border = if (isActive) ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(listOf(Cyan500.copy(alpha = 0.3f), Fuchsia500.copy(alpha = 0.3f)))
        ) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isActive) Icons.Default.Circle else Icons.Default.Circle,
                null,
                tint = if (isActive) Cyan400 else Color(0xFF334155),
                modifier = Modifier.size(8.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    date,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(
                onClick = { showDelete = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Close, "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
            }
        }
    }
    
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete Session") },
            text = { Text("Are you sure you want to delete this session?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDelete = false }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatSessionDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
