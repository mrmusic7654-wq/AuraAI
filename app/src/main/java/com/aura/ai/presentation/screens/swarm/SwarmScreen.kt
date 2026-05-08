package com.aura.ai.presentation.screens.swarm

import androidx.compose.animation.core.*
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

data class SwarmAgent(
    val id: String, val name: String, val type: AgentType, val status: AgentStatus,
    val currentTask: String, val requestsUsed: Int, val requestLimit: Int,
    val tokensUsed: Int, val tokenLimit: Int, val lastSync: String
)
enum class AgentType(val label: String) { RECON("Recon"), GUARDIAN("Guardian"), MINER("Miner"), CODER("Coder") }
enum class AgentStatus(val label: String) { ACTIVE("Active"), STANDBY("Standby"), RECHARGING("Recharging"), OFFLINE("Offline") }

@Composable
fun SwarmScreen() {
    val agents = remember {
        listOf(
            SwarmAgent("1", "AURA_PRIME", AgentType.RECON, AgentStatus.ACTIVE, "Scanning job boards", 342, 1500, 156000, 1000000, "2 min ago"),
            SwarmAgent("2", "SENTINEL_X", AgentType.GUARDIAN, AgentStatus.ACTIVE, "Monitoring GitHub repos", 128, 1500, 45000, 1000000, "30 sec ago"),
            SwarmAgent("3", "BIT_MINER", AgentType.MINER, AgentStatus.RECHARGING, "Extracting web data", 891, 1500, 720000, 1000000, "5 min ago"),
            SwarmAgent("4", "NEXUS_CODER", AgentType.CODER, AgentStatus.STANDBY, "Idle - awaiting directive", 45, 1500, 12000, 1000000, "1 hour ago"),
            SwarmAgent("5", "GHOST_FLEET", AgentType.RECON, AgentStatus.OFFLINE, "Disconnected", 1500, 1500, 980000, 1000000, "3 hours ago")
        )
    }
    var selectedAgent by remember { mutableStateOf<SwarmAgent?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Emerald400.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Emerald400.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Hub, null, tint = Emerald400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("SWARM NEXUS", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("${agents.count { it.status == AgentStatus.ACTIVE }} Active / ${agents.size} Total", style = MaterialTheme.typography.labelMedium, color = Emerald400)
            }
        }

        // Stats summary
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SwarmStatCard("Active", "${agents.count { it.status == AgentStatus.ACTIVE }}", Emerald400, Modifier.weight(1f))
            SwarmStatCard("Total Req", "${agents.sumOf { it.requestsUsed }}", Cyan500, Modifier.weight(1f))
            SwarmStatCard("Tokens", formatTokens(agents.sumOf { it.tokensUsed }), Fuchsia500, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Agent list
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(agents) { agent ->
                SwarmAgentCard(agent, selectedAgent == agent, { selectedAgent = if (selectedAgent == agent) null else agent })
            }
        }
    }
}

@Composable
fun SwarmStatCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SwarmAgentCard(agent: SwarmAgent, expanded: Boolean, onClick: () -> Unit) {
    val statusColor = when (agent.status) { AgentStatus.ACTIVE -> Emerald400; AgentStatus.STANDBY -> Amber400; AgentStatus.RECHARGING -> Violet400; AgentStatus.OFFLINE -> TextMuted }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(typeIcon(agent.type), null, tint = statusColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(agent.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(agent.currentTask, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (agent.status == AgentStatus.ACTIVE) Emerald400.copy(alpha = pulse) else statusColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(agent.status.label, style = MaterialTheme.typography.labelMedium, color = statusColor)
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailChip("Requests", "${agent.requestsUsed}/${agent.requestLimit}", Cyan500, Modifier.weight(1f))
                    DetailChip("Tokens", formatTokens(agent.tokensUsed), Fuchsia500, Modifier.weight(1f))
                    DetailChip("Sync", agent.lastSync, TextMuted, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DetailChip(label: String, value: String, accent: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
    }
}

fun typeIcon(type: AgentType) = when (type) { AgentType.RECON -> Icons.Default.TravelExplore; AgentType.GUARDIAN -> Icons.Default.Shield; AgentType.MINER -> Icons.Default.Diamond; AgentType.CODER -> Icons.Default.Code }
fun formatTokens(tokens: Int): String = if (tokens >= 1000) "${tokens / 1000}K" else "$tokens"
