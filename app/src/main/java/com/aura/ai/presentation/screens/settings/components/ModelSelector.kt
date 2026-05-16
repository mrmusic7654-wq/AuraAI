package com.aura.ai.presentation.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ModelOption(
    val id: String,
    val name: String,
    val description: String,
    val isFree: Boolean = true
)

val availableModels = listOf(
    ModelOption(
        id = "gemini-2.0-flash-exp",
        name = "Gemini 2.0 Flash",
        description = "Fast, free tier with 1,500 requests/day",
        isFree = true
    ),
    ModelOption(
        id = "gemini-1.5-pro",
        name = "Gemini 1.5 Pro",
        description = "More capable, requires billing",
        isFree = false
    ),
    ModelOption(
        id = "gemini-1.5-flash",
        name = "Gemini 1.5 Flash",
        description = "Balanced speed and capability",
        isFree = false
    )
)

@Composable
fun ModelSelector(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "AI Model",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            availableModels.forEach { model ->
                ModelOptionItem(
                    model = model,
                    isSelected = model.id == selectedModel,
                    onClick = { onModelSelected(model.id) }
                )
                
                if (model != availableModels.last()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelOptionItem(
    model: ModelOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (model.isFree) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "FREE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
