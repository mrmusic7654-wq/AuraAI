package com.aura.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.ai.presentation.screens.agent.AgentScreen
import com.aura.ai.presentation.screens.home.HomeScreen
import com.aura.ai.presentation.screens.settings.SettingsScreen
import com.aura.ai.presentation.screens.tasks.TasksScreen
import com.aura.ai.presentation.screens.scheduler.SchedulerScreen
import com.aura.ai.presentation.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuraCyberpunkTheme {
                AuraMainScreen()
            }
        }
    }
}

data class NavItem(val route: String, val title: String, val icon: ImageVector)

@Composable
fun AuraMainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        NavItem("nexus", "Nexus", Icons.Default.Memory),
        NavItem("neural", "Neural", Icons.Default.Psychology),
        NavItem("tasks", "Tasks", Icons.Default.Assignment),
        NavItem("scheduler", "Schedule", Icons.Default.Schedule),
        NavItem("swarm", "Swarm", Icons.Default.Hub),
        NavItem("protocol", "Protocol", Icons.Default.Security),
        NavItem("vault", "Vault", Icons.Default.Folder),
        NavItem("netsurfer", "Net", Icons.Default.Language),
        NavItem("market", "Market", Icons.Default.ShoppingCart),
        NavItem("matrix", "Matrix", Icons.Default.Terminal)
    )
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF020617).copy(alpha = 0.95f),
                    tonalElevation = 0.dp
                ) {
                    items.forEach { item ->
                        val selected = current == item.route
                        NavigationBarItem(
                            icon = { Icon(item.icon, item.title, tint = if (selected) Cyan500 else Color(0xFF64748B), modifier = Modifier.size(22.dp)) },
                            label = { Text(item.title, style = MaterialTheme.typography.labelMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)), color = if (selected) Color.White else Color(0xFF64748B)) },
                            selected = selected,
                            onClick = {
                                if (current != item.route) navController.navigate(item.route) {
                                    popUpTo("nexus") { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Cyan500.copy(alpha = 0.15f))
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
                composable("swarm") { PlaceholderScreen("Swarm", "Connected AI agents", Emerald400) }
                composable("protocol") { SettingsScreen() }
                composable("vault") { PlaceholderScreen("Vault", "File explorer", Indigo400) }
                composable("netsurfer") { PlaceholderScreen("NetSurfer", "Built-in browser", Blue400) }
                composable("market") { PlaceholderScreen("Market", "AI service marketplace", Amber400) }
                composable("matrix") { PlaceholderScreen("Matrix", "System logs & terminal", Rose400) }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, subtitle: String, accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(80.dp).background(accent.copy(alpha = 0.1f), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Text(title.take(1), style = MaterialTheme.typography.headlineLarge, color = accent, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Coming in next update...", style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}

val Indigo400 = Color(0xFF818CF8)
