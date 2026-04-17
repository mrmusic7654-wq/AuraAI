package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.ai.data.models.github.GitHubContent
import com.aura.ai.data.models.github.GitHubRepo

@Composable
fun RepoDetailScreen(
    repo: GitHubRepo,
    onBack: () -> Unit,
    onNavigateToFile: (String) -> Unit,
    onNavigateToWorkflows: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(
            title = repo.name,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onNavigateToWorkflows) {
                    Icon(Icons.Default.Build, contentDescription = "Workflows")
                }
            }
        )
        
        // Content would be loaded here
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(repo.description ?: "No description", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Default branch: ${repo.defaultBranch}")
                        Text("Visibility: ${if (repo.isPrivate) "Private" else "Public"}")
                    }
                }
            }
        }
    }
}
