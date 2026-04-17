package com.aura.ai.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aura.ai.presentation.screens.agent.AgentScreen
import com.aura.ai.presentation.screens.automation.AutomationScreen
import com.aura.ai.presentation.screens.history.HistoryScreen
import com.aura.ai.presentation.screens.home.HomeScreen
import com.aura.ai.presentation.screens.settings.SettingsScreen

@Composable
fun AuraNavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        
        composable(Screen.Agent.route) {
            AgentScreen()
        }
        
        composable(Screen.Automation.route) {
            AutomationScreen()
        }
        
        composable(Screen.History.route) {
            HistoryScreen()
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Agent : Screen("agent")
    object Automation : Screen("automation")
    object History : Screen("history")
    object Settings : Screen("settings")
}
