package com.aura.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlin.math.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraCyberpunkTheme { AuraMainScreen() } }
    }
}

data class NavItem(val route: String, val icon: ImageVector, val label: String)

@Composable
fun AuraMainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        NavItem("nexus", Icons.Default.Circle, "NX"),
        NavItem("neural", Icons.Default.Psychology, "NR"),
        NavItem("tasks", Icons.Default.Assignment, "TK"),
        NavItem("scheduler", Icons.Default.Schedule, "SC"),
        NavItem("swarm", Icons.Default.Hub, "SW"),
        NavItem("protocol", Icons.Default.Security, "PR"),
        NavItem("vault", Icons.Default.Folder, "VT"),
        NavItem("netsurfer", Icons.Default.Language, "NT"),
        NavItem("market", Icons.Default.ShoppingCart, "MK"),
        NavItem("matrix", Icons.Default.Terminal, "MX")
    )
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF020617))) {
        // Left Navigation Rail
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(56.dp)
                .background(Color(0xFF020617).copy(alpha = 0.98f))
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Logo
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(Cyan500.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Memory, null, tint = Cyan400, modifier = Modifier.size(20.dp)) }
            Spacer(modifier = Modifier.height(12.dp))

            // Nav items
            items.forEach { item ->
                val selected = current == item.route
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Cyan500.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable {
                            if (current != item.route) navController.navigate(item.route) {
                                popUpTo("nexus") { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(item.icon, null,
                            tint = if (selected) Cyan400 else Color(0xFF475569),
                            modifier = Modifier.size(18.dp))
                        Text(item.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 7.sp),
                            color = if (selected) Cyan400 else Color(0xFF475569))
                    }
                }
            }
        }

        // Main Content Area
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            GameBackground()
            NavHost(navController, startDestination = "nexus") {
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
fun GameBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "game")
    val scanLine by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "scan")
    val gridAlpha by infiniteTransition.animateFloat(0.03f, 0.08f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "grid")
    val orbPulse by infiniteTransition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "orb")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Diagonal grid lines
        for (i in -20..40) {
            val x = i * 60f
            drawLine(Color.White.copy(alpha = gridAlpha), Offset(x, 0f), Offset(x - h, h), strokeWidth = 0.5f)
        }

        // Horizontal scan lines
        val scanY = scanLine * h
        drawLine(Color.Cyan500.copy(alpha = 0.05f), Offset(0f, scanY), Offset(w, scanY), strokeWidth = 80f)

        // Corner decorations
        val cornerSize = 30f
        // Top-left
        drawLine(Color(0xFFFFD700).copy(alpha = 0.3f), Offset(10f, 10f), Offset(10f + cornerSize, 10f), strokeWidth = 2f)
        drawLine(Color(0xFFFFD700).copy(alpha = 0.3f), Offset(10f, 10f), Offset(10f, 10f + cornerSize), strokeWidth = 2f)
        // Top-right
        drawLine(Color(0xFFFFD700).copy(alpha = 0.3f), Offset(w - 10f, 10f), Offset(w - 10f - cornerSize, 10f), strokeWidth = 2f)
        drawLine(Color(0xFFFFD700).copy(alpha = 0.3f), Offset(w - 10f, 10f), Offset(w - 10f, 10f + cornerSize), strokeWidth = 2f)

        // Floating particles
        for (i in 0..30) {
            val px = (sin(i * 1.7 + orbPulse * 3) * w * 0.4f + w * 0.5f)
            val py = (cos(i * 2.1 + orbPulse * 2) * h * 0.4f + h * 0.5f)
            drawCircle(Color.Cyan500.copy(alpha = 0.2f), 2f, Offset(px, py))
        }
    }
}
