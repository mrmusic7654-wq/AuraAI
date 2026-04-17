package com.aura.ai.presentation.screens.github

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.ai.data.models.github.GitHubWorkflowRun
import com.aura.ai.data.models.github.WorkflowConclusion
import com.aura.ai.data.models.github.WorkflowStatus
import com.aura.ai.presentation.components.AuraTopBar

@Composable
fun WorkflowMonitorScreen(
    repoName: String,
    workflowRuns: List<GitHubWorkflowRun>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AuraTopBar(
            title = "Workflows - $repoName",
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
        } else if (workflowRuns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workflow runs found")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(workflowRuns) { run ->
                    WorkflowStatusCard(run = run)
                }
            }
        }
    }
}

@Composable
fun WorkflowStatusCard(run: GitHubWorkflowRun) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(run.name, style = MaterialTheme.typography.titleMedium)
                Text("Status: ${run.status}", style = MaterialTheme.typography.bodySmall)
                run.conclusion?.let {
                    Text("Conclusion: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            when {
                run.status == WorkflowStatus.COMPLETED -> {
                    when (run.conclusion) {
                        WorkflowConclusion.SUCCESS -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        WorkflowConclusion.FAILURE -> Icon(
                            Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error
                        )
                        else -> Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Pending"
                        )
                    }
                }
                else -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
