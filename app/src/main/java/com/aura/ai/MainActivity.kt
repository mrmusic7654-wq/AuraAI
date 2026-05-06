package com.aura.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.ai.presentation.screens.agent.AgentScreen
import com.aura.ai.presentation.screens.home.HomeScreen
import com.aura.ai.presentation.screens.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraMainScreen() }
    }
}

data class NavItem(val route: String, val title: String, val icon: ImageVector)

@Composable
fun AuraMainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        NavItem("home", "Home", Icons.Default.Home),
        NavItem("agent", "Agent", Icons.Default.Android),
        NavItem("settings", "Settings", Icons.Default.Settings)
    )
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, item.title) },
                        label = { Text(item.title) },
                        selected = current == item.route,
                        onClick = {
                            if (current != item.route) navController.navigate(item.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "home", Modifier.padding(padding)) {
            composable("home") { HomeScreen() }
            composable("agent") { AgentScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
