package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.presentation.components.*

@Composable
fun GitHubScreen(
    viewModel: GitHubViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(
            title = "GitHub",
            actions = {
                IconButton(onClick = { viewModel.loadRepositories() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                if (state.isAuthenticated) {
                    IconButton(onClick = { viewModel.showCreateDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Repo")
                    }
                }
            }
        )
        
        when {
            !state.isAuthenticated -> {
                GitHubAuthPrompt(
                    onAuthenticate = { viewModel.showAuthDialog() }
                )
            }
            state.isLoading && state.repositories.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.repositories.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No repositories found. Create one!")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.repositories) { repo ->
                        RepoCard(
                            repo = repo,
                            onClick = { viewModel.selectRepository(repo) }
                        )
                    }
                }
            }
        }
    }
    
    // Auth Dialog
    if (state.showAuthDialog) {
        GitHubAuthDialog(
            onDismiss = { viewModel.hideAuthDialog() },
            onAuthenticate = { token ->
                viewModel.authenticate(token)
            }
        )
    }
    
    // Create Repo Dialog
    if (state.showCreateDialog) {
        CreateRepoDialog(
            onDismiss = { viewModel.hideCreateDialog() },
            onCreate = { name, desc, isPrivate ->
                viewModel.createRepository(name, desc, isPrivate)
            }
        )
    }
    
    // Error Snackbar
    state.error?.let { error ->
        LaunchedEffect(error) {
            // Show snackbar
        }
    }
}

@Composable
fun GitHubAuthPrompt(
    onAuthenticate: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Connect GitHub",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Link your GitHub account to create repositories and automate app development.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onAuthenticate) {
                    Text("Connect GitHub")
                }
            }
        }
    }
}
