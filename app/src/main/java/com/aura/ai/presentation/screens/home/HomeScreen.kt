package com.aura.ai.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.aura.ai.presentation.components.AuraCard
import com.aura.ai.presentation.navigation.Screen

@Composable
fun HomeScreen(
    navController: NavHostController
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Welcome to Aura AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Your intelligent phone assistant",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Quick Actions Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(homeActions) { action ->
                AuraCard(
                    onClick = { navController.navigate(action.route) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.title,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = action.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Recent Activity
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Quick Tips",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Say 'Open WhatsApp and message Mom'")
                Text("• Say 'Search YouTube for cooking videos'")
                Text("• Say 'Turn on Bluetooth'")
                Text("• Create automation rules for repetitive tasks")
            }
        }
    }
}

data class HomeAction(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

val homeActions = listOf(
    HomeAction(
        title = "AI Agent",
        description = "Control your phone with voice",
        icon = Icons.Default.Android,
        route = Screen.Agent.route
    ),
    HomeAction(
        title = "Automation",
        description = "Create smart rules",
        icon = Icons.Default.Autorenew,
        route = Screen.Automation.route
    ),
    HomeAction(
        title = "History",
        description = "View past tasks",
        icon = Icons.Default.History,
        route = Screen.History.route
    ),
    HomeAction(
        title = "Settings",
        description = "Configure Aura",
        icon = Icons.Default.Settings,
        route = Screen.Settings.route
    )
)
