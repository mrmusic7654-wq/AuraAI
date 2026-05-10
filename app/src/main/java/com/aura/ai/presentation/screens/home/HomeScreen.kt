package com.aura.ai.presentation.screens.home

import android.app.ActivityManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.theme.*

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val ramInfo = remember { getRamInfo(context) }
    val storageInfo = remember { getStorageInfo() }

    val infiniteTransition = rememberInfiniteTransition(label = "orbit")
    val orbitAngle by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "angle")
    val pulse by infiniteTransition.animateFloat(0.6f, 1.2f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse")

    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        // Background orbit canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 3.5f
            val orbitRadius = 130f

            // Orbit ring
            drawCircle(Color.Cyan500.copy(alpha = 0.1f), orbitRadius, Offset(cx, cy), style = Stroke(1.5f))
            drawCircle(Color(0xFFFFD700).copy(alpha = 0.05f), orbitRadius * 0.7f, Offset(cx, cy), style = Stroke(0.8f))

            // Orbiting dot
            val dotAngle = orbitAngle * Math.PI / 180f
            val dotX = cx + orbitRadius * cos(dotAngle).toFloat()
            val dotY = cy + orbitRadius * sin(dotAngle).toFloat()
            drawCircle(Color(0xFFFFD700), 6f, Offset(dotX, dotY))

            // Connecting lines from center to stats
            val lineColor = Color.Cyan500.copy(alpha = 0.08f)
            drawLine(lineColor, Offset(cx, cy), Offset(cx - 120f, cy + 200f))
            drawLine(lineColor, Offset(cx, cy), Offset(cx + 130f, cy + 200f))
            drawLine(lineColor, Offset(cx, cy), Offset(cx - 100f, cy + 350f))

            // Small orbiting dots on rings
            for (i in 0..5) {
                val a = (orbitAngle * 0.7f + i * 60f) * Math.PI / 180f
                drawCircle(Color(0xFFFFD700).copy(alpha = 0.3f), 2f, Offset(cx + orbitRadius * 0.7f * cos(a).toFloat(), cy + orbitRadius * 0.7f * sin(a).toFloat()))
            }
        }

        // Content
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Center visionary circle
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(100.dp * pulse).clip(CircleShape)
                        .border(2.dp, Cyan500.copy(alpha = 0.4f), CircleShape)
                        .background(Cyan500.copy(alpha = 0.03f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Memory, null, tint = Cyan400, modifier = Modifier.size(50.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("AURA NEXUS", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black)
            Text("Vision Core // Neural Online", style = MaterialTheme.typography.labelMedium, color = Cyan400)

            Spacer(modifier = Modifier.height(50.dp))

            // Stat Cards connected via lines
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                NexusStatCard("RAM", ramInfo.first, Amber400)
                NexusStatCard("Storage", storageInfo.first, Blue400)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                NexusStatCard("Requests", "0/1500", Cyan500)
                NexusStatCard("Model", "2.5 Flash", Emerald500)
            }

            Spacer(modifier = Modifier.height(12.dp))
            NexusStatCard("CPU", "${Runtime.getRuntime().availableProcessors()} Cores Active", Rose400)

            Spacer(modifier = Modifier.weight(1f))

            // Bottom tip
            Card(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.8f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Cyan400, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tap NEURAL to chat with Gemini. Add API key in PROTOCOL.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun NexusStatCard(label: String, value: String, accent: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

fun getRamInfo(context: android.content.Context): Pair<String, String> {
    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val used = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024 * 1024)
    val total = memInfo.totalMem / (1024 * 1024 * 1024)
    return Pair("${used}GB", "${total}GB")
}

fun getStorageInfo(): Pair<String, String> {
    val stat = StatFs(Environment.getDataDirectory().path)
    val available = stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024 * 1024)
    val total = stat.blockCountLong * stat.blockSizeLong / (1024 * 1024 * 1024)
    return Pair("${available}GB", "${total}GB")
}
