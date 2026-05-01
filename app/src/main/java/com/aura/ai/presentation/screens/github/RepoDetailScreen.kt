package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.ai.presentation.components.AuraTopBar

@Composable
fun RepoDetailScreen(
    repoName: String,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(title = repoName)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Repository details coming soon")
        }
    }
}
