package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.ai.data.models.github.GitHubArtifact
import com.aura.ai.presentation.components.AuraTopBar

@Composable
fun ArtifactDownloadScreen(
    artifacts: List<GitHubArtifact>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onDownload: (GitHubArtifact) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(
            title = "Artifacts",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (artifacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No artifacts found")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(artifacts) { artifact ->
                    ArtifactCard(
                        artifact = artifact,
                        onDownload = { onDownload(artifact) }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtifactCard(
    artifact: GitHubArtifact,
    onDownload: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(artifact.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Size: ${formatFileSize(artifact.sizeInBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
