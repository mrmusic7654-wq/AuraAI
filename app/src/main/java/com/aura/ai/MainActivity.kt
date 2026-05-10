package com.aura.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.ai.presentation.screens.agent.AgentScreen
import com.aura.ai.presentation.screens.home.HomeScreen
import com.aura.ai.presentation.screens.market.MarketScreen
import com.aura.ai.presentation.screens.matrix.MatrixScreen
import com.aura.ai.presentation.screens.netsurfer.NetSurferScreen
import com.aura.ai.presentation.screens.scheduler.SchedulerScreen
import com.aura.ai.presentation.screens.settings.SettingsScreen
import com.aura.ai.presentation.screens.swarm.SwarmScreen
import com.aura.ai.presentation.screens.tasks.TasksScreen
import com.aura.ai.presentation.screens.vault.VaultScreen
import com.aura.ai.presentation.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.cos
import kotlin.math.sin

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraCyberpunkTheme { AuraMainScreen() } }
    }
}

data class NavItem(val route: String, val title: String, val icon: ImageVector)

@Composable
fun AuraMainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        NavItem("nexus", "NEXUS", Icons.Default.Memory),
        NavItem("neural", "NEURAL", Icons.Default.Psychology),
        NavItem("tasks", "TASKS", Icons.Default.Assignment),
        NavItem("scheduler", "SCHED", Icons.Default.Schedule),
        NavItem("swarm", "SWARM", Icons.Default.Hub),
        NavItem("protocol", "PROTO", Icons.Default.Security),
        NavItem("vault", "VAULT", Icons.Default.Folder),
        NavItem("netsurfer", "NET", Icons.Default.Language),
        NavItem("market", "MARKET", Icons.Default.ShoppingCart),
        NavItem("matrix", "MATRIX", Icons.Default.Terminal)
    )
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Box(modifier = Modifier.fillMaxSize()) {
        // Circuit Background
        CircuitBackground()

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF020617).copy(alpha = 0.97f),
                    tonalElevation = 0.dp
                ) {
                    items.forEach { item ->
                        val selected = current == item.route
                        NavigationBarItem(
                            icon = { Icon(item.icon, item.title, tint = if (selected) Cyan500 else Color(0xFF64748B), modifier = Modifier.size(20.dp)) },
                            label = {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 7.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    color = if (selected) Color.White else Color(0xFF64748B),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            onClick = {
                                if (current != item.route) navController.navigate(item.route) {
                                    popUpTo("nexus") { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Cyan500.copy(alpha = 0.15f)),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(navController, startDestination = "nexus", Modifier.padding(padding)) {
                composable("nexus") { HomeScreen() }
                composable("neural") { AgentScreen() }
                composable("tasks") { TasksScreen() }
                composable("scheduler") { SchedulerScreen() }
                composable("swarm") { SwarmScreen() }
                composable("protocol") { SettingsScreen() }
                composable("vault") { VaultScreen() }
                composable("netsurfer") { NetSurferScreen() }
                composable("market") { MarketScreen() }
                composable("matrix") { MatrixScreen() }
            }
        }
    }
}

@Composable
fun CircuitBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "circuit")
    val glowPhase by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(8000), RepeatMode.Reverse), label = "glow")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.5f

        // Draw circuit grid
        for (i in 0..20) {
            val x = i * w / 20f
            drawLine(Color(0xFF1A1A2E), Offset(x, 0f), Offset(x, h), strokeWidth = strokeWidth * 0.3f)
        }
        for (i in 0..30) {
            val y = i * h / 30f
            drawLine(Color(0xFF1A1A2E), Offset(0f, y), Offset(w, y), strokeWidth = strokeWidth * 0.3f)
        }

        // Draw golden circuit paths
        val paths = listOf(
            listOf(Offset(w * 0.05f, h * 0.1f), Offset(w * 0.2f, h * 0.1f), Offset(w * 0.2f, h * 0.3f), Offset(w * 0.35f, h * 0.3f)),
            listOf(Offset(w * 0.6f, h * 0.05f), Offset(w * 0.6f, h * 0.25f), Offset(w * 0.8f, h * 0.25f), Offset(w * 0.8f, h * 0.4f)),
            listOf(Offset(w * 0.15f, h * 0.7f), Offset(w * 0.15f, h * 0.85f), Offset(w * 0.4f, h * 0.85f)),
            listOf(Offset(w * 0.7f, h * 0.6f), Offset(w * 0.9f, h * 0.6f), Offset(w * 0.9f, h * 0.8f), Offset(w * 0.75f, h * 0.8f)),
            listOf(Offset(w * 0.5f, h * 0.9f), Offset(w * 0.5f, h * 0.95f), Offset(w * 0.65f, h * 0.95f))
        )

        paths.forEach { points ->
            for (i in 0 until points.size - 1) {
                val alpha = (0.15f + 0.1f * sin(glowPhase * 2 * Math.PI + i)).toFloat()
                drawLine(
                    Brush.linearGradient(listOf(Color(0xFFFFD700).copy(alpha = alpha), Color(0xFFFFA500).copy(alpha = alpha * 0.5f))),
                    points[i], points[i + 1], strokeWidth = strokeWidth * (1f + 0.5f * sin(glowPhase * 2 * Math.PI + i)).toFloat()
                )
                // Draw dots at corners
                drawCircle(Color(0xFFFFD700).copy(alpha = alpha * 1.5f), 3f, points[i], style = Stroke(strokeWidth))
            }
        }

        // Scattered gold dots
        for (i in 0..40) {
            val dotAlpha = (0.1f + 0.2f * sin(glowPhase * 3 + i * 0.5f)).toFloat()
            drawCircle(Color(0xFFFFD700).copy(alpha = dotAlpha), 1.5f, Offset(w * (i * 0.023f % 1f + 0.02f), h * (i * 0.031f % 1f + 0.02f)))
        }
    }
}
