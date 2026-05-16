package com.aura.ai.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Agent : Screen("agent")
    object Automation : Screen("automation")
    object History : Screen("history")
    object Settings : Screen("settings")
    object GitHub : Screen("github")
}
