package com.aura.ai.presentation.screens.home

import android.app.ActivityManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.theme.*
import java.io.File

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val ramInfo = remember { getRamInfo(context) }
    val storageInfo = remember { getStorageInfo() }
    var requestsUsed by remember { mutableIntStateOf(0) }
    var tokensUsed by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Logo with pulse
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulse by infiniteTransition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "pulse")
        
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(90.dp).clip(CircleShape)
                    .border(2.dp, Cyan500.copy(alpha = pulse * 0.5f), CircleShape)
                    .background(Cyan500.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Memory, null, tint = Cyan400, modifier = Modifier.size(48.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("AURA NEXUS", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Black)
        Text("System Dashboard // Neural Core Online", style = MaterialTheme.typography.labelMedium, color = Cyan400)
        
        Spacer(modifier = Modifier.height(28.dp))

        // ========== API USAGE SECTION ==========
        SectionHeader("API Usage", Icons.Default.Api, Cyan400)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Requests card
        UsageCard(
            title = "Requests",
            used = requestsUsed,
            total = 1500,
            unit = "",
            accent = Cyan500,
            icon = Icons.Default.Send
        )
        Spacer(modifier = Modifier.height(10.dp))
        
        // Tokens card
        UsageCard(
            title = "Tokens",
            used = tokensUsed,
            total = 1000000,
            unit = "",
            accent = Fuchsia500,
            icon = Icons.Default.TextFields
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // ========== DEVICE RESOURCES ==========
        SectionHeader("Device Resources", Icons.Default.Devices, Emerald400)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStatCard("RAM", ramInfo.first, ramInfo.second, Amber400, Modifier.weight(1f))
            MiniStatCard("Storage", storageInfo.first, storageInfo.second, Blue400, Modifier.weight(1f))
            MiniStatCard("CPU", "${Runtime.getRuntime().availableProcessors()} Cores", "Active", Rose400, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ========== QUICK ACTIONS ==========
        SectionHeader("Quick Actions", Icons.Default.FlashOn, Violet400)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard("New Task", Icons.Default.Add, Cyan500, Modifier.weight(1f))
            QuickActionCard("Swarm", Icons.Default.Hub, Emerald500, Modifier.weight(1f))
            QuickActionCard("Browse", Icons.Default.Language, Blue400, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Version footer
        Text("AURA AI v2.0 // NEXUS CORE", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
    }
}

@Composable
fun UsageCard(title: String, used: Int, total: Int, unit: String, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val progress = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("$used / $total", style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("${(progress * 100).toInt()}% used", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
fun MiniStatCard(label: String, value: String, sub: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
fun QuickActionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

fun getRamInfo(context: android.content.Context): Pair<String, String> {
    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val used = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024 * 1024)
    val total = memInfo.totalMem / (1024 * 1024 * 1024)
    return Pair("${used}GB", "${total}GB Total")
}

fun getStorageInfo(): Pair<String, String> {
    val stat = StatFs(Environment.getDataDirectory().path)
    val available = stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024 * 1024)
    val total = stat.blockCountLong * stat.blockSizeLong / (1024 * 1024 * 1024)
    return Pair("${available}GB Free", "${total}GB Total")
}
