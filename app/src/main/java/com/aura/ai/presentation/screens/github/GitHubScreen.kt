package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aura.ai.presentation.theme.*

@Composable
fun GitHubScreen(viewModel: GitHubViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    when {
        state.showFileViewer -> FileViewerScreen(viewModel, state)
        state.showRepoDetail -> RepoDetailScreen(viewModel, state)
        else -> MainGitHubScreen(viewModel, state)
    }

    // Create Repo Dialog
    if (state.showCreateDialog) CreateRepoDialog(viewModel)
}

// ===== MAIN SCREEN =====
@Composable
fun MainGitHubScreen(viewModel: GitHubViewModel, state: GitHubState) {
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Fuchsia500.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Fuchsia500.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Code, null, tint = Fuchsia400, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("GITHUB NEXUS", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text("${state.repos.size} Repositories", style = MaterialTheme.typography.labelMedium, color = Fuchsia400)
            }
            IconButton(onClick = { viewModel.loadRepositories() }) { Icon(Icons.Default.Refresh, null, tint = Fuchsia400) }
            IconButton(onClick = { viewModel.showCreateDialog() }) { Icon(Icons.Default.Add, null, tint = Fuchsia400) }
        }

        // Status
        if (state.result.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = if (state.result.startsWith("✅")) Emerald500.copy(alpha = 0.15f) else Rose400.copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.result, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearResult() }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Repo list
        if (state.isLoading && state.repos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Fuchsia400) }
        } else if (state.repos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Code, null, tint = TextMuted, modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No repositories", color = TextMuted)
                    Text("Tap + to create one", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.repos) { repo -> RepoCard(repo) { viewModel.selectRepo(repo) } }
            }
        }
    }
}

@Composable
fun RepoCard(repo: GitHubRepo, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public, null, tint = if (repo.isPrivate) Amber400 else Emerald400, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                if (repo.description.isNotBlank()) Text(repo.description, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
    }
}

// ===== REPO DETAIL =====
@Composable
fun RepoDetailScreen(viewModel: GitHubViewModel, state: GitHubState) {
    val repo = state.selectedRepo ?: return
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.hideRepoDetail() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text(repo.fullName, style = MaterialTheme.typography.labelMedium, color = Fuchsia400)
            }
            IconButton(onClick = { viewModel.loadWorkflowRuns(repo.fullName) }) { Icon(Icons.Default.Refresh, null, tint = Fuchsia400) }
        }

        // Actions
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.triggerWorkflow(repo.fullName, 0) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Emerald500), shape = RoundedCornerShape(12.dp)) { Text("Run Workflow") }
            Button(onClick = { viewModel.loadFileContent(repo.fullName, "app/build.gradle.kts") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Cyan500), shape = RoundedCornerShape(12.dp)) { Text("View Files") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Workflow runs
        Text("Workflow Runs", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        if (state.isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Fuchsia400) } }
        else if (state.workflowRuns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No workflow runs", color = TextMuted) }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.workflowRuns) { run -> WorkflowRunCard(run) }
            }
        }
    }
}

@Composable
fun WorkflowRunCard(run: GitHubWorkflowRun) {
    val statusColor = when {
        run.conclusion == "success" -> Emerald500; run.conclusion == "failure" -> Rose400; run.status == "in_progress" -> Amber400; else -> TextMuted
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(run.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${run.status} · ${run.conclusion ?: "pending"} · ${run.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

// ===== FILE VIEWER =====
@Composable
fun FileViewerScreen(viewModel: GitHubViewModel, state: GitHubState) {
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.hideFileViewer() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text(state.filePath, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (state.selectedRepo != null) {
                IconButton(onClick = { viewModel.startEditFile(state.filePath, state.fileContent) }) { Icon(Icons.Default.Edit, null, tint = Cyan400) }
            }
        }

        if (state.isEditingFile && state.selectedRepo != null) {
            // Edit mode
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                OutlinedTextField(value = state.editFileContent, onValueChange = { viewModel.updateEditContent(it) },
                    modifier = Modifier.fillMaxSize().weight(1f), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan500, unfocusedBorderColor = CyberBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.updateFile(state.selectedRepo!!.fullName, state.filePath, state.editFileContent, "Update ${state.filePath}") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Emerald500)) { Text("Save & Commit") }
                    Button(onClick = { viewModel.cancelEditFile() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) { Text("Cancel") }
                }
            }
        } else {
            // View mode
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(state.fileContent, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.White))
            }
        }
    }
}

// ===== CREATE DIALOG =====
@Composable
fun CreateRepoDialog(viewModel: GitHubViewModel) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = { viewModel.hideCreateDialog() }, title = { Text("Create Repository", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Repository Name") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Fuchsia500, focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Fuchsia500, focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Private", color = Color.White); Spacer(modifier = Modifier.width(8.dp)); Switch(checked = isPrivate, onCheckedChange = { isPrivate = it }) }
            }
        },
        confirmButton = { Button(onClick = { viewModel.createRepository(name, description, isPrivate) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = { viewModel.hideCreateDialog() }) { Text("Cancel") } }, containerColor = Color(0xFF0F172A))
}
