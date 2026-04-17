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
import com.aura.ai.data.models.github.ProjectTemplate
import com.aura.ai.data.models.github.TemplateCategory
import com.aura.ai.presentation.components.AuraTopBar

@Composable
fun TemplateGalleryScreen(
    onBack: () -> Unit,
    onSelectTemplate: (ProjectTemplate) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<TemplateCategory?>(null) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(
            title = "App Templates",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        // Category chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TemplateCategory.values()) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { 
                        selectedCategory = if (selectedCategory == category) null else category 
                    },
                    label = { Text(category.name) }
                )
            }
        }
        
        // Template grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sampleTemplates.filter { 
                selectedCategory == null || it.category == selectedCategory 
            }) { template ->
                TemplateCard(
                    template = template,
                    onClick = { onSelectTemplate(template) }
                )
            }
        }
    }
}

@Composable
fun TemplateCard(
    template: ProjectTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            Text(
                template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Sample templates for preview
val sampleTemplates = listOf(
    ProjectTemplate(
        id = "blank_compose",
        name = "Blank Compose App",
        description = "Empty Jetpack Compose project",
        category = TemplateCategory.COMPOSE_UI,
        files = emptyList()
    ),
    ProjectTemplate(
        id = "mvi_template",
        name = "MVI Architecture",
        description = "Compose app with MVI pattern",
        category = TemplateCategory.ANDROID_APP,
        files = emptyList()
    )
)
