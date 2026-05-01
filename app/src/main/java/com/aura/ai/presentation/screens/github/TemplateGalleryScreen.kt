package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.components.AuraTopBar

@Composable
fun TemplateGalleryScreen(
    onBack: () -> Unit,
    onSelectTemplate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(
            title = "Templates",
            actions = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(listOf("Compose App", "MVI Template", "API App", "Room DB")) { template ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectTemplate(template) }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(template, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
