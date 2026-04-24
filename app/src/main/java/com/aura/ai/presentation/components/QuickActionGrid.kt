package com.aura.ai.presentation.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class QuickAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val route: String,
    val color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
)

val defaultQuickActions = listOf(
    QuickAction("agent", "AI Agent", Icons.Default.Android, "agent"),
    QuickAction("automation", "Automation", Icons.Default.Autorenew, "automation"),
    QuickAction("github", "GitHub", Icons.Default.Code, "github"),
    QuickAction("history", "History", Icons.Default.History, "history"),
    QuickAction("settings", "Settings", Icons.Default.Settings, "settings"),
    QuickAction("voice", "Voice", Icons.Default.Mic, "voice")
)

@Composable
fun QuickActionGrid(
    actions: List<QuickAction> = defaultQuickActions,
    onActionClick: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(actions) { action ->
            QuickActionItem(
                action = action,
                onClick = { onActionClick(action) }
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    action: QuickAction,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = action.color.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.title,
                    tint = action.color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = action.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}
