@file:OptIn(ExperimentalMaterial3Api::class)

package com.aura.ai.presentation.screens.swarm

import androidx.compose.animation.core.*
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
fun SwarmScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A)))).verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Emerald400.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Emerald400.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Hub, null, tint = Emerald400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("SWARM NEXUS", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("Multi-Agent Orchestration", style = MaterialTheme.typography.labelMedium, color = Emerald400)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Empty state
        Box(
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(24.dp)).border(2.dp, Emerald400.copy(alpha = 0.2f), RoundedCornerShape(24.dp)).background(Emerald400.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Hub, null, tint = Emerald400.copy(alpha = 0.5f), modifier = Modifier.size(60.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("No Agents Connected", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text("Deploy AI swarm agents to distribute tasks across multiple API keys.", style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(horizontal = 40.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Swarm agents will appear here once integrated via the GitHub module.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
