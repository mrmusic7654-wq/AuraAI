package com.aura.ai.presentation.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class QuickAction(
    val id: String,
    val title: String,
    val icon: ImageVector
)

@Composable
fun QuickActionGrid(
    actions: List<QuickAction>,
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
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onActionClick(action) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = action.icon, contentDescription = action.title, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = action.title, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
